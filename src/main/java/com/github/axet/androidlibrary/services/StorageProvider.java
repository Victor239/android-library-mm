package com.github.axet.androidlibrary.services;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
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
    public static long TIMEOUT = 1 * 1000 * 60;
    public static final int MD5_SIZE = 32;

    public static final String CONTENTTYPE_FOLDER = "resource/folder";

    protected static ProviderInfo info;

    public static HashMap<String, Uri> hashs = new HashMap<>(); // hash -> original url
    public static HashMap<Uri, Long> uris = new HashMap<>(); // original url -> time

    protected Runnable refresh = new Runnable() {
        @Override
        public void run() {
            freeUris();
        }
    };
    protected Handler handler = new Handler();
    protected Storage storage;
    protected ContentResolver resolver;

    public static Intent openFolderIntent(Context context, Uri p) {
        if (Build.VERSION.SDK_INT >= 24 && context.getApplicationInfo().targetSdkVersion >= 24) { // 24+ failed to open file:// with FileUriExposedException
            p = share(context, p);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(p, CONTENTTYPE_FOLDER);
            FileProvider.grantPermissions(context, intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            return intent;
        } else { // 23 can open file://
            return openFolderIntent23(context, p);
        }
    }

    public static Intent openFolderIntent23(Context context, Uri p) {
        boolean perms = false;
        String s = p.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) && Build.VERSION.SDK_INT >= 21) { // convert content:///primary to file://
            String tree = DocumentsContract.getTreeDocumentId(p);
            String[] ss = tree.split(":"); // 1D13-0F08:private
            if (ss[0].equals(Storage.STORAGE_PRIMARY)) {
                File f = Environment.getExternalStorageDirectory();
                if (ss.length > 1)
                    f = new File(f, ss[1]);
                p = Uri.fromFile(f);
            } else {
                perms = true;
            }
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(p, CONTENTTYPE_FOLDER);
        if (perms)
            FileProvider.grantPermissions(context, intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    public static boolean isFolderCallable(Context context, Intent intent, String authory) {
        Uri p = intent.getData();
        String s = p.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT) && Build.VERSION.SDK_INT >= 21 && !p.getAuthority().equals(authory)) {
            String tree = DocumentsContract.getTreeDocumentId(p);
            String[] ss = tree.split(":"); // 1D13-0F08:private
            if (!ss[0].equals(Storage.STORAGE_PRIMARY)) {
                return false;
            }
        }
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (Build.VERSION.SDK_INT >= 24 && context.getApplicationInfo().targetSdkVersion >= 24)
                return false; // target sdk 24+ failed to open file:// links
        }
        return OptimizationPreferenceCompat.isCallable(context, intent);
    }

    public static Uri share(Context context, Uri u) { // original uri -> hased uri
        long now = System.currentTimeMillis();
        uris.put(u, now);
        String hash = Storage.md5(u.toString());
        hashs.put(hash, u);

        String name;

        String s = u.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT) && !DocumentsContract.isDocumentUri(context, u)) {
            String id = DocumentsContract.getTreeDocumentId(u);
            id = id.substring(id.indexOf(":") + 1);
            File f = new File(id);
            name = f.getName();
        } else {
            name = Storage.getDocumentName(u);
        }

        File path = new File(hash, name);
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).path(path.toString()).build();
    }

    public static String getAuthority() {
        if (info == null)
            return null; // service never been initalized (low api?)
        return info.authority;
    }

    public static boolean isStorageUri(Uri uri) {
        return uri.getPathSegments().get(0).length() == MD5_SIZE;
    }

    Uri find(Uri uri) { // hashed uri -> original uri
        String hash = uri.getPathSegments().get(0);
        Uri f = hashs.get(hash);
        if (f == null)
            return null;
        long now = System.currentTimeMillis();
        uris.put(f, now);
        return f;
    }

    void freeUris() {
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
        // Sanity check our security
        if (info.exported) {
            throw new SecurityException("Provider must not be exported");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    @Override
    public boolean onCreate() {
        storage = new Storage(getContext());
        resolver = getContext().getContentResolver();
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    @TargetApi(16)
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        Uri f = find(uri);
        if (f == null)
            return null;

        String s = f.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return resolver.query(f, projection, selection, selectionArgs, sortOrder, cancellationSignal);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            if (projection == null) {
                projection = FileProvider.COLUMNS;
            }

            File path = new File(f.getPath());

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
            throw new RuntimeException("unknown uri");
        }
    }

    @Override
    public String getType(Uri uri) {
        Uri f = find(uri);
        if (f == null)
            return null;
        String s = f.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return resolver.getType(f);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File ff = new File(f.getPath());
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(Storage.getExt(ff));
        } else {
            throw new RuntimeException("unknown uri");
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

        final int fileMode = FileProvider.modeToMode(mode);

        try {
            String s = f.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                return resolver.openFileDescriptor(f, mode);
            } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                File ff = new File(f.getPath());
                return ParcelFileDescriptor.open(ff, fileMode);
            } else {
                throw new RuntimeException("unknown uri");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
