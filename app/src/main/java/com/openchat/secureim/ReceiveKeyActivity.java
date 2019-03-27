package com.openchat.secureim;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.openchat.secureim.crypto.IdentityKeyParcelable;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.crypto.storage.OpenchatServiceIdentityKeyStore;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.EncryptingSmsDatabase;
import com.openchat.secureim.database.IdentityDatabase;
import com.openchat.secureim.database.PushDatabase;
import com.openchat.secureim.jobs.PushDecryptJob;
import com.openchat.secureim.jobs.SmsDecryptJob;
import com.openchat.secureim.recipients.Recipient;
import com.openchat.secureim.recipients.RecipientFactory;
import com.openchat.secureim.sms.IncomingIdentityUpdateMessage;
import com.openchat.secureim.sms.IncomingKeyExchangeMessage;
import com.openchat.secureim.sms.IncomingPreKeyBundleMessage;
import com.openchat.secureim.sms.IncomingTextMessage;
import com.openchat.secureim.util.Base64;
import com.openchat.secureim.util.MemoryCleaner;
import com.openchat.protocal.IdentityKey;
import com.openchat.protocal.InvalidKeyException;
import com.openchat.protocal.InvalidMessageException;
import com.openchat.protocal.InvalidVersionException;
import com.openchat.protocal.LegacyMessageException;
import com.openchat.protocal.protocol.KeyExchangeMessage;
import com.openchat.protocal.protocol.PreKeyOpenchatMessage;
import com.openchat.protocal.state.IdentityKeyStore;
import com.openchat.protocal.util.guava.Optional;
import com.openchat.imservice.api.messages.OpenchatServiceEnvelope;
import com.openchat.imservice.api.messages.OpenchatServiceGroup;

import java.io.IOException;

public class ReceiveKeyActivity extends Activity {

  private TextView descriptionText;

  private Button confirmButton;
  private Button cancelButton;

  private Recipient recipient;
  private int       recipientDeviceId;
  private long      messageId;

  private MasterSecret               masterSecret;
  private IncomingKeyExchangeMessage message;
  private IdentityKey                identityKey;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.receive_key_activity);

    initializeResources();

    try {
      initializeKey();
      initializeText();
    } catch (InvalidKeyException | InvalidVersionException | InvalidMessageException | LegacyMessageException ike) {
      Log.w("ReceiveKeyActivity", ike);
    }
    initializeListeners();
  }

  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  private void initializeText() {
    if (isTrusted(this.identityKey)) {
      initializeTrustedText();
    } else {
      initializeUntrustedText();
    }
  }

  private void initializeTrustedText() {
    descriptionText.setText(getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_trusted_but));
  }

  private void initializeUntrustedText() {
    SpannableString spannableString = new SpannableString(getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different) + " " +
                                                          getString(R.string.ReceiveKeyActivity_you_may_wish_to_verify_this_contact));
    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(ReceiveKeyActivity.this, VerifyIdentityActivity.class);
        intent.putExtra("recipient", recipient.getRecipientId());
        intent.putExtra("master_secret", masterSecret);
        intent.putExtra("remote_identity", new IdentityKeyParcelable(identityKey));
        startActivity(intent);
      }
    }, getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different).length() +1,
       spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    descriptionText.setText(spannableString);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private boolean isTrusted(IdentityKey identityKey) {
    long             recipientId      = recipient.getRecipientId();
    IdentityKeyStore identityKeyStore = new OpenchatServiceIdentityKeyStore(this, masterSecret);

    return identityKeyStore.isTrustedIdentity(recipientId, identityKey);
  }

  private void initializeKey()
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    IncomingTextMessage message = new IncomingTextMessage(recipient.getNumber(),
                                                          recipientDeviceId,
                                                          System.currentTimeMillis(),
                                                          getIntent().getStringExtra("body"),
                                                          Optional.<OpenchatServiceGroup>absent());

    if (getIntent().getBooleanExtra("is_bundle", false)) {
      this.message = new IncomingPreKeyBundleMessage(message, message.getMessageBody());
    } else if (getIntent().getBooleanExtra("is_identity_update", false)) {
      this.message = new IncomingIdentityUpdateMessage(message, message.getMessageBody());
    } else {
      this.message = new IncomingKeyExchangeMessage(message, message.getMessageBody());
    }

    this.identityKey = getIdentityKey(this.message);
  }

  private void initializeResources() {
    this.descriptionText      = (TextView) findViewById(R.id.description_text);
    this.confirmButton        = (Button)   findViewById(R.id.ok_button);
    this.cancelButton         = (Button)   findViewById(R.id.cancel_button);
    this.recipient            = RecipientFactory.getRecipientForId(this, getIntent().getLongExtra("recipient", -1), true);
    this.recipientDeviceId    = getIntent().getIntExtra("recipient_device_id", -1);
    this.messageId            = getIntent().getLongExtra("message_id", -1);
    this.masterSecret         = getIntent().getParcelableExtra("master_secret");
  }

  private void initializeListeners() {
    this.confirmButton.setOnClickListener(new OkListener());
    this.cancelButton.setOnClickListener(new CancelListener());
  }

  private IdentityKey getIdentityKey(IncomingKeyExchangeMessage message)
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    try {
      if (message.isIdentityUpdate()) {
        return new IdentityKey(Base64.decodeWithoutPadding(message.getMessageBody()), 0);
      } else if (message.isPreKeyBundle()) {
        boolean isPush = getIntent().getBooleanExtra("is_push", false);

        if (isPush) return new PreKeyOpenchatMessage(Base64.decode(message.getMessageBody())).getIdentityKey();
        else        return new PreKeyOpenchatMessage(Base64.decodeWithoutPadding(message.getMessageBody())).getIdentityKey();
      } else {
        return new KeyExchangeMessage(Base64.decodeWithoutPadding(message.getMessageBody())).getIdentityKey();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private class OkListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      new AsyncTask<Void, Void, Void> () {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
          dialog = ProgressDialog.show(ReceiveKeyActivity.this,
                                       getString(R.string.ReceiveKeyActivity_processing),
                                       getString(R.string.ReceiveKeyActivity_processing_key_exchange),
                                       true);
        }

        @Override
        protected Void doInBackground(Void... params) {
          Context               context          = ReceiveKeyActivity.this;
          IdentityDatabase      identityDatabase = DatabaseFactory.getIdentityDatabase(context);
          EncryptingSmsDatabase smsDatabase      = DatabaseFactory.getEncryptingSmsDatabase(context);
          PushDatabase          pushDatabase     = DatabaseFactory.getPushDatabase(context);

          identityDatabase.saveIdentity(masterSecret, recipient.getRecipientId(), identityKey);

          if (message.isIdentityUpdate()) {
            smsDatabase.markAsProcessedKeyExchange(messageId);
          } else {
            if (getIntent().getBooleanExtra("is_push", false)) {
              try {
                byte[]             body     = Base64.decode(message.getMessageBody());
                OpenchatServiceEnvelope envelope = new OpenchatServiceEnvelope(3, message.getSender(),
                                                                     message.getSenderDeviceId(), "",
                                                                     message.getSentTimestampMillis(),
                                                                     body);

                long pushId = pushDatabase.insert(envelope);

                ApplicationContext.getInstance(context)
                                  .getJobManager()
                                  .add(new PushDecryptJob(context, pushId));

                smsDatabase.deleteMessage(messageId);
              } catch (IOException e) {
                throw new AssertionError(e);
              }
            } else {
              ApplicationContext.getInstance(context)
                                .getJobManager()
                                .add(new SmsDecryptJob(context, messageId));
            }
          }

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          dialog.dismiss();
          finish();
        }
      }.execute();
    }
  }

  private class CancelListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      ReceiveKeyActivity.this.finish();
    }
  }
}
