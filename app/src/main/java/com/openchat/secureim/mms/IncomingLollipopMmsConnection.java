package com.openchat.secureim.mms;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.SmsManager;
import android.util.Log;

import com.openchat.secureim.providers.MmsBodyProvider;
import com.openchat.secureim.util.Hex;
import com.openchat.secureim.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class IncomingLollipopMmsConnection extends LollipopMmsConnection implements IncomingMmsConnection {
  public  static final String ACTION = IncomingLollipopMmsConnection.class.getCanonicalName() + "MMS_DOWNLOADED_ACTION";
  private static final String TAG    = IncomingLollipopMmsConnection.class.getSimpleName();

  public IncomingLollipopMmsConnection(Context context) {
    super(context, ACTION);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  @Override
  public synchronized void onResult(Context context, Intent intent) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
      Log.w(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
    }
    Log.w(TAG, "code: " + getResultCode() + ", result string: " + getResultData());
  }

  @Override
  @TargetApi(VERSION_CODES.LOLLIPOP)
  public synchronized @Nullable RetrieveConf retrieve(@NonNull String contentLocation,
                                                      byte[] transactionId) throws MmsException
  {
    beginTransaction();

    try {
      MmsBodyProvider.Pointer pointer = MmsBodyProvider.makeTemporaryPointer(getContext());

      Log.w(TAG, "downloading multimedia from " + contentLocation + " to " + pointer.getUri());
      SmsManager.getDefault().downloadMultimediaMessage(getContext(),
                                                        contentLocation,
                                                        pointer.getUri(),
                                                        null,
                                                        getPendingIntent());

      waitForResult();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Util.copy(pointer.getInputStream(), baos);
      pointer.close();

      Log.w(TAG, baos.size() + "-byte response: " + Hex.dump(baos.toByteArray()));

      return (RetrieveConf) new PduParser(baos.toByteArray()).parse();
    } catch (IOException | TimeoutException e) {
      Log.w(TAG, e);
      throw new MmsException(e);
    } finally {
      endTransaction();
    }
  }
}
