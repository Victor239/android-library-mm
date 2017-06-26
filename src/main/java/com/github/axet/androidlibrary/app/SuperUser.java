package com.github.axet.androidlibrary.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SuperUser {
    public static String TAG = SuperUser.class.getSimpleName();

    public static final String BIN_SU = "/system/xbin/su";
    public static final String BIN_TRUE = "/usr/bin/true";
    public static final String BIN_REBOOT = "/system/bin/reboot";

    public static void su(String cmd) {
        try {
            Process su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            su.waitFor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void reboot() {
        su(BIN_REBOOT);
    }

    public static boolean isRooted() {
        File f = new File(BIN_SU);
        return f.exists();
    }

    public static boolean rootTest() {
        try {
            su(BIN_TRUE);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void startService(Intent intent) {
        startService(intent.getComponent());
    }

    public static void startService(ComponentName name) {
        su("am startservice -n " + name.flattenToShortString());
    }

    public static void stopService(Intent intent) {
        stopService(intent.getComponent());
    }

    public static void stopService(ComponentName name) {
        su("am stopservice -n " + name.flattenToShortString());
    }

    public static boolean isReboot() {
        File f2 = new File(BIN_REBOOT);
        return isRooted() && f2.exists();
    }
}
