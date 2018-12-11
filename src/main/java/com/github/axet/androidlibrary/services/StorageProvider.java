package com.github.axet.androidlibrary.services;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

// Helps open external urls using local contentprovider and settings storage path. Use share()
//
// manifest.xml
// <application>
//   <provider
//     android:name="com.github.axet.androidlibrary.services.StorageProvider"
//     android:authorities="com.github.axet.androidlibrary"
//     android:exported="false"
//     android:grantUriPermissions="true">
//   </provider>
// </application>
//
// url example:
// content://com.github.axet.androidlibrary/35470c701a29ef8a9d58fbf8bd89dd83/image.jpg
public class StorageProvider extends ContentProvider {
    public static String TAG = StorageProvider.class.getSimpleName();

    public static long TIMEOUT = 1 * 1000 * 60;
    public static final int MD5_SIZE = 32;

    public static final String CONTENTTYPE_FOLDER = "resource/folder";
    public static final String SCHEME_FOLDER = "folder";

    public static final String FILE_PREFIX = "storage";
    public static final String FILE_SUFFIX = ".tmp";

    protected static HashMap<Class, StorageProvider> infos = new HashMap<>();

    public ProviderInfo info;
    public HashMap<String, Uri> hashs = new HashMap<>(); // hash -> original url
    public HashMap<Uri, Long> uris = new HashMap<>(); // original url -> time
    public HashMap<Uri, String> names = new HashMap<>(); // original url -> name

    protected Runnable refresh = new Runnable() {
        @Override
        public void run() {
            freeUris();
        }
    };
    protected Handler handler = new Handler();
    protected Storage storage;
    protected ContentResolver resolver;

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    public static boolean isFolderCallable(Context context, Intent intent, String authory) {
        Uri p = intent.getData();
        String s = p.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) && Build.VERSION.SDK_INT >= 21 && !p.getAuthority().equals(authory)) {
            String tree = DocumentsContract.getTreeDocumentId(p);
            String[] ss = tree.split(Storage.COLON, 2); // 1D13-0F08:private
            if (!ss[0].equals(Storage.STORAGE_PRIMARY))
                return false;
        }
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (Build.VERSION.SDK_INT >= 24 && context.getApplicationInfo().targetSdkVersion >= 24)
                return false; // target sdk 24+ failed to open file:// links
        }
        return OptimizationPreferenceCompat.isCallable(context, intent);
    }

    public static StorageProvider getProvider() {
        return infos.get(StorageProvider.class);
    }

    public static boolean isStorageUri(Uri uri) {
        return uri.getPathSegments().get(0).length() == MD5_SIZE;
    }

    @TargetApi(21)
    public static Uri filterFolderIntent(Context context, Uri uri) { // convert content:///primary to file://
        String tree;
        if (DocumentsContract.isDocumentUri(context, uri))
            tree = DocumentsContract.getDocumentId(uri);
        else
            tree = DocumentsContract.getTreeDocumentId(uri);
        String[] ss = tree.split(Storage.COLON, 2); // 1D13-0F08:folder_name
        if (ss[0].equals(Storage.STORAGE_PRIMARY)) {
            File f = new File(Environment.getExternalStorageDirectory(), ss[1]);
            uri = Uri.fromFile(f);
        } else {
            File[] ff = OpenFileDialog.getPortableList();
            for (File f : ff) {
                if (ss[0].equals(f.getName())) {
                    File r = new File(f, ss[1]);
                    uri = Uri.fromFile(r);
                }
            }
        }
        return uri;
    }

    public static Intent openFolderIntent(Context context, Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) && Build.VERSION.SDK_INT >= 21 && uri.getAuthority().startsWith(Storage.SAF))
            uri = filterFolderIntent(context, uri);
        s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE) && Build.VERSION.SDK_INT >= 24 && context.getApplicationInfo().targetSdkVersion >= 24) { // 24+ failed to open file:// with FileUriExposedException
            Uri.Builder b = uri.buildUpon();
            b.scheme(SCHEME_FOLDER); // replace file:// with folder://
            uri = b.build();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, CONTENTTYPE_FOLDER);
            FileProvider.grantPermissions(context, intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            return intent;
        } else { // 23 can open file://
            return openFolderIntent23(context, uri);
        }
    }

    public static Intent openFolderIntent23(Context context, Uri uri) {
        boolean perms = false;
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) && Build.VERSION.SDK_INT >= 21 && uri.getAuthority().startsWith(Storage.SAF)) { // convert content:///primary to file://
            Uri old = uri;
            uri = filterFolderIntent(context, uri);
            if (old == uri) // content:// uri not converted by filter, set perms
                perms = true;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, CONTENTTYPE_FOLDER);
        if (perms)
            FileProvider.grantPermissions(context, intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    public static Intent openIntent23(Context context, Uri uri) {
        boolean perms = false;
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) && Build.VERSION.SDK_INT >= 21 && uri.getAuthority().startsWith(Storage.SAF)) { // convert content://.../primary to file://
            Uri old = uri;
            uri = filterFolderIntent(context, uri);
            if (old == uri)
                perms = true;
        } else {
            perms = true;
        }
        String name = Storage.getName(context, uri);
        String type = Storage.getTypeByName(name);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setDataAndType(uri, type);
        if (perms)
            FileProvider.grantPermissions(context, intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    public static Intent shareIntent23(Context context, Uri uri, String type, String subject) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_EMAIL, "");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.shared_via, getApplicationName(context)));
        FileProvider.grantPermissions(context, intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return intent;
    }

    public void deleteTmp() {
        File tmp = getContext().getExternalCacheDir();
        deleteTmp(tmp);
        tmp = getContext().getCacheDir();
        deleteTmp(tmp);
    }

    public void deleteTmp(File tmp) {
        if (tmp == null)
            return;
        File[] ff = tmp.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().startsWith(FILE_PREFIX);
            }
        });
        if (ff == null)
            return;
        for (File f : ff) {
            f.delete();
        }
    }

    public static class InputStreamWriter {
        InputStream is;

        public InputStreamWriter() {
        }

        public InputStreamWriter(InputStream is) {
            this.is = is;
        }

        public void copy(OutputStream os) throws IOException {
            IOUtils.copy(is, os);
        }

        public long getSize() {
            return AssetFileDescriptor.UNKNOWN_LENGTH;
        }

        public void close() throws IOException {
            is.close();
        }
    }

    public static class ParcelInputStream extends ParcelFileDescriptor {
        Thread thread;
        ParcelFileDescriptor w;

        public ParcelInputStream(ParcelFileDescriptor[] ff) {
            super(ff[0]);
            w = ff[1];
        }

        public ParcelInputStream(final InputStream is) throws IOException {
            this(ParcelFileDescriptor.createPipe());
            thread = new Thread("ParcelInputStream") {
                @Override
                public void run() {
                    OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(w);
                    try {
                        IOUtils.copy(is, os);
                    } catch (IOException e) {
                        Log.d(TAG, "Copy error", e);
                    } finally {
                        try {
                            os.close();
                        } catch (IOException e) {
                            Log.d(TAG, "Copy close error", e);
                        }
                        try {
                            is.close();
                        } catch (IOException e) {
                            Log.d(TAG, "Copy close error", e);
                        }
                    }
                }
            };
            thread.start();
        }

        public ParcelInputStream() throws IOException {
            this(ParcelFileDescriptor.createPipe());
            thread = new Thread("ParcelInputStream") {
                @Override
                public void run() {
                    OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(w);
                    try {
                        copy(os);
                    } catch (IOException e) {
                        Log.d(TAG, "Copy error", e);
                    } finally {
                        try {
                            os.close();
                        } catch (IOException e) {
                            Log.d(TAG, "Copy close error", e);
                        }
                    }
                }
            };
            thread.start();
        }

        public void copy(OutputStream os) throws IOException {
        }

        @Override
        public long getStatSize() {
            return super.getStatSize();
        }
    }

    public StorageProvider() {
    }

    public Intent openIntent(Uri uri) {
        return openIntent(uri, null);
    }

    public Intent openIntent(Uri uri, String name) {
        if (Build.VERSION.SDK_INT >= 24 && getContext().getApplicationInfo().targetSdkVersion >= 24) { // API24+ failed to open file:// with FileUriExposedException
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File f = Storage.getFile(uri);
                if (f.isDirectory())
                    return openFolderIntent(getContext(), uri);
                uri = share(uri, name);
                return openIntent23(getContext(), uri);
            }
        }
        if (name != null)
            uri = share(uri, name);
        return openIntent23(getContext(), uri);
    }

    public Intent shareIntent(Uri uri, String type, String name) {
        return shareIntent(uri, null, type, name);
    }

    public Intent shareIntent(Uri uri, String name, String type, String subject) {
        if (Build.VERSION.SDK_INT >= 24 && getContext().getApplicationInfo().targetSdkVersion >= 24) { // API24+ failed to open file:// with FileUriExposedException
            uri = share(uri, name);
            return shareIntent23(getContext(), uri, type, subject);
        } else { // API23 can open file://
            if (name != null)
                uri = share(uri, name);
            return shareIntent23(getContext(), uri, type, subject);
        }
    }

    public Uri share(Uri u) { // original uri -> hased uri
        return share(u, null);
    }

    public Uri share(Uri u, String name) { // original uri -> hased uri
        long now = System.currentTimeMillis();
        uris.put(u, now);
        String hash = Storage.md5(u.toString());
        hashs.put(hash, u);

        if (name == null) {
            String s = u.getScheme();
            if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT) && !DocumentsContract.isDocumentUri(getContext(), u)) {
                String id = DocumentsContract.getTreeDocumentId(u);
                id = id.substring(id.indexOf(Storage.COLON) + 1);
                File f = new File(id);
                name = f.getName();
            } else {
                name = Storage.getName(getContext(), u);
            }
        }

        File path = new File(hash, name);
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).path(path.getPath()).build();
    }

    public String getAuthority() {
        if (info == null)
            return null; // service never been initalized (low api?)
        return info.authority;
    }

    public Uri find(Uri uri) { // hashed uri -> original uri
        String hash = uri.getPathSegments().get(0);
        Uri f = hashs.get(hash);
        if (f == null)
            return null;
        long now = System.currentTimeMillis();
        uris.put(f, now);
        return f;
    }

    public void freeUris() {
        long now = System.currentTimeMillis();
        for (Uri p : new HashSet<>(uris.keySet())) {
            long l = uris.get(p);
            if (l + TIMEOUT < now) {
                uris.remove(p);
                String hash = Storage.md5(p.toString());
                hashs.remove(hash);
            }
        }
        if (uris.size() == 0)
            return;
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, TIMEOUT);
    }

    @Override
    public void attachInfo(Context context, ProviderInfo i) {
        super.attachInfo(context, i);
        info = i;
        if (info.exported)
            throw new SecurityException("Provider must not be exported");
        if (!info.grantUriPermissions)
            throw new SecurityException("Provider must grant uri permissions");
        infos.put(getClass(), this);
    }

    @Override
    public boolean onCreate() {
        storage = new Storage(getContext());
        resolver = getContext().getContentResolver();
        return true;
    }

    @Nullable
    @Override
    @TargetApi(16)
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Uri f = find(uri);
        if (f == null)
            return null;

        String s = f.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return resolver.query(f, projection, selection, selectionArgs, sortOrder);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (projection == null)
                projection = FileProvider.COLUMNS;

            File path = Storage.getFile(f);

            if (path.isDirectory()) {
                File[] ff = path.listFiles();
                if (ff == null || ff.length == 0)
                    return null;

                final MatrixCursor cursor = new MatrixCursor(projection, 1);

                for (File file : ff) {
                    String[] cols = new String[projection.length];
                    Object[] values = new Object[projection.length];

                    int i = 0;
                    for (String col : projection) {
                        if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                            cols[i] = OpenableColumns.DISPLAY_NAME;
                            values[i++] = file.getName();
                        } else if (OpenableColumns.SIZE.equals(col)) {
                            cols[i] = OpenableColumns.SIZE;
                            values[i++] = file.length();
                        }
                    }

                    values = FileProvider.copyOf(values, i);

                    cursor.addRow(values);
                }

                return cursor;
            } else {
                final MatrixCursor cursor = new MatrixCursor(projection, 1);

                String[] cols = new String[projection.length];
                Object[] values = new Object[projection.length];

                int i = 0;
                for (String col : projection) {
                    if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                        cols[i] = OpenableColumns.DISPLAY_NAME;
                        values[i++] = uri.getLastPathSegment(); // contains original name
                    } else if (OpenableColumns.SIZE.equals(col)) {
                        cols[i] = OpenableColumns.SIZE;
                        values[i++] = path.length();
                    }
                }

                values = FileProvider.copyOf(values, i);

                cursor.addRow(values);
                return cursor;
            }
        } else {
            throw new Storage.UnknownUri();
        }
    }

    @Override
    public String getType(Uri uri) {
        Uri f = find(uri);
        if (f == null)
            return null;
        String s = f.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return resolver.getType(f);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(f);
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(Storage.getExt(k));
        } else {
            throw new Storage.UnknownUri();
        }
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Uri f = find(uri);
        if (f == null)
            return null;
        freeUris();
        try {
            String s = f.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                return resolver.openFileDescriptor(f, mode);
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                return ParcelFileDescriptor.open(Storage.getFile(f), FileProvider.modeToMode(mode));
            } else {
                throw new Storage.UnknownUri();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Uri f = find(uri);
        if (f == null)
            return null;
        freeUris();
        try {
            String s = f.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                return resolver.openAssetFileDescriptor(f, mode);
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(f);
                return new AssetFileDescriptor(ParcelFileDescriptor.open(k, FileProvider.modeToMode(mode)), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            } else {
                throw new Storage.UnknownUri();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String checkMode(String mode) { // check if caller required File on disk
        if (Build.VERSION.SDK_INT >= 19 && mode.equals("r")) {
            String[] ss = new String[]{ // know broken packages list which request socket (mode "r") but required file on disk, check ContentResolver#openFileDescriptor
                    "com.google.android.music"
            };
            Arrays.sort(ss);
            if (Arrays.binarySearch(ss, getCallingPackage()) >= 0)
                return "rw"; // rw means we request file on disk, check ContentResolver#openFileDescriptor
        }
        return mode;
    }

    public ParcelFileDescriptor openInputStream(final InputStreamWriter is, String mode) {
        deleteTmp(); // will not delete opened files
        try {
            if (checkMode(mode).equals("r")) { // r - can be pipe. check ContentProvider#openFile
                return new ParcelInputStream() {
                    @Override
                    public void copy(OutputStream os) throws IOException {
                        try {
                            is.copy(os);
                        } finally {
                            is.close();
                        }
                    }

                    @Override
                    public long getStatSize() {
                        return is.getSize();
                    }
                };
            } else { // rw - has to be File. check ContentProvider#openFile
                File tmp = getContext().getExternalCacheDir();
                if (tmp == null)
                    tmp = getContext().getCacheDir();
                tmp = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, tmp);
                FileOutputStream os = new FileOutputStream(tmp);
                try {
                    is.copy(os);
                } finally {
                    try {
                        os.close();
                    } catch (IOException e) {
                        Log.d(TAG, "copy close error", e);
                    }
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.d(TAG, "copy close error", e);
                    }
                }
                return ParcelFileDescriptor.open(tmp, FileProvider.modeToMode(mode));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
