package com.openchat.secureim.jobs;

import android.content.Context;
import android.util.Log;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.crypto.storage.OpenchatServiceOpenchatStore;
import com.openchat.secureim.jobs.requirements.MasterSecretRequirement;
import com.openchat.secureim.push.OpenchatServiceCommunicationFactory;
import com.openchat.secureim.util.VisibleForTesting;
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CleanPreKeysJob extends MasterSecretJob {

  private static final String TAG = CleanPreKeysJob.class.getSimpleName();

  private static final int ARCHIVE_AGE_DAYS = 15;

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
  public void onRun() throws RequirementNotMetException, IOException {
    try {
      MasterSecret             masterSecret      = getMasterSecret();
      SignedPreKeyStore        signedPreKeyStore = createSignedPreKeyStore(context, masterSecret);
      OpenchatServiceAccountManager accountManager    = createAccountManager(context);

      SignedPreKeyEntity currentSignedPreKey = accountManager.getSignedPreKey();

      if (currentSignedPreKey == null) return;

      SignedPreKeyRecord       currentRecord = signedPreKeyStore.loadSignedPreKey(currentSignedPreKey.getKeyId());
      List<SignedPreKeyRecord> allRecords    = signedPreKeyStore.loadSignedPreKeys();
      List<SignedPreKeyRecord> oldRecords    = removeRecordFrom(currentRecord, allRecords);

      Collections.sort(oldRecords, new SignedPreKeySorter());

      Log.w(TAG, "Old signed prekey record count: " + oldRecords.size());

      if (oldRecords.size() < 2) {
        return;
      }

      SignedPreKeyRecord latestRecord                = oldRecords.get(0);
      long               latestRecordArchiveDuration = System.currentTimeMillis() - latestRecord.getTimestamp();

      if (latestRecordArchiveDuration >= TimeUnit.DAYS.toMillis(ARCHIVE_AGE_DAYS)) {
        Iterator<SignedPreKeyRecord> iterator = oldRecords.iterator();
        iterator.next();

        while (iterator.hasNext()) {
          SignedPreKeyRecord expiredRecord = iterator.next();
          Log.w(TAG, "Removing signed prekey record: " + expiredRecord.getId() + " with timestamp: " + expiredRecord.getTimestamp());

          signedPreKeyStore.removeSignedPreKey(expiredRecord.getId());
        }
      }
    } catch (InvalidKeyIdException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof RequirementNotMetException)         return true;
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    if (throwable instanceof PushNetworkException)               return true;
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to execute clean signed prekeys task.");
  }

  private List<SignedPreKeyRecord> removeRecordFrom(SignedPreKeyRecord currentRecord,
                                                    List<SignedPreKeyRecord> records)

  {
    List<SignedPreKeyRecord> others = new LinkedList<>();

    for (SignedPreKeyRecord record : records) {
      if (record.getId() != currentRecord.getId()) {
        others.add(record);
      }
    }

    return others;
  }

  @VisibleForTesting
  protected OpenchatServiceAccountManager createAccountManager(Context context) {
    return OpenchatServiceCommunicationFactory.createManager(context);
  }

  protected SignedPreKeyStore createSignedPreKeyStore(Context context, MasterSecret masterSecret) {
    return new OpenchatServiceOpenchatStore(context, masterSecret);
  }

  private static class SignedPreKeySorter implements Comparator<SignedPreKeyRecord> {
    @Override
    public int compare(SignedPreKeyRecord lhs, SignedPreKeyRecord rhs) {
      if      (lhs.getTimestamp() < rhs.getTimestamp()) return -1;
      else if (lhs.getTimestamp() > rhs.getTimestamp()) return 1;
      else                                              return 0;
    }
  }

}
