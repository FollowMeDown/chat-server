package com.openchat.secureim.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.openchat.secureim.ApplicationContext;
import com.openchat.secureim.dependencies.InjectableType;
import com.openchat.secureim.jobs.PushReceiveJob;
import com.openchat.secureim.util.OpenchatServicePreferences;
import com.openchat.jobqueue.requirements.NetworkRequirement;
import com.openchat.jobqueue.requirements.NetworkRequirementProvider;
import com.openchat.jobqueue.requirements.RequirementListener;
import com.openchat.protocal.InvalidVersionException;
import com.openchat.imservice.api.OpenchatServiceMessagePipe;
import com.openchat.imservice.api.OpenchatServiceMessageReceiver;
import com.openchat.imservice.api.messages.OpenchatServiceEnvelope;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

public class MessageRetrievalService extends Service implements Runnable, InjectableType, RequirementListener {

  private static final String TAG = MessageRetrievalService.class.getSimpleName();

  public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
  public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";
  public static final  String ACTION_PUSH_RECEIVED     = "PUSH_RECEIVED";
  private static final long   REQUEST_TIMEOUT_MINUTES  = 1;

  private NetworkRequirement         networkRequirement;
  private NetworkRequirementProvider networkRequirementProvider;

  @Inject
  public OpenchatServiceMessageReceiver receiver;

  private int     activeActivities = 0;
  private boolean pushPending      = false;

  @Override
  public void onCreate() {
    super.onCreate();
    ApplicationContext.getInstance(this).injectDependencies(this);

    networkRequirement         = new NetworkRequirement(this);
    networkRequirementProvider = new NetworkRequirementProvider(this);

    networkRequirementProvider.setListener(this);
    new Thread(this, "MessageRetrievalService").start();
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_STICKY;

    if      (ACTION_ACTIVITY_STARTED.equals(intent.getAction()))  incrementActive();
    else if (ACTION_ACTIVITY_FINISHED.equals(intent.getAction())) decrementActive();
    else if (ACTION_PUSH_RECEIVED.equals(intent.getAction()))     incrementPushReceived();

    return START_STICKY;
  }

  @Override
  public void run() {
    while (true) {
      Log.w(TAG, "Waiting for websocket state change....");
      waitForConnectionNecessary();

      Log.w(TAG, "Making websocket connection....");
      OpenchatServiceMessagePipe pipe = receiver.createMessagePipe();

      try {
        while (isConnectionNecessary()) {
          try {
            Log.w(TAG, "Reading message...");
            pipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                      new OpenchatServiceMessagePipe.MessagePipeCallback() {
                        @Override
                        public void onMessage(OpenchatServiceEnvelope envelope) {
                          Log.w(TAG, "Retrieved envelope! " + envelope.getSource());

                          PushReceiveJob receiveJob = new PushReceiveJob(MessageRetrievalService.this);
                          receiveJob.handle(envelope, false);

                          decrementPushReceived();
                        }
                      });
          } catch (TimeoutException | InvalidVersionException e) {
            Log.w(TAG, e);
          }
        }
      } catch (Throwable e) {
        Log.w(TAG, e);
      } finally {
        Log.w(TAG, "Shutting down pipe...");
        shutdown(pipe);
      }

      Log.w(TAG, "Looping...");
    }
  }

  @Override
  public void onRequirementStatusChanged() {
    synchronized (this) {
      notifyAll();
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private synchronized void incrementActive() {
    activeActivities++;
    Log.w(TAG, "Active Count: " + activeActivities);
    notifyAll();
  }

  private synchronized void decrementActive() {
    activeActivities--;
    Log.w(TAG, "Active Count: " + activeActivities);
    notifyAll();
  }

  private synchronized void incrementPushReceived() {
    pushPending = true;
    notifyAll();
  }

  private synchronized void decrementPushReceived() {
    pushPending = false;
    notifyAll();
  }

  private synchronized boolean isConnectionNecessary() {
    Log.w(TAG, "Network requirement: " + networkRequirement.isPresent());
    return OpenchatServicePreferences.isWebsocketRegistered(this) &&
           (activeActivities > 0 || pushPending)             &&
           networkRequirement.isPresent();
  }

  private synchronized void waitForConnectionNecessary() {
    try {
      while (!isConnectionNecessary()) wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private void shutdown(OpenchatServiceMessagePipe pipe) {
    try {
      pipe.shutdown();
    } catch (Throwable t) {
      Log.w(TAG, t);
    }
  }

  public static void registerActivityStarted(Context activity) {
    Intent intent = new Intent(activity, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_ACTIVITY_STARTED);
    activity.startService(intent);
  }

  public static void registerActivityStopped(Context activity) {
    Intent intent = new Intent(activity, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_ACTIVITY_FINISHED);
    activity.startService(intent);
  }

  public static void registerPushReceived(Context context) {
    Intent intent = new Intent(context, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_PUSH_RECEIVED);
    context.startService(intent);
  }
}