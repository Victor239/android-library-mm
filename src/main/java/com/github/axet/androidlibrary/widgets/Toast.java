package com.github.axet.androidlibrary.widgets;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Toast {
    public static final int LENGTH_LONG = android.widget.Toast.LENGTH_LONG;
    public static final int LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT;

    public static final long SHORT_DURATION_TIMEOUT = 5000;
    public static final long LONG_DURATION_TIMEOUT = 1000;

    @IntDef({LENGTH_SHORT, LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Duration {
    }

    public Context context;
    public int d;
    public CharSequence m;
    public android.widget.Toast toast;
    public PopupWindow w;
    public Handler handler = new Handler();
    Runnable hide = new Runnable() {
        @Override
        public void run() {
            cancel();
        }
    };
    OnDismissListener dismissListener;

    public interface OnDismissListener {
        void onDismiss(Toast t);
    }

    public static String toString(Throwable e) {
        Throwable p;
        while ((p = e.getCause()) != null) {
            e = p;
        }
        String msg = e.getClass().getSimpleName();
        String m = e.getMessage();
        if (m != null && !m.isEmpty())
            msg += ": " + m;
        return msg;
    }

    public static Toast Error(Context context, String msg, Throwable e) {
        Toast t = Toast.makeText(context, msg + "\n" + toString(e), LENGTH_SHORT);
        t.show();
        return t;
    }

    public static Toast makeText(Context context, int r, @Duration int d) {
        return new Toast(context, android.widget.Toast.makeText(context, r, d), d, context.getString(r));
    }

    public static Toast makeText(Context context, CharSequence t, @Duration int d) {
        return new Toast(context, android.widget.Toast.makeText(context, t, d), d, t);
    }

    public static Toast onCreate(final Activity a) {
        String m = a.getIntent().getStringExtra("text");
        int d = a.getIntent().getIntExtra("duration", 0);
        final Toast t = Toast.makeText(a, m, d);
        View v = t.toast.getView();
        FrameLayout f = new FrameLayout(a) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                if (t.dismissListener != null)
                    t.dismissListener.onDismiss(t);
            }
        };
        f.addView(v);
        Window w = a.getWindow();
        w.requestFeature(Window.FEATURE_NO_TITLE);
        a.setContentView(f);
        w.setGravity(Gravity.BOTTOM);
        w.setWindowAnimations(android.R.style.Animation_Toast);
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        return t;
    }

    public Toast(Context context, android.widget.Toast t, int d, CharSequence m) {
        this.context = context;
        this.toast = t;
        this.d = d;
        this.m = m;
    }

    public void setOnDismissListener(OnDismissListener l) {
        dismissListener = l;
    }

    public Toast center() {
        TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
        if (v != null)
            v.setGravity(Gravity.CENTER);
        return this;
    }

    public void cancel() {
        toast.cancel();
        if (w != null) {
            w.dismiss();
            w = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (dismissListener != null)
            dismissListener.onDismiss(this);
    }

    public void setDuration(int d) {
        toast.setDuration(d);
    }

    public long getDuration() {
        int d = toast.getDuration();
        if (d != 0)
            return d;
        return this.d == LENGTH_SHORT ? SHORT_DURATION_TIMEOUT : LONG_DURATION_TIMEOUT;
    }

    public void show() {
        Runnable show = new Runnable() {
            @Override
            public void run() {
                View v = toast.getView();
                FrameLayout f = new FrameLayout(context) {
                    @Override
                    protected void onAttachedToWindow() {
                        super.onAttachedToWindow();
                    }

                    @Override
                    protected void onDetachedFromWindow() {
                        super.onDetachedFromWindow();
                        if (dismissListener != null)
                            dismissListener.onDismiss(Toast.this);
                    }
                };
                f.addView(v);
                toast.setView(f);
                toast.show();
            }
        };
        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            if (w != null) {
                w.dismiss();
                w = null;
            }
            if (context instanceof Activity) {
                View v = toast.getView();
                int ww = context.getResources().getDisplayMetrics().widthPixels;
                int hh = context.getResources().getDisplayMetrics().heightPixels;
                v.measure(View.MeasureSpec.makeMeasureSpec(ww, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.AT_MOST));
                w = new PopupWindow(v, v.getMeasuredWidth(), v.getMeasuredHeight(), false);
                w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                w.setContentView(v);
                w.setAnimationStyle(android.R.style.Animation_Toast);
                View p = ((Activity) context).getWindow().getDecorView();
                w.showAtLocation(p, Gravity.BOTTOM, 0, hh / 6);
                handler.removeCallbacks(hide);
                handler.postDelayed(hide, getDuration());
            } else { // from Service
                show.run();
            }
        } else {
            show.run();
        }
    }

}
