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
    public static final String XBIN = "/xbin";
    public static final String SBIN = "/sbin";
    public static final String BIN = "/bin";

    public static final String BIN_SU = which("su");
    public static final String BIN_TRUE = which("true");
    public static final String BIN_REBOOT = which("reboot");
    public static final String BIN_MOUNT = which("mount");
    public static final String BIN_CAT = which("cat");
    public static final String BIN_TOUCH = which("touch");
    public static final String BIN_RM = which("rm");
    public static final String BIN_MKDIR = which("mkdir");
    public static final String BIN_CHMOD = which("chmod");
    public static final String BIN_CHOWN = which("chown");
    public static final String BIN_MV = which("mv");
    public static final String BIN_CP = which("cp");
    public static final String BIN_KILL = which("kill");
    public static final String BIN_AM = which("am");
    public static final String BIN_EXIT = "exit"; // buildin

    public static final String CAT_TO = BIN_CAT + " << EOF > {0}\n{1}\nEOF\n";
    public static final String REMOUNT_SYSTEM = BIN_MOUNT + " -o remount,rw " + SYSTEM;
    public static final String MKDIRS = BIN_MKDIR + " -p {0}";
    public static final String TOUCH = BIN_TOUCH + " -a {0}";
    public static final String DELETE = BIN_RM + " -rf {0}";
    public static final String MV = BIN_MV + " {0} {1} || ( " + BIN_CP + " {0} {1} && " + BIN_RM + " {0} )";

    public static final String KILL_SELF = BIN_KILL + " -9 $$";
    public static final String SU1 = " || " + KILL_SELF;

    public static String which(String cmd) {
        for (String s : new String[]{SYSTEM + XBIN, SYSTEM + SBIN, SYSTEM + BIN,
                SYSTEM + USR + SBIN, SYSTEM + USR + BIN,
                USR + SBIN, USR + BIN,
                SBIN, BIN}) {
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

    // https://stackoverflow.com/questions/28734455/java-converting-file-pattern-to-regular-expression-pattern
    public static String wildcard(String wildcard) {
        StringBuilder s = new StringBuilder(wildcard.length());
        s.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    s.append(".*");
                    break;
                case '?':
                    s.append(".");
                    break;
                case '^': // escape character in cmd.exe
                    s.append("\\");
                    break;
                // escape special regexp-characters
                case '(':
                case ')':
                case '[':
                case ']':
                case '$':
                case '.':
                case '{':
                case '}':
                case '|':
                case '\\':
                    s.append("\\");
                    s.append(c);
                    break;
                default:
                    s.append(c);
                    break;
            }
        }
        s.append('$');
        return (s.toString());
    }

    public static int su(String pattern, Object... args) {
        return su1(MessageFormat.format(pattern, args));
    }

    public static int su1(String cmd) {
        return su(cmd + SU1);
    }

    public static int su(String cmd) {
        try {
            Process su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.flush();
            os.writeBytes(BIN_EXIT + "\n");
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
        su1(BIN_REBOOT);
    }

    public static boolean isRooted() {
        File f = new File(BIN_SU);
        return f.exists();
    }

    public static boolean rootTest() {
        try {
            su1(BIN_TRUE);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void startService(Intent intent) {
        startService(intent.getComponent());
    }

    public static void startService(ComponentName name) {
        su1(BIN_AM + " startservice -n " + name.flattenToShortString());
    }

    public static void stopService(Intent intent) {
        stopService(intent.getComponent());
    }

    public static void stopService(ComponentName name) {
        su1(BIN_AM + " stopservice -n " + name.flattenToShortString());
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
