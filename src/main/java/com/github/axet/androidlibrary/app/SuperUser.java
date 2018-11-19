package com.github.axet.androidlibrary.app;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
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

    public static int BUF_SIZE = 4 * 1024; // IOUtils#DEFAULT_BUFFER_SIZE

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
    public static final String CAT_TO = BIN_CAT + " << 'EOF' > {0}\n{1}\nEOF";
    public static final String REMOUNT_SYSTEM = BIN_MOUNT + " -o remount,rw " + SYSTEM;
    public static final String MKDIRS = BIN_MKDIR + " -p {0}";
    public static final String TOUCH = BIN_TOUCH + " -a {0}";
    public static final String DELETE = BIN_RM + " -rf {0}";
    public static final String MV = BIN_MV + " {0} {1} || ( " + BIN_CP + " {0} {1} && " + BIN_RM + " {0} )";

    public static final String KILL_SELF = BIN_KILL + " -9 $$";
    public static final String SU1 = " || " + KILL_SELF; // some su does not return error codes for pipe scripts, kill it from inside pipe if script fails

    public static final String EOL = "\n";

    public static class Commands {
        public StringBuilder sb = new StringBuilder();
        public boolean sete = true;
        public boolean stdout = false;
        public Boolean stderr = null; // null means get error only on error

        public Commands() {
        }

        public Commands(String cmd) {
            add(cmd);
        }

        public Commands sete(boolean b) {
            this.sete = b;
            return this;
        }

        public Commands stdout(boolean b) {
            stdout = b;
            return this;
        }

        public Commands stderr(boolean b) {
            stderr = b;
            return this;
        }

        public Commands add(String cmd) {
            sb.append(cmd);
            sb.append(EOL);
            return this;
        }

        public String build() {
            return sb.toString();
        }
    }

    public static class ResultCodeError extends RuntimeException {
        public ResultCodeError(String str) {
            super(str);
        }
    }

    public static class Result {
        public int res;
        public String stdout;
        public String stderr;
        public Throwable e;

        public Result(Commands cmd, Process p) {
            res = p.exitValue();
            captureOutputs(cmd, p);
        }

        public Result(Commands cmd, Process p, Exception e) {
            if (p == null) {
                this.res = 1;
                this.e = e;
                return;
            }
            this.res = p.exitValue();
            this.e = e;
            captureOutputs(cmd, p);
        }

        public void captureOutputs(Commands cmd, Process p) {
            if (cmd.stdout) {
                try {
                    stdout = IOUtils.toString(p.getInputStream(), Charset.defaultCharset());
                } catch (IOException e1) {
                    Log.d(TAG, "unable to get error", e1);
                }
            }
            if ((cmd.stderr != null && cmd.stderr) || (cmd.stderr == null && !ok())) {
                try {
                    stderr = IOUtils.toString(p.getErrorStream(), Charset.defaultCharset());
                } catch (IOException e) {
                    Log.d(TAG, "unable to get error", e);
                }
            }
        }

        public boolean ok() {
            return res == 0 && e == null;
        }

        public Result must() {
            if (!ok())
                throw new ResultCodeError(message());
            return this;
        }

        public String message() {
            if (e != null) {
                while (e.getCause() != null)
                    e = e.getCause();
                if (e.getMessage() == null)
                    return e.getClass().getSimpleName();
                return e.getMessage();
            }
            if (stderr != null && !stderr.isEmpty())
                return stderr;
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

    public static String escape(File p) {
        return escape(p.getAbsolutePath());
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
        return su(new Commands(cmd));
    }

    public static Result su(Commands cmd) {
        Process su = null;
        try {
            su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            if (cmd.sete) {
                os.writeBytes(SETE + EOL);
                os.flush();
            }
            os.writeBytes(cmd.build());
            os.flush();
            os.writeBytes(BIN_EXIT + EOL);
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
        Result r = su(BIN_TRUE);
        return r.ok();
    }

    public static Result startService(Intent intent) {
        return startService(intent.getComponent());
    }

    public static Result startService(ComponentName name) {
        return su(BIN_AM + " startservice -n " + name.flattenToShortString());
    }

    public static Result stopService(Intent intent) {
        return stopService(intent.getComponent());
    }

    public static Result stopService(ComponentName name) {
        return su(BIN_AM + " stopservice -n " + name.flattenToShortString());
    }

    public static boolean isReboot() {
        File f2 = new File(BIN_REBOOT);
        return isRooted() && f2.exists();
    }

    public static Result touch(File f) {
        return su(TOUCH, escape(f));
    }

    public static Result mkdirs(File f) {
        return su(MKDIRS, escape(f));
    }

    public static Result delete(File f) {
        return su(DELETE, escape(f));
    }

    public static Result mv(File f, File to) {
        return su(MV, escape(f), escape(to));
    }

    public static InputStream cat(Uri uri) {
        File f = Storage.getFile(uri);
        Commands cmd = new Commands(MessageFormat.format("cat {0}", escape(f)));
        try {
            final Process su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(cmd.build());
            os.flush();
            os.writeBytes(BIN_EXIT + EOL);
            os.flush();
            return new InputStream() {
                Process p = su;
                InputStream is = p.getInputStream();

                @Override
                public int read() throws IOException {
                    return is.read();
                }

                @Override
                public int read(@NonNull byte[] b, int off, int len) throws IOException {
                    return is.read(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    p.destroy();
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Result write(InputStream is, Uri uri) {
        File f = Storage.getFile(uri);
        Process su = null;
        Commands cmd = new Commands(MessageFormat.format(BIN_CAT + " > {0}", escape(f)));
        try {
            su = Runtime.getRuntime().exec(BIN_SU);
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(cmd.build());
            os.flush();
            byte[] buf = new byte[BUF_SIZE];
            int len;
            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            os.close();
            su.waitFor();
            return new Result(cmd, su);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(cmd, su, e);
        }
    }

    public static OutputStream write(final Uri uri) {
        return new OutputStream() {
            byte[] buf = new byte[BUF_SIZE];
            int off = 0;
            int len = 0; // from 0 .. len
            final Thread thread = new Thread("cat write") {
                @Override
                public void run() {
                    InputStream is = new InputStream() {
                        @Override
                        public int read() throws IOException {
                            synchronized (thread) {
                                try {
                                    if (len == 0)
                                        thread.wait();
                                    if (len == -1)
                                        return -1;
                                    int b = buf[off++];
                                    if (off >= len) {
                                        len = 0;
                                        off = 0;
                                    }
                                    thread.notifyAll();
                                    return b;
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                            }
                        }

                        @Override
                        public int read(@NonNull byte[] b, int off1, int len1) throws IOException {
                            synchronized (thread) {
                                try {
                                    if (len == 0)
                                        thread.wait();
                                    if (len == -1)
                                        return -1;
                                    int l = Math.min(len1, len - off);
                                    System.arraycopy(buf, off, b, off1, l);
                                    off += l;
                                    if (off >= len) {
                                        len = 0;
                                        off = 0;
                                    }
                                    thread.notifyAll();
                                    return l;
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                            }
                        }

                        @Override
                        public void close() throws IOException {
                            super.close();
                        }
                    };
                    SuperUser.write(is, uri);
                }
            };

            {
                thread.start();
            }

            @Override
            public void write(int b) throws IOException {
                synchronized (thread) {
                    try {
                        if (len >= buf.length)
                            thread.wait();
                        buf[len++] = (byte) b;
                        thread.notifyAll();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
            }

            @Override
            public void write(@NonNull byte[] b, int off1, int len1) throws IOException {
                synchronized (thread) {
                    try {
                        while (len1 > 0) {
                            int l;
                            while ((l = Math.min(len1, buf.length - len)) <= 0)
                                thread.wait();
                            System.arraycopy(b, off1, buf, len, l);
                            len += l;
                            off1 += l;
                            len1 -= l;
                            thread.notifyAll();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
            }

            @Override
            public void close() throws IOException {
                super.close();
                synchronized (thread) {
                    try {
                        if (len > 0)
                            thread.wait();
                        len = -1;
                        thread.notifyAll();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
            }
        };
    }
}
