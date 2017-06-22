package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.widget.Toast;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.AlarmManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Add users permission to app manifest:
 * <p>
 * &lt;uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" /&gt;
 */
public class SilencePreferenceCompat extends SwitchPreferenceCompat {

    @TargetApi(21)
    public SilencePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    @TargetApi(21)
    public SilencePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public SilencePreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public SilencePreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
        onResume();
    }

    @Override
    public boolean callChangeListener(Object newValue) {
        if (!super.callChangeListener(newValue))
            return false;
        boolean b = (boolean) newValue;
        if (b) {
            if (Build.VERSION.SDK_INT >= 23) {
                NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                if (!notificationManager.isNotificationPolicyAccessGranted()) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                    return false;
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                    return false;
                }
            }
        }
        return b;
    }

    public void onResume() {
        if (Build.VERSION.SDK_INT >= 23) {
            NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager.isNotificationPolicyAccessGranted()) {
                setChecked(true);
            } else {
                setChecked(false);
            }
        }
    }
}
