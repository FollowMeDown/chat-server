package com.openchat.secureim.jobs;

import android.content.Context;
import android.util.Log;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.dependencies.InjectableType;
import com.openchat.secureim.jobs.requirements.MasterSecretRequirement;
import com.openchat.jobqueue.JobParameters;
import com.openchat.protocal.InvalidKeyIdException;
import com.openchat.protocal.state.SignedPreKeyRecord;
import com.openchat.protocal.state.SignedPreKeyStore;
import com.openchat.imservice.api.OpenchatServiceAccountManager;
import com.openchat.imservice.push.SignedPreKeyEntity;
import com.openchat.imservice.push.exceptions.NonSuccessfulResponseCodeException;
import com.openchat.imservice.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.openchat.secureim.dependencies.OpenchatStorageModule.SignedPreKeyStoreFactory;

public class CleanPreKeysJob extends MasterSecretJob implements InjectableType {

  private static final String TAG = CleanPreKeysJob.class.getSimpleName();

  private static final int ARCHIVE_AGE_DAYS = 15;

  @Inject transient OpenchatServiceAccountManager accountManager;
  @Inject transient SignedPreKeyStoreFactory signedPreKeyStoreFactory;

  public CleanPreKeysJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(CleanPreKeysJob.class.getSimpleName())
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRetryCount(5)
                                .create());
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    try {
      SignedPreKeyStore  signedPreKeyStore   = signedPreKeyStoreFactory.create(masterSecret);
      SignedPreKeyEntity currentSignedPreKey = accountManager.getSignedPreKey();

      if (currentSignedPreKey == null) return;

      SignedPreKeyRecord             currentRecord = signedPreKeyStore.loadSignedPreKey(currentSignedPreKey.getKeyId());
      List<SignedPreKeyRecord>       allRecords    = signedPreKeyStore.loadSignedPreKeys();
      LinkedList<SignedPreKeyRecord> oldRecords    = removeRecordFrom(currentRecord, allRecords);

      Collections.sort(oldRecords, new SignedPreKeySorter());

      Log.w(TAG, "Old signed prekey record count: " + oldRecords.size());

      boolean foundAgedRecord = false;

      for (SignedPreKeyRecord oldRecord : oldRecords) {
        long archiveDuration = System.currentTimeMillis() - oldRecord.getTimestamp();

        if (archiveDuration >= TimeUnit.DAYS.toMillis(ARCHIVE_AGE_DAYS)) {
          if (!foundAgedRecord) {
            foundAgedRecord = true;
          } else {
            Log.w(TAG, "Removing signed prekey record: " + oldRecord.getId() + " with timestamp: " + oldRecord.getTimestamp());
            signedPreKeyStore.removeSignedPreKey(oldRecord.getId());
          }
        }
      }
    } catch (InvalidKeyIdException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Throwable throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    if (throwable instanceof PushNetworkException)               return true;
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to execute clean signed prekeys task.");
  }

  private LinkedList<SignedPreKeyRecord> removeRecordFrom(SignedPreKeyRecord currentRecord,
                                                          List<SignedPreKeyRecord> records)

  {
    LinkedList<SignedPreKeyRecord> others = new LinkedList<>();

    for (SignedPreKeyRecord record : records) {
      if (record.getId() != currentRecord.getId()) {
        others.add(record);
      }
    }

    return others;
  }

  private static class SignedPreKeySorter implements Comparator<SignedPreKeyRecord> {
    @Override
    public int compare(SignedPreKeyRecord lhs, SignedPreKeyRecord rhs) {
      if      (lhs.getTimestamp() > rhs.getTimestamp()) return -1;
      else if (lhs.getTimestamp() < rhs.getTimestamp()) return 1;
      else                                              return 0;
    }
  }

}
