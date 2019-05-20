package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v7.view.ContextThemeWrapper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;

import com.github.axet.androidlibrary.R;

import java.util.Arrays;

// Check android notification_template_base.xml for constants
public class RemoteNotificationCompat extends NotificationCompat {

    public static Bitmap getBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            bitmap = bitmapDrawable.getBitmap();
            if (bitmap != null)
                return bitmap;
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0)
            bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        else
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Drawable getApplicationIcon(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.getApplicationIcon(context.getApplicationInfo());
    }

    public static Bitmap getBitmap(Context context) {
        Drawable d = getApplicationIcon(context);
        return getBitmap(d);
    }

    public static Rect getAdaptivePaddings(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int nw = lp.width;
        int nh = lp.height;
        if (nw <= 0)
            nw = view.getWidth();
        if (nh <= 0)
            nh = view.getHeight();
        if (nw <= 0)
            nw = view.getMeasuredWidth();
        if (nh <= 0)
            nh = view.getMeasuredHeight();
        if (nw <= 0 || nh <= 0)
            throw new RuntimeException("Adaptive Icon view must be fixed");
        return getAdaptivePaddings(view.getContext(), nw, nh);
    }

    public static Rect getAdaptivePaddings(Context context, RemoteViews view, int id) {
        DimensionFactory size = new DimensionFactory(context, id);
        RemoteViewsCompat.applyTheme(context, view, size);
        if (size.width <= 0 || size.height <= 0)
            throw new RuntimeException("Adaptive Icon view must be fixed");
        return getAdaptivePaddings(context, size.width, size.height);
    }

    public static Rect getAdaptivePaddings(Context context, int nw, int nh) {
        int fg = ThemeUtils.dp2px(context, 108);
        int fp = ThemeUtils.dp2px(context, 72);
        int ap = (fg - fp) / 2; // adaptive icon padding = 18dp
        float nrw = nw / (float) fg; // layout icon ratio width
        float nrh = nh / (float) fg; // layout icon ratio height
        int wp = -(int) (ap * nrw);
        int hp = -(int) (ap * nrh);
        return new Rect(wp, hp, wp, hp);
    }

    @TargetApi(16)
    public static void setAdaptiveIcon(Context context, RemoteViews view, int id) {
        Rect r = getAdaptivePaddings(context, view, R.id.icon);
        view.setViewPadding(R.id.icon, r.left, r.top, r.right, r.bottom);
        view.setImageViewResource(R.id.icon, id);
    }

    @TargetApi(16)
    public static void setAdaptiveIcon(Context context, RemoteViews view, int nw, int nh, int id) {
        Rect r = getAdaptivePaddings(context, nw, nh);
        view.setViewPadding(R.id.icon, r.left, r.top, r.right, r.bottom);
        view.setImageViewResource(R.id.icon, id);
    }

    public static void setAdaptiveIcon(ImageView view, int id) {
        Rect r = getAdaptivePaddings(view);
        view.setPadding(r.left, r.top, r.right, r.bottom);
        view.setImageResource(id);
    }

    public static class DimensionFactory implements LayoutInflater.Factory {
        public Context context;
        public int width;
        public int height;
        public int id;

        public DimensionFactory(Context context, int id) {
            this.context = context;
            this.id = id;
        }

        public int getDimension(Context context, TypedValue out) {
            if (out.type == TypedValue.TYPE_STRING || out.type == TypedValue.TYPE_REFERENCE)
                out.data = context.getResources().getDimensionPixelOffset(out.resourceId); // xml color selector
            if (out.type == TypedValue.TYPE_DIMENSION) {
                int type = out.data & 0xff;
                int value = out.data >> 8;
                switch (type) {
                    case 1: // dp
                        out.data = ThemeUtils.dp2px(context, value); // xml color selector
                        break;
                    case 2: // sp
                        out.data = ThemeUtils.sp2px(context, value); // xml color selector
                        break;
                }
            }
            return out.data;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            if (Build.VERSION.SDK_INT >= 21 && this.context != context) // API21+ and 'android:theme' applied = ignore
                return null;

            int[] attrsArray = new int[]{
                    android.R.attr.id,
                    android.R.attr.layout_width,
                    android.R.attr.layout_height,
            };

            Arrays.sort(attrsArray); // know bug https://stackoverflow.com/questions/19034597

            final int ID = Arrays.binarySearch(attrsArray, android.R.attr.id);
            final int WIDTH = Arrays.binarySearch(attrsArray, android.R.attr.layout_width);
            final int HEIGHT = Arrays.binarySearch(attrsArray, android.R.attr.layout_height);

            Resources.Theme theme = context.getTheme();
            TypedArray ta = theme.obtainStyledAttributes(attrs, attrsArray, 0, 0);
            TypedValue out = new TypedValue();
            if (ta.getValue(ID, out)) {
                int id = out.resourceId;
                if (id == this.id) {
                    if (ta.getValue(WIDTH, out))
                        width = getDimension(context, out);
                    if (ta.getValue(HEIGHT, out))
                        height = getDimension(context, out);
                }
            }
            ta.recycle();
            return null;
        }
    }

    public static class Builder extends NotificationCompat.Builder {
        public NotificationChannelCompat channel;
        public RemoteViews compact;
        public RemoteViews big;
        public ContextThemeWrapper theme;

        protected Builder(Context context) {
            super(context);
        }

        public Builder(Context context, int layoutId) {
            super(context);
            create(layoutId);
        }

        public Builder(Context context, int layoutId, int bigId) {
            this(context, layoutId);
            create(layoutId, bigId);
        }

        @SuppressLint("RestrictedApi")
        public void create(int layoutId) {
            compact = new RemoteViews(mContext.getPackageName(), layoutId);
            setCustomContentView(compact);
            if (Build.VERSION.SDK_INT >= 21)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }

        @SuppressLint("RestrictedApi")
        public void create(int layoutId, int bigId) {
            create(layoutId);
            big = new RemoteViews(mContext.getPackageName(), bigId);
            setCustomBigContentView(big);
        }

        public Builder setChannel(NotificationChannelCompat channel) {
            this.channel = channel;
            channel.apply(this);
            return this;
        }

        public Builder setWhen(Notification n) {
            setWhen(n == null ? System.currentTimeMillis() : n.when);
            return this;
        }

        @SuppressLint("RestrictedApi")
        public Builder setTheme(int id) {
            theme = new ContextThemeWrapper(mContext, id);
            RemoteViewsCompat.applyTheme(theme, compact);
            if (big != null)
                RemoteViewsCompat.applyTheme(theme, big);
            return this;
        }

        public Builder setMainIntent(PendingIntent main) {
            compact.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            if (big != null)
                big.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            if (Build.VERSION.SDK_INT < 11)
                super.setContentIntent(main);
            return this;
        }

        public Builder setTitle(CharSequence title) {
            return setTitle(title, null);
        }

        public Builder setTitle(CharSequence title, CharSequence ticker) {
            super.setContentTitle(title);
            compact.setTextViewText(R.id.title, title);
            if (big != null)
                big.setTextViewText(R.id.title, title);
            setTicker(ticker); // few secs short tooltip
            return this;
        }

        public Builder setText(CharSequence text) {
            super.setContentText(text);
            compact.setTextViewText(R.id.text, text);
            if (big != null)
                big.setTextViewText(R.id.text, text);
            return this;
        }

        @SuppressLint("RestrictedApi")
        public int getThemeColor(int attr) {
            Context context = theme;
            if (context == null)
                context = mContext;
            return ThemeUtils.getThemeColor(context, attr);
        }

        public Builder setImageViewTint(int id, int color) { // android:tint="?attr/..." crashing <API21
            RemoteViewsCompat.setImageViewTint(compact, id, color);
            if (big != null)
                RemoteViewsCompat.setImageViewTint(big, id, color);
            return this;
        }

        public Builder setIcon(int id) {
            compact.setImageViewResource(R.id.icon, id);
            if (big != null)
                big.setImageViewResource(R.id.icon, id);
            return this;
        }

        @TargetApi(16)
        @SuppressLint("RestrictedApi")
        public Builder setAdaptiveIcon(int id) { // android adaptive foreground icon has 72dp out of 108dp
            Context context = theme;
            if (context == null)
                context = mContext;
            RemoteNotificationCompat.setAdaptiveIcon(context, compact, id);
            if (big != null)
                RemoteNotificationCompat.setAdaptiveIcon(context, big, id);
            return this;
        }

        public Builder setViewVisibility(int id, int v) {
            compact.setViewVisibility(id, v);
            if (big != null)
                big.setViewVisibility(id, v);
            return this;
        }

        public Builder setImageViewResource(int id, int res) {
            compact.setImageViewResource(id, res);
            if (big != null)
                big.setImageViewResource(id, res);
            return this;
        }

        public Builder setOnClickPendingIntent(int id, PendingIntent pe) {
            compact.setOnClickPendingIntent(id, pe);
            if (big != null)
                big.setOnClickPendingIntent(id, pe);
            return this;
        }

        public Builder setTextViewText(int id, CharSequence t) {
            compact.setTextViewText(id, t);
            if (big != null)
                big.setTextViewText(id, t);
            return this;
        }

        public Builder setContentDescription(int id, CharSequence text) {
            RemoteViewsCompat.setContentDescription(compact, id, text);
            if (big != null)
                RemoteViewsCompat.setContentDescription(big, id, text);
            return this;
        }

        public Builder setImageViewBitmap(int id, Bitmap bm) {
            compact.setImageViewBitmap(id, bm);
            if (big != null)
                big.setImageViewBitmap(id, bm);
            return this;
        }

        @Override
        public Notification build() {
            Notification n = super.build();
            if (channel != null)
                NotificationChannelCompat.setChannelId(n, channel.channelId); // builder recreate Notification object by prorerty
            return n;
        }
    }

    public static class Default extends Builder {
        int foreground; // foreground part of icon

        public Default(Context context) {
            super(context);
            create(R.layout.remoteview);
            setImageViewBitmap(R.id.icon, getBitmap(context));
            setViewVisibility(R.id.icon_circle, View.GONE);
        }

        public Default(Context context, int foreground) { // foregound icon have circle under it
            super(context);
            this.foreground = foreground;
            create(R.layout.remoteview);
            setIcon(foreground);
            if (Build.VERSION.SDK_INT >= 21)
                setImageViewTint(R.id.icon_circle, getThemeColor(android.R.attr.colorButtonNormal));
            else
                setImageViewTint(R.id.icon_circle, getThemeColor(android.R.attr.windowBackground));
        }

        @Override
        public Builder setTheme(int id) {
            super.setTheme(id);
            setImageViewTint(R.id.icon_circle, getThemeColor(R.attr.colorButtonNormal));
            if (foreground == 0) // clear default tint if here is app default icon
                setImageViewTint(R.id.icon, 0);
            return this;
        }
    }

    public static class Low extends Builder {
        private static final int LOW = R.layout.remoteview_low; // when public crashing javadoc

        public Low(Context context) {
            super(context);
            create(LOW);
        }

        public Low(Context context, int bigId) {
            super(context);
            if (Build.VERSION.SDK_INT >= 26)
                create(LOW, bigId);
            else
                create(bigId);
        }

        @SuppressLint("RestrictedApi")
        @Override
        public void create(int layoutId) {
            super.create(layoutId);
            if (compact.getLayoutId() == LOW)
                compact.setTextViewText(R.id.app_name_text, AboutPreferenceCompat.getApplicationName(mContext));
        }

        @Override
        public Builder setText(CharSequence text) {
            if (compact.getLayoutId() == LOW) {
                compact.setViewVisibility(R.id.header_text_divider, View.VISIBLE);
                compact.setTextViewText(R.id.header_text, text);
                compact.setViewVisibility(R.id.header_text, View.VISIBLE);
            }
            return super.setText(text);
        }

        @SuppressLint("RestrictedApi")
        @Override
        public Builder setAdaptiveIcon(int id) {
            Context context = theme;
            if (context == null)
                context = mContext;
            if (compact.getLayoutId() == LOW) {
                RemoteNotificationCompat.setAdaptiveIcon(context, compact, id);
                if (big != null) {
                    RemoteNotificationCompat.setAdaptiveIcon(context, big, id);
                }
                return this;
            } else {
                return super.setAdaptiveIcon(id);
            }
        }

        @Override
        public NotificationCompat.Builder setSmallIcon(int icon) {
            if (theme != null)
                setImageViewTint(R.id.icon_circle, getThemeColor(R.attr.colorButtonNormal));
            else if (Build.VERSION.SDK_INT >= 21)
                setImageViewTint(R.id.icon_circle, getThemeColor(android.R.attr.colorButtonNormal));
            else
                setImageViewTint(R.id.icon_circle, getThemeColor(android.R.attr.windowBackground));
            return super.setSmallIcon(icon);
        }
    }
}
