package com.github.axet.androidlibrary.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class AssetsDexLoader {

    public static final String JAR = "jar";
    public static final String DEX = "dex";
    public static final String CLASSES = "classes.dex";

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
        if (Build.VERSION.SDK_INT >= 21)
            return context.getCodeCacheDir();
        else
            return new File(context.getCacheDir(), "../code_cache");
    }

    public static File extract(Context context, String asset, String ext) throws IOException {
        AssetManager am = context.getAssets();
        InputStream is = am.open(asset);
        File tmp = File.createTempFile(Storage.getNameNoExt(asset), "." + ext, context.getCacheDir());
        FileOutputStream os = new FileOutputStream(tmp);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static ClassLoader deps(Context context, String... deps) {
        ClassLoader parent = DexClassLoader.getSystemClassLoader();
        try {
            for (String dep : deps) {
                AssetManager am = context.getAssets();
                String[] aa = am.list("");
                for (String a : aa) {
                    if (a.startsWith(dep)) {
                        if (a.endsWith("." + JAR)) {
                            File tmp = extract(context, a, JAR);
                            parent = load(context, tmp, parent);
                            tmp.delete();
                        }
                        if (a.endsWith("." + DEX)) {
                            InputStream is = am.open(a);
                            File tmp = File.createTempFile(Storage.getNameNoExt(a), "." + JAR, context.getCacheDir());
                            ZipOutputStream os = new ZipOutputStream(new FileOutputStream(tmp));
                            ZipEntry e = new ZipEntry(CLASSES);
                            os.putNextEntry(e);
                            IOUtils.copy(is, os);
                            os.close();
                            is.close();
                            parent = load(context, tmp, parent);
                            tmp.delete();
                        }
                    }
                }
            }
            return parent;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader load(Context context, File tmp, ClassLoader parent) throws IOException {
        return new DexClassLoader(tmp.getPath(), getCodeCacheDir(context).getPath(), null, parent);
    }

}
