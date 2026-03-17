package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.ActionBar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.github.axet.androidlibrary.app.AssetsDexLoader;

import java.text.Normalizer;
import java.util.Locale;

// menu.xml
//
//    <item
//            android:id="@+id/action_search"
//            android:icon="@drawable/ic_search_white_24dp"
//            android:title="Search"
//            app:actionViewClass="com.github.axet.androidlibrary.widgets.SearchView"
//            app:showAsAction="collapseActionView|ifRoom" />
//
// AndroidManifest.xml
//
//    <application>
//            ...
//            <activity>
//            ...
//            <meta-data
//            android:name="android.app.searchable"
//            android:resource="@xml/searchable" />
//
@Keep
public class SearchView extends androidx.appcompat.widget.SearchView {
    public static String TAG = SearchView.class.getSimpleName();

    ImageView mCloseButton;
    SearchAutoComplete mSearchSrcTextView;

    public OnExpandedListener expandedListener;
    public OnCollapsedListener collapsedListener;
    public OnCloseButtonListener closeButtonListener;

    public static class CollapseListener implements MenuItemCompat.OnActionExpandListener {
        public ActionBar appbar;
        public MenuItem current = null;

        public CollapseListener(final ActionBar appbar) {
            this.appbar = appbar;
        }

        @SuppressLint("RestrictedApi")
        public void addItem(MenuItem search) {
            MenuItemCompat.setOnActionExpandListener(search, this);
        }

        @SuppressLint("RestrictedApi")
        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            if (current != item) {
                current = item;
                appbar.collapseActionView();
            }
            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            return true;
        }
    }

    public interface OnCollapsedListener {
        void onCollapsed();
    }

    public interface OnCloseButtonListener {
        void onClosed();
    }

    public interface OnExpandedListener {
        void onExpanded();
    }

    public static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC); // й composed as two chars sometimes.
    }

    public static boolean filter(String filter, String text) {
        filter = normalize(filter).toLowerCase(Locale.US); // й composed as two chars sometimes.
        text = normalize(text).toLowerCase(Locale.US);
        boolean all = true;
        for (String f : filter.split("\\s+"))
            all &= text.contains(f);
        return all;
    }

    public SearchView(Context context) {
        super(context);
        create();
    }

    public SearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public SearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public void create() {
        mSearchSrcTextView = (SearchAutoComplete) findViewById(androidx.appcompat.R.id.search_src_text);
        mCloseButton = (ImageView) findViewById(androidx.appcompat.R.id.search_close_btn);
    }

    public void setOnCollapsedListener(OnCollapsedListener listener) {
        this.collapsedListener = listener;
    }

    public void setOnCloseButtonListener(OnCloseButtonListener listener) {
        this.closeButtonListener = listener;
        try {
            Class k = getClass().getSuperclass();
            final OnClickListener l = (OnClickListener) AssetsDexLoader.getPrivateField(k, "mOnClickListener").get(this);
            mCloseButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    l.onClick(v);
                    if (closeButtonListener != null)
                        closeButtonListener.onClosed();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unable to handle on close", e);
        }
    }

    @Override
    public void onActionViewCollapsed() {
        super.onActionViewCollapsed();
        if (collapsedListener != null)
            collapsedListener.onCollapsed();
    }

    public void setOnExpandedListener(OnExpandedListener l) {
        expandedListener = l;
    }

    @Override
    public void onActionViewExpanded() {
        super.onActionViewExpanded();
        if (expandedListener != null)
            expandedListener.onExpanded();
    }
}
