package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import java.io.File;

public class StoragePathPreference extends EditTextPreference {
    public String def;
    public OpenFileDialog f;
    AlertDialog d;

    public StoragePathPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StoragePathPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
    }

    public String getDefault() {
        return Environment.getExternalStorageDirectory().getPath();
    }

    @Override
    protected void showDialog(Bundle state) {
        f = new OpenFileDialog(getContext());
        f.setSelectFiles(false);
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

        String path = getText();

        File p = new File(path);

        if (path == null || path.isEmpty() || !p.canRead()) {
            path = getDefault();
            p = new File(path);
        }

        f.setCurrentPath(p);
        f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File ff = f.getCurrentPath();
                if (!ff.isDirectory())
                    ff = ff.getParentFile();
                String fileName = ff.getPath();
                if (callChangeListener(fileName)) {
                    setText(fileName);
                }
            }
        });
        d = f.create();
        d.show();
    }

    // load default value for sharedpropertiesmanager, or set it using xml.
    //
    // can't set dynamic values like '/sdcard'? he-he. so that what it for.
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        def = a.getString(index);
        return new File(getDefault(), def).getPath();
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }
}
