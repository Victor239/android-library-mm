package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

public abstract class AppCompatThemeActivity extends AppCompatActivity {
    public static String TAG = AppCompatThemeActivity.class.getSimpleName();
    
    public int themeId;

    public void setAppTheme(int id) {
        super.setTheme(id);
        themeId = id;
    }

    public abstract int getAppTheme();

    public int getAppThemePopup() {
        Log.d(TAG, "Implement getAppThemePopup() when setSupportActionBar is called");
        return getAppTheme();
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
            restartActivity();
        }
    }

    public void restartActivity() {
        finish();
        startActivity(new Intent(this, getClass()));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        toolbar.setPopupTheme(getAppThemePopup());
    }
}
