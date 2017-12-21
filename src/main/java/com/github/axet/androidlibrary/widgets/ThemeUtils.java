package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;

public class ThemeUtils {

    public static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    // get colors.xml
    public static int getColor(Context context, int id) {
        return ContextCompat.getColor(context, id);
    }

    // get attrs.xml / styles.xml
    public static int getThemeColor(Context context, int id) {
        TypedValue tv = new TypedValue();
        boolean found = context.getTheme().resolveAttribute(id, tv, true);
        if (found) {
            switch (tv.type) {
                case TypedValue.TYPE_INT_COLOR_ARGB4:
                case TypedValue.TYPE_INT_COLOR_ARGB8:
                case TypedValue.TYPE_INT_COLOR_RGB4:
                case TypedValue.TYPE_INT_COLOR_RGB8:
                    return tv.data;
                default:
                    return getColor(context, tv.resourceId);
            }
        } else {
            throw new RuntimeException("not found");
        }
    }
}
