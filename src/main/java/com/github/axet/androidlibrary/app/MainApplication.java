package com.github.axet.androidlibrary.app;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.github.axet.androidlibrary.R;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

public class MainApplication extends Application {
    public static final String TAG = MainApplication.class.getSimpleName();

    Handler handler = new Handler();
    Map<String, Runnable> alarms = new TreeMap<>();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try { // MultiDex.install(this);
            Class<?> klass = Class.forName("android.support.multidex.MultiDex");
            Method m = klass.getMethod("install", Context.class);
            m.invoke(null, this);
        } catch (Exception ignore) {
        }
    }

    public static String formatTime(int tt) {
        return String.format("%02d", tt);
    }

    public static String formatSize(Context context, long s) {
        if (s > 0.1 * 1024 * 1024 * 1024) {
            float f = s / 1024f / 1024f / 1024f;
            return context.getString(R.string.size_gb, f);
        } else if (s > 0.1 * 1024 * 1024) {
            float f = s / 1024f / 1024f;
            return context.getString(R.string.size_mb, f);
        } else {
            float f = s / 1024f;
            return context.getString(R.string.size_kb, f);
        }
    }

    static public String formatDuration(Context context, long diff) {
        int diffMilliseconds = (int) (diff % 1000);
        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        String str = "";

        if (diffDays > 0)
            str = diffDays + context.getString(R.string.days_symbol) + " " + formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else if (diffHours > 0)
            str = formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else
            str = formatTime(diffMinutes) + ":" + formatTime(diffSeconds);

        return str;
    }

    public static String formatLeft(Context context, int diff) {
        String str = "";

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0) {
            str = context.getResources().getQuantityString(R.plurals.days, diffDays, diffDays);
        } else if (diffHours > 0) {
            str = context.getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);
        } else if (diffMinutes > 0) {
            str = context.getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);
        } else if (diffSeconds > 0) {
            str = context.getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);
        }

        return str;
    }

    public static String formatLeftExact(Context context, long diff) {
        String str = "";

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.days, diffDays, diffDays);

        if (diffHours > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);

        if (diffMinutes > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);

        if (diffDays == 0 && diffHours == 0 && diffMinutes == 0 && diffSeconds > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);

        return str.trim();
    }

    static String intentId(int requestCode, Intent intent) {
        return requestCode + "_" + intent.getClass().getCanonicalName() + "_" + intent.getAction();
    }

    public static void setExact(final Context context, long time, final Intent intent) {
        final PendingIntent pe = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pe);
        } else if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, time, pe);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, time, pe);
        }
        long delay = time - System.currentTimeMillis();
        if (delay < 0)
            delay = 0;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    pe.send();
                } catch (PendingIntent.CanceledException e) {
                    Log.d(TAG, "pending exec failed", e);
                }
            }
        };
        MainApplication app = (MainApplication) context.getApplicationContext();
        String id = intentId(0, intent);
        Runnable old = app.alarms.put(id, r);
        app.handler.removeCallbacks(old);
        app.handler.postDelayed(r, delay);
    }

    public static void cancel(Context context, Intent intent) {
        PendingIntent pe = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pe);
        MainApplication app = (MainApplication) context.getApplicationContext();
        String id = intentId(0, intent);
        Runnable r = app.alarms.remove(id);
        app.handler.removeCallbacks(r);
    }
}
