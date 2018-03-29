package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.io.File;

public abstract class CacheImagesListAdapter extends BaseAdapter {
    public static final String TAG = CacheImagesListAdapter.class.getSimpleName();

    public Adapter adapter;

    public class Adapter extends CacheImagesAdapter {
        public Adapter(Context context) {
            super(context);
        }

        @Override
        public Bitmap downloadImageTask(DownloadImageTask task) {
            return CacheImagesListAdapter.this.downloadImageTask(task);
        }

        @Override
        public void downloadTaskUpdate(DownloadImageTask task, Object item, Object view) {
            CacheImagesListAdapter.this.downloadTaskUpdate(task, item, view);
        }

        @Override
        public void downloadTaskDone(DownloadImageTask task) {
            CacheImagesListAdapter.this.downloadTaskDone(task);
        }

        public void downloadTaskDoneSuper(DownloadImageTask task) {
            super.downloadTaskDone(task);
        }
    }

    public CacheImagesListAdapter(Context context) {
        adapter = new Adapter(context);
    }

    public File cacheUri(Uri u) {
        return adapter.cacheUri(u);
    }

    public void clearTasks() {
        adapter.clearTasks();
    }

    public void downloadTaskClean(Object view) {
        adapter.downloadTaskClean(view);
    }

    public void downloadTask(Object item, Object view) {
        adapter.downloadTask(item, view);
    }

    public Bitmap downloadImage(Uri cover) {
        return adapter.downloadImage(cover);
    }

    public Bitmap downloadImageTask(CacheImagesAdapter.DownloadImageTask task) {
        return null;
    }

    public void downloadTaskDone(CacheImagesAdapter.DownloadImageTask task) {
        adapter.downloadTaskDoneSuper(task);
    }

    public void downloadTaskUpdate(CacheImagesAdapter.DownloadImageTask task, Object i, Object o) {
    }

    public void updateView(CacheImagesAdapter.DownloadImageTask task, ImageView image, ProgressBar progress) {
        adapter.updateView(task, image, progress);
    }
}
