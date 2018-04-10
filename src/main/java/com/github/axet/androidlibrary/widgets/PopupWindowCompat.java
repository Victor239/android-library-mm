package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

public class PopupWindowCompat {

    public static Rect getOnScreenRect(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
    }

    public static float getDimension(Context context, int id) {
        TypedValue tv = new TypedValue();
        boolean found = context.getTheme().resolveAttribute(id, tv, true);
        if (found) {
            switch (tv.type) {
                case TypedValue.TYPE_DIMENSION:
                    return context.getResources().getDimension(tv.resourceId);
                default:
                    return context.getResources().getDimension(id);
            }
        } else {
            throw new RuntimeException("not found");
        }
    }

    public static void showAsDropDown(PopupWindow p, View anchor, int gravity) {
        Context context = anchor.getContext();
        View v = p.getContentView();
        Resources r = context.getResources();
        float f = getDimension(context, R.attr.dialogPreferredPadding);
        DisplayMetrics dm = r.getDisplayMetrics();
        int w = (int) (dm.widthPixels - f * 2);
        int h = (int) (dm.heightPixels - f * 2);
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
        h = v.getMeasuredHeight();
        p.setHeight(h);
        p.setWidth(w);
        int x = (dm.widthPixels - w) / 2, y = 0;
        Rect rect = getOnScreenRect(anchor);
        switch (gravity) {
            case Gravity.TOP: // popup at top of anchor
                if (rect.top - h < 0)
                    y = rect.bottom;
                else
                    y = rect.top - h;
                break;
            case Gravity.BOTTOM: // popup at bottom of anchor
                if (rect.bottom + h > dm.heightPixels)
                    y = rect.top - h;
                else
                    y = rect.bottom;
                break;
        }
        p.setFocusable(true);
        p.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    public static void showAsTooltip(PopupWindow p, View anchor, int gravity) {
        showAsTooltip(p, anchor, gravity, ThemeUtils.getColor(anchor.getContext(), R.color.button_material_light));
    }

    public static void showAsTooltip(final PopupWindow p, View anchor, int gravity, int background) {
        Context context = anchor.getContext();

        final View v = p.getContentView();
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null)
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout tooltip = new LinearLayout(context);
        tooltip.setLayoutParams(new ViewGroup.LayoutParams(lp.width, lp.height));
        tooltip.setOrientation(LinearLayout.VERTICAL);

        AppCompatImageView up = new AppCompatImageView(context);
        up.setImageResource(R.drawable.popup_triangle);
        ViewCompat.setRotation(up, 180);
        tooltip.addView(up, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout content = new FrameLayout(context);
        content.setBackgroundResource(R.drawable.popup_round);
        LinearLayout.LayoutParams clp;
        if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT)
            clp = new LinearLayout.LayoutParams(lp.width, 0, 1);
        else
            clp = new LinearLayout.LayoutParams(lp.width, lp.height);
        tooltip.addView(content, clp);

        AppCompatImageView down = new AppCompatImageView(context);
        down.setImageResource(R.drawable.popup_triangle);
        tooltip.addView(down, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(v);
        p.setContentView(tooltip);

        switch (gravity) { // hide arrows, to calcualte correct height
            case Gravity.TOP: // popup at top of anchor
                up.setVisibility(View.GONE);
                down.setVisibility(View.VISIBLE);
                break;
            case Gravity.BOTTOM: // popup at bottom of anchor
                up.setVisibility(View.VISIBLE);
                down.setVisibility(View.GONE);
                break;
        }

        Resources r = context.getResources();
        float f = getDimension(context, R.attr.dialogPreferredPadding);
        DisplayMetrics dm = r.getDisplayMetrics();
        int w = (int) (dm.widthPixels - f * 2);
        int h = (int) (dm.heightPixels - f * 2);
        int mw = View.MeasureSpec.AT_MOST;
        if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT)
            mw = View.MeasureSpec.EXACTLY;
        int mh = View.MeasureSpec.AT_MOST;
        if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT)
            mh = View.MeasureSpec.EXACTLY;
        tooltip.measure(View.MeasureSpec.makeMeasureSpec(w, mw), View.MeasureSpec.makeMeasureSpec(h, mh));
        h = tooltip.getMeasuredHeight();
        w = tooltip.getMeasuredWidth();
        p.setHeight(h);
        p.setWidth(w);
        Rect rect = getOnScreenRect(anchor);
        int x = rect.centerX() - w / 2;
        if (x < f)
            x = (int) f;
        int xr = (int) (dm.widthPixels - f - w);
        if (x > xr)
            x = xr;
        int y = 0;
        switch (gravity) {
            case Gravity.TOP: // popup at top of anchor
                if (rect.top - h < 0) {
                    y = rect.bottom;
                    gravity = Gravity.BOTTOM;
                } else {
                    y = rect.top - h;
                }
                break;
            case Gravity.BOTTOM: // popup at bottom of anchor
                if (rect.bottom + h > dm.heightPixels) {
                    y = rect.top - h;
                    gravity = Gravity.TOP;
                } else {
                    y = rect.bottom;
                }
                break;
        }

        ImageView arrow = null;
        switch (gravity) { // if tooltip window were repositioned, update arrows again
            case Gravity.TOP: // popup at top of anchor
                up.setVisibility(View.GONE);
                down.setVisibility(View.VISIBLE);
                arrow = down;
                break;
            case Gravity.BOTTOM: // popup at bottom of anchor
                up.setVisibility(View.VISIBLE);
                down.setVisibility(View.GONE);
                arrow = up;
                break;
        }

        Drawable triangle = DrawableCompat.wrap(arrow.getDrawable());
        DrawableCompat.setTint(triangle.mutate(), background);
        Drawable round = DrawableCompat.wrap(content.getBackground());
        DrawableCompat.setTint(round.mutate(), background);

        int l = rect.centerX() - x - arrow.getMeasuredWidth() / 2;
        int ll = ThemeUtils.dp2px(context, 10); // round background range left
        if (l < ll)
            l = ll;
        int lr = w - ThemeUtils.dp2px(context, 10) - arrow.getMeasuredWidth(); // round background range right
        if (l > lr)
            l = lr;
        ((LinearLayout.LayoutParams) arrow.getLayoutParams()).leftMargin = l;

        p.setFocusable(true);
        p.setOutsideTouchable(true);
        p.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        p.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        p.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                content.removeAllViews();
            }
        });
    }

}
