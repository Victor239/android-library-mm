package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.widget.Button;
import android.widget.Toast;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class OpenChoicer {
    public Context context;
    public OpenFileDialog.DIALOG_TYPE type;
    public boolean readonly;
    public Uri old;
    public Activity a;
    public Fragment f;
    public String[] perms;
    public int permsresult;
    public Activity sa;
    public Fragment sf;
    public int sresult;
    public String title;

    public static boolean isExternalSDPortable(Context context) {
        String path = System.getenv(OpenFileDialog.ANDROID_STORAGE);
        if (path == null || path.isEmpty())
            path = OpenFileDialog.DEFAULT_STORAGE_PATH;

        File storage = new File(path);
        File[] ff = storage.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String name = file.getName();
                Matcher m = OpenFileDialog.DEFAULT_STORAGE_PATTERN.matcher(name);
                if (m.matches()) {
                    if (file.canWrite())
                        return false; // does we have rw access /storage/1234-1234/* if so, skip
                    return true;
                }
                return false;
            }
        });
        if (ff != null && ff.length > 0) // we have files like /storage/1234-1234
            return true;

        File ext = Environment.getExternalStorageDirectory();
        if (ext == null)
            return false;

        ff = ContextCompat.getExternalFilesDirs(context, ""); // can show no external dir: https://stackoverflow.com/questions/33350250
        int count = 0;
        for (File f : ff) {
            if (f == null || f.getAbsolutePath().startsWith(ext.getAbsolutePath())) { // f can be null, if media unmounted
                continue;
            }
            count++;
        }
        if (count > 0) // have external SD formatted as portable?
            return true;

        return false;
    }

    @TargetApi(19)
    public static boolean showStorageAccessFramework(Context context, String path, String[] ss, boolean readonly) {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null)
            return true;
        if (!readonly && isExternalSDPortable(context)) // does external SD card formatted as portable?
            return true;
        if (path != null && path.startsWith(ContentResolver.SCHEME_CONTENT)) // showed saf before?
            return true;
        if (ss == null) // no permission enabled, use saf as main dialog
            return true;
        return false;
    }

    @TargetApi(19)
    public static boolean showStorageAccessFramework(Context context, String path, String[] ss, Intent intent, boolean readonly) {
        if (!OptimizationPreferenceCompat.isCallable(context, intent)) // samsung 6.0 has no Intent.OPEN_DOCUMENT activity to start, check before call
            return false;
        return showStorageAccessFramework(context, path, ss, readonly);
    }

    public OpenChoicer(OpenFileDialog.DIALOG_TYPE type, boolean readonly) {
        this.type = type;
        this.readonly = readonly;
    }

    public void setPermissionsDialog(Fragment f, String[] ss, int code) {
        this.context = f.getContext();
        this.f = f;
        this.perms = ss;
        this.permsresult = code;
    }

    public void setPermissionsDialog(Activity a, String[] ss, int code) {
        this.context = a;
        this.a = a;
        this.perms = ss;
        this.permsresult = code;
    }

    public void setStorageAccessFramework(Activity a, int code) {
        this.context = a;
        this.sa = a;
        this.sresult = code;
    }

    public void setStorageAccessFramework(Fragment f, int code) {
        this.context = f.getContext();
        this.sf = f;
        this.sresult = code;
    }

    public void show(Uri old) {
        this.old = old;
        if (Build.VERSION.SDK_INT >= 21) {
            boolean nofile = a == null && f == null;
            if (showSAF(nofile))
                return;
        }
        if (a != null) {
            if (Storage.permitted(a, perms, permsresult))
                fileDialog();
            return; // perms shown
        }
        if (f != null) {
            if (Storage.permitted(f, perms, permsresult))
                fileDialog();
            return; // perms shown
        }
        onDismiss(); // all failed, dismissed
    }

    @TargetApi(21)
    boolean showSAF(boolean force) {
        Intent intent;
        if (type == OpenFileDialog.DIALOG_TYPE.FILE_DIALOG) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (!readonly)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        // intent.putExtra(EXTRA_INITIAL_URI, old); // API 26+
        if (force || showStorageAccessFramework(context, old != null ? old.toString() : null, perms, intent, readonly)) {
            if (sa != null) {
                sa.startActivityForResult(intent, sresult);
                return true;
            }
            if (sf != null) {
                sf.startActivityForResult(intent, sresult);
                return true;
            }
        }
        return false;
    }

    public void fileDialog() {
        fileDialogBuild().show();
    }

    public OpenFileDialog fileDialogBuild() {
        final OpenFileDialog dialog = new OpenFileDialog(context, type, readonly);
        if (old != null)
            dialog.setCurrentPath(new File(old.getPath()));
        dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                File f = dialog.getCurrentPath();
                onResult(Uri.fromFile(f), false);
            }
        });
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                OpenChoicer.this.onCancel();
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                OpenChoicer.this.onDismiss();
            }
        });
        return dialog;
    }

    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            onCancel();
            onDismiss();
            return;
        }
        Uri u = data.getData();
        if (Build.VERSION.SDK_INT >= 21) {
            ContentResolver resolver = context.getContentResolver();
            try {
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (!readonly)
                    flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                resolver.takePersistableUriPermission(u, flags);
                onResult(u, false);
            } catch (SecurityException e) { // remote SAF?
                onResult(u, true);
            }
        }
        onDismiss();
    }

    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        if (Storage.permitted(context, permissions)) {
            fileDialog();
        } else {
            onRequestPermissionsFailed();
        }
    }

    public void onRequestPermissionsFailed() {
        if (showSAF(true))
            return;
        Toast.makeText(context, R.string.not_permitted, Toast.LENGTH_SHORT).show();
        onCancel();
        onDismiss();
    }

    public void onDismiss() {
    }

    public void onCancel() {
    }

    public void onResult(Uri uri) {
        ;
    }

    public void onResult(Uri uri, boolean tmp) {
        onResult(uri);
    }

    public void setTitle(String title) {
        this.title = title;
    }
}