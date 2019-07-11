package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.view.WindowCallbackWrapper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.github.axet.androidlibrary.widgets.AppCompatThemeActivity;

public abstract class AppCompatFullscreenThemeActivity extends AppCompatThemeActivity {
    public static final int UI_ANIMATION_DELAY = 300;

    public final Handler handler = new Handler();
    public Window w;
    public View decorView;
    public boolean fullscreen;
    public Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            hideSystemUI();
        }
    };
    public Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null)
                actionBar.show();
        }
    };

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        w = getWindow();
        Window.Callback callback = w.getCallback();
        w.setCallback(new WindowCallbackWrapper(callback) {
            @SuppressLint("RestrictedApi")
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                super.onWindowFocusChanged(hasFocus);
                if (hasFocus)
                    setFullscreen(fullscreen);
            }
        });
        decorView = w.getDecorView();
    }

    public void toggle() {
        setFullscreen(!fullscreen);
    }

    public void setFullscreen(boolean b) {
        if (fullscreen == b) {
            if (b)// fix bug when system UI reappear after screen went off
                hideSystemUI();
            return;
        }
        fullscreen = b;
        if (b) {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null)
                actionBar.hide();

            handler.removeCallbacks(mShowPart2Runnable);
            handler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            showSystemUI();
            handler.removeCallbacks(mHidePart2Runnable);
            handler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
        }
    }

    public void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= 11)
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    public void showSystemUI() {
        if (Build.VERSION.SDK_INT >= 11)
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFullscreen(fullscreen); // refresh
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
