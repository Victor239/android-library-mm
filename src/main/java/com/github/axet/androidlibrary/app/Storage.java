package com.github.axet.androidlibrary.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStatVfs;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Storage {
    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    protected Context context;

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

        return file;
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
        long last = f.lastModified();
        if (f.renameTo(to)) {
            to.setLastModified(last);
            return;
        }
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
            in.close();
            out.close();
            f.delete();
            to.setLastModified(last);
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

    public boolean exists(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor childCursor = null;
            try {
                childCursor = contentResolver.query(uri, null, null, null, null);
                if (childCursor.moveToNext()) {
                    return true;
                }
            } catch (RuntimeException e) { // not found catched here
                ;
            } finally {
                if (childCursor != null)
                    childCursor.close();
            }
            return false;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f1 = new File(uri.getPath());
            return f1.exists();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Uri child(Uri uri, String name) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String f = DocumentsContract.getTreeDocumentId(uri) + "/" + name;
            Uri test = DocumentsContract.buildDocumentUriUsingTree(uri, f);
            return test;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f1 = new File(uri.getPath(), name);
            return Uri.fromFile(f1);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public void delete(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver resolver = context.getContentResolver();
            DocumentsContract.deleteDocument(resolver, f);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File ff = new File(f.getPath());
            delete(ff);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public String getNameNoExt(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getNameNoExt(new File(getDocumentName(f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getNameNoExt(new File(f.getPath()));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public String getExt(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getExt(new File(getDocumentName(f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getExt(new File(f.getPath()));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Uri rename(Uri f, String t) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver resolver = context.getContentResolver();
            return DocumentsContract.renameDocument(resolver, f, t);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = new File(f.getPath());
            File ff = new File(f1.getParent(), s);
            if (ff.exists())
                ff = Storage.getNextFile(ff);
            f1.renameTo(ff);
            return Uri.fromFile(ff);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    // parent = DocumentsContract.buildTreeDocumentUri(t.getAuthority(), DocumentsContract.getTreeDocumentId(t));
    public Uri getNextFile(Uri parent, String name, String ext) {
        String s = parent.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String fileName;
            if (ext == null || ext.isEmpty())
                fileName = name;
            else
                fileName = String.format("%s.%s", name, ext);

            Uri uri = child(parent, fileName);

            int i = 1;
            while (exists(uri)) {
                if (ext == null || ext.isEmpty())
                    fileName = String.format("%s (%d)", name, i);
                else
                    fileName = String.format("%s (%d).%s", name, i, ext);
                fileName = fileName.trim(); // if filename is empty
                uri = child(parent, fileName);
                i++;
            }

            return uri;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f1 = new File(parent.getPath());
            return Uri.fromFile(getNextFile(f1, name, ext));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public long getFree(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri docTreeUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            try {
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(docTreeUri, "r");
                StructStatVfs stats = Os.fstatvfs(pfd.getFileDescriptor());
                return stats.f_bavail * stats.f_bsize;
            } catch (FileNotFoundException | ErrnoException e) {
                throw new RuntimeException(e);
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File file = new File(uri.getPath());
            return getFree(file);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Uri getStoragePath(String path) {
        Uri uri = Uri.parse(path);
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return uri;
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = getStoragePath(new File(path));
            return Uri.fromFile(f);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public static String getDocumentName(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            String id = DocumentsContract.getDocumentId(uri);
            File f = new File(id);
            return f.getName();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = new File(uri.getPath());
            return f.getName();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public String getName(Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor childCursor = null;
        try {
            childCursor = contentResolver.query(uri, null, null, null, null);
            if (childCursor.moveToNext()) {
                return childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            }
        } catch (RuntimeException e) {
            ;
        } finally {
            if (childCursor != null)
                childCursor.close();
        }
        return null;
    }

    public long getLength(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor childCursor = null;
            try {
                childCursor = contentResolver.query(uri, null, null, null, null);
                if (childCursor.moveToNext()) {
                    return childCursor.getLong(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                }
            } catch (RuntimeException e) {
                ;
            } finally {
                if (childCursor != null)
                    childCursor.close();
            }
            return -1;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = new File(uri.getPath());
            return f.length();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public long getLast(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor childCursor = null;
            try {
                childCursor = contentResolver.query(uri, null, null, null, null);
                if (childCursor.moveToNext()) {
                    return childCursor.getLong(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                }
            } catch (RuntimeException e) {
                ;
            } finally {
                if (childCursor != null)
                    childCursor.close();
            }
            return 0;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = new File(uri.getPath());
            return f.lastModified();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public String getTargetName(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) { // saf folder for content
            ContentResolver contentResolver = context.getContentResolver();
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            Cursor docCursor = contentResolver.query(docUri, null, null, null, null);
            try {
                if (docCursor.moveToNext()) {
                    String saf = "saf://" + docCursor.getString(docCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    final List<String> paths = uri.getPathSegments();
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        String parent = DocumentsContract.getTreeDocumentId(uri);
                        String docId = DocumentsContract.getDocumentId(uri);
                        docId = docId.substring(parent.length());
                        saf += docId;
                    }
                    return saf;
                }
            } finally {
                docCursor.close();
            }
            return null;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) { // full destionation for files
            File f = new File(uri.getPath());
            return f.getAbsolutePath();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    // move should not call getNextFile()
    public Uri move(File f, Uri t) {
        String s = t.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            String ext = getExt(t);
            String n = getDocumentName(t);
            ContentResolver contentResolver = context.getContentResolver();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(t, DocumentsContract.getTreeDocumentId(t));
            Uri toUri = DocumentsContract.createDocument(contentResolver, docUri, mime, n);
            try {
                InputStream is = new FileInputStream(f);
                OutputStream os = contentResolver.openOutputStream(toUri);
                IOUtils.copy(is, os);
                is.close();
                os.close();
                f.delete();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return toUri;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File parent = f.getParentFile();

            String ext = getExt(t);
            String n = getNameNoExt(t);

            File tf = new File(t.getPath());
            File td = tf.getParentFile();

            if (Storage.isSame(parent, td))
                return null;

            if (!td.exists() && !td.mkdirs())
                throw new RuntimeException("unable to create: " + td);

            File to = Storage.getNextFile(td, n, ext);

            if (Storage.isSame(f, to))
                return null;

            Storage.move(f, to);

            return Uri.fromFile(to);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    void takePersistableUriPermission(Uri uri) {
        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
    }

}
