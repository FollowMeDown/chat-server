package com.openchat.secureim.jobs;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.crypto.MmsCipher;
import com.openchat.secureim.crypto.storage.OpenchatServiceOpenchatStore;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.MmsDatabase;
import com.openchat.secureim.database.NoSuchMessageException;
import com.openchat.secureim.jobs.requirements.MasterSecretRequirement;
import com.openchat.secureim.mms.ApnUnavailableException;
import com.openchat.secureim.mms.MediaConstraints;
import com.openchat.secureim.mms.MmsRadio;
import com.openchat.secureim.mms.MmsRadioException;
import com.openchat.secureim.mms.MmsSendResult;
import com.openchat.secureim.mms.OutgoingMmsConnection;
import com.openchat.secureim.notifications.MessageNotifier;
import com.openchat.secureim.recipients.RecipientFormattingException;
import com.openchat.secureim.recipients.Recipients;
import com.openchat.secureim.transport.InsecureFallbackApprovalException;
import com.openchat.secureim.transport.UndeliverableMessageException;
import com.openchat.secureim.util.Hex;
import com.openchat.secureim.util.NumberUtil;
import com.openchat.secureim.util.TelephonyUtil;
import com.openchat.jobqueue.JobParameters;
import com.openchat.jobqueue.requirements.NetworkRequirement;
import com.openchat.protocal.NoSessionException;

import java.io.IOException;
import java.util.Arrays;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsSendJob extends SendJob {

  private static final String TAG = MmsSendJob.class.getSimpleName();

  private final long messageId;

  public MmsSendJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("mms-operation")
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withPersistence()
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onSend(MasterSecret masterSecret) throws MmsException, NoSuchMessageException, IOException {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      MmsSendResult result = deliver(masterSecret, message);

      if (result.isUpgradedSecure()) {
        database.markAsSecure(messageId);
      }

      database.markAsSent(messageId, result.getMessageId(), result.getResponseStatus());
    } catch (UndeliverableMessageException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (InsecureFallbackApprovalException e) {
      Log.w(TAG, e);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  public MmsSendResult deliver(MasterSecret masterSecret, SendReq message)
      throws UndeliverableMessageException, InsecureFallbackApprovalException
  {

    validateDestinations(message);

    MmsRadio radio = MmsRadio.getInstance(context);

    try {
      if (isCdmaDevice()) {
        Log.w(TAG, "Sending MMS directly without radio change...");
        try {
          return sendMms(masterSecret, radio, message, false, false);
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      Log.w(TAG, "Sending MMS with radio change and proxy...");
      radio.connect();

      try {
        try {
          return sendMms(masterSecret, radio, message, true, true);
        } catch (IOException e) {
          Log.w(TAG, e);
        }

        Log.w(TAG, "Sending MMS with radio change and without proxy...");

        try {
          return sendMms(masterSecret, radio, message, true, false);
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          throw new UndeliverableMessageException(ioe);
        }
      } finally {
        radio.disconnect();
      }

    } catch (MmsRadioException mre) {
      Log.w(TAG, mre);
      throw new UndeliverableMessageException(mre);
    }
  }

  private MmsSendResult sendMms(MasterSecret masterSecret, MmsRadio radio, SendReq message,
                                boolean usingMmsRadio, boolean useProxy)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {
    String  number         = TelephonyUtil.getManager(context).getLine1Number();
    boolean upgradedSecure = false;

    prepareMessageMedia(masterSecret, message, MediaConstraints.MMS_CONSTRAINTS, true);

    if (MmsDatabase.Types.isSecureType(message.getDatabaseMessageBox())) {
      message        = getEncryptedMessage(masterSecret, message);
      upgradedSecure = true;
    }

    if (number != null && number.trim().length() != 0) {
      message.setFrom(new EncodedStringValue(number));
    }

    try {
      byte[] pdu = new PduComposer(context, message).make();

      if (pdu == null) {
        throw new UndeliverableMessageException("PDU composition failed, null payload");
      }

      OutgoingMmsConnection connection = new OutgoingMmsConnection(context, radio.getApnInformation(), pdu);
      SendConf              conf       = connection.send(usingMmsRadio, useProxy);

      if (conf == null) {
        throw new UndeliverableMessageException("No M-Send.conf received in response to send.");
      } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
        throw new UndeliverableMessageException("Got bad response: " + conf.getResponseStatus());
      } else if (isInconsistentResponse(message, conf)) {
        throw new UndeliverableMessageException("Mismatched response!");
      } else {
        return new MmsSendResult(conf.getMessageId(), conf.getResponseStatus(), upgradedSecure, false);
      }
    } catch (ApnUnavailableException aue) {
      throw new IOException("no APN was retrievable");
    }
  }

  private SendReq getEncryptedMessage(MasterSecret masterSecret, SendReq pdu)
      throws InsecureFallbackApprovalException, UndeliverableMessageException
  {
    try {
      MmsCipher cipher = new MmsCipher(new OpenchatServiceOpenchatStore(context, masterSecret));
      return cipher.encrypt(context, pdu);
    } catch (NoSessionException e) {
      throw new InsecureFallbackApprovalException(e);
    } catch (RecipientFormattingException e) {
      throw new AssertionError(e);
    }
  }

  private boolean isInconsistentResponse(SendReq message, SendConf response) {
    Log.w(TAG, "Comparing: " + Hex.toString(message.getTransactionId()));
    Log.w(TAG, "With:      " + Hex.toString(response.getTransactionId()));
    return !Arrays.equals(message.getTransactionId(), response.getTransactionId());
  }

  private boolean isCdmaDevice() {
    return ((TelephonyManager)context
        .getSystemService(Context.TELEPHONY_SERVICE))
        .getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
  }

  private void validateDestination(EncodedStringValue destination) throws UndeliverableMessageException {
    if (destination == null || !NumberUtil.isValidSmsOrEmail(destination.getString())) {
      throw new UndeliverableMessageException("Invalid destination: " +
                                                  (destination == null ? null : destination.getString()));
    }
  }

  private void validateDestinations(SendReq message) throws UndeliverableMessageException {
    if (message.getTo() != null) {
      for (EncodedStringValue to : message.getTo()) {
        validateDestination(to);
      }
    }

    if (message.getCc() != null) {
      for (EncodedStringValue cc : message.getCc()) {
        validateDestination(cc);
      }
    }

    if (message.getBcc() != null) {
      for (EncodedStringValue bcc : message.getBcc()) {
        validateDestination(bcc);
      }
    }

    if (message.getTo() == null && message.getCc() == null && message.getBcc() == null) {
      throw new UndeliverableMessageException("No to, cc, or bcc specified!");
    }
  }

  private void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    if (recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }

}
