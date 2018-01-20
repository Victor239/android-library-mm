package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.androidlibrary.R;

public class AppCompatSettingsThemeActivity extends AppCompatThemeActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(themeKey)) {
            finish();
            startActivity(new Intent(this, getClass()));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

}
