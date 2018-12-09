package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;

public abstract class AppCompatSettingsThemeActivity extends AppCompatThemeActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            if (preference instanceof SeekBarPreference) {
                preference.setSummary(((SeekBarPreference) preference).format((Float) value));
            } else if (preference instanceof NameFormatPreferenceCompat) {
                preference.setSummary(((NameFormatPreferenceCompat) preference).getFormatted(stringValue));
            } else if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    public static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getAll().get(preference.getKey()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setAppTheme(getAppTheme());
        super.onCreate(savedInstanceState);
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    public abstract String getAppThemeKey();

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getAppThemeKey())) {
            restartActivity();
        }
    }

}
