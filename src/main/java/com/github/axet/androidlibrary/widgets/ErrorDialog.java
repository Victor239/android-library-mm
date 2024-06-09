package com.github.axet.androidlibrary.widgets;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.preferences.TTSPreferenceCompat;
import com.github.axet.androidlibrary.services.StorageProvider;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

public class ErrorDialog extends AlertDialog.Builder {
    public static final String TAG = ErrorDialog.class.getSimpleName();

    public static String ERROR = "Error"; // title

    public static Thread.UncaughtExceptionHandler OLD;

    public File error;

    public static StringBuilder fullCrash(Context context, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(AboutPreferenceCompat.getApplicationName(context));
        sb.append(" ");
        sb.append(AboutPreferenceCompat.getVersion(context));
        sb.append("\n");
        sb.append("\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        sb.append(sw);
        return sb;
    }

    public static File saveCrash(Context context, Throwable e) {
        Log.e(TAG, "Error", e);
        StringBuilder sb = fullCrash(context, e);
        File d = context.getFilesDir();
        d = new File(d, "crash_" + System.currentTimeMillis() + ".txt");
        try {
            FileUtils.write(d, sb, Charset.defaultCharset());
        } catch (IOException fe) {
            Log.d(TAG, "Write crash", fe);
        }
        return d;
    }

    public static void unhandled(final Context context) {
        if (OLD != null)
            return;
        OLD = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                unhandled(context, t, e);
            }
        });
    }

    public static void unhandled(final Context context, Thread thread) {
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                unhandled(context, t, e);
            }
        });
    }

    public static void unhandled(Context context, Thread t, Throwable e) {
        if (context instanceof Application) {
            saveCrash(context, e);
            Toast.makeText(context, ERROR + " " + toMessage(e), Toast.LENGTH_SHORT).show();
        } else {
            Error(context, e);
        }
    }

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

    public ErrorDialog(@NonNull Context context, Throwable e) {
        this(context, toMessage(e));
    }

    public ErrorDialog(@NonNull Context context, String msg) {
        super(context);
        setTitle(ERROR);
        setMessage(msg);
        setIcon(android.R.drawable.ic_dialog_alert);
        if (error != null) {
            setNeutralButton(TTSPreferenceCompat.getImageText(context, R.drawable.ic_open_in_new_black_24dp, R.attr.colorAccent), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = "crash.txt";
                    Uri uri = Uri.fromFile(error);
                    Intent share = StorageProvider.getProvider().shareIntent(uri, Storage.getTypeByName(name), Storage.getNameNoExt(name));
                    if (!OptimizationPreferenceCompat.startActivity(getContext(), share))
                        android.widget.Toast.makeText(getContext(), "Unsupported", Toast.LENGTH_SHORT).show();
                }
            });
        }
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    public static void Post(final Context context, final Throwable e) {
        final File f = saveCrash(context, e);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(context, toMessage(e), f);
            }
        });
    }

    public static void Post(final Context context, final String e) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(context, e);
            }
        });
    }

    public static AlertDialog Error(Context context, Throwable e) {
        return Error(context, e, null);
    }

    public static AlertDialog Error(Context context, Throwable e, File f) {
        saveCrash(context, e);
        return Error(context, ErrorDialog.toMessage(e), f);
    }

    public static AlertDialog Error(Context context, String msg) {
        return Error(context, msg, null);
    }

    public static AlertDialog Error(Context context, String msg, File f) {
        ErrorDialog builder = new ErrorDialog(context, msg);
        builder.error = f;
        return builder.show();
    }
}
