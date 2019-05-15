package com.github.axet.androidlibrary.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.widgets.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;

public class PersistentService extends Service {
    public static final String TAG = PersistentService.class.getSimpleName();

    public static int NOTIFICATION_PERSISTENT_ICON = 1;
    public static String PREFERENCE_OPTIMIZATION = "optimization";
    public static String PREFERENCE_NEXT = "next";
    public static NotificationChannelCompat CHANNEL_STATUS;

    protected OptimizationPreferenceCompat.ServiceReceiver optimization;
    protected Notification notification;

    public static void start(Context context, Intent intent) {
        OptimizationPreferenceCompat.startService(context, intent);
    }

    public static void stop(Context context, Intent intent) {
        context.stopService(intent);
    }

    public static void startIfEnabled(Context context, boolean b, Intent intent) {
        OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(context, PREFERENCE_OPTIMIZATION);
        if ((Build.VERSION.SDK_INT < 26 && b) || state.icon) // always running service for <API26
            start(context, intent);
        else
            stop(context, intent);
    }

    public class ServiceReceiver extends OptimizationPreferenceCompat.ServiceReceiver {
        public ServiceReceiver(Context context, Class<? extends Service> service, String key) {
            super(context, service, key);
            filters.addAction(OptimizationPreferenceCompat.ICON_UPDATE);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            String a = intent.getAction();
            if (a != null && a.equals(OptimizationPreferenceCompat.ICON_UPDATE))
                updateIcon();
        }

        public void updateIcon() {
            PersistentService.this.updateIcon(null);
        }

        @Override
        public void register() {
            super.register();
            OptimizationPreferenceCompat.setKillCheck(PersistentService.this, next, PREFERENCE_NEXT);
        }

        @Override
        public void unregister() {
            super.unregister();
            OptimizationPreferenceCompat.setKillCheck(PersistentService.this, 0, PREFERENCE_NEXT);
        }
    }

    public static class SettingsReceiver extends BroadcastReceiver {
        public Intent intent;
        public IntentFilter filters = new IntentFilter();

        public SettingsReceiver(Intent intent) {
            this.intent = intent;
            filters.addAction(OptimizationPreferenceCompat.ICON_UPDATE);
        }

        public void register(Context context) {
            context.registerReceiver(this, filters);
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(OptimizationPreferenceCompat.ICON_UPDATE)) {
                OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(context, PREFERENCE_OPTIMIZATION);
                if (state.icon)
                    start(context, this.intent);
                else
                    stop(context, this.intent);
            }
        }
    }

    public PersistentService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        optimization = createOptimization();

        updateIcon();
    }

    public ServiceReceiver createOptimization() {
        ServiceReceiver optimization = new ServiceReceiver(this, getClass(), PREFERENCE_OPTIMIZATION);
        optimization.create();
        return optimization;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (optimization != null) {
            optimization.close();
            optimization = null;
        }

        hideIcon();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (optimization.onStartCommand(intent, flags, startId)) {
            Log.d(TAG, "onStartCommand restart"); // crash fail
            onRestartCommand();
        }
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand " + action);
            onStartCommand(intent);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void onRestartCommand() {
    }

    public void onStartCommand(Intent intent) {
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        optimization.onTaskRemoved(rootIntent);
    }

    public int getAppTheme() {
        return R.style.AppThemeLightLib;
    }

    public Notification build(Intent intent) {
        PackageManager pm = getPackageManager();
        Intent l = pm.getLaunchIntentForPackage(getPackageName());

        PendingIntent main = PendingIntent.getActivity(this, 0, l, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Low(this, R.layout.remoteview);

        builder.setTheme(getAppTheme())
                .setChannel(CHANNEL_STATUS)
                .setImageViewTint(R.id.icon_circle, builder.getThemeColor(R.attr.colorButtonNormal))
                .setTitle(AboutPreferenceCompat.getApplicationName(this))
                .setText(getString(R.string.optimization_alive))
                .setWhen(notification)
                .setMainIntent(main)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_circle);

        return builder.build();
    }

    public void updateIcon() {
        updateIcon(null);
    }

    public void updateIcon(Intent intent) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(this, PREFERENCE_OPTIMIZATION);
        if (intent != null || state.icon || Build.VERSION.SDK_INT >= 26 && getApplicationInfo().targetSdkVersion >= 26) {
            Notification n = build(intent);
            if (notification == null)
                startForeground(NOTIFICATION_PERSISTENT_ICON, n);
            else
                nm.notify(NOTIFICATION_PERSISTENT_ICON, n);
            notification = n;
        } else {
            hideIcon();
        }
    }

    public void hideIcon() {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        stopForeground(false);
        nm.cancel(NOTIFICATION_PERSISTENT_ICON);
        notification = null;
    }
}
