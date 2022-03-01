package com.github.axet.androidlibrary.app;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.UUID;

public class PrefStorage extends HashMap<Integer, PrefStorage.PrefDelayed> {
    private static final String TAG = PrefStorage.class.getSimpleName();

    public static PrefStorage DELAYED = new PrefStorage();

    public static void create(Context context) {
        DELAYED.load(context);
    }

    public static Class getCallingClass() {
        StackTraceElement[] ss = Thread.currentThread().getStackTrace();
        String k = PrefStorage.class.getCanonicalName();
        int i = 1;
        for (; i < ss.length; i++) {
            StackTraceElement s = ss[i];
            if (s.getClassName().equals(k))
                break;
        }
        for (; i < ss.length; i++) {
            StackTraceElement s = ss[i];
            if (!s.getClassName().equals(k))
                break;
        }
        if (i < ss.length) {
            StackTraceElement s = ss[i];
            try {
                return Class.forName(s.getClassName());
            } catch (ClassNotFoundException ignore) {
                return null;
            }
        }
        return null;
    }

    public static String PrefString(int key) {
        return DELAYED.add(getCallingClass(), key);
    }

    public static class PrefDelayed {
        public Class c;
        public UUID uuid;
        public int key;

        public PrefDelayed(Class c, int key, UUID u) {
            this.c = c;
            this.key = key;
            this.uuid = u;
        }
    }

    public void load(Context context) {
        for (Integer k : keySet()) {
            PrefDelayed d = get(k);
            for (Field f : d.c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType().equals(String.class)) {
                    try {
                        String v = (String) f.get(null);
                        if (v != null && v.equals(d.uuid.toString())) {
                            String dv = context.getString(d.key);
                            f.setAccessible(true);
                            f.set(null, dv);
                        }
                    } catch (IllegalAccessException e) {
                        Log.w(TAG, e);
                    }
                }
            }
        }
    }

    public String add(Class c, int key) {
        UUID u = UUID.randomUUID();
        PrefDelayed d = new PrefDelayed(c, key, u);
        put(key, d);
        return u.toString();
    }
}
