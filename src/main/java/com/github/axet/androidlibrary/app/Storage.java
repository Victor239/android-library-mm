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
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStatVfs;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FileUtils;
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Storage {
    private static final String TAG = Storage.class.getSimpleName();

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final String SAF = "com.android.externalstorage";

    protected Context context;
    protected ContentResolver resolver;

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

    public static boolean delete(File f) {
        return FileUtils.deleteQuietly(f);
    }

    public static boolean isSame(File f, File t) {
        try {
            return f.getCanonicalPath().equals(t.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File move(File f, File to) {
        long last = f.lastModified();
        if (f.renameTo(to)) {
            to.setLastModified(last);
            return to;
        }
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
            in.close();
            out.close();
            f.delete();
            to.setLastModified(last);
            return to;
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

    public static File getFile(Uri u) {
        String p = u.getPath();
        return new File(p);
    }

    public Storage(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    public Context getContext() {
        return context;
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

    public boolean isLocalStorage(Uri u) {
        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT))
            return false;
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = getFile(u);
            File internal = getLocalInternal();

            File external = getLocalExternal();
            if (external != null) // some old phones <15API with disabled sdcard return null
                if (external.equals(f))
                    return true;

            return internal.equals(f);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public File getLocalStorage() {
        File internal = getLocalInternal();

        File external = getLocalExternal();
        if (external == null) // some old phones <15API with disabled sdcard return null
            return internal;

        return external;
    }

    public File fallbackStorage() {
        File internal = getLocalInternal();

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS))
                return internal;
        }

        File external = getLocalExternal();

        if (external == null)
            return internal;

        return external;
    }

    public File getStoragePath(File file) {
        if (ejected(file) || !file.canWrite())
            return getLocalStorage();
        return file;
    }

    public boolean exists(Uri uri) { // document query uri
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Cursor childCursor = null;
            try {
                childCursor = resolver.query(uri, null, null, null, null);
                if (childCursor != null) {
                    boolean n = childCursor.moveToNext();
                    childCursor.close();
                    if (n)
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
            File f1 = getFile(uri);
            if (!f1.canRead())
                return false;
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
            File f1 = new File(getFile(uri), name);
            return Uri.fromFile(f1);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public boolean delete(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return DocumentsContract.deleteDocument(resolver, f);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File ff = getFile(f);
            return delete(ff);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public String getNameNoExt(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getNameNoExt(new File(getDocumentName(f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getNameNoExt(getFile(f));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public String getExt(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getExt(new File(getDocumentName(f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getExt(getFile(f));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Uri rename(Uri f, String t) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return DocumentsContract.renameDocument(resolver, f, t);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(f);
            File ff = new File(f1.getParent(), t);
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
            File f1 = getFile(parent);
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
                ParcelFileDescriptor pfd = resolver.openFileDescriptor(docTreeUri, "r");
                StructStatVfs stats = Os.fstatvfs(pfd.getFileDescriptor());
                return stats.f_bavail * stats.f_bsize;
            } catch (FileNotFoundException | ErrnoException e) {
                return 0;
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File file = getFile(uri);
            return getFree(file);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public boolean permitted(Uri uri, int takeFlags) {
        ContentResolver resolver = context.getContentResolver();
        try {
            resolver.takePersistableUriPermission(uri, takeFlags);
            Cursor childCursor = null;
            try {
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                childCursor = resolver.query(doc, null, null, null, null);
                if (childCursor != null) {
                    boolean n = childCursor.moveToNext();
                    childCursor.close();
                    if (n)
                        return true;
                }
            } catch (RuntimeException e) { // not found catched here
                ;
            } finally {
                if (childCursor != null)
                    childCursor.close();
            }
            return false;
        } catch (SecurityException e) {
            Log.d(TAG, "open SAF failed", e);
        }
        return false;
    }

    public boolean ejected(Uri path) { // check target forlder for RW access if does not exist, and R if exists
        String s = path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return !permitted(path, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File p = new File(path.getPath());
            return ejected(p);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public static boolean ejected(File p) { // check target forlder for RW access if does not exist, and R if exists
        if (!p.exists()) {
            while (!p.exists()) {
                p = p.getParentFile();
            }
            if (p.canWrite())
                return false; // torrent parent folder not exist, but we have write access and can create subdirs
            else
                return true; // no write access - ejected
        }
        return !p.canRead(); // readonly check
    }

    public Uri getStoragePath(String path) {
        File f;
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            if (permitted(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                return u;
            f = fallbackStorage(); // we need to fallback to local storage internal or exernal
        } else if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = Storage.getFile(Uri.parse(path));
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS)) {
            return Uri.fromFile(getLocalStorage());
        } else {
            return Uri.fromFile(getStoragePath(f));
        }
    }

    @TargetApi(21)
    public static String getDocumentPath(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            String id = DocumentsContract.getDocumentId(uri);
            String parent = DocumentsContract.getTreeDocumentId(uri);
            id = id.substring(parent.length() + 1);
            File f = new File(id);
            return f.getPath();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.getName();
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
            File f = getFile(uri);
            return f.getName();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public String getName(Uri uri) {
        Cursor childCursor = null;
        try {
            childCursor = resolver.query(uri, null, null, null, null);
            if (childCursor != null && childCursor.moveToNext()) {
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
            Cursor childCursor = null;
            try {
                childCursor = resolver.query(uri, null, null, null, null);
                if (childCursor != null && childCursor.moveToNext()) {
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
            File f = getFile(uri);
            return f.length();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public long getLast(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Cursor childCursor = null;
            try {
                childCursor = resolver.query(uri, null, null, null, null);
                if (childCursor != null && childCursor.moveToNext()) {
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
            File f = getFile(uri);
            return f.lastModified();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public String getTargetName(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) { // saf folder for content
            String saf = null;
            if (DocumentsContract.isDocumentUri(context, uri)) {
                Uri docUri = DocumentsContract.buildDocumentUri(uri.getAuthority(), DocumentsContract.getDocumentId(uri));
                try {
                    Cursor docCursor = resolver.query(docUri, null, null, null, null);
                    if (docCursor != null) {
                        if (docCursor.moveToNext()) {
                            saf = "saf://.../";
                            saf += docCursor.getString(docCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        }
                        docCursor.close();
                    }
                } catch (SecurityException e) {
                    Log.d(TAG, "Unable to get folder", e);
                }
            } else {
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                try {
                    Cursor docCursor = resolver.query(docUri, null, null, null, null);
                    if (docCursor != null) {
                        if (docCursor.moveToNext()) {
                            saf = "saf://";
                            saf += docCursor.getString(docCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        }
                        docCursor.close();
                    }
                } catch (SecurityException e) {
                    Log.d(TAG, "Unable to get folder", e);
                }
            }
            return saf;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) { // full destionation for files
            File f = getFile(uri);
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
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(t, DocumentsContract.getTreeDocumentId(t));
            Uri toUri = DocumentsContract.createDocument(resolver, docUri, mime, n);
            try {
                InputStream is = new FileInputStream(f);
                OutputStream os = resolver.openOutputStream(toUri);
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

            File tf = getFile(t);
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
    synchronized public Uri createFile(Uri parent, String path) {
        Uri u = child(parent, path);
        if (exists(u))
            return u;

        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(u, DocumentsContract.getTreeDocumentId(u));

        String p = new File(path).getParent();
        if (p != null && !p.isEmpty()) {
            docUri = createFolder(parent, p);
        }

        Log.d(TAG, "createFile " + path);
        String ext = getExt(u);
        String n = getDocumentName(u);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return DocumentsContract.createDocument(resolver, docUri, mime, n);
    }

    @TargetApi(21)
    synchronized public Uri createFolder(Uri parent, String path) {
        Uri c = child(parent, path);
        if (exists(c))
            return c;

        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(c, DocumentsContract.getTreeDocumentId(c));

        File f = new File(path);
        String p = f.getParent();
        if (p != null && !p.isEmpty()) {
            docUri = createFolder(parent, p);
        }

        Log.d(TAG, "createFolder " + path);

        String n = f.getName();
        return DocumentsContract.createDocument(resolver, docUri, DocumentsContract.Document.MIME_TYPE_DIR, n);
    }

}
