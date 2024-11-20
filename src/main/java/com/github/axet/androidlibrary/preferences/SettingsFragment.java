package com.github.axet.androidlibrary.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.androidlibrary.activities.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String TAG = SettingsFragment.class.getSimpleName();

    public Handler handler = new Handler();
    OptimizationPreferenceCompat.SettingsReceiver receiver;

    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        Bundle bundle = new Bundle();
        fragment.setArguments(bundle);
        return fragment;
    }

    public static void notificationPolicySettings(Context context) { // also need manifeset settings
        if (Build.VERSION.SDK_INT >= 23)
            context.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    public static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            return AppCompatSettingsThemeActivity.sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, value);
        }
    };

    public static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getAll().get(preference.getKey()));
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
    }

    @SuppressLint("RestrictedApi")
    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        return view;
    }

    public void fixFabMargins(final View view, int r_fab_margin, int r_appbar_padding_top) {
        final Context context = getContext();
        RecyclerView v = getListView();

        int fab_margin = (int) getResources().getDimension(r_fab_margin);
        int fab_size = ThemeUtils.dp2px(getActivity(), 61);
        int pad = 0;
        int top = 0;
        if (Build.VERSION.SDK_INT <= 16) { // so, it bugged only on 16
            pad = ThemeUtils.dp2px(context, 10);
            top = (int) getResources().getDimension(r_appbar_padding_top);
        }

        v.setClipToPadding(false);
        v.setPadding(pad, top, pad, pad + fab_size + fab_margin);

        // FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ContentFrameLayout.LayoutParams.WRAP_CONTENT, ContentFrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        // lp.setMargins(fab_margin, fab_margin, fab_margin, fab_margin);
        // fab.setLayoutParams(lp);
        // layout.addView(fab);

        // fix nexus 9 tabled bug, when fab showed offscreen
        handler.post(new Runnable() {
            @Override
            public void run() {
                view.requestLayout();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void setMenuVisibility(final boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (!menuVisible)
            return;
        Activity a = getActivity();
        if (a == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    setMenuVisibility(menuVisible);
                }
            });
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
}
