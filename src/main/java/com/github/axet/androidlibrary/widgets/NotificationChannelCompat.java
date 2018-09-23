package com.github.axet.androidlibrary.widgets;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// https://developer.android.com/training/notify-user/channels
public class NotificationChannelCompat {
    public static final String TAG = NotificationChannelCompat.class.getSimpleName();

    public static final String EXTRA_CHANNEL_ID = "android.intent.extra.CHANNEL_ID";
    public static final String ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";
    public static final String ACTION_CHANNEL_NOTIFICATION_SETTINGS = "android.settings.CHANNEL_NOTIFICATION_SETTINGS";
    public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";

    public String channelId;
    public Object nc;
    public Class NotificationChannelClass;

    public static void setChannelId(Notification n, String channelId) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Class klass = n.getClass();
                Field f = klass.getDeclaredField("mChannelId");
                f.setAccessible(true);
                f.set(n, channelId);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void showSettings(Context context, String channelId) {
        Intent intent = new Intent(ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, context.getPackageName());
        intent.putExtra(EXTRA_CHANNEL_ID, channelId);
        context.startActivity(intent);
    }

    public static void showSettings(Context context) {
        Intent intent = new Intent(ACTION_APP_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, context.getPackageName());
        context.startActivity(intent);
    }

    public NotificationChannelCompat(Context context, String id, String name, int i) {
        this.channelId = id;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationChannelClass = Class.forName("android.app.NotificationChannel");
                nc = NotificationChannelClass.getConstructor(String.class, CharSequence.class, int.class).newInstance(id, name, i);
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                Class NotificationManagerClass = nm.getClass();
                Method CreateNotificationChannel = NotificationManagerClass.getDeclaredMethod("createNotificationChannel", NotificationChannelClass);
                CreateNotificationChannel.invoke(nm, nc);
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setDescription(String str) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("setDescription", String.class);
                m.invoke(nc, str);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setSound(Uri sound, AudioAttributes attr) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("setSound", Uri.class, AudioAttributes.class);
                m.invoke(nc, sound, attr);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void apply(Notification n) {
        NotificationChannelCompat.setChannelId(n, channelId);
    }
}
