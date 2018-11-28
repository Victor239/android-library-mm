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
import android.support.v4.provider.DocumentFile;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
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
    public final static String STORAGE_HOME = "home"; // 'Documents' folder on internal sdcard

    public static final String CONTENTTYPE_OCTETSTREAM = "application/octet-stream";
    public static final String CONTENTTYPE_OPUS = "audio/opus";
    public static final String CONTENTTYPE_OGG = "audio/ogg";
    public static final String CONTENTTYPE_FB2 = "application/x-fictionbook";
    public static final String CONTENTTYPE_RAR = "application/x-rar-compressed";

    public static final String COLON = ":";

    protected Context context;
    protected ContentResolver resolver;

    public static String toHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in)
            builder.append(String.format("%02x", b));
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

    public static File relative(File base, File file) { // system/test64/123 - system/test64 == 123, but not 'system/test'
        String f = file.getPath();
        String r = relative(base.getPath(), f);
        if (r == null)
            return null;
        if (f == r)
            return file;
        return new File(r);
    }

    public static String relative(String base, String file) { // home:system64 <-> home:system64/test, but not home:system
        if (file.startsWith(base)) {
            int l = base.length();
            if (l == 0) // base is ""
                return file;
            if (base.charAt(l - 1) != File.separatorChar && file.length() > l) { // base not ends with '/', same path or relative?
                if (file.charAt(l) != File.separatorChar)
                    return null; // not relative
                l++; // 'l' points to '/'
            }
            return file.substring(l); // "" or relative path
        }
        return null; // not relative
    }

    public static String formatNextFile(String name, int i, String ext) {
        if (i == 0) {
            if (ext == null || ext.isEmpty())
                return name;
            else
                return String.format("%s.%s", name, ext);
        } else {
            if (ext == null || ext.isEmpty())
                return String.format("%s (%d)", name, i);
            else
                return String.format("%s (%d).%s", name, i, ext);
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
                case '+':
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
        if (Build.VERSION.SDK_INT >= 18)
            return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
        else
            return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
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
        return getNextFile(parent, name, 0, ext);
    }

    public static File getNextFile(File parent, String name, int i, String ext) {
        String fileName;
        fileName = formatNextFile(name, i, ext);

        File file = new File(parent, fileName);

        i++;
        while (file.exists()) {
            fileName = formatNextFile(name, i, ext);
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
        return new File(u.getPath());
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
    public static String getDocumentStorage(Context context, Uri uri) { // tree uri can points to 'home:'
        String id;
        if (DocumentsContract.isDocumentUri(context, uri))
            id = DocumentsContract.getDocumentId(uri);
        else
            id = DocumentsContract.getTreeDocumentId(uri);
        String[] ss = id.split(COLON, 2);
        return getDocumentStorage(ss[0]);
    }

    public static String getDocumentStorage(String s) {
        String path;
        if (s.equals(STORAGE_PRIMARY))
            path = "[i]";
        else if (s.equals(STORAGE_HOME))
            path = "[h]";
        else
            path = "[e]";
        return path;
    }

    @TargetApi(21)
    public static DocumentFile getDocumentFile(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri))
            return DocumentFile.fromSingleUri(context, uri);
        else
            return DocumentFile.fromTreeUri(context, uri);
    }

    @TargetApi(21)
    public static String getDocumentPath(Context context, Uri uri) { // for display purpose
        String pid = DocumentsContract.getTreeDocumentId(uri);
        String[] pss = pid.split(COLON, 2);
        if (!DocumentsContract.isDocumentUri(context, uri))
            return pss[1];
        String did = DocumentsContract.getDocumentId(uri);
        if (pid.equals(did))
            return pss[1];
        String[] dss = did.split(COLON, 2);
        if (!pss[0].equals(dss[0])) {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, dss[0] + COLON);
            String n = DocumentFile.fromSingleUri(context, doc).getName();
            if (pss[1].isEmpty())
                pss[1] = n;
            else
                pss[1] = new File(pss[1], n).getPath();
        }
        File d;
        if (dss[1].isEmpty())
            return pss[1];
        else if (pss[1].isEmpty())
            return dss[1];
        return new File(pss[1], dss[1]).getPath();
    }

    @TargetApi(21)
    public static Uri getDocumentParent(Context context, Uri uri) {
        String pid = DocumentsContract.getTreeDocumentId(uri);
        String[] pss = pid.split(COLON, 2);
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            File p = new File(pss[1]);
            p = p.getParentFile();
            if (p == null)
                return null;
            return DocumentsContract.buildDocumentUriUsingTree(uri, p.getPath());
        }
        String did = DocumentsContract.getDocumentId(uri);
        String[] dss = did.split(COLON, 2);
        File p = new File(dss[1]);
        p = p.getParentFile();
        if (p == null) {
            if (pid.equals(did))
                return null;
            if (pss[0].equals(dss[0]) || dss[1].isEmpty())
                return DocumentsContract.buildDocumentUriUsingTree(uri, pid);
            return DocumentsContract.buildDocumentUriUsingTree(uri, dss[0] + COLON);
        }
        return DocumentsContract.buildDocumentUriUsingTree(uri, dss[0] + COLON + p.getPath());
    }

    public static Uri getParent(Context context, Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri).getParentFile();
            if (f == null)
                return null;
            return Uri.fromFile(f);
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentParent(context, uri);
        } else {
            throw new UnknownUri();
        }
    }

    @TargetApi(21)
    public static Uri getDocumentChild(Context context, Uri uri, String name) {
        String id;
        if (DocumentsContract.isDocumentUri(context, uri))
            id = DocumentsContract.getDocumentId(uri);
        else
            id = DocumentsContract.getTreeDocumentId(uri);
        String[] ss = id.split(COLON, 2);
        File f = new File(ss[1], name);
        return DocumentsContract.buildDocumentUriUsingTree(uri, ss[0] + COLON + f.getPath());
    }

    @TargetApi(21)
    public static String getDocumentChildPath(Uri uri) {
        String id = DocumentsContract.getDocumentId(uri);
        String parent = DocumentsContract.getTreeDocumentId(uri);
        id = id.substring(parent.length() + 1);
        return id;
    }

    @TargetApi(21)
    public static String getDisplayName(Context context, Uri uri) {
        String saf = "sdcard";
        String unk = "sdcard[x]://";
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String id = DocumentsContract.getTreeDocumentId(uri);
            String[] ss = id.split(COLON, 2); // 1D13-0F08:private
            if (ss.length > 1)
                return saf + getDocumentStorage(ss[0]) + "://" + getDocumentPath(context, uri);
            else
                return unk + id; // uknown device path. new saf location?
        } else {
            String id = DocumentsContract.getTreeDocumentId(uri);
            String[] ss = id.split(COLON, 2); // 1D13-0F08:private
            if (ss.length > 1) // has colon
                return saf + getDocumentStorage(ss[0]) + "://" + ss[1];
            else
                return unk + id; // uknown device path. new saf location?
        }
    }

    @TargetApi(19)
    public static boolean isDocumentExists(Context context, Uri uri) {
        return getDocumentFile(context, uri).exists();
    }

    @TargetApi(21)
    public static long getDocumentFree(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Uri docTreeUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(docTreeUri, "r");
            StructStatVfs stats = Os.fstatvfs(pfd.getFileDescriptor());
            return stats.f_bavail * stats.f_bsize;
        } catch (Exception e) { // IllegalArgumentException | FileNotFoundException | ErrnoException | NullPointerException (readExceptionWithFileNotFoundExceptionFromParcel)
            return 0;
        }
    }

    @TargetApi(21)
    public static boolean isEjected(Context context, Uri uri, int takeFlags) { // check folder existes and childs can be read
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

    public static String getName(Context context, Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getContentName(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getFile(uri).getName();
        } else {
            throw new UnknownUri();
        }
    }

    @TargetApi(19)
    public static String getContentName(Context context, Uri uri) {
        if (uri.getAuthority().startsWith(SAF)) // query crashed for DocumentsContract.isTreeUri() uris
            return getDocumentName(context, uri);
        else
            return getQueryName(context, uri);
    }

    public static String getQueryName(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToNext())
                    return cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            } finally {
                cursor.close();
            }
        }
        return uri.getLastPathSegment();
    }

    @TargetApi(21)
    public static String getDocumentName(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String id = DocumentsContract.getDocumentId(uri);
            String[] ss = id.split(COLON, 2);
            if (ss[1].isEmpty()) { // unknown id format or root
                String pid = DocumentsContract.getTreeDocumentId(uri);
                if (pid.equals(id))
                    return OpenFileDialog.ROOT;
                return getQueryName(context, uri); // DocumentFile.getName() return null for non existent files
            }
            return new File(ss[1]).getName(); // not using query when it is possible
        } else {
            String id = DocumentsContract.getTreeDocumentId(uri);
            String[] ss = id.split(COLON, 2);
            if (ss[1].isEmpty()) // always true here
                return OpenFileDialog.ROOT;
            DocumentFile f = DocumentFile.fromTreeUri(context, uri);
            return f.getName(); // unknown id format
        }
    }

    @TargetApi(21)
    public static String buildDocumentPath(Context context, Uri start, Uri end) {
        if (!DocumentsContract.isDocumentUri(context, end))
            return OpenFileDialog.ROOT;
        String eid = DocumentsContract.getDocumentId(end);
        String path = "";
        ContentResolver resolver = context.getContentResolver();
        if (!DocumentsContract.isDocumentUri(context, start))
            start = DocumentsContract.buildDocumentUriUsingTree(start, DocumentsContract.getTreeDocumentId(start));
        Cursor cursor = resolver.query(start, null, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    if (d) {
                        Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(start, id);
                        Cursor cursor2 = resolver.query(doc, null, null, null, null);
                        if (cursor2 != null) {
                            try {
                                while (cursor2.moveToNext()) {
                                    id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                    name = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                                    type = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                                    d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                                    if (relative(id, eid) != null) {
                                        path = new File(path, name).getPath();
                                        if (d) {
                                            Uri uri = DocumentsContract.buildDocumentUriUsingTree(doc, id);
                                            return new File(path, buildDocumentPath(context, uri, end)).getPath();
                                        }
                                    }
                                }
                            } finally {
                                cursor2.close();
                            }
                        }
                    } else {
                        return new File(name).getPath();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return path;
    }

    @TargetApi(21)
    public static Uri getDocumentTreeUri(Uri treeUri) { // get document folder from document uri
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
        return getTypeByExt(ext);
    }

    public static String getTypeByExt(String ext) {
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
            case "rar":
                return CONTENTTYPE_RAR;
        }
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (type == null)
            return CONTENTTYPE_OCTETSTREAM;
        return type;
    }

    public static class UnknownUri extends RuntimeException {
    }

    public static class Node {
        public Uri uri;
        public String name;
        public boolean dir;
        public long size;
        public long last;

        public Node() {
        }

        public Node(Uri uri, String n, boolean dir, long size, long last) {
            this.uri = uri;
            this.name = n;
            this.dir = dir;
            this.size = size;
            this.last = last;
        }

        public Node(File f) {
            this.uri = Uri.fromFile(f);
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        public Node(DocumentFile f) {
            this.uri = f.getUri();
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        @TargetApi(21)
        public Node(Uri doc, Cursor cursor) {
            String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
            String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
            name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
            last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
            uri = DocumentsContract.buildDocumentUriUsingTree(doc, id);
            dir = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
        }

        public String toString() {
            return (dir ? "" : "@") + name;
        }
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
            return isLocalStorage(getFile(u));
        } else {
            throw new UnknownUri();
        }
    }

    public boolean isLocalStorage(File f) {
        String path = f.getPath();
        if (path.startsWith(context.getApplicationInfo().dataDir))
            return true;

        File internal = getLocalInternal();

        File external = getLocalExternal();
        if (external != null) // some old phones <15API with disabled sdcard return null
            if (path.startsWith(external.getPath()))
                return true;

        return path.startsWith(internal.getPath());
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
        if (ejected(file))
            return getLocalStorage();
        if (file.exists() && canWrite(file))
            return file;
        File p = file.getParentFile();
        if (!canWrite(p)) // storage con points to non existed folder, but parent should be writable
            return getLocalStorage();
        return file;
    }

    public boolean exists(Uri uri) { // document query uri
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return isDocumentExists(context, uri);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(uri);
            if (!f1.canRead())
                return false;
            return f1.exists();
        } else {
            throw new UnknownUri();
        }
    }

    public Uri child(Uri uri, String name) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentChild(context, uri, name);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f1 = new File(getFile(uri), name);
            return Uri.fromFile(f1);
        } else {
            throw new UnknownUri();
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
            throw new UnknownUri();
        }
    }

    public String getNameNoExt(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getNameNoExt(new File(getContentName(context, f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getNameNoExt(getFile(f));
        } else {
            throw new UnknownUri();
        }
    }

    public String getExt(Uri f) {
        String s = f.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getExt(new File(getContentName(context, f)));
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return getExt(getFile(f));
        } else {
            throw new UnknownUri();
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
            throw new UnknownUri();
        }
    }

    // parent = DocumentsContract.buildTreeDocumentUri(t.getAuthority(), DocumentsContract.getTreeDocumentId(t));
    public Uri getNextFile(Uri parent, String name, String ext) {
        return getNextFile(parent, name, 0, ext);
    }

    public Uri getNextFile(Uri parent, String name, int i, String ext) {
        String s = parent.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String fileName = formatNextFile(name, i, ext);

            Uri uri = child(parent, fileName);

            i++;
            while (exists(uri)) {
                fileName = formatNextFile(name, i, ext);
                fileName = fileName.trim(); // if filename is empty
                uri = child(parent, fileName);
                i++;
            }

            return uri;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f1 = getFile(parent);
            return Uri.fromFile(getNextFile(f1, name, i, ext));
        } else {
            throw new UnknownUri();
        }
    }

    public long getFree(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFree(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            try {
                File file = getFile(uri);
                return getFree(file);
            } catch (Exception e) { // IllegalArgumentException
                return 0;
            }
        } else {
            throw new UnknownUri();
        }
    }

    public boolean ejected(Uri path) { // check target 'parent RW' access if child does not exist, and 'child R' if exists
        String s = path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return isEjected(context, path, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            return ejected(getFile(path));
        } else {
            throw new UnknownUri();
        }
    }

    public Uri getStoragePath(String path) {
        File f;
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            if (!isEjected(context, u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                return u;
            f = fallbackStorage(); // we need to fallback to local storage internal or exernal
        } else if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = getFile(Uri.parse(path));
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS_RW)) {
            return Uri.fromFile(getLocalStorage());
        } else {
            return Uri.fromFile(getStoragePath(f));
        }
    }

    public String getName(Uri uri) {
        return getName(context, uri);
    }

    public long getLength(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFile(context, uri).length();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.length();
        } else {
            throw new UnknownUri();
        }
    }

    public long getLastModified(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFile(context, uri).lastModified();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = getFile(uri);
            return f.lastModified();
        } else {
            throw new UnknownUri();
        }
    }

    public String getDisplayName(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) { // saf folder for content
            return getDisplayName(context, uri);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) { // full destionation for files
            return getFile(uri).getPath();
        } else {
            throw new UnknownUri();
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
            throw new UnknownUri();
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
                Uri tt = createFolder(dir, getContentName(context, t));
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
                File to = Storage.getFile(dir);
                if (!to.exists() && !to.mkdirs()) {
                    throw new RuntimeException("No permissions: " + to);
                }
                File tofile = new File(to, f.getName());
                return Uri.fromFile(move(f, tofile));
            }
        } else {
            throw new UnknownUri();
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
        if (p != null && !p.isEmpty())
            docUri = createFolder(docUri, p);

        Log.d(TAG, "createFile " + path);
        String ext = getExt(u);
        String n = getDocumentName(context, u);
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

    public boolean touch(Uri uri, String name) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            File m = new File(k, name);
            try {
                new FileOutputStream(m, true).close();
                return true;
            } catch (IOException e) {
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri doc = child(uri, name);
            DocumentFile file = getDocumentFile(context, doc);
            if (file.exists()) {
                try {
                    ContentResolver resolver = context.getContentResolver();
                    OutputStream os = resolver.openOutputStream(doc, "wa");
                    os.close();
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } else {
                doc = createFile(uri, name);
                return doc != null;
            }
        } else {
            throw new Storage.UnknownUri();
        }
    }

    public Uri mkdir(Uri to, String name) {
        String s = to.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(to);
            File m = new File(k, name);
            if (m.mkdir())
                return Uri.fromFile(m);
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri e = child(to, name);
            DocumentFile m = DocumentFile.fromSingleUri(context, e);
            if (m.exists()) // directory or file == failed
                return null;
            File f = new File(name);
            File p = f.getParentFile();
            if (p != null) {
                to = child(to, p.getPath());
                name = f.getName();
            }
            return DocumentsContract.createDocument(resolver, to, DocumentsContract.Document.MIME_TYPE_DIR, name); // createFolder() 'mkdirs' mode
        } else {
            throw new Storage.UnknownUri();
        }
        return null;
    }

    public interface NodeFilter {
        boolean accept(Node n);
    }

    public ArrayList<Node> list(Uri uri) {
        return list(uri, null);
    }

    public ArrayList<Node> list(Uri uri, NodeFilter filter) { // Node.name = file name _no_ root uris
        ArrayList<Node> files = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File file = Storage.getFile(uri);
            File[] ff = file.listFiles();
            if (ff != null) {
                for (File f : ff) {
                    Node n = new Node(f);
                    if (filter == null || filter.accept(n))
                        files.add(n);
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id;
            if (DocumentsContract.isDocumentUri(getContext(), uri))
                id = DocumentsContract.getDocumentId(uri);
            else
                id = DocumentsContract.getTreeDocumentId(uri);
            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
            ContentResolver resolver = getContext().getContentResolver();
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Node n = new Node(doc, cursor);
                    if (filter == null || filter.accept(n))
                        files.add(n);
                }
                cursor.close();
            }
        } else {
            throw new Storage.UnknownUri();
        }
        return files;
    }

    @TargetApi(21)
    public String buildDocumentPath(Uri end) {
        return buildDocumentPath(DocumentsContract.buildDocumentUriUsingTree(end, DocumentsContract.getTreeDocumentId(end)), end);
    }

    @TargetApi(21)
    public String buildDocumentPath(Uri start, Uri end) {
        return buildDocumentPath(context, start, end);
    }

    public ArrayList<Node> walk(Uri root, Uri uri) {
        return walk(root, uri, null);
    }

    public ArrayList<Node> walk(Uri root, Uri uri, NodeFilter filter) { // Node.name = path relative to 'root' and _return_ root uris
        ArrayList<Node> files = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File r = Storage.getFile(root);
            File f = Storage.getFile(uri);
            files.add(new Storage.Node(uri, relative(r, f).getPath(), f.isDirectory(), f.length(), f.lastModified()));
            File[] kk = f.listFiles();
            if (kk != null) {
                for (File k : kk) {
                    Node n = new Node(Uri.fromFile(k), relative(r, k).getPath(), k.isDirectory(), k.length(), k.lastModified());
                    if (filter == null || filter.accept(n))
                        files.add(n);
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String p = buildDocumentPath(root, uri);
            ContentResolver resolver = context.getContentResolver();
            Uri doc;
            if (DocumentsContract.isDocumentUri(getContext(), uri))
                doc = uri;
            else
                doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                    long last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                    boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    files.add(new Storage.Node(uri, p, d, size, last)); // root uri (unchanged)
                    if (d) {
                        doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
                        Cursor cursor2 = resolver.query(doc, null, null, null, null);
                        if (cursor2 != null) {
                            while (cursor2.moveToNext()) {
                                id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                String name = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                                type = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                                size = cursor2.getLong(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                                last = cursor2.getLong(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                                d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                                Node n = new Node(DocumentsContract.buildDocumentUriUsingTree(doc, id), new File(p, name).getPath(), d, size, last);
                                if (filter == null || filter.accept(n))
                                    files.add(n);
                            }
                            cursor2.close();
                        }
                    }
                }
                cursor.close();
            }
        } else {
            throw new Storage.UnknownUri();
        }
        return files;
    }
}
