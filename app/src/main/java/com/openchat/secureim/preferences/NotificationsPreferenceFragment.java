package com.openchat.secureim.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.support.v4.preference.PreferenceFragment;
import android.text.TextUtils;
import android.util.Log;

import com.openchat.secureim.ApplicationPreferencesActivity;
import com.openchat.secureim.R;
import com.openchat.secureim.util.OpenchatServicePreferences;

import java.util.Arrays;

public class NotificationsPreferenceFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_notifications);

    this.findPreference(OpenchatServicePreferences.LED_COLOR_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(OpenchatServicePreferences.LED_BLINK_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(OpenchatServicePreferences.RINGTONE_PREF)
        .setOnPreferenceChangeListener(new RingtoneSummaryListener());
    this.findPreference(OpenchatServicePreferences.REPEAT_ALERTS_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());

    initializeListSummary((ListPreference) findPreference(OpenchatServicePreferences.LED_COLOR_PREF));
    initializeListSummary((ListPreference) findPreference(OpenchatServicePreferences.LED_BLINK_PREF));
    initializeListSummary((ListPreference) findPreference(OpenchatServicePreferences.REPEAT_ALERTS_PREF));
    initializeRingtoneSummary((RingtonePreference) findPreference(OpenchatServicePreferences.RINGTONE_PREF));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__notifications);
  }

  private class ListSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
      ListPreference listPref = (ListPreference) preference;

      final int entryIndex = Arrays.asList(listPref.getEntryValues()).indexOf(value);
      listPref.setSummary(entryIndex >= 0 && entryIndex < listPref.getEntries().length
                          ? listPref.getEntries()[entryIndex]
                          : getString(R.string.preferences__led_color_unknown));
      return true;
    }
  }

  private class RingtoneSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      String value = (String) newValue;

      if (TextUtils.isEmpty(value)) {
        preference.setSummary(R.string.preferences__default);
      } else {
        Ringtone tone = RingtoneManager.getRingtone(getActivity(), Uri.parse(value));
        if (tone != null) {
          preference.setSummary(tone.getTitle(getActivity()));
        }
      }

      return true;
    }
  }

  private void initializeListSummary(ListPreference pref) {
    pref.setSummary(pref.getEntry());
  }

  private void initializeRingtoneSummary(RingtonePreference pref) {
    RingtoneSummaryListener listener =
      (RingtoneSummaryListener) pref.getOnPreferenceChangeListener();
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

    listener.onPreferenceChange(pref, sharedPreferences.getString(pref.getKey(), ""));
  }

  public static CharSequence getSummary(Context context) {
    final int onCapsResId   = R.string.ApplicationPreferencesActivity_On;
    final int offCapsResId  = R.string.ApplicationPreferencesActivity_Off;

    return context.getString(OpenchatServicePreferences.isNotificationsEnabled(context) ? onCapsResId : offCapsResId);
  }
}
