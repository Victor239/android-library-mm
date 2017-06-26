package com.github.axet.androidlibrary.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Storage {
    protected Context context;

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public Storage(Context context) {
        this.context = context;
    }

    public File getLocalInternal() {
        return context.getFilesDir();
    }

    public File getLocalExternal() {
        File external = context.getExternalFilesDir("");

        // Starting in KITKAT, no permissions are required to read or write to the getExternalFilesDir;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS))
                return null;
        }

        return external;
    }

    public File getLocalStorage() {
        File internal = getLocalInternal();

        File external = getLocalExternal();
        if (external == null) // some old phones <15API with disabled sdcard return null
            return internal;

        return external;
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
        if (i >= 0) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    // "test (1)" --> "test"
    public static String filterDups(String fileName) {
        Pattern p = Pattern.compile("(.*)\\s\\(\\d+\\)");
        Matcher m = p.matcher(fileName);
        if (m.matches()) {
            fileName = m.group(1);
            return filterDups(fileName);
        }
        return fileName;
    }

    public static File getNextFile(File f) {
        File parent = f.getParentFile();
        String fileName = f.getName();

        String extension = "";

        int i = fileName.lastIndexOf('.');
        if (i >= 0) {
            extension = fileName.substring(i + 1);
            fileName = fileName.substring(0, i);
        }

        fileName = filterDups(fileName);

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
            fileName = fileName.trim(); // if filename is empty
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

    public static boolean exist(Context context, Uri uri, String name) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor childCursor = contentResolver.query(uri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null); // MediaStore.Files.FileColumns.TITLE + " = ?"
        try {
            while (childCursor.moveToNext()) {
                String t = childCursor.getString(0);
                if (t.equals(name))
                    return true;
            }
            return false;
        } finally {
            childCursor.close();
        }
    }

    // contentresolver helper
    public static String getNextFile(Context context, Uri childrenUri, String name, String ext) {
        String fileName;
        if (ext == null || ext.isEmpty())
            fileName = name;
        else
            fileName = String.format("%s.%s", name, ext);

        int i = 1;
        while (exist(context, childrenUri, fileName)) {
            if (ext == null || ext.isEmpty())
                fileName = String.format("%s (%d)", name, i);
            else
                fileName = String.format("%s (%d).%s", name, i, ext);
            fileName = fileName.trim(); // if filename is empty
            i++;
        }

//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            throw new RuntimeException("Unable to create: " + file, e);
//        }

        return fileName;
    }

    public static void delete(File f) {
        f.delete();
    }

    public static boolean isSame(File f, File t) {
        try {
            return f.getCanonicalPath().equals(t.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void move(File f, File to) {
        if (f.renameTo(to))
            return;
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
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

    public static boolean permitted(Activity a, String[] ss, int code) {
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(a, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(a, ss, code);
                return false;
            }
        }
        return true;
    }

    public static boolean permitted(Fragment f, String[] ss, int code) {
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(f.getContext(), s) != PackageManager.PERMISSION_GRANTED) {
                f.requestPermissions(ss, code);
                return false;
            }
        }
        return true;
    }

    public static void showPermissions(Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        context.startActivity(intent);
    }
}
