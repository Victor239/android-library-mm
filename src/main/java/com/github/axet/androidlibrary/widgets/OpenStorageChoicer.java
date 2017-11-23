package com.github.axet.androidlibrary.widgets;

import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.widget.Button;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OpenStorageChoicer extends OpenChoicer {
    public String def;
    public Storage storage;

    public OpenStorageChoicer(Storage storage, OpenFileDialog.DIALOG_TYPE type, boolean readonly) {
        super(type, readonly);
        this.storage = storage;
    }

    public OpenStorageChoicer(OpenFileDialog.DIALOG_TYPE type, boolean readonly) {
        super(type, readonly);
    }

    public static String getDefault() {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null) // Android Studio pref editor
            return "/sdcard";
        return ext.getPath();
    }

    public void showFiles() {
        final List<String> ss = new ArrayList<>();
        File local = storage.getLocalInternal();
        ss.add(local.getAbsolutePath());
        File ext = storage.getLocalExternal();
        if (ext != null)
            ss.add(ext.getAbsolutePath());
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        File summ = Storage.getFile(old);
        builder.setSingleChoiceItems(ss.toArray(new CharSequence[]{}), ss.indexOf(summ.getAbsolutePath()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String fileName = ss.get(which);
                File f = new File(fileName);
                Uri u = Uri.fromFile(f);
                onResult(u, true);
                dialog.dismiss();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                OpenStorageChoicer.this.onCancel();
            }
        });
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                OpenStorageChoicer.this.onDismiss();
            }
        });
        Dialog d = builder.create();
        d.show();
    }

    @Override
    public void fileDialog() {
        if (!readonly && !Storage.permitted(context, Storage.PERMISSIONS)) {
            showFiles();
        } else {
            final OpenFileDialog f = fileDialogBuild();
            final AlertDialog d = f.create();
            f.setChangeFolderListener(new Runnable() {
                @Override
                public void run() {
                    File ff = f.getCurrentPath();
                    if (!ff.isDirectory())
                        ff = ff.getParentFile();
                    if (!ff.canWrite()) {
                        Button b2 = d.getButton(AlertDialog.BUTTON_POSITIVE);
                        b2.setEnabled(false);
                    } else {
                        Button b2 = d.getButton(AlertDialog.BUTTON_POSITIVE);
                        b2.setEnabled(true);
                    }
                }
            });
            d.show();
        }
    }

    @Override
    public void onRequestPermissionsFailed() {
        if (showSAF(true))
            return;
        showFiles();
    }

    @Override
    public OpenFileDialog fileDialogBuild() {
        final OpenFileDialog d = super.fileDialogBuild();

        d.setNeutralButton(R.string.default_folder, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File path = new File(getDefault(), def);
                path = storage.getStoragePath(path);
                d.setCurrentPath(path);
            }
        });

        return d;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }
}
