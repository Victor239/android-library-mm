package com.github.axet.androidlibrary.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.github.axet.androidlibrary.R;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;

public class MainApplication extends Application {
    public static final String TAG = MainApplication.class.getSimpleName();

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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

    public int getVersion(String key, int id) {
        final SharedPreferences defaultValueSp = getSharedPreferences(android.support.v7.preference.PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE);
        if (!defaultValueSp.getBoolean(android.support.v7.preference.PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
            android.support.v7.preference.PreferenceManager.setDefaultValues(this,id, false);
            return -1;
        } else {
            SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(this);
            return shared.getInt(key, 0);
        }
    }
}
