package com.github.axet.androidlibrary.app;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.MessageFormat;

public class SuperUser {
    public static String TAG = SuperUser.class.getSimpleName();

    public static final String SYSTEM = "/system";
    public static final String ETC = "/etc";
    public static final String USR = "/usr";
    public static final String XBIN = "/xbin";
    public static final String SBIN = "/sbin";
    public static final String BIN = "/bin";

    public static final String BIN_SH = which("sh");
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
    public static final String BIN_EXIT = "exit"; // build-in
    public static final String BIN_SET = "set"; // build-in

    public static final String SETE = BIN_SET + " -e";
    public static final String CAT_TO = BIN_CAT + " << EOF > {0}\n{1}\nEOF";
    public static final String REMOUNT_SYSTEM = BIN_MOUNT + " -o remount,rw " + SYSTEM;
    public static final String MKDIRS = BIN_MKDIR + " -p {0}";
    public static final String TOUCH = BIN_TOUCH + " -a {0}";
    public static final String DELETE = BIN_RM + " -rf {0}";
    public static final String MV = BIN_MV + " {0} {1} || ( " + BIN_CP + " {0} {1} && " + BIN_RM + " {0} )";

    public static final String KILL_SELF = BIN_KILL + " -9 $$";
    public static final String SU1 = " || " + KILL_SELF; // some su does not return error codes for pipe scripts, kill it from inside pipe if script fails

    public static class Command {
        public StringBuilder sb = new StringBuilder();
        public boolean sete = true;
        public boolean stdout = true;
        public boolean stderr = true;

        public Command() {
        }

        public Command(String cmd) {
            add(cmd);
        }

        public Command(String cmd, boolean o, boolean e) {
            add(cmd);
            stdout = o;
            stderr = e;
        }

        public Command sete(boolean b) {
            this.sete = b;
            return this;
        }

        public Command add(String cmd) {
            sb.append(cmd);
            sb.append("\n");
            return this;
        }

        public String cmds() {
            return sb.toString();
        }
    }

    public static class Result {
        public int res;
        public String stdout;
        public String stderr;
        public Exception e;

        public Result(Command cmd, Process p) {
            res = p.exitValue();
            captureOutputs(cmd, p);
        }

        public Result(Command cmd, Process p, Exception e) {
            if (p == null) {
                res = 1;
                this.e = e;
                return;
            }

            res = p.exitValue();
            captureOutputs(cmd, p);
            this.e = e;
        }

        public void captureOutputs(Command cmd, Process p) {
            if (cmd.stdout) {
                try {
                    stdout = IOUtils.toString(p.getInputStream(), Charset.defaultCharset());
                } catch (IOException e1) {
                    Log.d(TAG, "unable to get error", e1);
                }
            }
            if (cmd.stderr) {
                try {
                    stderr = IOUtils.toString(p.getErrorStream(), Charset.defaultCharset());
                } catch (IOException e) {
                    Log.d(TAG, "unable to get error", e);
                }
            }
        }

        public boolean ok() {
            return res == 0;
        }

        public void must() {
            if (res != 0)
                throw new RuntimeException(message());
        }

        public String message() {
            if (!stderr.isEmpty())
                return stderr;
            if (e != null)
                return e.toString();
            return "error: " + res;
        }
    }

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

    public static String escape(String p) {
        p = p.replaceAll(" ", "\\ "); // ' ' -> '\'
        p = p.replaceAll("\"", "\\\""); // '"' -> '\"'
        return p;
    }

    public static Result su(String pattern, Object... args) {
        return su(MessageFormat.format(pattern, args));
    }

    public static Result su(String cmd) {
        return su(new Command(cmd));
    }

    public static Result su(Command cmd) {
        Process su = null;
        try {
            su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            if (cmd.sete) {
                os.writeBytes(SETE + "\n");
                os.flush();
            }
            os.writeBytes(cmd.cmds());
            os.flush();
            os.writeBytes(BIN_EXIT + "\n");
            os.flush();
            su.waitFor();
            return new Result(cmd, su);
        } catch (IOException e) {
            return new Result(cmd, su, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(cmd, su, e);
        }
    }

    public static Result reboot() {
        return su(BIN_REBOOT);
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
        su(BIN_AM + " startservice -n " + name.flattenToShortString());
    }

    public static void stopService(Intent intent) {
        stopService(intent.getComponent());
    }

    public static void stopService(ComponentName name) {
        su(BIN_AM + " stopservice -n " + name.flattenToShortString());
    }

    public static boolean isReboot() {
        File f2 = new File(BIN_REBOOT);
        return isRooted() && f2.exists();
    }

    public static Result touch(File f) {
        String p = f.getAbsolutePath();
        return su(TOUCH, escape(p));
    }

    public static Result mkdirs(File f) {
        String p = f.getAbsolutePath();
        return su(MKDIRS, escape(p));
    }

    public static Result delete(File f) {
        String p = f.getAbsolutePath();
        return su(DELETE, escape(p));
    }

    public static Result mv(File f, File to) {
        return su(MV, escape(f.getAbsolutePath()), escape(to.getAbsolutePath()));
    }
}
