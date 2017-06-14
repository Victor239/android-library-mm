package com.github.axet.androidlibrary.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AlarmManager {
    public static final String TAG = AlarmManager.class.getSimpleName();

    public static final long SEC1 = 1000;
    public static final long SEC3 = 3 * SEC1;
    public static final long SEC10 = 10 * SEC1;
    public static final long MIN1 = 60 * SEC1;
    public static final long MIN2 = 2 * MIN1;
    public static final long MIN5 = 5 * MIN1;
    public static final long MIN15 = 15 * MIN1;
    public static final long HOUR1 = 60 * MIN1;
    public static final long HOUR12 = 12 * HOUR1;
    public static final long DAY1 = 24 * HOUR1;

    public static String formatTime(long time) {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return s.format(new Date(time));
    }

    public static PendingIntent createPendingIntent(Context context, Intent intent) {
        try {
            Class<?> klass = Class.forName(intent.getComponent().getClassName());
            if (Service.class.isAssignableFrom(klass)) {
                return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            } else if (Activity.class.isAssignableFrom(klass)) {
                return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            } else {
                throw new RuntimeException("Unknown PenedingIntent type");
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static PendingIntent set(Context context, long time, Intent intent) {
        PendingIntent pe = createPendingIntent(context, intent);
        android.app.AlarmManager alarm = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.set(android.app.AlarmManager.RTC_WAKEUP, time, pe);
        return pe;
    }

    public static PendingIntent setExact(Context context, long time, Intent intent) {
        PendingIntent pe = createPendingIntent(context, intent);
        android.app.AlarmManager alarm = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            alarm.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, time, pe);
        } else if (Build.VERSION.SDK_INT >= 19) {
            alarm.setExact(android.app.AlarmManager.RTC_WAKEUP, time, pe);
        } else {
            alarm.set(android.app.AlarmManager.RTC_WAKEUP, time, pe);
        }
        return pe;
    }

    public static PendingIntent setAlarm(Context context, long time, Intent intent) {
        PendingIntent pe = createPendingIntent(context, intent);
        android.app.AlarmManager alarm = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 21) {
            alarm.setAlarmClock(new android.app.AlarmManager.AlarmClockInfo(time, pe), pe);
        } else if (Build.VERSION.SDK_INT >= 19) {
            alarm.setExact(android.app.AlarmManager.RTC_WAKEUP, time, pe);
        } else {
            alarm.set(android.app.AlarmManager.RTC_WAKEUP, time, pe);
        }
        return pe;
    }

    public static void cancel(Context context, Intent intent) {
        PendingIntent pe = createPendingIntent(context, intent);
        android.app.AlarmManager alarm = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pe);
    }

    public static String checkId(long requestCode, Intent intent) {
        return requestCode + "_" + intent.getClass().getCanonicalName() + "_" + intent.getAction();
    }

    public class Check {
        public long time;
        public Runnable r;
        public Intent intent;
        public PendingIntent pe;
        PowerManager.WakeLock wlCpu;

        public Check(long time, Runnable r, Intent intent, PendingIntent pe) {
            this.time = time;
            this.r = r;
            this.intent = intent;
            this.pe = pe;
        }

        public void close() {
            wakeClose();
        }

        public void wakeLock() {
            if (wlCpu != null)
                return;
            Log.d(TAG, "Wake CPU lock " + time);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wlCpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, AlarmManager.class.getCanonicalName() + "_" + time + "_cpulock");
            wlCpu.acquire();
        }

        public void wakeClose() {
            if (wlCpu == null)
                return;
            Log.d(TAG, "Wake CPU lock close " + time);
            if (wlCpu.isHeld())
                wlCpu.release();
            wlCpu = null;
        }
    }

    Context context;
    Handler handler = new Handler();
    Map<String, Check> check = new HashMap<>();

    public AlarmManager(Context context) {
        this.context = context;
    }

    public void close() {
        for (String s : check.keySet()) {
            Check old = check.get(s);
            old.close();
        }
        check.clear();
    }

    public void set(long time, Intent intent) {
        PendingIntent pe = set(context, time, intent);
        checkPost(time, intent, pe);
    }

    public void setExact(long time, Intent intent) {
        PendingIntent pe = setExact(context, time, intent);
        checkPost(time, intent, pe);
    }

    public void setAlarm(long time, Intent intent) {
        PendingIntent pe = setAlarm(context, time, intent);
        checkPost(time, intent, pe);
    }

    public void cancel(Intent intent) {
        cancel(context, intent);
        checkCancel(intent);
    }

    public void update() {
        ArrayList<Check> cc = new ArrayList<>(check.values());
        for (Check c : cc) {
            checkPost(c.time, c.intent, c.pe);
        }
    }

    public void checkPost(final long time, final Intent intent, final PendingIntent pe) {
        final String id = checkId(0, intent);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                long cur = System.currentTimeMillis();
                if (cur < time) {
                    checkPost(time, intent, pe);
                } else {
                    Check c = check.remove(id);
                    if (c != null)
                        c.close();
                    try {
                        pe.send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "unable to execute", e); // already processed by AlarmManager?
                    }
                }
            }
        };
        Check c = new Check(time, r, intent, pe);
        Check old = check.put(id, c);
        if (old != null) {
            old.close();
        }
        long cur = System.currentTimeMillis();
        long delay = time - cur;
        if (delay < 0) // instant?
            delay = 0;
        if (OptimizationPreferenceCompat.isHuawei(context)) {
            if (delay < MIN15) {
                c.wakeLock();
            }
        }
        int diffMilliseconds = (int) (cur % 1000);
        int diffSeconds = (int) (cur / 1000 % 60);
        if (delay < SEC1) {
            ; // nothing
        } else if (delay < SEC10) {
            long step = SEC1;
            delay = step - diffMilliseconds;
        } else if (delay < MIN1) {
            long step = SEC10;
            delay = step - diffMilliseconds;
        } else if (delay < MIN15) {
            long step = MIN1;
            delay = step - diffSeconds * 1000 - diffMilliseconds;
        }
        Log.d(TAG, "delaying " + MainApplication.formatDuration(context, delay) + " " + formatTime(cur) + " " + formatTime(time));
        handler.postDelayed(r, delay);
    }

    public void checkCancel(Intent intent) {
        String id = checkId(0, intent);
        Check old = check.remove(id);
        if (old != null) {
            old.close();
        }
    }
}
