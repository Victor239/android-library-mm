package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.services.DeviceAdmin;

public class AdminPreferenceCompat extends SwitchPreferenceCompat {

    Activity a;
    Fragment f;
    int code;

    @TargetApi(21)
    public AdminPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    @TargetApi(21)
    public AdminPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public AdminPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public AdminPreferenceCompat(Context context) {
        super(context);
        create();
    }

    void create() {
        onResume();
    }

    public void setActivity(Activity a, int code) {
        this.a = a;
        this.code = code;
    }

    public void setFragment(Fragment f, int code) {
        this.f = f;
        this.code = code;
    }

    public void onResume() {
        updateAdmin();
        setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                Context context = getContext();
                boolean b = (boolean) o;
                if (b) {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName c = new ComponentName(context, DeviceAdmin.class);
                    if (!dpm.isAdminActive(c)) {
                        if (Build.VERSION.SDK_INT >= 18) {
                            if (dpm.isDeviceOwnerApp(context.getPackageName())) // already device owner exit
                                return true; // allow change
                        }
                        requestAdmin();
                        return false; // cancel change
                    }
                } else {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName c = new ComponentName(context, DeviceAdmin.class);
                    if (dpm.isAdminActive(c)) {
                        dpm.removeActiveAdmin(c);
                    }
                    if (Build.VERSION.SDK_INT >= 18) {
                        if (dpm.isDeviceOwnerApp(context.getPackageName())) { // device owner can changed
                            DeviceAdmin.removeDeviceOwner(context);
                        }
                    }
                    updateAdminSummary(); // update summary
                }
                return true; // allow change
            }
        });
    }

    public void updateAdminSummary() {
        Context context = getContext();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        String summary = "Enable device admin access";

        if (Build.VERSION.SDK_INT >= 18) {
            if (dpm.isDeviceOwnerApp(context.getPackageName())) { // device owner can't cahnge
                summary += " (Device Owner enabled)";
            }
        }

        setSummary(summary);
    }

    public void updateAdmin() {
        Context context = getContext();

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName c = new ComponentName(context, DeviceAdmin.class);

        if (isChecked()) {
            boolean b = dpm.isAdminActive(c);
            if (Build.VERSION.SDK_INT >= 24) {
                b |= dpm.isDeviceOwnerApp(context.getPackageName());
            }
            setChecked(b);
        }

        updateAdminSummary();
    }

    public boolean requestAdmin() {
        Context context = getContext();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName c = new ComponentName(context, DeviceAdmin.class);
        if (!dpm.isAdminActive(c)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, c);
            if (a != null)
                a.startActivityForResult(intent, code);
            if (f != null)
                f.startActivityForResult(intent, code);
            return true;
        }
        return false;
    }

    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) // 0 - cancel, -1 - ok
            setChecked(true);
    }
}
