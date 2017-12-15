package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;

public class ThemeUtils {


    public static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static int getColor(Context context, int id) {
        TypedValue tv = new TypedValue();
        boolean found = context.getTheme().resolveAttribute(id, tv, true);
        if (found) {
            return ContextCompat.getColor(context, tv.resourceId); // resolve attr.xml
        } else {
            return ContextCompat.getColor(context, id); // resolve colors.xml
        }
//        TypedValue typedValue = new TypedValue();
//        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{id});
//        int color = a.getColor(0, 0);
//        a.recycle();
//        return color;
    }
}
