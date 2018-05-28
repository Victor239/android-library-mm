package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupWindow;

public class ProximitySensor implements SensorEventListener {
    SensorManager sm;
    Sensor proximity;
    WindowManager.LayoutParams old;
    Window w;
    View decorView;
    PopupWindow p;
    View anchor;

    public ProximitySensor(Window w) {
        this.w = w;
        Context context = w.getContext();
        sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        anchor = new View(context);
        anchor.setBackgroundColor(Color.BLACK);
        decorView = w.getDecorView();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        p = new PopupWindow(anchor, dm.widthPixels, dm.heightPixels);
    }

    public void turnScreenOff() {
        old = new WindowManager.LayoutParams();
        WindowManager.LayoutParams params = w.getAttributes();
        old.copyFrom(params);
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.screenBrightness = 0;
        w.setAttributes(params);

        p.setFocusable(false);
        p.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0);

        hideSystemUI();
    }

    public void turnScreenOn() {
        if (old == null)
            return;

        p.dismiss();

        WindowManager.LayoutParams params = w.getAttributes();
        params.copyFrom(old);
        w.setAttributes(params);

        showSystemUI();

        old = null;
    }

    public void create() {
        if (sm != null && proximity != null)
            sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void close() {
        if (old != null)
            turnScreenOn();
        if (sm != null && proximity != null)
            sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float distance = event.values[0];
        if (distance <= 2) { // always 0 or 5 on my device (cm)
            onNear();
        } else {
            onFar();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onNear() {
    }

    public void onFar() {
    }

    void showSystemUI() {
        if (Build.VERSION.SDK_INT >= 11)
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        if (Build.VERSION.SDK_INT >= 11)
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
