package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;

public abstract class AppCompatThemeActivity extends AppCompatActivity {
    public static String TAG = AppCompatThemeActivity.class.getSimpleName();

    public int themeId;

    public static void showLocked(Window w) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        // enable popup keyboard when locked
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 21)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    public static void showDialogLocked(Window w) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        Context context = w.getContext();
        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= 21)
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
    }

    public static void startHome(Activity a) {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_HOME);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(main);
        a.overridePendingTransition(0, 0);
    }

    public static void moveTaskToBack(Activity a) {
        a.moveTaskToBack(true);
        a.overridePendingTransition(0, 0);
    }

    public static class ScreenReceiver extends BroadcastReceiver {
        public Activity a;
        public Runnable off = new Runnable() {
            @Override
            public void run() { // call once after boot (some phones ignores moveTaskToBack first call after boot)
                try {
                    startHome(a);
                } catch (SecurityException e) { // hueway phones
                    Log.d(TAG, "startHome failed", e);
                    moveTaskToBack(a);
                }
                off = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            moveTaskToBack(a);
                        } catch (Exception e) { // NullPointerException on some 7.0 phones
                            Log.d(TAG, "moveTaskToBack failed", e);
                            startHome(a);
                        }
                    }
                };
            }
        };
        public IntentFilter filter = new IntentFilter();

        public ScreenReceiver() {
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
        }

        public void registerReceiver(Activity a) {
            this.a = a;
            this.a.registerReceiver(this, filter);
        }

        public void close() {
            a.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a == null)
                return;
            if (a.equals(Intent.ACTION_SCREEN_OFF))
                onScreenOff();
        }

        public void onScreenOff() {
            off.run();
        }
    }

    public void setAppTheme(int id) {
        super.setTheme(id);
        themeId = id;
    }

    public abstract int getAppTheme();

    @SuppressLint("RestrictedApi")
    public int getAppThemeBar(Toolbar toolbar) { // old api, need to set theme excplitly for toolbar
        ViewParent parent = toolbar.getParent();
        if (parent instanceof ViewGroup) { // AppBarLayout
            Context t = ((ViewGroup) parent).getContext();
            if (t instanceof ContextThemeWrapper) {
                try {
                    Class<?> clazz = t.getClass();
                    Method method = clazz.getMethod("getThemeResId");
                    return (Integer) method.invoke(t);
                } catch (Exception e) {
                    Log.d(TAG, "unable to get parent theme", e);
                }
            }
            if (t instanceof android.support.v7.view.ContextThemeWrapper) {
                return ((android.support.v7.view.ContextThemeWrapper) t).getThemeResId();
            }
        }
        return 0;
    }

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
        if (themeId != getAppTheme())
            restartActivity();
    }

    public void restartActivity() {
        finish();
        startActivity(new Intent(this, getClass()));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        Context theme = getSupportActionBar().getThemedContext();
        int id = getAppThemeBar(toolbar);
        if (theme != null && id != 0) {
            if (theme == this)
                Log.e(TAG, "set 'theme' attribute for for Toolbar");
            else
                theme.setTheme(id);
        }
        toolbar.setPopupTheme(getAppThemePopup());
    }
}
