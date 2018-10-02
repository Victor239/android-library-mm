package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v7.view.ContextThemeWrapper;
import android.widget.RemoteViews;

import com.github.axet.androidlibrary.R;

// Check android notification_template_base.xml for constants
public class RemoteNotificationCompat extends NotificationCompat {
    public static class Builder extends NotificationCompat.Builder {
        public NotificationChannelCompat channel;
        public RemoteViews view;
        public ContextThemeWrapper theme;

        public Builder(Context context, int layoutId) {
            super(context);
            create(layoutId);
        }

        public void create(int layoutId) {
            view = new RemoteViews(mContext.getPackageName(), layoutId);
            if (Build.VERSION.SDK_INT >= 21)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            setContent(view);
        }

        public Builder setChannel(NotificationChannelCompat channel) {
            this.channel = channel;
            channel.apply(this);
            return this;
        }

        @SuppressLint("RestrictedApi")
        public Builder setTheme(int id) {
            theme = new ContextThemeWrapper(mContext, id);
            if (Build.VERSION.SDK_INT < 21)
                RemoteViewsCompat.applyTheme(theme, view);
            return this;
        }

        public Builder setMainIntent(PendingIntent main) {
            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            if (Build.VERSION.SDK_INT < 11)
                setContentIntent(main);
            return this;
        }

        public Builder setTitle(String title) {
            return setTitle(title, null);
        }

        public Builder setTitle(String title, String ticker) {
            view.setTextViewText(R.id.title, title);
            setContentTitle(title);
            setTicker(ticker); // few secs short tooltip
            return this;
        }

        public Builder setText(String text) {
            view.setTextViewText(R.id.text, text);
            setContentText(text);
            return this;
        }

        public Builder setWhen(Notification n) {
            setWhen(n == null ? System.currentTimeMillis() : n.when);
            return this;
        }

        public Builder setImageViewTint(int id, int attr) { // android:tint="?attr/..." crashing <API21
            RemoteViewsCompat.setImageViewTint(view, id, ThemeUtils.getThemeColor(theme, attr));
            return this;
        }

        public Builder setIcon(int id) {
            view.setImageViewResource(R.id.icon, id);
            return this;
        }

        @Override
        public Notification build() {
            Notification n = super.build();
            NotificationChannelCompat.setChannelId(n, channel.channelId); // API26+
            return n;
        }
    }
}
