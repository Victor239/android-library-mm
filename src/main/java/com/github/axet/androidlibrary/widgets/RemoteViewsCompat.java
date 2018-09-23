package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

public class RemoteViewsCompat {

    public static void setBackgroundColor(RemoteViews view, int id, int color) {
        view.setInt(id, "setBackgroundColor", color);
    }

    public static void setImageViewTint(RemoteViews view, int id, int color) {
        view.setInt(id, "setColorFilter", color);
    }

    public static void setContentDescription(RemoteViews view, int id, CharSequence text) {
        view.setCharSequence(id, "setContentDescription", text);
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
                };
                TypedArray ta = context.obtainStyledAttributes(attrs, attrsArray);
                TypedValue out = new TypedValue();
                if (ta.getValue(0, out)) {
                    int id = out.resourceId;
                    if (ta.getValue(1, out))
                        setBackgroundColor(view, id, out.data);
                    if (name.equals("TextView")) {
                        TextView t = new TextView(context, attrs); // 'textColor' not seen by obtainStyledAttributes() for unknown reason
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
