package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;

import com.github.axet.androidlibrary.R;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class UriImagesAdapter extends ThreadPoolExecutor implements ListAdapter {
    private static final String TAG = UriImagesAdapter.class.getSimpleName();

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    public static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<Runnable>(128);

    public static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
        }
    };

    public Map<Uri, DownloadImageTask> views = new TreeMap<>();
    public Map<ImageView, DownloadImageTask> images = new HashMap<>();
    public DataSetObserver listener;
    public Context context;
    public Map<DownloadImageTask, Runnable> tasks = new ConcurrentHashMap<>();
    public Map<Runnable, DownloadImageTask> runs = new ConcurrentHashMap<>();
    protected DownloadImageTask current;

    public static class DownloadImageTask extends AsyncTask<Uri, Void, Bitmap> {
        public Bitmap bm;
        public HashSet<ImageView> views = new HashSet<>(); // one task can set multiple ImageView's, except reused ones;
        public HashSet<ProgressBar> progress = new HashSet<>();
        public boolean done;
        public boolean start;
        public Uri cover;

        public DownloadImageTask(ProgressBar p, ImageView i, Uri c) {
            progress.add(p);
            views.add(i);
            cover = c;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected Bitmap doInBackground(Uri... urls) {
            Bitmap bm = null;
            try {
                String s = cover.getScheme();
                if (s.startsWith(WebViewCustom.SCHEME_HTTP)) {
                    InputStream in = new URL(cover.toString()).openStream();
                    bm = BitmapFactory.decodeStream(in);
                } else {
                    bm = BitmapFactory.decodeFile(cover.getPath());
                }
            } catch (Exception e) {
                Log.e(TAG, "broken download", e);
            }
            return bm;
        }

        protected void onPostExecute(Bitmap result) {
            done = true;
            for (ProgressBar p : progress)
                p.setVisibility(View.GONE);
            bm = result;
            if (bm == null)
                return;
            for (ImageView v : views)
                v.setImageBitmap(bm);
        }
    }

    public UriImagesAdapter(Context context) {
        super(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
        allowCoreThreadTimeOut(true);
        this.context = context;
    }

    public Uri getCover(int position) {
        return null;
    }

    public void refresh() {
    }

    public void notifyDataSetChanged() {
        if (listener != null)
            listener.onChanged();
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        DownloadImageTask task = runs.get(r);
        if (task != null)
            task.start = true;
    }

    @Override
    public void execute(Runnable command) {
        tasks.put(current, command);
        runs.put(command, current);
        super.execute(command);
        current = null;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        DownloadImageTask task = runs.remove(r);
        if (task != null)
            tasks.remove(task);
    }

    public void execute(DownloadImageTask task) {
        current = task;
        if (Build.VERSION.SDK_INT < 11)
            task.execute();
        else
            task.executeOnExecutor(this);
    }

    public void cancel(DownloadImageTask task) {
        task.cancel(true);
        Runnable r = tasks.remove(task);
        if (r != null) {
            sPoolWorkQueue.remove(r);
            runs.remove(r);
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        listener = observer;
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        listener = null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return null;
    }

    public View getView(Uri cover, View convertView, ImageView image, ProgressBar progress) {
        progress.setVisibility(View.GONE);

        if (cover != null) {
            DownloadImageTask task = images.get(image);
            if (task != null) { // reuse image
                task.views.remove(image);
                task.progress.remove(progress);
                if (!task.start) {
                    cancel(task);
                    views.remove(task.cover);
                }
            }
            task = views.get(cover);
            if (task != null) { // add new ImageView to populate on finish
                task.views.add(image);
                task.progress.add(progress);
            }
            if (task == null) {
                task = new DownloadImageTask(progress, image, cover);
                views.put(cover, task);
                images.put(image, task);
                execute(task);
            }
            if (task.bm != null) {
                image.setImageBitmap(task.bm);
            } else {
                image.setImageResource(R.drawable.ic_image_black_24dp);
            }
            for (ProgressBar p : task.progress) {
                p.setVisibility(task.done ? View.GONE : View.VISIBLE);
            }
        }

        return convertView;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    public void clearTasks() {
        for (Uri item : views.keySet()) {
            DownloadImageTask t = views.get(item);
            t.cancel(true);
        }
        views.clear();
        for (ImageView item : images.keySet()) {
            DownloadImageTask t = images.get(item);
            t.cancel(true);
        }
        images.clear();

        tasks.clear();
        runs.clear();
    }
}
