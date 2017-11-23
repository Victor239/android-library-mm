package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StoragePathPreferenceCompat extends EditTextPreference {
    public String def;
    public Storage storage = new Storage(getContext());
    public OpenStorageChoicer choicer;

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

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StoragePathPreferenceCompat(Context context) {
        this(context, null);
    }

    public void create() {
        choicer = new OpenStorageChoicer(storage, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
            @Override
            public void onResult(Uri uri) {
                if (callChangeListener(uri.toString())) {
                    setText(uri.toString());
                }
            }
        };
        choicer.def = def;
        choicer.setTitle(getTitle().toString());
    }

    @Override
    public void onClick() {
        onClickDialog();
    }

    public void onClickDialog() {
        String f = StoragePathPreference.getPath(this);
        Uri u = storage.getStoragePath(f);
        choicer.show(u);
    }

    @Override
    public boolean callChangeListener(Object newValue) {
        updatePath((String) newValue);
        return super.callChangeListener(newValue);
    }

    // load default value for sharedpropertiesmanager, or set it using xml.
    //
    // can't set dynamic values like '/sdcard'? he-he. so that what it for.
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        def = a.getString(index);
        File path = new File(StoragePathPreference.getDefault(), def);
        return path.getPath();
    }

    public void updatePath(String path) {
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = storage.getStoragePath(path);
            String n = storage.getDisplayName(u); // can be null
            setSummary(n);
            return;
        }
        File f;
        if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            Uri u = Uri.parse(path);
            f = Storage.getFile(u);
        } else {
            f = new File(path);
        }
        File p = storage.getStoragePath(f);
        String s = "";
        if (p != null) // support for 'not selected'
            s = p.toString();
        setSummary(s);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        String f = StoragePathPreference.getPath(this);
        updatePath(f);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    public void setPermissionsDialog(Fragment f, String[] ss, int code) {
        choicer.setPermissionsDialog(f, ss, code);
    }

    public void setPermissionsDialog(Activity a, String[] ss, int code) {
        choicer.setPermissionsDialog(a, ss, code);
    }

    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        choicer.onRequestPermissionsResult(permissions, grantResults);
    }

    public void setStorageAccessFramework(Activity a, int code) {
        choicer.setStorageAccessFramework(a, code);
    }

    public void setStorageAccessFramework(Fragment f, int code) {
        choicer.setStorageAccessFramework(f, code);
    }

    @TargetApi(19)
    public void onActivityResult(int resultCode, Intent data) {
        choicer.onActivityResult(resultCode, data);
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
        choicer.setStorage(storage);
    }
}
