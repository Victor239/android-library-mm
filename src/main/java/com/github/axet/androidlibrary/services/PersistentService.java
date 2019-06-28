package com.github.axet.androidlibrary.services;

import android.annotation.SuppressLint;
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

// Several services types available:
//
// 1) Persistent Service + Persistent Icon (Torrent Client)
//    - Battery Optimization settings
//    - No Persistent Icon option (override PersistentService.updateIcon() to keep intent != null)
// 2) If Enabled Service + Periodic events (Hourly Reminder / Volume Warning)
//    - Battery Optimization settings
//    - Persistent Icon option (PersistentService.isPersistent mandatory call)
// 3) If Enabled Service (Call Recorder / Media Merger)
//    - Battery Optimization settings
//    - Persistent Icon option (OptimizationPreferenceCompat.setIcon() mandatory call)
// 4) Long Operation Service (Audio Recorder)
//    - No Battery Optimization settings
//    - No Persistent Icon option (override PersistentService.updateIcon() to keep intent != null, ServiceReceiver.isOptimization() {return true})
// 5) Long Operation no kill check (Hourly Reminder FireAlarmService)
//    - No Battery Optimization settings
//    - No Persistent Icon option (override PersistentService.updateIcon() to keep intent != null, override onCreateOptimization() {})
public class PersistentService extends Service {
    public static final String TAG = PersistentService.class.getSimpleName();

    protected int id = 1; // persistent icon id
    protected OptimizationPreferenceCompat.ServiceReceiver optimization;
    protected Notification notification;

    public static void start(Context context, Intent intent) {
        OptimizationPreferenceCompat.startService(context, intent);
    }

    public static void stop(Context context, Intent intent) {
        context.stopService(intent);
    }

    public static boolean isPersistent(Context context, boolean b, String key) {
        OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(context, key);
        return (Build.VERSION.SDK_INT < 26 && b) || state.icon;
    }

    public static boolean startIfPersistent(Context context, boolean b, Intent intent, String key) { // if service is optional keep running service for <API26
        if (isPersistent(context, b, key)) {
            start(context, intent);
            return true;
        } else {
            stop(context, intent);
            return false;
        }
    }

    public class ServiceReceiver extends OptimizationPreferenceCompat.ServiceReceiver {
        public String keyNext;

        public ServiceReceiver(String key, String next) {
            super(PersistentService.this, PersistentService.this.getClass(), key);
            this.keyNext = next;
            this.filters.addAction(OptimizationPreferenceCompat.ICON_UPDATE);
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
        public boolean isOptimization() {
            return super.isOptimization();
        }

        @Override
        public void register() {
            super.register();
            OptimizationPreferenceCompat.setKillCheck(context, next, keyNext);
        }

        @Override
        public void unregister() {
            super.unregister();
            OptimizationPreferenceCompat.setKillCheck(context, 0, keyNext);
        }
    }

    public static class SettingsReceiver extends BroadcastReceiver {
        public String key; // "optimization"
        public Intent intent;
        public IntentFilter filters = new IntentFilter();

        public SettingsReceiver(Intent intent, String key) {
            this.key = key;
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
                OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(context, key);
                if (state.icon)
                    start(context, this.intent);
                else
                    stop(context, this.intent);
            }
        }
    }

    @SuppressLint("RestrictedApi")
    public class PersistentIconBuilder extends RemoteNotificationCompat.Low {
        public PersistentIconBuilder() {
            super(PersistentService.this, R.layout.remoteview);
        }

        public PersistentIconBuilder create() {
            return create(getAppTheme(), getChannelStatus());
        }

        public PersistentIconBuilder create(int theme, NotificationChannelCompat channel) {
            PackageManager pm = mContext.getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(mContext.getPackageName());
            PendingIntent main = PendingIntent.getActivity(mContext, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT);

            setTheme(theme)
                    .setChannel(channel)
                    .setImageViewTint(R.id.icon_circle, getThemeColor(R.attr.colorButtonNormal))
                    .setTitle(AboutPreferenceCompat.getApplicationName(mContext))
                    .setText(getString(R.string.optimization_alive))
                    .setWhen(notification)
                    .setMainIntent(main)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_circle);

            return this;
        }

        public int getAppTheme() {
            return R.style.AppThemeLightLib;
        }

        public NotificationChannelCompat getChannelStatus() {
            return new NotificationChannelCompat(mContext, "status", "Status", NotificationManagerCompat.IMPORTANCE_LOW);
        }
    }

    public PersistentService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        onCreateOptimization();
        updateIcon();
    }

    public void onCreateOptimization() {
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

    public Notification build(Intent intent) {
        return new PersistentIconBuilder().create().build();
    }

    public void updateIcon() {
        updateIcon(null);
    }

    public void updateIcon(Intent intent) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        if (intent != null || OptimizationPreferenceCompat.getState(this, optimization.key).icon || Build.VERSION.SDK_INT >= 26 && getApplicationInfo().targetSdkVersion >= 26) {
            Notification n = build(intent);
            if (notification == null) {
                startForeground(id, n);
            } else {
                String co = NotificationChannelCompat.getChannelId(notification);
                String cn = NotificationChannelCompat.getChannelId(n);
                if (co == null && cn != null || co != null && cn == null || co != null && cn != null && !co.equals(cn))
                    nm.cancel(id);
                nm.notify(id, n);
            }
            notification = n;
        } else {
            hideIcon();
        }
    }

    public void hideIcon() {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        stopForeground(false);
        nm.cancel(id);
        notification = null;
    }
}
