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
 * &lt;uses-permission android:name="android.permission.ACCESS_WIFI_STATE" /&gt;
 * &lt;uses-permission android:name="android.permission.CHANGE_WIFI_STATE" /&gt;
 * &lt;uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" /&gt;
 */
public abstract class WifiKeepReceiver extends BroadcastReceiver {

    public static String TAG = WifiKeepReceiver.class.getSimpleName();

    public final String WIFI = getClass().getName() + ".WIFI"; // superclass prefix

    public static final String BIN_PING = SuperUser.which("ping");

    public Context context;
    public IntentFilter filter = new IntentFilter();

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

    public WifiKeepReceiver(Context context) {
        this.context = context;
        filter.addAction(WIFI);
    }

    public void create() {
        context.registerReceiver(this, filter);
        wifi();
    }

    public void close() {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, intent.toString() + " " + action);
        if (action == null)
            return;
        if (action.equals(WIFI)) {
            wifi();
        }
    }

    public void wifi() {
        Intent intent = new Intent();
        intent.setAction(WIFI);
        if (getKeep()) {
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

    public abstract boolean getKeep();
}
