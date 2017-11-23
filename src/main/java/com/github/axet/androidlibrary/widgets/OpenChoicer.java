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
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.widget.Button;
import android.widget.Toast;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        boolean b = false;
        if (a != null)
            b = Storage.permitted(a, perms, permsresult);
        if (f != null)
            b = Storage.permitted(f, perms, permsresult);
        if (b)
            fileDialog();
        else // all failed, dismissed
            onDismiss();
    }

    @TargetApi(21)
    boolean showSAF(boolean force) {
        Intent intent;
        if (type == OpenFileDialog.DIALOG_TYPE.FILE_DIALOG) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (!readonly)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (!readonly)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        // intent.putExtra(EXTRA_INITIAL_URI, old); // API 26+
        if (force || StoragePathPreferenceCompat.showStorageAccessFramework(context, old != null ? old.toString() : null, perms, intent, readonly)) {
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
                resolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
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