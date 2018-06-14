package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.util.AttributeSet;

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
// proguard-rules.pro
//
// -keep class com.github.axet.androidlibrary.widgets.SearchView {*;}
//
public class SearchView extends android.support.v7.widget.SearchView {
    public OnCloseListener listener;

    public static boolean filter(String filter, String text) {
        filter = Normalizer.normalize(filter, Normalizer.Form.NFC).toLowerCase(Locale.US); // й composed to two chars sometime.
        text = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.US);
        boolean all = true;
        for (String f : filter.split("\\s+"))
            all &= text.contains(f);
        return all;
    }

    public SearchView(Context context) {
        super(context);
    }

    public SearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnCloseListener(OnCloseListener listener) {
        this.listener = listener;
    }

    @Override
    public void onActionViewCollapsed() {
        super.onActionViewCollapsed();
        if (listener != null)
            listener.onClose();
    }
}
