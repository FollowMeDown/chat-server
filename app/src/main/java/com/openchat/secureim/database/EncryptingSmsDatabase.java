package com.openchat.secureim.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import com.openchat.secureim.R;
import com.openchat.secureim.crypto.AsymmetricMasterCipher;
import com.openchat.secureim.crypto.AsymmetricMasterSecret;
import com.openchat.secureim.crypto.MasterSecretUnion;
import com.openchat.secureim.crypto.MasterSecretUtil;
import com.openchat.secureim.database.model.DisplayRecord;
import com.openchat.secureim.database.model.SmsMessageRecord;
import com.openchat.secureim.sms.IncomingTextMessage;
import com.openchat.secureim.sms.OutgoingTextMessage;
import com.openchat.secureim.util.LRUCache;
import com.openchat.protocal.InvalidMessageException;
import com.openchat.secureim.crypto.MasterCipher;
import com.openchat.secureim.crypto.MasterSecret;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EncryptingSmsDatabase extends SmsDatabase {

  private final PlaintextCache plaintextCache = new PlaintextCache();

  public EncryptingSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private String getAsymmetricEncryptedBody(AsymmetricMasterSecret masterSecret, String body) {
    AsymmetricMasterCipher bodyCipher = new AsymmetricMasterCipher(masterSecret);
    return bodyCipher.encryptBody(body);
  }

  private String getEncryptedBody(MasterSecret masterSecret, String body) {
    MasterCipher bodyCipher = new MasterCipher(masterSecret);
    String ciphertext       = bodyCipher.encryptBody(body);
    plaintextCache.put(ciphertext, body);

    return ciphertext;
  }

  public long insertMessageOutbox(MasterSecretUnion masterSecret, long threadId,
                                  OutgoingTextMessage message, boolean forceSms,
                                  long timestamp)
  {
    long type = Types.BASE_OUTBOX_TYPE;

    if (masterSecret.getMasterSecret().isPresent()) {
      message = message.withBody(getEncryptedBody(masterSecret.getMasterSecret().get(), message.getMessageBody()));
      type   |= Types.ENCRYPTION_SYMMETRIC_BIT;
    } else {
      message = message.withBody(getAsymmetricEncryptedBody(masterSecret.getAsymmetricMasterSecret().get(), message.getMessageBody()));
      type   |= Types.ENCRYPTION_ASYMMETRIC_BIT;
    }

    return insertMessageOutbox(threadId, message, type, forceSms, timestamp);
  }

  public Pair<Long, Long> insertMessageInbox(@NonNull MasterSecretUnion masterSecret,
                                             @NonNull IncomingTextMessage message)
  {
    if (masterSecret.getMasterSecret().isPresent()) {
      return insertMessageInbox(masterSecret.getMasterSecret().get(), message);
    } else {
      return insertMessageInbox(masterSecret.getAsymmetricMasterSecret().get(), message);
    }
  }

  private Pair<Long, Long> insertMessageInbox(@NonNull MasterSecret masterSecret,
                                              @NonNull IncomingTextMessage message)
  {
    long type = Types.BASE_INBOX_TYPE | Types.ENCRYPTION_SYMMETRIC_BIT;

    message = message.withMessageBody(getEncryptedBody(masterSecret, message.getMessageBody()));

    return insertMessageInbox(message, type);
  }

  private Pair<Long, Long> insertMessageInbox(@NonNull AsymmetricMasterSecret masterSecret,
                                              @NonNull IncomingTextMessage message)
  {
    long type = Types.BASE_INBOX_TYPE | Types.ENCRYPTION_ASYMMETRIC_BIT;

    message = message.withMessageBody(getAsymmetricEncryptedBody(masterSecret, message.getMessageBody()));

    return insertMessageInbox(message, type);
  }

  public Pair<Long, Long> updateBundleMessageBody(MasterSecretUnion masterSecret, long messageId, String body) {
    long type = Types.BASE_INBOX_TYPE | Types.SECURE_MESSAGE_BIT;
    String encryptedBody;

    if (masterSecret.getMasterSecret().isPresent()) {
      encryptedBody = getEncryptedBody(masterSecret.getMasterSecret().get(), body);
      type         |= Types.ENCRYPTION_SYMMETRIC_BIT;
    } else {
      encryptedBody = getAsymmetricEncryptedBody(masterSecret.getAsymmetricMasterSecret().get(), body);
      type         |= Types.ENCRYPTION_ASYMMETRIC_BIT;
    }

    return updateMessageBodyAndType(messageId, encryptedBody, Types.TOTAL_MASK, type);
  }

  public void updateMessageBody(MasterSecretUnion masterSecret, long messageId, String body) {
    long type;

    if (masterSecret.getMasterSecret().isPresent()) {
      body = getEncryptedBody(masterSecret.getMasterSecret().get(), body);
      type = Types.ENCRYPTION_SYMMETRIC_BIT;
    } else {
      body = getAsymmetricEncryptedBody(masterSecret.getAsymmetricMasterSecret().get(), body);
      type = Types.ENCRYPTION_ASYMMETRIC_BIT;
    }

    updateMessageBodyAndType(messageId, body, Types.ENCRYPTION_MASK, type);
  }

  public Reader getMessages(MasterSecret masterSecret, int skip, int limit) {
    Cursor cursor = super.getMessages(skip, limit);
    return new DecryptingReader(masterSecret, cursor);
  }

  public Reader getOutgoingMessages(MasterSecret masterSecret) {
    Cursor cursor = super.getOutgoingMessages();
    return new DecryptingReader(masterSecret, cursor);
  }

  public SmsMessageRecord getMessage(MasterSecret masterSecret, long messageId) throws NoSuchMessageException {
    Cursor           cursor = super.getMessage(messageId);
    DecryptingReader reader = new DecryptingReader(masterSecret, cursor);
    SmsMessageRecord record = reader.getNext();

    reader.close();

    if (record == null) throw new NoSuchMessageException("No message for ID: " + messageId);
    else                return record;
  }

  public Reader getDecryptInProgressMessages(MasterSecret masterSecret) {
    Cursor cursor = super.getDecryptInProgressMessages();
    return new DecryptingReader(masterSecret, cursor);
  }

  public Reader readerFor(MasterSecret masterSecret, Cursor cursor) {
    return new DecryptingReader(masterSecret, cursor);
  }

  public class DecryptingReader extends SmsDatabase.Reader {

    private final MasterCipher masterCipher;

    public DecryptingReader(MasterSecret masterSecret, Cursor cursor) {
      super(cursor);
      this.masterCipher = new MasterCipher(masterSecret);
    }

    @Override
    protected DisplayRecord.Body getBody(Cursor cursor) {
      long type         = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
      String ciphertext = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));

      if (ciphertext == null) {
        return new DisplayRecord.Body("", true);
      }

      try {
        if (SmsDatabase.Types.isSymmetricEncryption(type)) {
          String plaintext = plaintextCache.get(ciphertext);

          if (plaintext != null)
            return new DisplayRecord.Body(plaintext, true);

          plaintext = masterCipher.decryptBody(ciphertext);

          plaintextCache.put(ciphertext, plaintext);
          return new DisplayRecord.Body(plaintext, true);
        } else {
          return new DisplayRecord.Body(ciphertext, true);
        }
      } catch (InvalidMessageException e) {
        Log.w("EncryptingSmsDatabase", e);
        return new DisplayRecord.Body(context.getString(R.string.EncryptingSmsDatabase_error_decrypting_message), true);
      }
    }
  }

  private static class PlaintextCache {
    private static final int MAX_CACHE_SIZE = 2000;
    private static final Map<String, SoftReference<String>> decryptedBodyCache =
        Collections.synchronizedMap(new LRUCache<String, SoftReference<String>>(MAX_CACHE_SIZE));

    public void put(String ciphertext, String plaintext) {
      decryptedBodyCache.put(ciphertext, new SoftReference<String>(plaintext));
    }

    public String get(String ciphertext) {
      SoftReference<String> plaintextReference = decryptedBodyCache.get(ciphertext);

      if (plaintextReference != null) {
        String plaintext = plaintextReference.get();

        if (plaintext != null) {
          return plaintext;
        }
      }

      return null;
    }
  }
}
