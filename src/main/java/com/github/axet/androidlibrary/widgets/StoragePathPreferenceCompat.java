package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.support.v7.preference.EditTextPreference;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.app.Storage;

import java.io.File;

public class StoragePathPreferenceCompat extends EditTextPreference {
    public String def;
    Storage storage = new Storage(getContext());

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
        StoragePathPreference.showDialog(getContext(), this);
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
        File path = new File(StoragePathPreference.getDefault(), def);
        return path.getPath();
    }

    void updatePath(File path) {
        File summ = storage.getStoragePath(path);
        setSummary(summ.toString());
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        updatePath(StoragePathPreference.getPath(this));
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }
}
