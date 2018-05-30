package com.github.axet.androidlibrary.widgets;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupWindow;

import java.lang.reflect.Method;

public class ProximityShader implements SensorEventListener {
    Dialog d;
    Sensor proximity;
    Context context;

    public void clearFlags(WindowManager.LayoutParams attrs, int flags) {
        setFlags(attrs, 0, flags);
    }

    public static void setFlags(WindowManager.LayoutParams attrs, int flags, int mask) {
        attrs.flags = (attrs.flags & ~mask) | (flags & mask);
    }

    public static void setWindowLayoutTypeCompat(PopupWindow w, int layoutType) {
        if (Build.VERSION.SDK_INT >= 23) {
            w.setWindowLayoutType(layoutType);
        } else {
            try {
                Class c = w.getClass();
                Method m = c.getMethod("setWindowLayoutType", Integer.TYPE);
                if (m != null) {
                    m.invoke(w, layoutType);
                }
            } catch (Exception e) {
            }
        }
    }

    public static void showSystemUI(Window w) {
        if (Build.VERSION.SDK_INT >= 11) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    public static void hideSystemUI(Window w) {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        if (Build.VERSION.SDK_INT >= 11) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    public ProximityShader(Context context) {
        this.context = context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void closeSystemDialogs() {
        Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(it);
    }

    public void turnScreenOff() {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) // no window create support
            return;
        if (d != null) // already hidded
            return;

        boolean locked = (((Activity) context).getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) == WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

        final View anchor = new View(context);
        anchor.setBackgroundColor(Color.BLACK);

        d = new Dialog(context);
        d.setCancelable(false);
        final Window w = d.getWindow();
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(anchor);
        if (Build.VERSION.SDK_INT >= 11) {
            w.getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    closeSystemDialogs();
                    hideSystemUI(w);
                }
            });
        }
        w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.BLACK), 0));
        WindowManager.LayoutParams attrs = w.getAttributes();
        attrs.screenBrightness = 0;
        attrs.screenOrientation = Configuration.ORIENTATION_PORTRAIT;
        attrs.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (locked)
            attrs.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        w.setAttributes(attrs);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        d.show();

        closeSystemDialogs();
        hideSystemUI(w);
    }

    public void turnScreenOn() {
        if (d != null) {
            d.dismiss();
            d = null;
        }
    }

    public void create() {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            if (proximity != null)
                sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void close() {
        turnScreenOn();

        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            sm.unregisterListener(this);
            proximity = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (proximity == null) // seems like onSensorChanged can be called after unregisterListener for unhandled events
            return;
        float distance = event.values[0];
        if (distance <= 0) { // always 0 or 5 on my device (cm)
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
}
