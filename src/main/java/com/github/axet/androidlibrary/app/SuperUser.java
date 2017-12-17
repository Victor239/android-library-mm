package com.github.axet.androidlibrary.app;

import android.content.ComponentName;
import android.content.Intent;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class SuperUser {
    public static String TAG = SuperUser.class.getSimpleName();

    public static final String SYSTEM = "/system";

    public static final String BIN_SU = SYSTEM + "/xbin/su";
    public static final String BIN_TRUE = "/usr/bin/true";
    public static final String BIN_REBOOT = SYSTEM + "/bin/reboot";

    public static final String SUCAT = "cat << EOF > {0}\n{1}\nEOF";
    public static final String MOUNT = "mount {0}";
    public static final String REMOUNT_SYSTEM = MessageFormat.format(MOUNT, "-o remount,rw " + SYSTEM);
    public static final String MKDIRS = "mkdir -p {0}";
    public static final String TOUCH = "touch -a {0}";
    public static final String DELETE = "rm -rf {0}";
    public static final String CHMOD = "chmod {0} {1}";
    public static final String CHOWN = "chown {0} {1}";
    public static final String MV = "mv {0} {1} || ( cp {0} {1} && rm {0} )";
    public static final String EXIT = "exit";

    public static final String KILL = " || kill -9 $$"; // some su does not return error codes in scripts, kill it

    public static int su(String pattern, Object... args) {
        return su(MessageFormat.format(pattern, args));
    }

    public static int su(String cmd) {
        try {
            Process su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(cmd + KILL + "\n");
            os.flush();
            os.writeBytes(EXIT + "\n");
            os.flush();
            su.waitFor();
            return su.exitValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return -1;
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

    public static String escapePath(String p) {
        p = p.replaceAll(" ", "\\ "); // ' ' -> '\'
        p = p.replaceAll("\"", "\\\""); // '"' -> '\"'
        return p;
    }

    public static boolean touch(File f) {
        String p = f.getAbsolutePath();
        return su(TOUCH, escapePath(p)) == 0;
    }

    public static boolean mkdirs(File f) {
        String p = f.getAbsolutePath();
        return su(MKDIRS, escapePath(p)) == 0;
    }

    public static boolean delete(File f) {
        String p = f.getAbsolutePath();
        return su(DELETE, escapePath(p)) == 0;
    }

    public static boolean mv(File f, File to) {
        return su(MV, escapePath(f.getAbsolutePath()), escapePath(to.getAbsolutePath())) == 0;
    }
}
