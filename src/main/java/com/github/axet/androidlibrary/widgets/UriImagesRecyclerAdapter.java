package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.github.axet.androidlibrary.R;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class UriImagesRecyclerAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    private static final String TAG = UriImagesRecyclerAdapter.class.getSimpleName();

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

    public Map<Object, DownloadImageTask> downloadViews = new HashMap<>();
    public Map<Object, DownloadImageTask> downloadItems = new HashMap<>();
    public Context context;
    public Map<DownloadImageTask, Runnable> tasks = new ConcurrentHashMap<>();
    public Map<Runnable, DownloadImageTask> runs = new ConcurrentHashMap<>();
    protected DownloadImageTask current;
    public UriImagesExecutor executor = new UriImagesExecutor();

    public class UriImagesExecutor extends ThreadPoolExecutor {
        public UriImagesExecutor() {
            super(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
            allowCoreThreadTimeOut(true);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            DownloadImageTask task = runs.get(r);
            if (task != null) {
                synchronized (task.lock) {
                    task.start = true;
                }
            }
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
    }

    public static class DownloadImageTask extends AsyncTask<Object, Void, Bitmap> {
        public final Object lock = new Object();
        public Bitmap bm;
        public Object item;
        public HashSet<Object> views = new HashSet<>(); // one task can set multiple ImageView's, except reused ones;
        public boolean start; // start download thread
        public boolean done; // done downloading (may be failed)
        UriImagesRecyclerAdapter a;

        public DownloadImageTask(UriImagesRecyclerAdapter a, Object item, Object v) {
            this.a = a;
            this.item = item;
            views.add(v);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected Bitmap doInBackground(Object... urls) {
            return a.downloadImageTask(this);
        }

        protected void onPostExecute(Bitmap result) {
            if (isCancelled())
                return;
            done = true;
            if (result != null)
                bm = result;
            a.downloadTaskDone(this);
        }
    }

    public UriImagesRecyclerAdapter(Context context) {
        this.context = context;
    }

    public void refresh() {
    }

    public void downloadTaskClean(Object view) {
        DownloadImageTask task = downloadViews.get(view);
        if (task != null) { // reuse image
            task.views.remove(view);
            downloadViews.remove(view);
            synchronized (task.lock) {
                if (!task.start && task.views.size() == 0 && !task.done) {
                    executor.cancel(task);
                    downloadItems.remove(task.item);
                }
            }
        }
    }

    public void downloadTask(Object item, Object view) {
        downloadTaskClean(view);
        DownloadImageTask task = downloadItems.get(item);
        if (task != null) { // add new ImageView to populate on finish
            if (task.done) {
                downloadTaskUpdate(task, item, view);
                return;
            }
            task.views.add(view);
            downloadViews.put(view, task);
        }
        if (task == null) {
            task = new DownloadImageTask(this, item, view);
            downloadViews.put(view, task);
            downloadItems.put(item, task);
            executor.execute(task);
        }
        downloadTaskUpdate(task, item, view);
    }

    public void downloadTaskDone(DownloadImageTask task) {
        for (Object o : task.views)
            downloadTaskUpdate(task, task.item, o);
    }

    public void downloadTaskUpdate(DownloadImageTask task, Object item, Object view) {
    }

    public Bitmap downloadImageTask(DownloadImageTask task) {
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    public void clearTasks() {
        for (Object item : downloadViews.keySet()) {
            DownloadImageTask t = downloadViews.get(item);
            executor.cancel(t);
        }
        downloadViews.clear();

        for (Object item : downloadItems.keySet()) {
            DownloadImageTask t = downloadItems.get(item);
            executor.cancel(t);
        }
        downloadItems.clear();

        tasks.clear();
        runs.clear();
    }

    public Bitmap downloadImage(Uri cover) {
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

    public void updateView(DownloadImageTask task, ImageView image, ProgressBar progress) {
        if (task != null && task.bm != null) {
            image.setImageBitmap(task.bm);
        } else {
            image.setImageResource(R.drawable.ic_image_black_24dp);
        }
        progress.setVisibility((task == null || task.done) ? View.GONE : View.VISIBLE);
    }
}
