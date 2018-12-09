package com.github.axet.androidlibrary.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.github.axet.androidlibrary.app.SuperUser;

public class ErrorDialog extends AlertDialog.Builder {
    public static final String TAG = ErrorDialog.class.getSimpleName();

    public static Throwable getCause(Throwable e) { // get to the bottom
        Throwable c = null;
        while (e != null) {
            c = e;
            e = e.getCause();
        }
        return c;
    }

    public static String toMessage(Throwable e) { // eat RuntimeException's
        Throwable p = e;
        while (e instanceof RuntimeException) {
            e = e.getCause();
            if (e != null)
                p = e;
        }
        String msg = p.getMessage();
        if (msg == null || msg.isEmpty())
            msg = p.getClass().getCanonicalName();
        return msg;
    }

    public ErrorDialog(@NonNull Context context, String msg) {
        super(context);
        setTitle("Error");
        setMessage(msg);
        setIcon(android.R.drawable.ic_dialog_alert);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    public static void Post(final Activity a, final Throwable e) {
        Log.e(TAG, "Error", e);
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (a.isFinishing())
                    return;
                Error(a, toMessage(e));
            }
        });
    }

    public static void Post(final Activity a, final String e) {
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (a.isFinishing())
                    return;
                Error(a, e);
            }
        });
    }

    public static AlertDialog Error(Activity a, Throwable e) {
        Log.e(TAG, "Error", e);
        return Error(a, ErrorDialog.toMessage(e));
    }

    public static AlertDialog Error(Activity a, String msg) {
        ErrorDialog builder = new ErrorDialog(a, msg);
        return builder.show();
    }
}
