package com.openchat.secureim.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;
import android.view.View;

import com.openchat.secureim.R;
import com.openchat.secureim.OpenchatServiceExpiredException;
import com.openchat.secureim.util.OpenchatServicePreferences;
import com.openchat.secureim.util.Util;

public class ExpiredBuildReminder extends Reminder {

  private static final String TAG = ExpiredBuildReminder.class.getSimpleName();

  public ExpiredBuildReminder() {
    super(R.drawable.ic_warning_dark,
          R.string.reminder_header_expired_build,
          R.string.reminder_header_expired_build_details);
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible(Context context) {
    return !Util.isBuildFresh();
  }

}
