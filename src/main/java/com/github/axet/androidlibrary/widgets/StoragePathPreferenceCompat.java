package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.support.v4.app.Fragment;
import android.support.v7.preference.EditTextPreference;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.app.Storage;

import java.io.File;

public class StoragePathPreferenceCompat extends EditTextPreference {
    public String def;
    public Storage storage = new Storage(getContext());
    public Fragment f;
    public Activity a;
    public String[] ss;
    public int code;
    Activity sa;
    Fragment sf;
    public int scode;

    @TargetApi(19)
    public static boolean showStorageAccessFramework(Context context, String path, String[] ss) {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null)
            return true;
        File[] ff = context.getExternalFilesDirs("");
        int count = 0;
        for (File f : ff) {
            if (f.getAbsolutePath().startsWith(ext.getAbsolutePath())) {
                continue;
            }
            count++;
        }
        if (count > 0) // have external drive?
            return true;
        if (path.startsWith(ContentResolver.SCHEME_CONTENT)) // showed saf before?
            return true;
        if (ss == null) // no permission enabled, use saf as main dialog
            return true;
        return false;
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StoragePathPreferenceCompat(Context context) {
        this(context, null);
    }

    @Override
    protected void onClick() {
        if (f != null) {
            if (!Storage.permitted(f, ss, code))
                return;
        }
        if (a != null) {
            if (!Storage.permitted(a, ss, code))
                return;
        }
        String f = StoragePathPreference.getPath(this);
        if (Build.VERSION.SDK_INT >= 21 && showStorageAccessFramework(getContext(), f, ss)) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (sf != null) {
                sf.startActivityForResult(intent, scode);
                return;
            }
            if (sa != null) {
                sa.startActivityForResult(intent, scode);
                return;
            }
        }
        StoragePathPreference.showDialog(getContext(), this);
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
        if (path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            String n = storage.getTargetName(u);
            setSummary(n);
        } else {
            File summ = storage.getStoragePath(new File(path));
            setSummary(summ.toString());
        }
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
        this.ss = ss;
        this.f = f;
        this.code = code;
    }

    public void setPermissionsDialog(Activity a, String[] ss, int code) {
        this.ss = ss;
        this.a = a;
        this.code = code;
    }

    public void onRequestPermissionsResult() {
        StoragePathPreference.showDialog(getContext(), this);
    }

    public void setStorageAccessFramework(Activity a, int code) {
        this.sa = a;
        this.scode = code;
    }

    public void setStorageAccessFramework(Fragment f, int code) {
        this.sf = f;
        this.scode = code;
    }

    @TargetApi(19)
    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        Uri uri = data.getData();
        getContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        if (callChangeListener(uri.toString())) {
            setText(uri.toString());
        }
    }
}
