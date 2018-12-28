package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.support.annotation.Keep;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Field;
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

@Keep
public class SearchView extends android.support.v7.widget.SearchView {
    public static String TAG = SearchView.class.getSimpleName();

    public OnCollapsedListener collapsedListener;
    public OnCloseButtonListener closeButtonListener;
    ImageView mCloseButton;
    SearchAutoComplete mSearchSrcTextView;

    public interface OnCollapsedListener {
        void onCollapsed();
    }

    public interface OnCloseButtonListener {
        void onClosed();
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
        mSearchSrcTextView = (SearchAutoComplete) findViewById(android.support.v7.appcompat.R.id.search_src_text);
        mCloseButton = (ImageView) findViewById(android.support.v7.appcompat.R.id.search_close_btn);
    }

    public void setOnCollapsedListener(OnCollapsedListener listener) {
        this.collapsedListener = listener;
    }

    public void setOnCloseButtonListener(OnCloseButtonListener listener) {
        this.closeButtonListener = listener;
        try {
            Class k = getClass().getSuperclass();
            Field f = k.getDeclaredField("mOnClickListener");
            f.setAccessible(true);
            final OnClickListener l = (OnClickListener) f.get(this);
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

}
