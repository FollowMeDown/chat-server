package com.openchat.secureim.jobs;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.openchat.secureim.crypto.MasterCipher;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.PartDatabase;
import com.openchat.secureim.dependencies.InjectableType;
import com.openchat.secureim.jobs.requirements.MasterSecretRequirement;
import com.openchat.secureim.util.Base64;
import com.openchat.secureim.util.Util;
import com.openchat.jobqueue.JobParameters;
import com.openchat.jobqueue.requirements.NetworkRequirement;
import com.openchat.protocal.InvalidMessageException;
import com.openchat.imservice.api.OpenchatServiceMessageReceiver;
import com.openchat.imservice.api.messages.OpenchatServiceAttachmentPointer;
import com.openchat.imservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import com.openchat.imservice.api.push.exceptions.PushNetworkException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;

public class AttachmentDownloadJob extends MasterSecretJob implements InjectableType {

  private static final String TAG = AttachmentDownloadJob.class.getSimpleName();

  @Inject transient OpenchatServiceMessageReceiver messageReceiver;

  private final long messageId;

  public AttachmentDownloadJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    PartDatabase database = DatabaseFactory.getPartDatabase(context);

    Log.w(TAG, "Downloading push parts for: " + messageId);

    List<Pair<Long, PduPart>> parts = database.getParts(messageId);

    for (Pair<Long, PduPart> partPair : parts) {
      retrievePart(masterSecret, partPair.second, messageId, partPair.first);
      Log.w(TAG, "Got part: " + partPair.first);
    }
  }

  @Override
  public void onCanceled() {
    PartDatabase              database = DatabaseFactory.getPartDatabase(context);
    List<Pair<Long, PduPart>> parts    = database.getParts(messageId);

    for (Pair<Long, PduPart> partPair : parts) {
      markFailed(messageId, partPair.second, partPair.first);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return (exception instanceof PushNetworkException);
  }

  private void retrievePart(MasterSecret masterSecret, PduPart part, long messageId, long partId)
      throws IOException
  {
    PartDatabase database       = DatabaseFactory.getPartDatabase(context);
    File         attachmentFile = null;

    try {
      attachmentFile = createTempFile();

      OpenchatServiceAttachmentPointer pointer    = createAttachmentPointer(masterSecret, part);
      InputStream                 attachment = messageReceiver.retrieveAttachment(pointer, attachmentFile);

      database.updateDownloadedPart(masterSecret, messageId, partId, part, attachment);
    } catch (InvalidPartException | NonSuccessfulResponseCodeException | InvalidMessageException | MmsException e) {
      Log.w(TAG, e);
      markFailed(messageId, part, partId);
    } finally {
      if (attachmentFile != null)
        attachmentFile.delete();
    }
  }

  private OpenchatServiceAttachmentPointer createAttachmentPointer(MasterSecret masterSecret, PduPart part)
      throws InvalidPartException
  {
    try {
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      long         id           = Long.parseLong(Util.toIsoString(part.getContentLocation()));
      byte[]       key          = masterCipher.decryptBytes(Base64.decode(Util.toIsoString(part.getContentDisposition())));
      String       relay        = null;

      if (part.getName() != null) {
        relay = Util.toIsoString(part.getName());
      }

      return new OpenchatServiceAttachmentPointer(id, null, key, relay);
    } catch (InvalidMessageException | IOException e) {
      Log.w(TAG, e);
      throw new InvalidPartException(e);
    }
  }

  private File createTempFile() throws InvalidPartException {
    try {
      File file = File.createTempFile("push-attachment", "tmp");
      file.deleteOnExit();

      return file;
    } catch (IOException e) {
      throw new InvalidPartException(e);
    }
  }

  private void markFailed(long messageId, PduPart part, long partId) {
    try {
      PartDatabase database = DatabaseFactory.getPartDatabase(context);
      database.updateFailedDownloadedPart(messageId, partId, part);
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private class InvalidPartException extends Exception {
    public InvalidPartException(Exception e) {super(e);}
  }
}
