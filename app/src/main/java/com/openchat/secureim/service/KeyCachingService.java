package com.openchat.secureim.service;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.openchat.secureim.ApplicationContext;
import com.openchat.secureim.DatabaseUpgradeActivity;
import com.openchat.secureim.DummyActivity;
import com.openchat.secureim.R;
import com.openchat.secureim.RoutingActivity;
import com.openchat.secureim.crypto.InvalidPassphraseException;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.crypto.MasterSecretUtil;
import com.openchat.secureim.notifications.MessageNotifier;
import com.openchat.secureim.util.DynamicLanguage;
import com.openchat.secureim.util.ParcelUtil;
import com.openchat.secureim.util.OpenchatServicePreferences;
import com.openchat.jobqueue.EncryptionKeys;

import java.util.concurrent.TimeUnit;

public class KeyCachingService extends Service {

  public static final int SERVICE_RUNNING_ID = 4141;

  public  static final String KEY_PERMISSION           = "com.openchat.secureim.ACCESS_SECRETS";
  public  static final String NEW_KEY_EVENT            = "com.openchat.secureim.service.action.NEW_KEY_EVENT";
  public  static final String CLEAR_KEY_EVENT          = "com.openchat.secureim.service.action.CLEAR_KEY_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = "com.openchat.secureim.service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = "com.openchat.secureim.service.action.CLEAR_KEY";
  public  static final String DISABLE_ACTION           = "com.openchat.secureim.service.action.DISABLE";
  public  static final String ACTIVITY_START_EVENT     = "com.openchat.secureim.service.action.ACTIVITY_START_EVENT";
  public  static final String ACTIVITY_STOP_EVENT      = "com.openchat.secureim.service.action.ACTIVITY_STOP_EVENT";
  public  static final String LOCALE_CHANGE_EVENT      = "com.openchat.secureim.service.action.LOCALE_CHANGE_EVENT";

  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private PendingIntent pending;
  private int activitiesRunning = 0;
  private final IBinder binder  = new KeySetBinder();

  private static MasterSecret masterSecret;

  public KeyCachingService() {}

  public static synchronized MasterSecret getMasterSecret(Context context) {
    if (masterSecret == null && OpenchatServicePreferences.isPasswordDisabled(context)) {
      try {
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
        Intent       intent       = new Intent(context, KeyCachingService.class);

        context.startService(intent);

        return masterSecret;
      } catch (InvalidPassphraseException e) {
        Log.w("KeyCachingService", e);
      }
    }

    return masterSecret;
  }

  public void setMasterSecret(final MasterSecret masterSecret) {
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret = masterSecret;

      foregroundService();
      broadcastNewSecret();
      startTimeoutIfAppropriate();

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          if (!DatabaseUpgradeActivity.isUpdate(KeyCachingService.this)) {
            ApplicationContext.getInstance(KeyCachingService.this)
                              .getJobManager()
                              .setEncryptionKeys(new EncryptionKeys(ParcelUtil.serialize(masterSecret)));
            MessageNotifier.updateNotification(KeyCachingService.this, masterSecret);
          }
          return null;
        }
      }.execute();
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION:         handleClearKey();        break;
        case ACTIVITY_START_EVENT:     handleActivityStarted(); break;
        case ACTIVITY_STOP_EVENT:      handleActivityStopped(); break;
        case PASSPHRASE_EXPIRED_EVENT: handleClearKey();        break;
        case DISABLE_ACTION:           handleDisableService();  break;
        case LOCALE_CHANGE_EVENT:      handleLocaleChanged();   break;
      }
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    this.pending = PendingIntent.getService(this, 0, new Intent(PASSPHRASE_EXPIRED_EVENT, null,
                                                                this, KeyCachingService.class), 0);

    if (OpenchatServicePreferences.isPasswordDisabled(this)) {
      try {
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException e) {
        Log.w("KeyCachingService", e);
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w("KeyCachingService", "KCS Is Being Destroyed!");
    handleClearKey();
  }

  
  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Intent intent = new Intent(this, DummyActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  private void handleActivityStarted() {
    Log.w("KeyCachingService", "Incrementing activity count...");

    AlarmManager alarmManager = (AlarmManager)this.getSystemService(ALARM_SERVICE);
    alarmManager.cancel(pending);
    activitiesRunning++;
  }

  private void handleActivityStopped() {
    Log.w("KeyCachingService", "Decrementing activity count...");

    activitiesRunning--;
    startTimeoutIfAppropriate();
  }

  private void handleClearKey() {
    this.masterSecret = null;
    stopForeground(true);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(KeyCachingService.this, null);
        return null;
      }
    }.execute();
  }

  private void handleDisableService() {
    if (OpenchatServicePreferences.isPasswordDisabled(this))
      stopForeground(true);
  }

  private void handleLocaleChanged() {
    dynamicLanguage.updateServiceLocale(this);
    foregroundService();
  }

  private void startTimeoutIfAppropriate() {
    boolean timeoutEnabled = OpenchatServicePreferences.isPassphraseTimeoutEnabled(this);

    if ((activitiesRunning == 0) && (this.masterSecret != null) && timeoutEnabled && !OpenchatServicePreferences.isPasswordDisabled(this)) {
      long timeoutMinutes = OpenchatServicePreferences.getPassphraseTimeoutInterval(this);
      long timeoutMillis  = TimeUnit.MINUTES.toMillis(timeoutMinutes);

      Log.w("KeyCachingService", "Starting timeout: " + timeoutMillis);

      AlarmManager alarmManager = (AlarmManager)this.getSystemService(ALARM_SERVICE);
      alarmManager.cancel(pending);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, pending);
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void foregroundServiceModern() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_openchatservice_passphrase_cached));
    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setWhen(0);
    builder.setPriority(Notification.PRIORITY_MIN);

    builder.addAction(R.drawable.ic_menu_lock_holo_dark, getString(R.string.KeyCachingService_lock), buildLockIntent());
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private void foregroundServiceICS() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
    RemoteViews remoteViews            = new RemoteViews(getPackageName(), R.layout.key_caching_notification);

    remoteViews.setOnClickPendingIntent(R.id.lock_cache_icon, buildLockIntent());

    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setContent(remoteViews);
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private void foregroundServiceLegacy() {
    Notification notification  = new Notification(R.drawable.icon_cached,
                                                  getString(R.string.KeyCachingService_openchatservice_passphrase_cached),
                                                  System.currentTimeMillis());
    notification.setLatestEventInfo(getApplicationContext(),
                                    getString(R.string.KeyCachingService_passphrase_cached),
                                    getString(R.string.KeyCachingService_openchatservice_passphrase_cached),
                                    buildLaunchIntent());
    notification.tickerText = null;

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, notification);
  }

  private void foregroundService() {
    if (OpenchatServicePreferences.isPasswordDisabled(this)) {
      stopForeground(true);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      foregroundServiceModern();
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      foregroundServiceICS();
    } else {
      foregroundServiceLegacy();
    }
  }

  private void broadcastNewSecret() {
    Log.w("service", "Broadcasting new secret...");

    Intent intent = new Intent(NEW_KEY_EVENT);
    intent.putExtra("master_secret", masterSecret);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, 0);
    return pendingIntent;
  }

  private PendingIntent buildLaunchIntent() {
    Intent intent              = new Intent(this, RoutingActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent launchIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
    return launchIntent;
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return binder;
  }

  public class KeySetBinder extends Binder {
    public KeyCachingService getService() {
      return KeyCachingService.this;
    }
  }

  public static void registerPassphraseActivityStarted(Context activity) {
    Intent intent = new Intent(activity, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_START_EVENT);
    activity.startService(intent);
  }

  public static void registerPassphraseActivityStopped(Context activity) {
    Intent intent = new Intent(activity, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_STOP_EVENT);
    activity.startService(intent);
  }
}
