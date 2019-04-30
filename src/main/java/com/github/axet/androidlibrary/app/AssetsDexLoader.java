package com.github.axet.androidlibrary.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class AssetsDexLoader {
    public static String TAG = AssetsDexLoader.class.getSimpleName();

    public static final String JAR = "jar";
    public static final String DEX = "dex";
    public static final String CLASSES = "classes.dex";
    public static final String CODE_CAHCE = "code_cache";

    public static DexFile[] getDexs(ClassLoader l) {
        try {
            Field mDexs = l.getClass().getDeclaredField("mDexs");
            mDexs.setAccessible(true);
            return (DexFile[]) mDexs.get(l);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File getCodeCacheDir(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            return context.getCodeCacheDir();
        } else {
            File file = new File(context.getApplicationInfo().dataDir, CODE_CAHCE);
            if (!Storage.mkdirs(file))
                throw new RuntimeException("unable to create: " + file);
            return file;
        }
    }

    public static File getExternalCodeCacheDir(Context context) {
        File ext = context.getExternalCacheDir();
        if (ext == null)
            return null;
        else
            ext = new File(ext.getParentFile(), CODE_CAHCE);
        if (!Storage.mkdirs(ext))
            return null;
        return ext;
    }

    public static File extract(Context context, String asset) throws IOException { // extract asset into .jar
        AssetManager am = context.getAssets();
        InputStream is = am.open(asset);
        File tmp = File.createTempFile(Storage.getNameNoExt(asset), "." + JAR, context.getCacheDir());
        FileOutputStream os = new FileOutputStream(tmp);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static File pack(Context context, String asset) throws IOException { // pack .dex into .jar/classes.dex
        AssetManager am = context.getAssets();
        InputStream is = am.open(asset);
        File tmp = File.createTempFile(Storage.getNameNoExt(asset), "." + JAR, context.getCacheDir());
        ZipOutputStream os = new ZipOutputStream(new FileOutputStream(tmp));
        ZipEntry e = new ZipEntry(CLASSES);
        os.putNextEntry(e);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static ClassLoader deps(Context context, String... deps) {
        ClassLoader parent = null;
        try {
            for (String dep : deps) {
                AssetManager am = context.getAssets();
                String[] aa = am.list("");
                for (String a : aa) {
                    if (a.startsWith(dep)) {
                        if (a.endsWith("." + JAR)) {
                            File tmp = extract(context, a);
                            parent = load(context, tmp, parent);
                            tmp.delete(); // getCodeCacheDir() should keep classes
                        }
                        if (a.endsWith("." + DEX)) {
                            File tmp = pack(context, a);
                            parent = load(context, tmp, parent);
                            tmp.delete(); // getCodeCacheDir() should keep classes
                        }
                    }
                }
            }
            return parent; // return null if no jars/dex found
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader load(Context context, File tmp, ClassLoader parent) {
        if (parent == null)
            parent = DexClassLoader.getSystemClassLoader();
        File ext = getExternalCodeCacheDir(context);
        if (ext == null)
            ext = getCodeCacheDir(context);
        return new DexClassLoader(tmp.getPath(), ext.getPath(), null, parent);
    }

    public static class ThreadLoader {
        public static final HashMap<Class, Object> locks = new HashMap<>();
        public static Thread thread;

        public Context context;
        public String[] deps; // delayed load

        public ThreadLoader(Context context, String... deps) {
            this.context = context;
            this.deps = Arrays.asList(deps).toArray(new String[]{});
        }

        public ThreadLoader(Context context, boolean block, String... deps) {
            this(context, deps);
            init(block);
        }

        public void init(boolean block) {
            if (need())
                load(block);
        }

        public boolean need() {
            return true;
        }

        public ClassLoader deps() {
            return AssetsDexLoader.deps(context, deps);
        }

        public void load() {
            done(deps());
        }

        public Object lock() {
            Class k = getClass();
            synchronized (locks) {
                Object v = locks.get(k);
                if (v == null) {
                    v = new Object();
                    locks.put(k, v);
                }
                return v;
            }
        }

        public void load(boolean block) {
            Thread t;
            synchronized (lock()) {
                if (thread == null) {
                    if (block) {
                        load();
                    } else {
                        thread = new Thread("ThreadLoader") {
                            @Override
                            public void run() {
                                try {
                                    load();
                                } catch (Exception e) {
                                    Log.e(TAG, "error", e);
                                    error(e);
                                }
                            }
                        };
                        thread.start();
                    }
                    return;
                } else {
                    t = thread;
                }
            }
            if (block) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } // else return
        }

        public void error(Exception e) {
        }

        public void done(ClassLoader l) {
        }
    }
}
