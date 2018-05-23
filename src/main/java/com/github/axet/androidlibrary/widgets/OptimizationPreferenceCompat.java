package com.github.axet.androidlibrary.widgets;

import android.Manifest;
import android.annotation.TargetApi;
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
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.AlarmManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

//
// Add users permission to app manifest:
//
// <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
//
public class OptimizationPreferenceCompat extends SwitchPreferenceCompat {
    public static String TAG = OptimizationPreferenceCompat.class.getSimpleName();

    // http://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it/35220476
    public static Intent huawei = IntentClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
    // http://stackoverflow.com/questions/37205106/how-do-i-avoid-that-my-app-enters-optimization-on-samsung-devices
    // http://stackoverflow.com/questions/34074955/android-exact-alarm-is-always-3-minutes-off/34085645#34085645
    public static Intent samsung = IntentClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity");
    // http://www.ithao123.cn/content-11070929.html
    public static Intent miui = IntentClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
    public static Intent vivo = IntentClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity");
    public static Intent oppo = IntentClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity");

    public static Intent[] ALL = new Intent[]{huawei, samsung, miui, vivo, oppo};
    public static Intent[] COMMON = new Intent[]{miui, vivo, oppo};

    public static int REFRESH = 15 * AlarmManager.MIN1;
    public static int CHECK_DELAY = 5 * AlarmManager.MIN1;

    // checkbox for old phones, which fires 15 minutes event
    public static final String PING = OptimizationPreferenceCompat.class.getCanonicalName() + ".PING";
    public static final String PONG = OptimizationPreferenceCompat.class.getCanonicalName() + ".PONG";
    public static final String SERVICE_CHECK = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_CHECK";
    public static final String SERVICE_RESTART = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_RESTART";

    // all service related code, for old phones, where AlarmManager will be used to keep app running
    protected Class<? extends Service> service;

    public static void enable(Context context, long next, Class<? extends Service> service) {
        Intent intent = new Intent(context, service);
        intent.setAction(SERVICE_CHECK);
        AlarmManager.set(context, next, intent);
    }

    public static void disable(Context context, Class<? extends Service> service) {
        Intent intent = new Intent(context, service);
        intent.setAction(SERVICE_CHECK);
        AlarmManager.cancel(context, intent);
    }

    public static void disableKill(Context context, Class<?> klass) {
        ComponentName name = new ComponentName(context, klass);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    public static Intent IntentClassName(String p, String n) {
        Intent intent = new Intent();
        intent.setClassName(p, n);
        return intent;
    }

    @TargetApi(23)
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final String n = context.getPackageName();
        return pm.isIgnoringBatteryOptimizations(n);
    }

    @TargetApi(19)
    public static String getUserSerial(Context context) {
        Object userManager = context.getSystemService(Context.USER_SERVICE);
        if (null == userManager)
            return "";
        try {
            Method myUserHandleMethod = android.os.Process.class.getMethod("myUserHandle", (Class<?>[]) null);
            Object myUserHandle = myUserHandleMethod.invoke(android.os.Process.class, (Object[]) null);
            Method getSerialNumberForUser = userManager.getClass().getMethod("getSerialNumberForUser", myUserHandle.getClass());
            Long userSerial = (Long) getSerialNumberForUser.invoke(userManager, myUserHandle);
            if (userSerial != null) {
                return String.valueOf(userSerial);
            } else {
                return "";
            }
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException ignored) {
        }
        return "";
    }

    public static void huaweiProtectedApps(Context context) {
        try {
            String cmd = "am start -n " + huawei.getComponent().flattenToShortString();
            if (Build.VERSION.SDK_INT >= 17) {
                cmd += " --user " + getUserSerial(context);
            }
            Runtime.getRuntime().exec(cmd);
        } catch (IOException ignored) {
        }
    }

    public static boolean isCallable(Context context, Intent intent) {
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static boolean isHuawei(Context context) {
        return isCallable(context, huawei);
    }

    public static boolean isSamsung(Context context) {
        return isCallable(context, samsung);
    }

    public static boolean startActivity(Context context, Intent intent) {
        if (isCallable(context, intent)) {
            try {
                context.startActivity(intent);
                return true;
            } catch (SecurityException e) {
                Log.d(TAG, "unable to start activity", e);
            }
        }
        return false;
    }

    @TargetApi(23)
    public static void showOptimization(Context context) {
        final String n = context.getPackageName();
        if (isIgnoringBatteryOptimizations(context)) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(context, intent);
        } else {
            if (context.getPackageManager().checkPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, context.getPackageName()) != PackageManager.PERMISSION_GRANTED)
                Log.e(TAG, "Permission not granted: " + Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + n));
            if (!startActivity(context, intent)) { // some samsung phones does not have this
                intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(context, intent);
            }
        }
    }

    public static void build(final AlertDialog.Builder builder, String msg, DialogInterface.OnClickListener click) {
        builder.setTitle(R.string.optimization_dialog);
        builder.setMessage(msg);
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        DialogInterface.OnClickListener opt = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showOptimization(builder.getContext());
            }
        };
        if (Build.VERSION.SDK_INT >= 23) {
            if (click != null)
                builder.setNeutralButton(R.string.menu_settings, click);
            builder.setPositiveButton(android.R.string.yes, opt);
        } else {
            builder.setPositiveButton(android.R.string.yes, click);
        }
    }

    public static AlertDialog.Builder buildKilledWarning(final Context context, boolean showCommons) {
        AlertDialog.Builder b = buildWarning(context, showCommons);
        b.setMessage(R.string.optimization_killed);
        return b;
    }

    public static AlertDialog.Builder buildWarning(final Context context, boolean showCommons) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (isHuawei(context)) {
            build(builder, "You have to change the power plan to “normal” under settings → power saving to let application be exact on time.", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    huaweiProtectedApps(context);
                }
            });
            return builder;
        } else if (isSamsung(context)) {
            build(builder, "Consider disabling Samsung SmartManager to keep application running in background.", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (!startActivity(context, samsung)) {
                        Toast.makeText(context, "Unable to show settings", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return builder;
        } else {
            for (Intent intent : COMMON) {
                if (isCallable(context, intent)) {
                    final Intent i = intent;
                    build(builder, context.getString(R.string.optimization_message), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!startActivity(context, i)) {
                                Toast.makeText(context, "Unable to show settings", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    return builder;
                }
            }
        }
        if (showCommons) {
            build(builder, context.getString(R.string.optimization_message), null);
            return builder;
        } else {
            return null;
        }
    }

    public static void showWarning(Context context) {
        AlertDialog.Builder builder = buildWarning(context, true);
        showWarning(context, builder);
    }

    public static void showWarning(Context context, AlertDialog.Builder builder) {
        if (builder != null)
            showWarning(context, builder.create());
        else
            showWarning(context, (AlertDialog) null);
    }

    public static void showWarning(Context context, AlertDialog d) {
        if (d != null) {
            d.show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            showOptimization(context);
        }
    }

    public static void setKillCheck(Context context, long time, String key) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putString(key, System.currentTimeMillis() + ";" + time);
        edit.commit();
    }

    public static boolean needKillWarning(Context context, String key) { // true - need show warning dialog
        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(context);
        Object n = shared.getAll().get(key);
        long set; // alarm set time
        long next; // alarm next time
        if (n == null) {
            set = System.currentTimeMillis();
            next = 0;
        } else if (n instanceof Long) { // old version
            set = System.currentTimeMillis();
            next = (Long) n;
        } else {
            String[] nn = ((String) n).split(";");
            set = Long.valueOf(nn[0]);
            next = Long.valueOf(nn[1]);
        }
        if (next == 0)
            return false; // no missed alarm
        long time = System.currentTimeMillis();
        if (next > time)
            return false; // alarm in the future
        long uptime = SystemClock.elapsedRealtime(); // milliseconds since boot, including time spent in sleep
        long boot = time - uptime; // boot time
        if (next < boot)
            return false; // we lost alarm, while device were offline, skip warning
        if (set < boot)
            return false; // we did reboot device between set alarm and boot time, skip warning
        return true;
    }

    public static class ApplicationReceiver extends BroadcastReceiver {
        protected Context context;
        protected Class<? extends Service> service;

        public ApplicationReceiver(Context context, Class<? extends Service> klass) {
            this.context = context;
            this.service = klass;
            IntentFilter ff = new IntentFilter();
            ff.addAction(service.getCanonicalName() + PING);
            context.registerReceiver(this, ff);
        }

        public void close() {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(service.getCanonicalName() + PING)) {
                Intent pong = new Intent(service.getCanonicalName() + PONG);
                context.sendBroadcast(pong);
            }
        }
    }

    public static class ServiceReceiver extends BroadcastReceiver {
        protected Context context;
        protected String key;
        protected Handler handler = new Handler();
        protected Class<? extends Service> service;
        protected long next;
        protected Runnable check = new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(context, service);
                intent.setAction(SERVICE_RESTART);
                context.startService(intent);
            }
        };

        public ServiceReceiver(final Context context, final Class<? extends Service> service, String key) {
            this.key = key;
            this.context = context;
            this.service = service;
            disableKill(context, service);
            IntentFilter ff = new IntentFilter();
            ff.addAction(service.getCanonicalName() + PONG);
            context.registerReceiver(this, ff);
            register();
        }

        public void close() {
            context.unregisterReceiver(this);
            unregister();
        }

        // return true if app need to be started
        public boolean onStartCommand(Intent intent, int flags, int startId) {
            register();
            if (intent == null)
                return true; // null if service were restarted by system after crash / low memory
            String a = intent.getAction();
            if (a == null)
                return false;
            if (a.equals(SERVICE_CHECK)) {
                check();
            }
            if (a.equals(SERVICE_RESTART)) {
                return true;
            }
            return false;
        }

        public void check() { // disable when here is no ApplicationReceiver
            handler.postDelayed(check, CHECK_DELAY);
            Intent i = new Intent(service.getCanonicalName() + PING);
            context.sendBroadcast(i);
        }

        public void onTaskRemoved(Intent intent) {
            next = System.currentTimeMillis() + 10 * AlarmManager.SEC1;
            register();
        }

        public void register() {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!isIgnoringBatteryOptimizations(context)) {
                    unregister();
                    return;
                }
            } else {
                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                boolean b = shared.getBoolean(key, false);
                if (!b) {
                    unregister();
                    return;
                }
            }
            long cur = System.currentTimeMillis();
            if (next < cur)
                next = cur + REFRESH;
            enable(context, next, service);
        }

        public void unregister() {
            disable(context, service);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(service.getCanonicalName() + PONG)) {
                handler.removeCallbacks(check);
            }
        }
    }

    @TargetApi(21)
    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    @TargetApi(21)
    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public OptimizationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public OptimizationPreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
    }

    public void enable(Class<? extends Service> service) {
        this.service = service;
    }

    public void onResume() {
        if (Build.VERSION.SDK_INT < 23) {
            for (Intent intent : ALL) {
                if (isCallable(getContext(), intent)) {
                    setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            showWarning(getContext()); // show commons
                            return false;
                        }
                    });
                    setVisible(true);
                    return;
                }
            }
            if (service != null) {
                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                boolean b = shared.getBoolean(getKey(), false);
                setChecked(b);
                setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        if (b) {
                            AlertDialog.Builder builder = buildWarning(getContext(), true);
                            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    enable(getContext(), System.currentTimeMillis(), service);
                                    final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                                    SharedPreferences.Editor edit = shared.edit();
                                    edit.putBoolean(getKey(), true);
                                    edit.commit();
                                    setChecked(true);
                                }
                            });
                            showWarning(getContext(), builder); // show commons
                        } else {
                            disable(getContext(), service);
                            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                            SharedPreferences.Editor edit = shared.edit();
                            edit.putBoolean(getKey(), false);
                            edit.commit();
                            setChecked(false);
                        }
                        return false;
                    }
                });
                setVisible(true);
                return;
            }
            setVisible(false);
        } else {
            boolean b = isIgnoringBatteryOptimizations(getContext());
            if (service != null) {
                if (b) {
                    enable(getContext(), System.currentTimeMillis(), service);
                } else {
                    disable(getContext(), service);
                }
            }
            setChecked(b);
            setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                @TargetApi(23)
                public boolean onPreferenceChange(Preference preference, Object o) {
                    AlertDialog.Builder builder = buildWarning(getContext(), !isIgnoringBatteryOptimizations(getContext()));  // hide commons
                    showWarning(getContext(), builder);
                    return false;
                }
            });
        }
    }
}
