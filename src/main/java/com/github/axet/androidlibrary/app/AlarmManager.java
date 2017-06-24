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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AlarmManager {
    public static final String TAG = AlarmManager.class.getSimpleName();

    public static final int SEC1 = 1000;
    public static final int SEC3 = 3 * SEC1;
    public static final int SEC10 = 10 * SEC1;
    public static final int MIN1 = 60 * SEC1;
    public static final int MIN2 = 2 * MIN1;
    public static final int MIN5 = 5 * MIN1;
    public static final int MIN15 = 15 * MIN1;
    public static final int HOUR1 = 60 * MIN1;
    public static final int HOUR12 = 12 * HOUR1;
    public static final int DAY1 = 24 * HOUR1;

    Context context;
    Handler handler = new Handler();
    Map<String, Alarm> check = new HashMap<>();

    public static String formatTime(long time) {
        return MainApplication.SIMPLE.format(new Date(time));
    }

    static public String formatDuration(Context context, long diff) {
        int diffMilliseconds = (int) (diff % 1000);

        return MainApplication.formatDuration(context, diff) + "." + diffMilliseconds;
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

    public class Alarm {
        public long time;
        public Runnable r;
        public Intent intent;
        public PendingIntent pe;
        public PowerManager.WakeLock wlCpu;

        public Alarm(long time, Runnable r, Intent intent, PendingIntent pe) {
            this.time = time;
            this.r = r;
            this.intent = intent;
            this.pe = pe;
        }

        public void close() {
            wakeClose();
            handler.removeCallbacks(r);
        }

        public void wakeLock() {
            if (wlCpu != null)
                return;
            Log.d(TAG, "wakeLock() " + formatTime(time));
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wlCpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, AlarmManager.class.getCanonicalName() + "_" + time + "_cpulock");
            wlCpu.acquire();
        }

        public void wakeClose() {
            if (wlCpu == null)
                return;
            Log.d(TAG, "wakeClose() " + formatTime(time));
            if (wlCpu.isHeld())
                wlCpu.release();
            wlCpu = null;
        }

        public void wakeTake(Alarm c) {
            wakeClose();
            wlCpu = c.wlCpu;
            c.wlCpu = null;
        }

        public boolean isLocked() {
            if (wlCpu == null)
                return false;
            return wlCpu.isHeld();
        }

        public String toString() {
            return AlarmManager.formatTime(time);
        }
    }

    public AlarmManager(Context context) {
        this.context = context;
    }

    public void close() {
        for (String s : check.keySet()) {
            Alarm old = check.get(s);
            old.close();
        }
        check.clear();
    }

    public Alarm set(long time, Intent intent) {
        PendingIntent pe = set(context, time, intent);
        return checkPost(time, intent, pe);
    }

    public Alarm setExact(long time, Intent intent) {
        PendingIntent pe = setExact(context, time, intent);
        return checkPost(time, intent, pe);
    }

    public Alarm setAlarm(long time, Intent intent) {
        PendingIntent pe = setAlarm(context, time, intent);
        return checkPost(time, intent, pe);
    }

    public void cancel(Intent intent) {
        cancel(context, intent);
        checkCancel(intent);
    }

    public void update() {
        ArrayList<Alarm> cc = new ArrayList<>(check.values());
        for (Alarm c : cc) {
            checkPost(c.time, c.intent, c.pe);
        }
    }

    public Alarm checkPost(final long time, final Intent intent, final PendingIntent pe) {
        final String id = checkId(0, intent);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                long cur = System.currentTimeMillis();
                if (cur < time) {
                    checkPost(time, intent, pe);
                } else {
                    Alarm c = check.remove(id);
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
        Alarm c = new Alarm(time, r, intent, pe);
        Alarm old = check.put(id, c);
        if (old != null) {
            c.wakeTake(old);
            old.close();
        }
        long cur = System.currentTimeMillis();
        long delay = time - cur;
        if (delay < 0) // instant?
            delay = 0;
        long diffMilliseconds = cur % 1000;
        long diffSeconds = (cur / 1000 % 60) * 1000;
        if (delay <= SEC1) {
            ; // nothing
        } else if (delay <= SEC10) {
            long step = SEC1;
            delay = step - diffMilliseconds;
        } else if (delay <= MIN1) {
            long step = SEC10;
            if (delay - step < step) // if 0:11, make step 00:01
                step = delay - step + diffMilliseconds;
            delay = step - diffMilliseconds;
        } else if (delay <= MIN5) {
            long step = MIN1;
            if (delay - step < step) // if 1:30, make step 00:30
                step = delay - step + diffSeconds + diffMilliseconds;
            delay = step - diffSeconds - diffMilliseconds;
        } else if (delay <= MIN15) {
            long step = MIN5;
            if (delay - step < step) // if 5:30, make step 00:30
                step = delay - step + diffSeconds + diffMilliseconds;
            delay = step - diffSeconds - diffMilliseconds;
        }
        Log.d(TAG, "delaying " + formatDuration(context, delay) + ", " + formatTime(time));
        handler.postDelayed(r, delay);
        return c;
    }

    public void checkCancel(Intent intent) {
        String id = checkId(0, intent);
        Alarm old = check.remove(id);
        if (old != null) {
            old.close();
        }
    }
}
