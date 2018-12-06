package com.github.axet.androidlibrary.app;

import android.app.Notification;
import android.content.Context;
import android.util.Log;

public class NotificationManagerCompat {
    public static final String TAG = NotificationManagerCompat.class.getSimpleName();

    public android.support.v4.app.NotificationManagerCompat nm;

    public static NotificationManagerCompat from(Context context) {
        return new NotificationManagerCompat(context);
    }

    public NotificationManagerCompat(Context context) {
        nm = android.support.v4.app.NotificationManagerCompat.from(context);
    }

    public void cancel(int id) {
        nm.cancel(id);
    }

    public void notify(int id, Notification notification) {
        try {
            nm.notify(id, notification);
        } catch (Exception e) { // catching TransactionTooLargeException and retyring
            Log.e(TAG, "notify", e);
            nm.notify(id, notification);
        }
    }
}
