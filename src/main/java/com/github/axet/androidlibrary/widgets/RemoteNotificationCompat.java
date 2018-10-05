package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v7.view.ContextThemeWrapper;
import android.view.View;
import android.widget.RemoteViews;

import com.github.axet.androidlibrary.R;

// Check android notification_template_base.xml for constants
public class RemoteNotificationCompat extends NotificationCompat {

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    public static class Builder extends NotificationCompat.Builder {
        public NotificationChannelCompat channel;
        public RemoteViews compact;
        public RemoteViews big;
        public ContextThemeWrapper theme;

        public Builder(Context context, int layoutId) {
            super(context);
            compact = new RemoteViews(mContext.getPackageName(), layoutId);
            setCustomContentView(compact);
            if (Build.VERSION.SDK_INT >= 21)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }

        public Builder(Context context, int layoutId, int bigId) {
            this(context, layoutId);
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
                setContentIntent(main);
            return this;
        }

        public Builder setTitle(String title) {
            return setTitle(title, null);
        }

        public Builder setTitle(String title, String ticker) {
            compact.setTextViewText(R.id.title, title);
            if (big != null)
                big.setTextViewText(R.id.title, title);
            setContentTitle(title);
            setTicker(ticker); // few secs short tooltip
            return this;
        }

        public Builder setText(String text) {
            compact.setTextViewText(R.id.text, text);
            if (big != null)
                big.setTextViewText(R.id.text, text);
            setContentText(text);
            return this;
        }

        public Builder setImageViewTint(int id, int attr) { // android:tint="?attr/..." crashing <API21
            RemoteViewsCompat.setImageViewTint(compact, id, ThemeUtils.getThemeColor(theme, attr));
            if (big != null)
                RemoteViewsCompat.setImageViewTint(big, id, ThemeUtils.getThemeColor(theme, attr));
            return this;
        }

        public Builder setIcon(int id) {
            compact.setImageViewResource(R.id.icon, id);
            if (big != null)
                big.setImageViewResource(R.id.icon, id);
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

        @Override
        public Notification build() {
            Notification n = super.build();
            if (channel != null)
                NotificationChannelCompat.setChannelId(n, channel.channelId); // builder recreate Notification object by prorerty
            return n;
        }
    }

    public static class Low extends Builder {
        public Low(Context context) {
            super(context, R.layout.remoteview_low);
            compact.setTextViewText(R.id.app_name_text, getApplicationName(context));
        }

        public Low(Context context, int bigId) {
            super(context, Build.VERSION.SDK_INT >= 26 ? R.layout.remoteview_low : bigId);
            compact.setTextViewText(R.id.app_name_text, getApplicationName(context));
            if (Build.VERSION.SDK_INT >= 26) {
                big = new RemoteViews(mContext.getPackageName(), bigId);
                setCustomBigContentView(big);
            }
        }

        @Override
        public Builder setText(String text) {
            compact.setViewVisibility(R.id.header_text_divider, View.VISIBLE);
            compact.setTextViewText(R.id.header_text, text);
            compact.setViewVisibility(R.id.header_text, View.VISIBLE);
            return super.setText(text);
        }

        @Override
        public NotificationCompat.Builder setSmallIcon(int icon) {
            compact.setImageViewResource(R.id.icon, icon);
            setImageViewTint(R.id.icon_circle, R.attr.colorButtonNormal);
            return super.setSmallIcon(icon);
        }
    }
}
