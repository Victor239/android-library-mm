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
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
            Field mDexs = getPrivateField(l.getClass(), "mDexs");
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
        File tmp = new File(context.getCacheDir(), Storage.getNameNoExt(asset) + "." + JAR);
        FileOutputStream os = new FileOutputStream(tmp);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static File pack(Context context, String asset) throws IOException { // pack .dex into .jar/classes.dex
        AssetManager am = context.getAssets();
        InputStream is = am.open(asset);
        File tmp = new File(context.getCacheDir(), Storage.getNameNoExt(asset) + "." + JAR);
        ZipOutputStream os = new ZipOutputStream(new FileOutputStream(tmp));
        ZipEntry e = new ZipEntry(CLASSES);
        os.putNextEntry(e);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static Field getPrivateField(Class cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public static Method getPrivateMethod(Class c, String name, Class<?>... args) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    public static Constructor getPrivateConstructor(Class c, Class<?>... args) throws NoSuchMethodException {
        Constructor m = c.getDeclaredConstructor(args);
        m.setAccessible(true);
        return m;
    }

    public static Object newInstance(final Class clazz) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        if (Build.VERSION.SDK_INT < 11) { // API10
            return getPrivateMethod(ObjectInputStream.class, "newInstance", Class.class, Class.class).invoke(null, clazz, Object.class);
        } else if (Build.VERSION.SDK_INT < 18) {
            throw new NoSuchMethodException(); // API11-17
        } else { // API18+
            Class Unsafe = Class.forName("sun.misc.Unsafe");
            return Unsafe.getDeclaredMethod("allocateInstance", Class.class).invoke(Unsafe.getDeclaredMethod("getUnsafe").invoke(null), clazz);
        }
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
            parent = context.getClassLoader();
        File ext = getExternalCodeCacheDir(context);
        if (ext == null)
            ext = getCodeCacheDir(context);
        return new DexClassLoader(tmp.getPath(), ext.getPath(), null, parent);
    }

    public static boolean setBootClassLoader(ClassLoader l) {
        try {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            Field parent = getPrivateField(ClassLoader.class, "parent");
            ClassLoader old = (ClassLoader) parent.get(system);
            ArrayList<ClassLoader> ss = new ArrayList<>();
            ClassLoader cl = system;
            while (ss.add(cl) && (cl = (ClassLoader) parent.get(cl)) != null) {
                if (cl == l)
                    return true; // already installed
            }
            ClassLoader cc = null;
            cl = l;
            while ((cl = (ClassLoader) parent.get(cl)) != null && !ss.contains(cl))
                cc = cl;
            parent.set(cc, old);
            parent.set(system, l);
            return true;
        } catch (Exception ignore) {
            return false;
        }
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
