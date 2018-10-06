package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.view.ContextThemeWrapper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RemoteViews;

import com.github.axet.androidlibrary.R;

import java.util.Arrays;

public class RemoteViewsCompat {
    public static final String TAG = RemoteViewsCompat.class.getSimpleName();

    public static class ThemeFactory implements LayoutInflater.Factory {
        public Context context;
        public RemoteViews view;

        public ThemeFactory(Context context, RemoteViews view) {
            this.context = context;
            this.view = view;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            if (Build.VERSION.SDK_INT >= 21 && this.context != context) // API21+ and 'android:theme' applied = ignore
                return null;

            int[] attrsArray = new int[]{
                    android.R.attr.id,
                    android.R.attr.background,
                    android.R.attr.tint,
                    android.R.attr.textColor,
            };

            Arrays.sort(attrsArray); // know bug https://stackoverflow.com/questions/19034597

            final int ID = Arrays.binarySearch(attrsArray, android.R.attr.id);
            final int BACKGROUND = Arrays.binarySearch(attrsArray, android.R.attr.background);
            final int TEXTCOLOR = Arrays.binarySearch(attrsArray, android.R.attr.textColor);
            final int TINT = Arrays.binarySearch(attrsArray, android.R.attr.tint);

            Resources.Theme theme = context.getTheme();
            TypedArray ta = theme.obtainStyledAttributes(attrs, attrsArray, 0, 0);
            TypedValue out = new TypedValue();
            if (ta.getValue(ID, out)) {
                int id = out.resourceId;
                if (ta.getValue(BACKGROUND, out))
                    setBackgroundColor(view, id, getColor(context, out));
                if (ta.getValue(TEXTCOLOR, out)) {
                    view.setTextColor(id, getColor(context, out));
                }
                if (ta.getValue(TINT, out))
                    setImageViewTint(view, id, getColor(context, out));
                if (name.equals(ImageButton.class.getSimpleName())) {
                    if (Build.VERSION.SDK_INT <= 10) { // seems like API10 and below does not support notification buttons
                        view.setViewVisibility(id, View.GONE);
                    } else {
                        if (!ta.hasValue(BACKGROUND)) { // no background set
                            int res = getImageButtonBackground(theme, context);
                            if (res != 0)
                                setBackgroundResource(view, id, res);
                        }
                    }
                }
            }
            ta.recycle();
            return null;
        }

        public int getColor(Context context, TypedValue out) {
            if (out.type == TypedValue.TYPE_STRING)
                out.data = ContextCompat.getColor(context, out.resourceId); // xml color selector
            return out.data;
        }

        @SuppressLint("RestrictedApi")
        public int getImageButtonStyle(Resources.Theme theme, Context context) {
            TypedValue style = new TypedValue();
            if (theme.resolveAttribute(R.attr.imageButtonStyle, style, true)) {
                if (style.resourceId == R.style.Widget_AppCompat_ImageButton) {
                    ContextThemeWrapper w = new ContextThemeWrapper(context, style.resourceId);
                    Resources.Theme t = w.getTheme();
                    TypedValue out = new TypedValue();
                    if (t.resolveAttribute(android.R.attr.background, out, true)) {
                        if (out.string != null) {
                            String[] ss = new String[]{
                                    "res/drawable/btn_default_material.xml", // API21
                                    "res/drawable/abc_btn_default_mtrl_shape.xml" // API16
                            };
                            for (String s : ss) {
                                if (out.string.equals(s)) { // AppCompat material button
                                    if (t.resolveAttribute(android.R.attr.imageButtonStyle, out, true)) { // which theme light or dark?
                                        switch (out.resourceId) {
                                            case android.R.style.Widget_Holo_ImageButton:
                                            case android.R.style.Widget_Material_ImageButton:
                                                return android.R.style.Widget_Material_ImageButton;
                                            case android.R.style.Widget_Holo_Light_ImageButton:
                                            case android.R.style.Widget_Material_Light_ImageButton:
                                                return android.R.style.Widget_Material_Light_ImageButton;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return style.resourceId;
            }
            return 0;
        }

        @SuppressLint("RestrictedApi")
        public int getImageButtonBackground(Resources.Theme theme, Context context) {
            TypedValue out = new TypedValue();
            int style = getImageButtonStyle(theme, context);
            switch (style) {
                case android.R.style.Widget_Material_ImageButton:
                    return R.drawable.remoteview_btn_dark;
                case android.R.style.Widget_Material_Light_ImageButton:
                    return R.drawable.remoteview_btn_light;
                case 0:
                    break;
                default:
                    ContextThemeWrapper w = new ContextThemeWrapper(context, style);
                    Resources.Theme t = w.getTheme();
                    if (t.resolveAttribute(android.R.attr.background, out, true))
                        return out.resourceId;
            }
            return 0;
        }
    }

    public static void setBackgroundColor(RemoteViews view, int id, int color) {
        view.setInt(id, "setBackgroundColor", color);
    }

    public static void setBackgroundResource(RemoteViews view, int id, int res) {
        view.setInt(id, "setBackgroundResource", res);
    }

    public static void setImageViewTint(RemoteViews view, int id, int color) {
        view.setInt(id, "setColorFilter", color);
    }

    public static void setContentDescription(RemoteViews view, int id, CharSequence text) {
        view.setCharSequence(id, "setContentDescription", text);
    }

    public static int findAttr(AttributeSet attrs, String name) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeName(i).equals(name))
                return i;
        }
        return -1;
    }

    public static int findAttr(AttributeSet attrs, int id) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeNameResource(i) == id)
                return i;
        }
        return -1;
    }

    public static int getAttributeAttributeValue(AttributeSet attrs, int index) {
        String v = attrs.getAttributeValue(index);
        if (v.startsWith("?"))
            return Integer.valueOf(v.substring(1));
        return 0; // invalid resource
    }

    public static boolean getAttr(AttributeSet attrs, int id, TypedValue out) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeNameResource(i) == id) {
                String v = attrs.getAttributeValue(i);
                switch (v.charAt(0)) {
                    case '?':
                        out.type = TypedValue.TYPE_ATTRIBUTE;
                        out.resourceId = Integer.valueOf(v.substring(1));
                        break;
                    case '@':
                        out.type = TypedValue.TYPE_REFERENCE;
                        out.resourceId = Integer.valueOf(v.substring(1));
                        break;
                }
                return true;
            }
        }
        return false;
    }

    public static void applyTheme(final Context context, final RemoteViews view) {
        applyTheme(context, view, new ThemeFactory(context, view));
    }

    public static void applyTheme(final Context context, final RemoteViews view, LayoutInflater.Factory factory) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater = inflater.cloneInContext(context);
        inflater.setFactory(factory);
        inflater.inflate(view.getLayoutId(), null);
    }

}
