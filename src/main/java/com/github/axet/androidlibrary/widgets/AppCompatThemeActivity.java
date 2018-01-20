package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.androidlibrary.R;

public class AppCompatThemeActivity extends AppCompatActivity {
    public String themeKey;
    public String themeDark;
    public int lightTheme = R.style.AppThemeLightLib;
    public int darkTheme = R.style.AppThemeDarkLib;
    public int themeId;

    public void createTheme(String theme, String themeDark, int light, int dark) {
        this.themeKey = theme;
        this.themeDark = themeDark;
        this.lightTheme = light;
        this.darkTheme = dark;
    }

    public void createTheme(String theme, int themeDark, int light, int dark) {
        createTheme(theme, getString(themeDark), light, dark);
    }

    public void setAppTheme(int id) {
        super.setTheme(id);
        themeId = id;
    }

    public int getAppTheme() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = shared.getString(themeKey, "");
        if (theme.equals(themeDark)) {
            return darkTheme;
        } else {
            return lightTheme;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setAppTheme(getAppTheme());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (themeId != getAppTheme()) {
            finish();
            startActivity(new Intent(this, getClass()));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }
}
