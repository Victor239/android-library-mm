package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RemoteViews;
import android.widget.TextView;

public class RemoteViewsCompat {
    public static final String TAG = RemoteViewsCompat.class.getSimpleName();

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

    public static void applyTheme(Context context, final RemoteViews view) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater = inflater.cloneInContext(context);
        inflater.setFactory(new LayoutInflater.Factory() {
            @Override
            public View onCreateView(String name, Context context, AttributeSet attrs) {
                int[] attrsArray = new int[]{
                        android.R.attr.id, // 0
                        android.R.attr.background, // 1
                        android.R.attr.tint, // 2
                        android.R.attr.textColor, // 3
                        android.R.attr.textAppearance, // 4
                        android.R.attr.theme, // 5
                };
                Resources.Theme theme = context.getTheme();
                TypedArray ta = theme.obtainStyledAttributes(attrs, attrsArray, 0, 0);
                TypedValue out = new TypedValue();
                if (ta.getValue(0, out)) {
                    int id = out.resourceId;
                    if (ta.getValue(1, out))
                        setBackgroundColor(view, id, out.data);
                    if (name.equals("TextView")) {
                        TextView t = new TextView(context, attrs); // 'textColor' not seen by obtainStyledAttributes() for unknown reason
                        view.setTextColor(id, t.getCurrentTextColor());
                    }
                    if (name.equals("Button")) {
                        Button t = new Button(context, attrs); // 'textColor' not seen by obtainStyledAttributes() for unknown reason
                        view.setTextColor(id, t.getCurrentTextColor());
                    }
                    if (name.equals("ImageButton") || name.equals("ImageView")) {
                        if (ta.getValue(2, out))
                            setImageViewTint(view, id, out.data);
                    }
                }
                ta.recycle();
                return null;
            }
        });
        inflater.inflate(view.getLayoutId(), null);
    }

}
