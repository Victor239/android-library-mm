package com.github.axet.androidlibrary.widgets;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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

    @IntDef({NotificationManagerCompat.IMPORTANCE_NONE, NotificationManagerCompat.IMPORTANCE_MIN, NotificationManagerCompat.IMPORTANCE_LOW,
            NotificationManagerCompat.IMPORTANCE_DEFAULT, NotificationManagerCompat.IMPORTANCE_HIGH, NotificationManagerCompat.IMPORTANCE_MAX,
            NotificationManagerCompat.IMPORTANCE_UNSPECIFIED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Importance {
    }

    public String channelId;
    public Object channel;
    public Class NotificationChannelClass;

    public static void setChannelId(NotificationCompat.Builder builder, String channelId) {
        try {
            Class klass = NotificationCompat.Builder.class;
            Field mPublicVersion = klass.getDeclaredField("mPublicVersion");
            mPublicVersion.setAccessible(true);
            Notification n = (Notification) mPublicVersion.get(builder);
            if (n != null)
                NotificationChannelCompat.setChannelId(n, channelId);
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "unable to set public", e);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "unable to set public", e);
        }
        NotificationChannelCompat.setChannelId(builder.mNotification, channelId);
    }

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

    public NotificationChannelCompat(Context context, String id, String name, @Importance int importance) {
        this.channelId = id;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationChannelClass = Class.forName("android.app.NotificationChannel");
                channel = NotificationChannelClass.getConstructor(String.class, CharSequence.class, int.class).newInstance(id, name, importance);
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                Class NotificationManagerClass = nm.getClass();
                Method CreateNotificationChannel = NotificationManagerClass.getDeclaredMethod("createNotificationChannel", NotificationChannelClass);
                CreateNotificationChannel.invoke(nm, channel);
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
                m.invoke(channel, str);
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
                m.invoke(channel, sound, attr);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Uri getSound() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("getSound");
                return (Uri) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public AudioAttributes getAudioAttributes() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("getAudioAttributes");
                return (AudioAttributes) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public boolean shouldVibrate() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("shouldVibrate");
                return (boolean) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public void enableVibration(boolean b) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("enableVibration", boolean.class);
                m.invoke(channel, b);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public long[] getVibrationPattern() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("getVibrationPattern");
                return (long[]) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public boolean shouldShowLights() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("shouldShowLights");
                return (boolean) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public void enableLights(boolean b) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("enableLights", boolean.class);
                m.invoke(channel, b);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int getLightColor() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("getLightColor");
                return (int) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return Color.BLACK;
    }

    public int getImportance() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("getImportance");
                return (int) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;
    }

    public String getGroup() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("getGroup");
                return (String) m.invoke(channel);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public void setGroup(String groupId) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = NotificationChannelClass.getDeclaredMethod("setGroup", String.class);
                m.invoke(channel, groupId);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void apply(NotificationCompat.Builder builder) {
        int defaults = 0;
        Uri sound = null;
        switch (getImportance()) {
            case NotificationManagerCompat.IMPORTANCE_MAX:
                builder.setPriority(NotificationCompat.PRIORITY_MAX);
                defaults |= NotificationCompat.DEFAULT_ALL;
                sound = getSound();
                break;
            case NotificationManagerCompat.IMPORTANCE_HIGH:
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                defaults |= NotificationCompat.DEFAULT_ALL;
                sound = getSound();
                break;
            case NotificationManagerCompat.IMPORTANCE_DEFAULT:
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                defaults |= NotificationCompat.DEFAULT_SOUND;
                sound = getSound();
                break;
            case NotificationManagerCompat.IMPORTANCE_LOW:
                builder.setPriority(NotificationCompat.PRIORITY_LOW);
                break;
            case NotificationManagerCompat.IMPORTANCE_MIN:
                builder.setPriority(NotificationCompat.PRIORITY_MIN);
                break;
        }
        if (sound != null)
            builder.setSound(sound);
        if (shouldVibrate()) {
            long[] p = getVibrationPattern();
            if (p == null)
                defaults |= NotificationCompat.DEFAULT_VIBRATE;
            else
                builder.setVibrate(p);
        }
        if (shouldShowLights())
            builder.setLights(getLightColor(), 1000, 500);
        builder.setDefaults(defaults);

        String group = getGroup();
        if (group != null)
            builder.setGroup(group);

        NotificationChannelCompat.setChannelId(builder, channelId);
    }
}
