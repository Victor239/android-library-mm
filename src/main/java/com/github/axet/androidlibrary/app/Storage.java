package com.github.axet.androidlibrary.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
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

import com.github.axet.androidlibrary.widgets.OpenFileDialog;

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
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Storage {
    private static final String TAG = Storage.class.getSimpleName();

    protected static boolean permittedForce = false; // bugged phones has no PackageManager.ACTION_REQUEST_PERMISSIONS acitivty allow it all

    public static final String PATH_TREE = "tree";
    public static final String[] PERMISSIONS_RO = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    public static final String[] PERMISSIONS_RW = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final String SAF = "com.android.externalstorage";

    public final static String STORAGE_PRIMARY = "primary"; // sdcard name

    public static final String CONTENTTYPE_OCTETSTREAM = "application/octet-stream";
    public static final String CONTENTTYPE_OPUS = "audio/opus";
    public static final String CONTENTTYPE_OGG = "audio/ogg";
    public static final String CONTENTTYPE_FB2 = "application/x-fictionbook";

    protected Context context;
    protected ContentResolver resolver;

    public static String toHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static String md5(String str) {
        try {
            byte[] bytesOfMessage = str.getBytes(Charset.defaultCharset());
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytesOfMessage);
            return toHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    public static long getFree(File f) {
        while (!f.exists()) {
            f = f.getParentFile();
            if (f == null)
                return 0;
        }
        StatFs fsi = new StatFs(f.getPath());
        if (Build.VERSION.SDK_INT < 18)
            return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
        else
            return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
    }

    public static String getNameNoExt(File f) {
        return getNameNoExt(f.getName());
    }

    public static String getNameNoExt(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            fileName = fileName.substring(0, i);
        }
        return fileName;
    }

    public static String getExt(String fileName) { // FilenameUtils.getExtension(n)
        int i = fileName.lastIndexOf('.');
        if (i >= 0) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    public static String getExt(File f) {
        String fileName = f.getName();
        return getExt(fileName);
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
        copy(f, to);
        delete(f);
        return to;
    }


    public static File copy(File f, File to) {
        long last = f.lastModified();
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
            in.close();
            out.close();
            if (last > 0)
                to.setLastModified(last);
            return to;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean permitted(Context context, String[] ss) {
        if (permittedForce)
            return true;
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
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(a, s) != PackageManager.PERMISSION_GRANTED) {
                try {
                    ActivityCompat.requestPermissions(a, ss, code);
                } catch (ActivityNotFoundException e) {
                    permittedForce = true;
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public static boolean permitted(Fragment f, String[] ss, int code) {
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(f.getContext(), s) != PackageManager.PERMISSION_GRANTED) {
                try {
                    f.requestPermissions(ss, code);
                } catch (ActivityNotFoundException e) {
                    permittedForce = true;
                    return true;
                }
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

    public static boolean ejected(File p) { // check target 'parent RW' access if child does not exist, and 'child R' if exists
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

    @TargetApi(21)
    public static String getDocumentStorage(Uri uri) {
        String id = DocumentsContract.getDocumentId(uri);
        id = id.substring(0, id.indexOf(":"));
        return getDocumentStorage(id);
    }

    public static String getDocumentStorage(String s) {
        String path;
        if (s.equals(STORAGE_PRIMARY))
            path = "[i]";
        else
            path = "[e]";
        return path;
    }

    @TargetApi(21)
    public static String getDocumentPath(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            String id = DocumentsContract.getDocumentId(uri);
            id = id.substring(id.indexOf(":") + 1);
            File f = new File(id);
            return f.getPath();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.getPath();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public static String getDocumentChildPath(Uri uri) {
        String id = DocumentsContract.getDocumentId(uri);
        String parent = DocumentsContract.getTreeDocumentId(uri);
        id = id.substring(parent.length() + 1);
        return id;
    }

    @TargetApi(21)
    public static String getDocumentName(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            String id = DocumentsContract.getDocumentId(uri);
            id = id.substring(id.indexOf(":") + 1);
            File f = new File(id);
            return f.getName();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.getName();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    // get document folder from document uri
    @TargetApi(21)
    public static Uri getDocumentTreeUri(Uri treeUri) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(treeUri.getAuthority()).appendPath(PATH_TREE)
                .appendPath(DocumentsContract.getTreeDocumentId(treeUri))
                .build();
    }

    public static boolean canWrite(File f) {
        if (!f.canWrite())
            return false;
        if (f.exists() && f.getFreeSpace() > 0)
            return true;
        File p = f.getParentFile();
        if (!f.exists() && !p.canWrite())
            return false;
        if (!f.exists() && p.exists() && p.getFreeSpace() > 0)
            return true;
        return false;
    }

    public static String getTypeByName(String fileName) {
        String ext = Storage.getExt(fileName);
        if (ext == null || ext.isEmpty()) {
            return CONTENTTYPE_OCTETSTREAM; // replace 'null'
        }
        ext = ext.toLowerCase();
        switch (ext) {
            case "opus":
                return CONTENTTYPE_OPUS; // android missing
            case "ogg":
                return CONTENTTYPE_OGG; // replace 'application/ogg'
            case "fb2":
                return CONTENTTYPE_FB2;
        }
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (type == null)
            return CONTENTTYPE_OCTETSTREAM;
        return type;
    }

    public Storage(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    public Context getContext() {
        return context;
    }

    public File getLocalInternal() {
        return OpenFileDialog.getLocalInternal(context);
    }

    public File getLocalExternal() {
        File external = context.getExternalFilesDir("");

        // Starting in KITKAT, no permissions are required to read or write to the getExternalFilesDir;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS_RW))
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
            if (!permitted(context, PERMISSIONS_RW))
                return internal;
        }

        File external = getLocalExternal();

        if (external == null)
            return internal;

        return external;
    }

    public File getStoragePath(File file) {
        File p = file.getParentFile();
        if (ejected(file) || !canWrite(p))
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
            File f;
            if (DocumentsContract.isDocumentUri(context, uri)) {
                f = new File(DocumentsContract.getDocumentId(uri));
            } else {
                f = new File(DocumentsContract.getTreeDocumentId(uri));
            }
            f = new File(f, name);
            return DocumentsContract.buildDocumentUriUsingTree(uri, f.getPath());
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
            } catch (Exception e) { // IllegalArgumentException | FileNotFoundException | ErrnoException | NullPointerException (readExceptionWithFileNotFoundExceptionFromParcel)
                return 0;
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            try {
                File file = getFile(uri);
                return getFree(file);
            } catch (Exception e) { // IllegalArgumentException
                return 0;
            }
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public boolean ejected(Uri uri, int takeFlags) { // check folder existes and childs can be read
        ContentResolver resolver = context.getContentResolver();
        try {
            resolver.takePersistableUriPermission(uri, takeFlags);
            Cursor childCursor = null;
            Cursor childCursor2 = null;
            try {
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                childCursor = resolver.query(doc, null, null, null, null); // check target folder
                if (childCursor != null && childCursor.moveToNext()) {
                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                    childCursor2 = resolver.query(childrenUri, null, null, null, null); // check read directory content
                    if (childCursor2 != null) {
                        return false;
                    }
                }
            } finally {
                if (childCursor != null)
                    childCursor.close();
                if (childCursor2 != null)
                    childCursor2.close();
            }
            return true;
        } catch (RuntimeException e) {
            Log.d(TAG, "open SAF failed", e);
        }
        return true;
    }

    public boolean ejected(Uri path) { // check target 'parent RW' access if child does not exist, and 'child R' if exists
        String s = path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return ejected(path, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File p = new File(path.getPath());
            return ejected(p);
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Uri getStoragePath(String path) {
        File f;
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            if (!ejected(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                return u;
            f = fallbackStorage(); // we need to fallback to local storage internal or exernal
        } else if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = Storage.getFile(Uri.parse(path));
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS_RW)) {
            return Uri.fromFile(getLocalStorage());
        } else {
            return Uri.fromFile(getStoragePath(f));
        }
    }

    public String getName(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Cursor childCursor = null;
            try {
                childCursor = resolver.query(f, null, null, null, null);
                if (childCursor != null && childCursor.moveToNext()) {
                    return childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                }
            } catch (RuntimeException e) { // ignore
                ;
            } finally {
                if (childCursor != null)
                    childCursor.close();
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getFile(f).getName();
        } else {
            throw new RuntimeException("unknown uri");
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
            } catch (RuntimeException e) { // ignore
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

    public long getLastModified(Uri uri) {
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
    public String getDisplayName(Uri uri) {
        String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) { // saf folder for content
            String saf = "sdcard";
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String id = DocumentsContract.getDocumentId(uri);
                String[] ss = id.split(":"); // 1D13-0F08:private
                return saf + getDocumentStorage(ss[0]) + "://" + getDocumentPath(uri);
            } else {
                String tree = DocumentsContract.getTreeDocumentId(uri);
                String[] ss = tree.split(":"); // 1D13-0F08:private
                String path;
                if (ss.length > 1) {
                    path = getDocumentStorage(ss[0]) + "://" + ss[1];
                } else {
                    path = getDocumentStorage(ss[0]) + "://";
                }
                return saf + path;
            }
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) { // full destionation for files
            File f = getFile(uri);
            return f.getAbsolutePath();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    public Uri move(File f, Uri dir, String t) {
        Uri u = createFile(dir, t);
        if (u == null)
            throw new RuntimeException("unable to create file " + t);
        try {
            InputStream is = new FileInputStream(f);
            OutputStream os = resolver.openOutputStream(u);
            IOUtils.copy(is, os);
            is.close();
            os.close();
            delete(f);
            return u;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // call getNextFile() on 't'
    public Uri move(File f, Uri t) {
        String s = t.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri root = getDocumentTreeUri(t);
            return move(f, root, getDocumentChildPath(t));
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

            return Uri.fromFile(Storage.move(f, to));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Uri migrate(File f, Uri dir) {
        String s = dir.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Log.d(TAG, "migrate: " + f + " --> " + getDisplayName(dir));
            String n = getNameNoExt(f);
            String e = getExt(f);
            Uri t = getNextFile(dir, n, e);
            if (f.isDirectory()) {
                Uri tt = createFolder(dir, getDocumentName(t));
                File[] files = f.listFiles();
                if (files != null) {
                    for (File m : files) {
                        migrate(m, tt);
                    }
                }
                delete(f);
                return tt;
            } else {
                return move(f, dir, getDocumentChildPath(t));
            }
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            Log.d(TAG, "migrate: " + f + " --> " + dir.getPath());
            if (f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File ff : files) {
                        File tt = new File(getFile(dir), ff.getName());
                        tt.mkdirs();
                        move(ff, tt);
                    }
                }
                delete(f);
                return dir;
            } else {
                File to = new File(dir.getPath());
                if (!to.exists() && !to.mkdirs()) {
                    throw new RuntimeException("No permissions: " + to);
                }
                File tofile = new File(to, f.getName());
                return Uri.fromFile(move(f, tofile));
            }
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @TargetApi(21)
    synchronized public Uri createFile(Uri parent, String path) {
        Uri u = child(parent, path);
        if (exists(u))
            return u;

        String id;
        if (DocumentsContract.isDocumentUri(context, parent)) {
            id = DocumentsContract.getDocumentId(parent);
        } else {
            id = DocumentsContract.getTreeDocumentId(parent);
        }
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(parent, id);

        String p = new File(path).getParent();
        if (p != null && !p.isEmpty()) {
            docUri = createFolder(docUri, p);
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

        String id;
        if (DocumentsContract.isDocumentUri(context, parent)) {
            id = DocumentsContract.getDocumentId(parent);
        } else {
            id = DocumentsContract.getTreeDocumentId(parent);
        }
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(parent, id);

        File p = new File(path);
        String n = p.getParent();
        if (n != null && !n.isEmpty()) {
            docUri = createFolder(docUri, n);
        }

        Log.d(TAG, "createFolder " + path);
        return DocumentsContract.createDocument(resolver, docUri, DocumentsContract.Document.MIME_TYPE_DIR, p.getName());
    }

}
