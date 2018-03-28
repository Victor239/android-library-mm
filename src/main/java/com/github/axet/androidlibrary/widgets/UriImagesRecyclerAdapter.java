package com.github.axet.androidlibrary.widgets;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.widget.ImageView;
import android.widget.ProgressBar;

public abstract class UriImagesRecyclerAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    public static final String TAG = UriImagesRecyclerAdapter.class.getSimpleName();

    Adapter adapter = new Adapter();

    public class Adapter extends UriImagesAdapter {
        @Override
        public Bitmap downloadImageTask(DownloadImageTask task) {
            return UriImagesRecyclerAdapter.this.downloadImageTask(task);
        }

        @Override
        public void downloadTaskUpdate(DownloadImageTask task, Object item, Object view) {
            UriImagesRecyclerAdapter.this.downloadTaskUpdate(task, item, view);
        }

        @Override
        public void downloadTaskDone(DownloadImageTask task) {
            UriImagesRecyclerAdapter.this.downloadTaskDone(task);
        }

        public void downloadTaskDoneSuper(DownloadImageTask task) {
            super.downloadTaskDone(task);
        }
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

    public Bitmap downloadImageTask(UriImagesAdapter.DownloadImageTask task) {
        return null;
    }

    public void downloadTaskDone(UriImagesAdapter.DownloadImageTask task) {
        adapter.downloadTaskDoneSuper(task);
    }

    public void downloadTaskUpdate(UriImagesAdapter.DownloadImageTask task, Object i, Object o) {
    }

    public void updateView(UriImagesAdapter.DownloadImageTask task, ImageView image, ProgressBar progress) {
        adapter.updateView(task, image, progress);
    }

}
