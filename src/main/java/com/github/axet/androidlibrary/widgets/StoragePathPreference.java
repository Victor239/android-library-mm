package com.github.axet.androidlibrary.widgets;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;
import android.widget.Button;

import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StoragePathPreference extends EditTextPreference {
    public String def;
    public Storage storage = new Storage(getContext());

    public static String getText(Object o) {
        if (o instanceof StoragePathPreference)
            return ((StoragePathPreference) o).getText();
        if (o instanceof StoragePathPreferenceCompat)
            return ((StoragePathPreferenceCompat) o).getText();
        throw new RuntimeException("unknown class");
    }

    public static String getTitle(Object o) {
        if (o instanceof StoragePathPreference)
            return ((StoragePathPreference) o).getTitle().toString();
        if (o instanceof StoragePathPreferenceCompat)
            return ((StoragePathPreferenceCompat) o).getTitle().toString();
        throw new RuntimeException("unknown class");
    }

    public static boolean callChangeListener(Object o, String name) {
        if (o instanceof StoragePathPreference)
            return ((StoragePathPreference) o).callChangeListener(name);
        if (o instanceof StoragePathPreferenceCompat)
            return ((StoragePathPreferenceCompat) o).callChangeListener(name);
        throw new RuntimeException("unknown class");
    }

    public static void setText(Object o, String name) {
        if (o instanceof StoragePathPreference) {
            ((StoragePathPreference) o).setText(name);
            return;
        }
        if (o instanceof StoragePathPreferenceCompat) {
            ((StoragePathPreferenceCompat) o).setText(name);
            return;
        }
        throw new RuntimeException("unknown class");
    }

    public static String getDefault() {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null) // Android Studio pref editor
            return "/sdcard";
        return ext.getPath();
    }

    public static String getPath(Object object) {
        String path = getText(object);

        if (path == null || path.isEmpty()) {
            path = getDefault();
        }

        return path;
    }

    public static void showDialog(Storage storage, final Object pref) {
        if (!Storage.permitted(storage.getContext(), Storage.PERMISSIONS)) {
            final List<String> ss = new ArrayList<>();
            ss.add(storage.getLocalInternal().getAbsolutePath());
            File ext = storage.getLocalExternal();
            if (ext != null)
                ss.add(ext.getAbsolutePath());
            AlertDialog.Builder builder = new AlertDialog.Builder(storage.getContext());
            builder.setTitle(getTitle(pref));
            Uri u = storage.getStoragePath(getPath(pref));
            File summ = new File(u.getPath());
            builder.setSingleChoiceItems(ss.toArray(new CharSequence[]{}), ss.indexOf(summ.getAbsolutePath()), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String fileName = ss.get(which);
                    if (callChangeListener(pref, fileName)) {
                        setText(pref, fileName);
                    }
                    dialog.dismiss();
                }
            });
            Dialog d = builder.create();
            d.show();
        } else {
            final OpenFileDialog f = new OpenFileDialog(storage.getContext(), OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG);

            Uri u = storage.getStoragePath(getPath(pref));
            File p = new File(u.getPath());

            f.setCurrentPath(p);
            f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    File ff = f.getCurrentPath();
                    if (!ff.isDirectory())
                        ff = ff.getParentFile();
                    String fileName = ff.getPath();
                    if (callChangeListener(pref, fileName)) {
                        setText(pref, fileName);
                    }
                }
            });
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

    public interface DialogDelayed {
        public OpenFileDialog createDialog();
    }

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
    protected void showDialog(Bundle state) {
        showDialog(storage, this);
    }

    @Override
    public boolean callChangeListener(Object newValue) {
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

    void updatePath(File path) {
        File summ = storage.getStoragePath(path);
        setSummary(summ.toString());
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        updatePath(new File(getPath(this)));
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }
}
