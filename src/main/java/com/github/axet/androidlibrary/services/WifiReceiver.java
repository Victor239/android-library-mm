package com.github.axet.androidlibrary.services;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.SuperUser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *     <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 *     <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
 *     <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 */
public class WifiReceiver extends BroadcastReceiver {

    public static String TAG = WifiReceiver.class.getSimpleName();

    public static final String WIFI = WifiReceiver.class.getCanonicalName() + ".WIFI";

    public static final String BIN_PING = SuperUser.which("ping");

    public Context context;
    IntentFilter filter = new IntentFilter();

    public static boolean isConnectedWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    public static void wifi(final Context context, boolean keep) {
        Intent intent = new Intent();
        intent.setAction(WIFI);
        if (keep) {
            long inSeconds = 1 * 60;
            final long next = System.currentTimeMillis() + (inSeconds * 1000l);
            AlarmManager.set(context, next, intent);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

                    boolean isConnected = false;
                    boolean isWiFi = false;
                    if (activeNetwork != null) {
                        isConnected = activeNetwork.isConnected();
                        isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
                    }

                    WifiManager w = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    DhcpInfo d = w.getDhcpInfo();

                    if (!isWiFi || !isConnected || !ping(d.gateway) || !dns()) {
                        w.setWifiEnabled(false);
                        w.setWifiEnabled(true);
                    }

                    if (Build.VERSION.SDK_INT <= 18 && SuperUser.isRooted()) {
                        gtalk(context);
                    }

                    if (isWiFi && isConnected) {
                        Intent gt = new Intent("com.google.android.intent.action.GTALK_HEARTBEAT");
                        context.sendBroadcast(gt);
                        Intent mcs = new Intent("com.google.android.intent.action.MCS_HEARTBEAT");
                        context.sendBroadcast(mcs);
                    }
                }
            });
            t.start();
        } else {
            AlarmManager.cancel(context, intent);
        }
    }

    public static boolean ping(String ip) {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process mIpAddrProcess = runtime.exec(BIN_PING + " -q -c1 -w2 " + ip);
            int mExitValue = mIpAddrProcess.waitFor();
            if (mExitValue == 0) {
                return true;
            } else {
                return false;
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Log.d(TAG, "ping failed", e);
        }
        return false;
    }

    public static boolean ping(int ip) {
        String ipStr = String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        return ping(ipStr);
    }

    public static boolean dns() {
        InetAddress a = null;
        try {
            a = InetAddress.getByName("google.com");
        } catch (UnknownHostException e) {
        }
        return a != null && !a.equals("");
    }

    // https://forum.fairphone.com/t/help-with-xprivacy-settings-relating-to-google-apps/5741/7
    public static void gtalk(Context context) {
        ComponentName gtalk = new ComponentName("com.google.android.gsf", "com.google.android.gsf.gtalkservice.service.GTalkService");
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (gtalk.compareTo(service.service) == 0) {
                return;
            }
        }
        try {
            SuperUser.startService(gtalk);
        } catch (RuntimeException e) {
            Log.d(TAG, "Unable to start gtalk", e);
        }
    }

    public WifiReceiver(Context context) {
        this.context = context;
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WIFI);
    }

    public void wifiFilter() {
        filter = new IntentFilter();
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    }

    public void keepFilter() {
        filter = new IntentFilter();
        filter.addAction(WIFI);
    }

    public void create() {
        context.registerReceiver(this, filter);
        keep();
        if (getOnly() && !WifiReceiver.isConnectedWifi(context)) {
            pause();
        }
    }

    public void close() {
        context.unregisterReceiver(this);
    }

    public void keep() {
        wifi(context, getKeep());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, intent.toString() + " " + action);
        if (action == null)
            return;
        if (action.equals(WIFI)) {
            keep();
        }
        boolean wifi = getOnly();
        if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
            SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
            Log.d(TAG, state.toString());
            if (wifi) { // suplicant only correspond to 'wifi only'
                if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                    resume();
                    return;
                }
                if (isConnectedWifi(context)) { // maybe 'state' have incorrect state. check system service additionaly.
                    resume();
                    return;
                }
                pause();
                return;
            }
        }
        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            NetworkInfo state = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            Log.d(TAG, state.toString());
            if (state.isConnected()) {
                if (wifi) { // wifi only?
                    switch (state.getType()) {
                        case ConnectivityManager.TYPE_WIFI:
                        case ConnectivityManager.TYPE_ETHERNET:
                            resume();
                            return;
                    }
                } else { // resume for any connection type
                    resume();
                    return;
                }
            }
            // if not state.isConnected() maybe it is not correct, check service information
            if (wifi) {
                if (isConnectedWifi(context)) {
                    resume();
                    return;
                }
            } else {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null) { // connected to the internet
                    resume();
                    return;
                }
            }
            pause();
        }
    }

    public void resume() {
    }

    public void pause() {
    }

    public boolean getKeep() {
        return false;
    }

    public boolean getOnly() {
        return false;
    }
}
