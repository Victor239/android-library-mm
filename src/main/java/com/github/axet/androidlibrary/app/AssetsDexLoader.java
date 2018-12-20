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

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class AssetsDexLoader {

    public static final String EXT = "jar";

    public static DexFile[] getDexs(ClassLoader l) {
        try {
            Field mDexs = l.getClass().getDeclaredField("mDexs");
            mDexs.setAccessible(true);
            return (DexFile[]) mDexs.get(l);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader deps(Context context, String... deps) {
        ClassLoader parent = DexClassLoader.getSystemClassLoader();
        try {
            for (String dep : deps) {
                AssetManager am = context.getAssets();
                String[] aa = am.list("");
                for (String a : aa) {
                    if (a.startsWith(dep) && a.endsWith("." + EXT)) {
                        InputStream is = am.open(a);
                        File tmp = File.createTempFile("jar", EXT, context.getCacheDir());
                        FileOutputStream os = new FileOutputStream(tmp);
                        IOUtils.copy(is, os);
                        os.close();
                        is.close();
                        parent = load(context, tmp, parent);
                        tmp.delete();
                    }
                }
            }
            return parent;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader load(Context context, File tmp, ClassLoader parent) throws IOException {
        File opt;
        if (Build.VERSION.SDK_INT >= 21)
            opt = context.getCodeCacheDir();
        else
            opt = new File(context.getCacheDir(), "../code_cache");
        return new DexClassLoader(tmp.getPath(), opt.getPath(), null, parent);
    }

}
