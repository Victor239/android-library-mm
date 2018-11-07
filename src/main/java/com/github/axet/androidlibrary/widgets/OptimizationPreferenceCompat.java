package com.github.axet.androidlibrary.widgets;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.v7.view.WindowCallbackWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.AlarmManager;

import org.json.JSONException;
import org.json.JSONObject;

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
    public static boolean ICON = false; // default no persistent icon option

    // checkbox for old phones, which fires 15 minutes event
    public static final String PING = OptimizationPreferenceCompat.class.getCanonicalName() + ".PING";
    public static final String PONG = OptimizationPreferenceCompat.class.getCanonicalName() + ".PONG";
    public static final String SERVICE_CHECK = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_CHECK";
    public static final String SERVICE_RESTART = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_RESTART";
    public static final String SERVICE_UPDATE = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_UPDATE";
    public static final String ICON_UPDATE = OptimizationPreferenceCompat.class.getCanonicalName() + ".ICON_UPDATE";

    // all service related code, for old phones, where AlarmManager will be used to keep app running
    protected Class<? extends Service> service;

    public static ComponentName startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26) {
            Class k = context.getClass();
            try {
                Method m = k.getMethod("startForegroundService", Intent.class);
                return (ComponentName) m.invoke(context, intent);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            return context.startService(intent);
        }
    }

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

    @SuppressLint("RestrictedApi")
    public static PreferenceViewHolder inflate(Preference p, ViewGroup root) {
        LayoutInflater inflater = LayoutInflater.from(p.getContext());
        View pref = inflater.inflate(p.getLayoutResource(), root);
        ViewGroup widgetFrame = (ViewGroup) pref.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null) {
            if (p.getWidgetLayoutResource() != 0) {
                inflater.inflate(p.getWidgetLayoutResource(), widgetFrame);
            } else {
                widgetFrame.setVisibility(View.GONE);
            }
        }
        PreferenceViewHolder h = new PreferenceViewHolder(pref);
        p.onBindViewHolder(h);
        return h;
    }

    public static void build(final WarningBuilder builder, String msg, DialogInterface.OnClickListener click) {
        final Context context = builder.getContext();
        builder.builder.setTitle(R.string.optimization_dialog);
        final DialogInterface.OnClickListener opt = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showOptimization(context);
            }
        };
        if (ICON) {
            if (click != null)
                builder.builder.setNeutralButton(R.string.menu_settings, click);
            LinearLayout ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.VERTICAL);
            int dp5 = ThemeUtils.dp2px(context, 5);
            ll.setPadding(dp5, dp5, dp5, dp5);
            TextView desc = new TextView(context);
            TextViewCompat.setTextAppearance(desc, R.style.TextAppearance_AppCompat_Body1);
            desc.setText(msg);
            ll.addView(desc);
            builder.icon = new SwitchPreferenceCompat(context);
            builder.icon.setTitle(context.getString(R.string.optimization_icon));
            builder.icon.setSummary(context.getString(R.string.optimization_icon_summary));
            builder.iconHolder = inflate(builder.icon, null);
            builder.icon.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (Build.VERSION.SDK_INT < 23) {
                        State23 state = getState23(context, builder.key);
                        state.icon = (boolean) newValue;
                        saveState(context, state, builder.key);
                    } else {
                        State state = getState(context, builder.key);
                        state.icon = (boolean) newValue;
                        saveState(context, state, builder.key);
                    }
                    builder.updateIcon();
                    Intent intent = new Intent(ICON_UPDATE);
                    context.sendBroadcast(intent);
                    return false;
                }
            });
            builder.updateIcon();
            ll.addView(builder.iconHolder.itemView);
            if (Build.VERSION.SDK_INT >= 23) {
                builder.optimization = new SwitchPreferenceCompat(context);
                builder.optimization.setTitle(context.getString(R.string.optimization_system));
                builder.optimization.setSummary(context.getString(R.string.optimization_system_summary));
                Drawable d = context.getDrawable(R.drawable.ic_open_in_new_black_24dp);
                d = DrawableCompat.wrap(d);
                DrawableCompat.setTint(d, ThemeUtils.getThemeColor(context, android.R.attr.colorForeground));
                builder.optimization.setIcon(d);
                builder.optimizationHolder = inflate(builder.optimization, null);
                builder.optimization.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        opt.onClick(null, 0);
                        return false;
                    }
                });
                builder.updateOptimization();
                ll.addView(builder.optimizationHolder.itemView);
            } else {
                final SwitchPreferenceCompat alive = new SwitchPreferenceCompat(context);
                alive.setTitle(context.getString(R.string.optimization_alive));
                alive.setSummary(context.getString(R.string.optimization_alive_summary));
                State23 state = getState23(builder.context, builder.key);
                alive.setChecked(state.service);
                final PreferenceViewHolder h = inflate(alive, null);
                alive.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        if (b) {
                            builder.serviceEnable.run();
                            alive.setChecked(true);
                            alive.onBindViewHolder(h);
                        } else {
                            builder.serviceDisable.run();
                            alive.setChecked(false);
                            alive.onBindViewHolder(h);
                        }
                        return false;
                    }
                });
                ll.addView(h.itemView);
            }
            ScrollView scroll = new ScrollView(context);
            scroll.addView(ll);
            builder.builder.setView(scroll);
            builder.builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        } else {
            builder.builder.setMessage(msg);
            builder.builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            if (Build.VERSION.SDK_INT >= 23) {
                if (click != null)
                    builder.builder.setNeutralButton(R.string.menu_settings, click);
                builder.builder.setPositiveButton(android.R.string.yes, opt);
            } else {
                builder.builder.setPositiveButton(android.R.string.yes, click);
            }
        }
    }

    public static WarningBuilder buildKilledWarning(final Context context, boolean showCommons, String key) {
        WarningBuilder b = buildWarning(context, showCommons, key);
        b.builder.setMessage(R.string.optimization_killed);
        return b;
    }

    public static WarningBuilder buildWarning(final Context context, boolean showCommons, String key) {
        WarningBuilder builder = new WarningBuilder(context, key);
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
        if (showCommons || ICON) {
            build(builder, context.getString(R.string.optimization_message), null);
            return builder;
        } else {
            return null;
        }
    }

    public static void showWarning(Context context, String key) {
        WarningBuilder builder = buildWarning(context, true, key);
        showWarning(context, builder);
    }

    public static void showWarning(Context context, WarningBuilder builder) {
        if (builder != null)
            showWarning(context, builder.create());
        else
            showWarning(context, (AlertDialog) null);
    }

    public static void showWarning(Context context, final AlertDialog d) {
        if (d != null) {
            d.show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23)
            showOptimization(context);
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

    public static State23 getState23(Context context, String key) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            String json = shared.getString(key, "");
            return new State23(json);
        } catch (ClassCastException | JSONException e) {
            boolean b = shared.getBoolean(key, false);
            return new State23(b);
        }
    }

    public static State getState(Context context, String key) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            String json = shared.getString(key, "");
            return new State(json);
        } catch (ClassCastException | JSONException e) {
            return new State();
        }
    }

    public static void saveState(Context context, State state, String key) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        try {
            edit.putString(key, state.save().toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        edit.commit();
    }

    public static class WarningBuilder {
        public String key;
        public Context context;
        public AlertDialog.Builder builder;
        public AlertDialog dialog;
        public SwitchPreferenceCompat icon;
        public PreferenceViewHolder iconHolder;
        public SwitchPreferenceCompat optimization;
        public PreferenceViewHolder optimizationHolder;
        public Runnable serviceEnable;
        public Runnable serviceDisable;
        public OptimizationPreferenceCompat pref;

        public WarningBuilder(Context context, String key) {
            this.key = key;
            this.context = context;
            this.builder = new AlertDialog.Builder(context);
        }

        public Context getContext() {
            return context;
        }

        public AlertDialog create() {
            dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    WarningBuilder.this.onShow();
                }
            });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    WarningBuilder.this.onDismiss();
                }
            });
            return dialog;
        }

        public void updateIcon() {
            State state = getState(builder.getContext(), key);
            icon.setChecked(state.icon);
            icon.onBindViewHolder(iconHolder);
        }

        public void updateOptimization() {
            boolean b = isIgnoringBatteryOptimizations(builder.getContext());
            optimization.setChecked(b);
            optimization.onBindViewHolder(optimizationHolder);
        }

        public void show() {
            if (dialog == null)
                create();
            dialog.show();
        }

        public void onDismiss() {
            if (pref != null)
                pref.onResume();
        }

        public void onShow() {
            Window w = dialog.getWindow();
            w.setCallback(new WindowCallbackWrapper(w.getCallback()) {
                @SuppressLint("RestrictedApi")
                @Override
                public void onWindowFocusChanged(boolean hasFocus) {
                    super.onWindowFocusChanged(hasFocus);
                    WarningBuilder.this.onWindowFocusChanged(hasFocus);
                }
            });
        }

        public void onWindowFocusChanged(boolean hasFocus) {
            if (ICON) {
                if (Build.VERSION.SDK_INT >= 23) {
                    updateOptimization();
                }
            }
        }
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
                OptimizationPreferenceCompat.startService(context, intent);
            }
        };

        public ServiceReceiver(final Context context, final Class<? extends Service> service, String key) {
            this.key = key;
            this.context = context;
            this.service = service;
            disableKill(context, service);
            IntentFilter ff = new IntentFilter();
            ff.addAction(SERVICE_UPDATE);
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

        public void check() { // override when here is no ApplicationReceiver
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
                State23 state = getState23(context, key);
                if (!state.service) {
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
            if (a.equals(SERVICE_UPDATE)) {
                register();
            }
        }
    }

    public static class State23 extends State { // API23<
        public boolean service;

        public State23() {
        }

        public State23(boolean b) {
            service = b;
        }

        public State23(String json) throws JSONException {
            load(json);
        }

        public JSONObject save() throws JSONException {
            JSONObject j = super.save();
            j.put("service", service);
            return j;
        }

        public void load(JSONObject json) throws JSONException {
            super.load(json);
            service = json.optBoolean("service", false);
        }
    }

    public static class State { // state API23+
        public boolean icon;

        public State() {
        }

        public State(String json) throws JSONException {
            load(json);
        }

        public JSONObject save() throws JSONException {
            JSONObject j = new JSONObject();
            j.put("icon", icon);
            return j;
        }

        public void load(JSONObject json) throws JSONException {
            icon = json.optBoolean("icon", false);
        }

        public void load(String json) throws JSONException {
            if (json == null || json.isEmpty())
                return;
            load(new JSONObject(json));
        }
    }

    public static class NotificationIcon {
        public Notification notification;
        public NotificationChannelCompat channel;
        public Service context;
        public int iconId;
        public String key;
        public String text;
        public String description;
        public int theme = R.style.AppThemeDarkLib;
        public int bigID = -1;
        public int icon = R.drawable.ic_circle;

        public NotificationIcon(Service context, int iconId) {
            this.context = context;
            this.iconId = iconId;
            this.description = context.getString(R.string.optimization_alive);
        }

        public NotificationIcon(Service context, int iconId, String key, String text) {
            this(context, iconId);
            this.key = key;
            this.text = text;
        }

        public NotificationIcon(Service context, int iconId, String key, String text, int theme) {
            this(context, iconId, key, text);
            this.theme = theme;
        }

        public NotificationIcon(Service context, int iconId, String key, String text, int theme, int bigID) {
            this(context, iconId, key, text, theme);
            this.bigID = bigID;
        }

        public void onCreate() {
            channel = new NotificationChannelCompat(context, key, text, NotificationManagerCompat.IMPORTANCE_LOW);
            if (Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26)
                show(true);
        }

        public void onDestroy() {
            if (Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26)
                show(false);
        }

        @SuppressLint("RestrictedApi")
        public Notification build() {
            String title = context.getApplicationInfo().name;
            String text = description;

            RemoteNotificationCompat.Builder builder;

            if (bigID == -1)
                builder = new RemoteNotificationCompat.Low(context);
            else
                builder = new RemoteNotificationCompat.Low(context, bigID);

            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(context.getPackageName());

            PendingIntent main = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setTheme(theme)
                    .setChannel(channel)
                    .setTitle(title)
                    .setText(text)
                    .setWhen(notification)
                    .setMainIntent(main)
                    .setOngoing(true)
                    .setSmallIcon(icon);

            return builder.build();
        }

        public void show(boolean show) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (!show) {
                context.stopForeground(false);
                nm.cancel(iconId);
                notification = null;
            } else {
                Notification n = build();
                if (notification == null)
                    context.startForeground(iconId, n);
                else
                    nm.notify(iconId, n);
                notification = n;
            }
        }
    }

    @TargetApi(21)
    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @TargetApi(21)
    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OptimizationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OptimizationPreferenceCompat(Context context) {
        super(context);
    }

    public void enable(Class<? extends Service> service) {
        this.service = service;
    }

    public void onResume() {
        if (Build.VERSION.SDK_INT < 23) { // 1) devices below 23
            for (Intent intent : ALL) {
                if (isCallable(getContext(), intent)) { // 2) devices in special supported list below 23
                    setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            showWarning(getContext(), getKey()); // show commons
                            return false;
                        }
                    });
                    setVisible(true);
                    return;
                }
            }
            if (service != null) { // 3) apps with service/ping mechanics below 23 getKey() used to store service and icon booleans
                State23 state = getState23(getContext(), getKey());
                setChecked(state.service);
                setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        final Runnable enable = new Runnable() {
                            @Override
                            public void run() {
                                State23 state = getState23(getContext(), getKey());
                                state.service = true;
                                saveState(getContext(), state, getKey());
                                setChecked(true);
                                getContext().sendBroadcast(new Intent(SERVICE_UPDATE));
                            }
                        };
                        Runnable disable = new Runnable() {
                            @Override
                            public void run() {
                                State23 state = getState23(getContext(), getKey());
                                state.service = false;
                                saveState(getContext(), state, getKey());
                                setChecked(false);
                                getContext().sendBroadcast(new Intent(SERVICE_UPDATE));
                            }
                        };
                        if (ICON) {
                            WarningBuilder builder = buildWarning(getContext(), true, getKey());
                            builder.serviceEnable = enable;
                            builder.serviceDisable = disable;
                            showWarning(getContext(), builder); // show commons
                            return false;
                        }
                        if (b) {
                            WarningBuilder builder = buildWarning(getContext(), true, getKey());
                            builder.builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    enable.run();
                                }
                            });
                            showWarning(getContext(), builder); // show commons
                        } else {
                            disable.run();
                        }
                        return false;
                    }
                });
                setVisible(true);
                return;
            }
            if (ICON) { // 4) apps with persistent icon and no service settings below 23
                State state = getState(getContext(), getKey());
                setChecked(state.icon);
                setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        if (b) {
                            WarningBuilder builder = buildWarning(getContext(), true, getKey());
                            builder.builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    State state = getState(getContext(), getKey());
                                    state.icon = true;
                                    saveState(getContext(), state, getKey());
                                    setChecked(true);
                                }
                            });
                            showWarning(getContext(), builder); // show commons
                        } else {
                            State state = getState(getContext(), getKey());
                            state.icon = false;
                            saveState(getContext(), state, getKey());
                            setChecked(false);
                        }
                        return false;
                    }
                });
                setVisible(true);
                return;
            }
            setVisible(false);
        } else { // 5) getKey() icon boolean stored
            boolean b = isIgnoringBatteryOptimizations(getContext());
            if (ICON) {
                State state = getState(getContext(), getKey());
                b |= state.icon;
            }
            setChecked(b);
            setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                @TargetApi(23)
                public boolean onPreferenceChange(Preference preference, Object o) {
                    WarningBuilder builder = buildWarning(getContext(), !isIgnoringBatteryOptimizations(getContext()), getKey());  // hide commons
                    if (builder != null)
                        builder.pref = OptimizationPreferenceCompat.this;
                    showWarning(getContext(), builder);
                    return false;
                }
            });
        }
    }
}
