package com.github.axet.androidlibrary.widgets;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.github.axet.androidlibrary.app.Storage;

import com.github.axet.androidlibrary.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StoragePathPreference extends EditTextPreference {
    public String def;
    public OpenFileDialog f;
    AlertDialog d;
    Storage storage = new Storage(getContext());

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
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null) // Android Studio pref editor
            return "/sdcard";
        return ext.getPath();
    }

    @Override
    protected void showDialog(Bundle state) {
        Storage storage = new Storage(getContext());
        if (!Storage.permitted(getContext(), Storage.PERMISSIONS)) {
            final List<String> ss = new ArrayList<>();
            ss.add(storage.getLocalInternal().getAbsolutePath());
            File ext = storage.getLocalExternal();
            if (ext != null)
                ss.add(ext.getAbsolutePath());
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(getTitle());
            File summ = storage.getStoragePath(getPath());
            builder.setSingleChoiceItems(ss.toArray(new CharSequence[]{}), ss.indexOf(summ.getAbsolutePath()), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String fileName = ss.get(which);
                    if (callChangeListener(fileName)) {
                        setText(fileName);
                    }
                    dialog.dismiss();
                }
            });
            Dialog d = builder.create();
            d.show();
        } else {
            f = new OpenFileDialog(getContext(), OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG);
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

            File p = getPath();

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
    }

    @Override
    protected boolean callChangeListener(Object newValue) {
        updatePath(new File((String) newValue));
        return super.callChangeListener(newValue);
    }

    // load default value for sharedpropertiesmanager, or set it using xml.
    //
    // can't set dynamic values like '/sdcard'? he-he. so that what it for.
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        def = a.getString(index);
        File path = new File(getDefault(), def);
        return path.getPath();
    }

    File getPath() {
        String path = getText();

        if (path == null || path.isEmpty()) {
            path = getDefault();
        }

        File p = new File(path);

        return p;
    }

    void updatePath(File path) {
        File summ = storage.getStoragePath(path);
        setSummary(summ.toString());
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        updatePath(getPath());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }
}
