package com.github.axet.androidlibrary.widgets;

import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.widget.Button;
import android.widget.Toast;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OpenStorageChoicer extends OpenChoicer {
    public String def; // default name (for default location) can be null
    public Storage storage;

    public static String getDefault() {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null) // Android Studio pref editor
            return "/sdcard";
        return ext.getPath();
    }

    public OpenStorageChoicer(OpenFileDialog.DIALOG_TYPE type, boolean readonly) {
        super(type, readonly);
    }

    public OpenStorageChoicer(Storage storage, OpenFileDialog.DIALOG_TYPE type, boolean readonly) {
        super(type, readonly);
        this.storage = storage;
    }

    public OpenStorageChoicer(Storage storage, OpenFileDialog.DIALOG_TYPE type, boolean readonly, String def) {
        super(type, readonly);
        this.storage = storage;
        this.def = def;
    }

    @Override
    public OpenFileDialog fileDialogBuild() {
        final OpenFileDialog d = super.fileDialogBuild();

        if (def != null) {
            d.setNeutralButton(R.string.default_folder, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    File path = new File(getDefault(), def);
                    path = storage.getStoragePath(path);
                    d.setCurrentPath(path);
                    Toast.makeText(context, path.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        return d;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public void setDefault(String def) {
        this.def = def;
    }
}
