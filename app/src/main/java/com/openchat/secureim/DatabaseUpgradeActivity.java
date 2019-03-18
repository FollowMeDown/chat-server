package com.openchat.secureim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;

import com.openchat.secureim.crypto.IdentityKeyUtil;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.EncryptingSmsDatabase;
import com.openchat.secureim.database.PushDatabase;
import com.openchat.secureim.database.SmsDatabase;
import com.openchat.secureim.database.model.SmsMessageRecord;
import com.openchat.secureim.jobs.CreateSignedPreKeyJob;
import com.openchat.secureim.jobs.PushDecryptJob;
import com.openchat.secureim.jobs.SmsDecryptJob;
import com.openchat.secureim.notifications.MessageNotifier;
import com.openchat.secureim.util.ParcelUtil;
import com.openchat.secureim.util.Util;
import com.openchat.secureim.util.VersionTracker;
import com.openchat.jobqueue.EncryptionKeys;
import com.openchat.imservice.api.messages.OpenchatServiceEnvelope;

import java.io.File;
import java.util.SortedSet;
import java.util.TreeSet;

public class DatabaseUpgradeActivity extends Activity {

  public static final int NO_MORE_KEY_EXCHANGE_PREFIX_VERSION  = 46;
  public static final int MMS_BODY_VERSION                     = 46;
  public static final int TOFU_IDENTITIES_VERSION              = 50;
  public static final int CURVE25519_VERSION                   = 63;
  public static final int ASYMMETRIC_MASTER_SECRET_FIX_VERSION = 73;
  public static final int NO_V1_VERSION                        = 83;
  public static final int SIGNED_PREKEY_VERSION                = 83;
  public static final int NO_DECRYPT_QUEUE_VERSION             = 84;

  private static final SortedSet<Integer> UPGRADE_VERSIONS = new TreeSet<Integer>() {{
    add(NO_MORE_KEY_EXCHANGE_PREFIX_VERSION);
    add(TOFU_IDENTITIES_VERSION);
    add(CURVE25519_VERSION);
    add(ASYMMETRIC_MASTER_SECRET_FIX_VERSION);
    add(NO_V1_VERSION);
    add(SIGNED_PREKEY_VERSION);
    add(NO_DECRYPT_QUEUE_VERSION);
  }};

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = getIntent().getParcelableExtra("master_secret");

    if (needsUpgradeTask()) {
      Log.w("DatabaseUpgradeActivity", "Upgrading...");
      setContentView(R.layout.database_upgrade_activity);

      ProgressBar indeterminateProgress = (ProgressBar)findViewById(R.id.indeterminate_progress);
      ProgressBar determinateProgress   = (ProgressBar)findViewById(R.id.determinate_progress);

      new DatabaseUpgradeTask(indeterminateProgress, determinateProgress)
          .execute(VersionTracker.getLastSeenVersion(this));
    } else {
      VersionTracker.updateLastSeenVersion(this);
      ApplicationContext.getInstance(this)
                        .getJobManager()
                        .setEncryptionKeys(new EncryptionKeys(ParcelUtil.serialize(masterSecret)));
      MessageNotifier.updateNotification(DatabaseUpgradeActivity.this, masterSecret);
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }
  }

  private boolean needsUpgradeTask() {
    int currentVersionCode = Util.getCurrentApkReleaseVersion(this);
    int lastSeenVersion    = VersionTracker.getLastSeenVersion(this);

    Log.w("DatabaseUpgradeActivity", "LastSeenVersion: " + lastSeenVersion);

    if (lastSeenVersion >= currentVersionCode)
      return false;

    for (int version : UPGRADE_VERSIONS) {
      Log.w("DatabaseUpgradeActivity", "Comparing: " + version);
      if (lastSeenVersion < version)
        return true;
    }

    return false;
  }

  public static boolean isUpdate(Context context) {
    try {
      int currentVersionCode  = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
      int previousVersionCode = VersionTracker.getLastSeenVersion(context);

      return previousVersionCode < currentVersionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  public interface DatabaseUpgradeListener {
    public void setProgress(int progress, int total);
  }

  private class DatabaseUpgradeTask extends AsyncTask<Integer, Double, Void>
      implements DatabaseUpgradeListener
  {

    private final ProgressBar indeterminateProgress;
    private final ProgressBar determinateProgress;

    public DatabaseUpgradeTask(ProgressBar indeterminateProgress, ProgressBar determinateProgress) {
      this.indeterminateProgress = indeterminateProgress;
      this.determinateProgress   = determinateProgress;
    }

    @Override
    protected Void doInBackground(Integer... params) {
      Context context = DatabaseUpgradeActivity.this.getApplicationContext();

      Log.w("DatabaseUpgradeActivity", "Running background upgrade..");
      DatabaseFactory.getInstance(DatabaseUpgradeActivity.this)
                     .onApplicationLevelUpgrade(context, masterSecret, params[0], this);

      if (params[0] < CURVE25519_VERSION) {
        if (!IdentityKeyUtil.hasCurve25519IdentityKeys(context)) {
          IdentityKeyUtil.generateCurve25519IdentityKeys(context, masterSecret);
        }
      }

      if (params[0] < NO_V1_VERSION) {
        File v1sessions = new File(context.getFilesDir(), "sessions");

        if (v1sessions.exists() && v1sessions.isDirectory()) {
          File[] contents = v1sessions.listFiles();

          if (contents != null) {
            for (File session : contents) {
              session.delete();
            }
          }

          v1sessions.delete();
        }
      }

      if (params[0] < SIGNED_PREKEY_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new CreateSignedPreKeyJob(context, masterSecret));
      }

      if (params[0] < NO_DECRYPT_QUEUE_VERSION) {
        EncryptingSmsDatabase smsDatabase  = DatabaseFactory.getEncryptingSmsDatabase(getApplicationContext());
        PushDatabase          pushDatabase = DatabaseFactory.getPushDatabase(getApplicationContext());

        SmsDatabase.Reader smsReader  = null;
        Cursor             pushReader = null;

        SmsMessageRecord record;

        try {
          smsReader = smsDatabase.getDecryptInProgressMessages(masterSecret);

          while ((record = smsReader.getNext()) != null) {
            ApplicationContext.getInstance(getApplicationContext())
                              .getJobManager()
                              .add(new SmsDecryptJob(getApplicationContext(), record.getId()));
          }
        } finally {
          if (smsReader != null)
            smsReader.close();
        }

        try {
          pushReader = pushDatabase.getPending();

          while ((pushReader != null && pushReader.moveToNext())) {
            ApplicationContext.getInstance(getApplicationContext())
                .getJobManager()
                .add(new PushDecryptJob(getApplicationContext(), pushReader.getLong(pushReader.getColumnIndexOrThrow(PushDatabase.ID))));
          }
        } finally {
          if (pushReader != null)
            pushReader.close();
        }
      }

      return null;
    }

    @Override
    protected void onProgressUpdate(Double... update) {
      indeterminateProgress.setVisibility(View.GONE);
      determinateProgress.setVisibility(View.VISIBLE);

      double scaler = update[0];
      determinateProgress.setProgress((int)Math.floor(determinateProgress.getMax() * scaler));
    }

    @Override
    protected void onPostExecute(Void result) {
      VersionTracker.updateLastSeenVersion(DatabaseUpgradeActivity.this);
      ApplicationContext.getInstance(DatabaseUpgradeActivity.this)
                        .getJobManager()
                        .setEncryptionKeys(new EncryptionKeys(ParcelUtil.serialize(masterSecret)));

      MessageNotifier.updateNotification(DatabaseUpgradeActivity.this, masterSecret);

      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }

    @Override
    public void setProgress(int progress, int total) {
      publishProgress(((double)progress / (double)total));
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && "LGE".equalsIgnoreCase(Build.BRAND)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && "LGE".equalsIgnoreCase(Build.BRAND)) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }
}
