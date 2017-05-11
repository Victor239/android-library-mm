package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
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

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.MainApplication;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Add users permission to app manifest:
 * <p>
 * &lt;uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" /&gt;
 */
public class OptimizationPreferenceCompat extends SwitchPreferenceCompat {
    // http://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it/35220476
    static Intent huawei = IntentClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
    // http://stackoverflow.com/questions/37205106/how-do-i-avoid-that-my-app-enters-optimization-on-samsung-devices
    // http://stackoverflow.com/questions/34074955/android-exact-alarm-is-always-3-minutes-off/34085645#34085645
    static Intent samsung = IntentClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity");
    // http://www.ithao123.cn/content-11070929.html
    static Intent miui = IntentClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
    static Intent vivo = IntentClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity");
    static Intent oppo = IntentClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity");

    static Intent[] ALL = new Intent[]{huawei, samsung, miui, vivo, oppo};
    static Intent[] COMMON = new Intent[]{miui, vivo, oppo};

    public static int REFRESH = 15 * 60 * 1000;
    public static int CHECK_DELAY = 5 * 1000;

    // checkbox for old phones, which fires 15 minutes event
    public static final String PREFERENCE_OPTIMIZATION_SERVICE = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE";
    public static final String PREFERENCE_OPTIMIZATION_WARNING = OptimizationPreferenceCompat.class.getCanonicalName() + ".WARNING";
    public static final String PING = OptimizationPreferenceCompat.class.getCanonicalName() + ".PING";
    public static final String PONG = OptimizationPreferenceCompat.class.getCanonicalName() + ".PONG";
    public static final String SERVICE_CHECK = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_CHECK";
    public static final String SERVICE_RESTART = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_RESTART";

    public static void enable(Context context, long next, Class<? extends Service> service) {
        Intent intent = new Intent(context, service);
        intent.setAction(SERVICE_CHECK);
        MainApplication.setExact(context, next, intent);
    }

    public static void disable(Context context, Class<? extends Service> service) {
        Intent intent = new Intent(context, service);
        intent.setAction(SERVICE_CHECK);
        MainApplication.cancel(context, intent);
    }

    // all service related code, for old phones, where AlarmManager will be used to keep app running
    Class<? extends Service> service;

    public static class ApplicationReceiver extends BroadcastReceiver {
        Context context;

        public ApplicationReceiver(Context context) {
            this.context = context;
            IntentFilter ff = new IntentFilter();
            ff.addAction(PING);
            context.registerReceiver(this, ff);
        }

        public void close() {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(PING)) {
                Intent pong = new Intent(PONG);
                context.sendBroadcast(pong);
            }
        }
    }

    public static class ServiceReceiver extends BroadcastReceiver {
        Context context;
        Handler handler = new Handler();
        Class<? extends Service> service;
        Runnable check = new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(context, service);
                intent.setAction(SERVICE_RESTART);
                context.sendBroadcast(intent);
            }
        };
        long next;

        public ServiceReceiver(final Context context, final Class<? extends Service> service) {
            this.context = context;
            this.service = service;
            IntentFilter ff = new IntentFilter();
            ff.addAction(PONG);
            context.registerReceiver(this, ff);
            register();
        }

        public void close() {
            context.unregisterReceiver(this);
            unregister();
        }

        public boolean onStartCommand(Intent intent, int flags, int startId) {
            register();
            if (intent == null)
                return true;
            String a = intent.getAction();
            if (a == null)
                return false;
            if (a.equals(SERVICE_CHECK)) {
                handler.postDelayed(check, CHECK_DELAY);
                Intent i = new Intent(PING);
                context.sendBroadcast(i);
            }
            if (a.equals(SERVICE_RESTART)) {
                return true;
            }
            return false;
        }

        void register() {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
            boolean b = shared.getBoolean(PREFERENCE_OPTIMIZATION_SERVICE, false);
            if (!b) {
                unregister();
                return;
            }
            long cur = System.currentTimeMillis();
            if (next < cur)
                next = cur + REFRESH;
            enable(context, next, service);
        }

        void unregister() {
            disable(context, service);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(PONG)) {
                handler.removeCallbacks(check);
            }
        }
    }

    public static Intent IntentClassName(String p, String n) {
        Intent intent = new Intent();
        intent.setClassName(p, n);
        return intent;
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

    void create() {
        onResume();
    }

    static String getUserSerial(Context context) {
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

    static void huaweiProtectedApps(Context context) {
        try {
            String cmd = "am start -n " + huawei.getComponent().flattenToShortString();
            if (Build.VERSION.SDK_INT >= 17) {
                cmd += " --user " + getUserSerial(context);
            }
            Runtime.getRuntime().exec(cmd);
        } catch (IOException ignored) {
        }
    }

    static boolean isCallable(Context context, Intent intent) {
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    static boolean isHuawei(Context context) {
        return isCallable(context, huawei);
    }

    static AlertDialog.Builder commonWarning(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.optimization_dialog);
        builder.setMessage(R.string.optimization_message);
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        return builder;
    }

    static AlertDialog.Builder huaweiWarning(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Huawei Settings");
        builder.setMessage("You have to change the power plan to “normal” under settings → power saving to let application be exact on time.");
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        return builder;
    }

    static boolean isSamsung(Context context) {
        return isCallable(context, samsung);
    }

    static AlertDialog.Builder samsungWarninig(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Samsung Settings");
        builder.setMessage("Consider disabling Samsung SmartManager to keep application running in background.");
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        return builder;
    }

    public void enable(Class<? extends Service> service) {
        this.service = service;
        onResume();
    }

    public void onResume() {
        final PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        final String n = getContext().getPackageName();
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
                boolean b = shared.getBoolean(PREFERENCE_OPTIMIZATION_SERVICE, false);
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
                                    edit.putBoolean(PREFERENCE_OPTIMIZATION_SERVICE, true);
                                    edit.commit();
                                    setChecked(true);
                                }
                            });
                            showWarning(getContext(), builder); // show commons
                        } else {
                            disable(getContext(), service);
                            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                            SharedPreferences.Editor edit = shared.edit();
                            edit.putBoolean(PREFERENCE_OPTIMIZATION_SERVICE, false);
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
            setChecked(pm.isIgnoringBatteryOptimizations(n));
            setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                @TargetApi(23)
                public boolean onPreferenceChange(Preference preference, Object o) {
                    AlertDialog.Builder builder = buildWarning(getContext(), !pm.isIgnoringBatteryOptimizations(n));  // hide commons
                    showWarning(getContext(), builder);
                    return false;
                }
            });
        }
    }

    @TargetApi(23)
    static void showOptimization(Context context) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final String n = context.getPackageName();
        if (pm.isIgnoringBatteryOptimizations(n)) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + n));
            context.startActivity(intent);
        }
    }

    public static boolean needWarning(Context context) { // first start warning dialog
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = shared.getBoolean(PREFERENCE_OPTIMIZATION_WARNING, true);
        if (b) {
            for (Intent intent : ALL) {
                if (isCallable(context, intent))
                    return true;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                final String n = context.getPackageName();
                if (!pm.isIgnoringBatteryOptimizations(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void setPositive(final AlertDialog.Builder builder, DialogInterface.OnClickListener click) {
        DialogInterface.OnClickListener opt = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showOptimization(builder.getContext());
            }
        };
        if (Build.VERSION.SDK_INT >= 23) {
            if (click != null)
                builder.setNeutralButton(R.string.settings, click);
            builder.setPositiveButton(android.R.string.yes, opt);
        } else {
            builder.setPositiveButton(android.R.string.yes, click);
        }
    }

    public static AlertDialog.Builder buildWarning(final Context context, boolean showCommons) {
        if (isHuawei(context)) {
            AlertDialog.Builder builder = huaweiWarning(context);
            setPositive(builder, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    huaweiProtectedApps(context);
                }
            });
            return builder;
        } else if (isSamsung(context)) {
            AlertDialog.Builder builder = samsungWarninig(context);
            setPositive(builder, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    context.startActivity(samsung);
                }
            });
            return builder;
        } else {
            for (Intent intent : COMMON) {
                if (isCallable(context, intent)) {
                    AlertDialog.Builder builder = commonWarning(context);
                    final Intent i = intent;
                    setPositive(builder, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            context.startActivity(i);
                        }
                    });
                    return builder;
                }
            }
        }
        if (showCommons) {
            AlertDialog.Builder builder = commonWarning(context);
            setPositive(builder, null);
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
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putBoolean(PREFERENCE_OPTIMIZATION_WARNING, false);
        edit.commit();
        if (d != null) {
            d.show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            showOptimization(context);
        }
    }
}
