package com.github.axet.androidlibrary.services;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * <application>
 * <provider
 * android:name=".AssetsProvider"
 * android:authorities="com.github.axet.appspace"
 * android:exported="false"
 * android:grantUriPermissions="true">
 * </provider>
 * </application>
 */
public class AssetsProvider extends ContentProvider {
    static Map<Uri, String> types = new HashMap<>();
    static Map<Uri, String> names = new HashMap<>();
    static Map<Uri, AssetFileDescriptor> files = new HashMap<>();

    static ProviderInfo info;

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        this.info = info;
        AssetManager am = getContext().getAssets();
        try {
            String[] a = am.list("");
            for (String f : a) {
                addFile(f, am.openFd(f));
            }
        } catch (IOException e) {
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        AssetManager am = getContext().getAssets();
        String file_name = uri.getLastPathSegment();
        if (file_name == null)
            throw new FileNotFoundException();
        AssetFileDescriptor afd = null;
        try {
            afd = am.openFd(file_name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return afd;
    }

    public static Uri addFile(String name, AssetFileDescriptor file) {
        Uri u = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + info.authority + "/" + name);
        String type = MimeTypeMap.getFileExtensionFromUrl(u.toString());
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type);
        types.put(u, type);
        names.put(u, name);
        files.put(u, file);
        return u;
    }

    @Override
    public String getType(Uri p1) {
        return types.get(p1);
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

    @Override
    public Cursor query(Uri p1, String[] p2, String p3, String[] p4, String p5) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        if (projection == null) {
            projection = FileProvider.COLUMNS;
        }

        String file_name = uri.getLastPathSegment();
        if (file_name == null)
            return null;

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = names.get(uri);
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = files.get(uri).getLength();
            }
        }
        cols = FileProvider.copyOf(cols, i);
        values = FileProvider.copyOf(values, i);
        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }
}