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
    public static final String ETC = "/etc";
    public static final String USR = "/usr";

    public static final String BIN_SU = path("su");
    public static final String BIN_TRUE = path("true");
    public static final String BIN_REBOOT = path("reboot");
    public static final String BIN_MOUNT = path("mount");
    public static final String BIN_CAT = path("cat");
    public static final String BIN_TOUCH = path("touch");
    public static final String BIN_RM = path("rm");
    public static final String BIN_MKDIR = path("mkdir");
    public static final String BIN_CHMOD = path("chmod");
    public static final String BIN_CHOWN = path("chown");
    public static final String BIN_MV = path("mv");
    public static final String BIN_CP = path("cp");
    public static final String BIN_KILL = path("kill");

    public static final String SUCAT = BIN_CAT + " << EOF > {0}\n{1}\nEOF";
    public static final String MOUNT = BIN_MOUNT + " {0}";
    public static final String REMOUNT_SYSTEM = MessageFormat.format(MOUNT, "-o remount,rw " + SYSTEM);
    public static final String MKDIRS = BIN_MKDIR + " -p {0}";
    public static final String TOUCH = BIN_TOUCH + " -a {0}";
    public static final String DELETE = BIN_RM + " -rf {0}";
    public static final String CHMOD = BIN_CHMOD + " {0} {1}";
    public static final String CHOWN = BIN_CHOWN + "{0} {1}";
    public static final String MV = BIN_MV + " {0} {1} || ( " + BIN_CP + " {0} {1} && " + BIN_RM + " {0} )";
    public static final String EXIT = "exit";

    public static final String KILL = " || " + BIN_KILL + " -9 $$"; // some su does not return error codes in scripts, kill it

    public static String path(String cmd) {
        for (String s : new String[]{SYSTEM + "/xbin", SYSTEM + "/sbin", SYSTEM + "/bin",
                SYSTEM + USR + "/sbin", SYSTEM + USR + "/bin",
                USR + "/sbin", USR + "/bin",
                "/sbin", "/bin"}) {
            String f = find(s + "/" + cmd);
            if (f != null)
                return f;
        }
        return cmd;
    }

    public static String find(String... args) {
        for (String s : args) {
            File f = new File(s);
            if (f.exists())
                return s;
        }
        return null;
    }

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
