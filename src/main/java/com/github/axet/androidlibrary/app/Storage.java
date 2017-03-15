package com.github.axet.androidlibrary.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StatFs;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Storage {
    protected Context context;

    public Storage(Context context) {
        this.context = context;
    }

    public File getLocalStorage() {
        return new File(context.getApplicationInfo().dataDir);
    }

    public File getStoragePath(File file) {
        File parent = file.getParentFile();
        while (!parent.exists())
            parent = file.getParentFile();
        if ((file.canWrite() || parent.canWrite())) {
            return file;
        } else {
            return getLocalStorage();
        }
    }

    public static long getFree(File f) {
        while (!f.exists())
            f = f.getParentFile();

        StatFs fsi = new StatFs(f.getPath());
        if (Build.VERSION.SDK_INT < 18)
            return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
        else
            return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
    }

    public static String getNameNoExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            fileName = fileName.substring(0, i);
        }
        return fileName;
    }

    public static String getExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    public static File getNextFile(File parent, File f) {
        String fileName = f.getName();

        String extension = "";

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
            fileName = fileName.substring(0, i);
        }

        return getNextFile(parent, fileName, extension);
    }

    public static File getNextFile(File parent, String name, String ext) {
        String fileName;
        if (ext == null || ext.isEmpty())
            fileName = name;
        else
            fileName = String.format("%s.%s", name, ext);

        File file = new File(parent, fileName);

        int i = 1;
        while (file.exists()) {
            if (ext == null || ext.isEmpty())
                fileName = String.format("%s (%d)", name, i);
            else
                fileName = String.format("%s (%d).%s", name, i, ext);
            file = new File(parent, fileName);
            i++;
        }

//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            throw new RuntimeException("Unable to create: " + file, e);
//        }

        return file;
    }

    public static void delete(File f) {
        f.delete();
    }

    public static void move(File f, File to) {
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));

            byte[] buf = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            f.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean permitted(Context context, String[] ss) {
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean permitted(Activity context, String[] ss, int code) {
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context, ss, code);
                return false;
            }
        }
        return true;
    }
}
