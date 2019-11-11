package com.github.axet.androidlibrary.widgets;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.AssetsDexLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Toast {
    public static final String TAG = Toast.class.getSimpleName();

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
    PopupWindow.OnDismissListener dismissListener;

    public static void Post(final Activity a, final Throwable e) {
        Log.e(TAG, "Post", e);
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Error(a, ErrorDialog.toMessage(e));
            }
        });
    }

    public static void Post(final Activity a, final String msg) {
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Error(a, msg);
            }
        });
    }

    public static void Post(final Context context, final String msg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(context, msg);
            }
        });
    }

    public static void Post(final Context context, final Throwable e) {
        Log.e(TAG, "Post", e);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(context, e);
            }
        });
    }

    public static Toast Error(Context context, Throwable e) {
        Log.e(TAG, "Error", e);
        return Error(context, ErrorDialog.toMessage(e));
    }

    public static Toast Error(Context context, String msg) {
        Toast t = makeText(context, msg, LENGTH_SHORT);
        t.show();
        return t;
    }

    public static Toast Error(Context context, String msg, Throwable e) {
        Log.e(TAG, "Error", e);
        Toast t = makeText(context, msg + "\n" + ErrorDialog.toMessage(e), LENGTH_SHORT);
        t.show();
        return t;
    }

    public static Toast makeText(Context context, int r, @Duration int d) {
        return new Toast(context, android.widget.Toast.makeText(context, r, d), d, context.getString(r));
    }

    public static Toast makeText(Context context, CharSequence t, @Duration int d) {
        return new Toast(context, android.widget.Toast.makeText(context, t, d), d, t);
    }

    public static void setContext(@NonNull View view, @NonNull Context context) {
        if (Build.VERSION.SDK_INT == 25) { // only happens API25, Android 7.1
            try {
                AssetsDexLoader.getPrivateField(View.class, "mContext").set(view, context);
            } catch (Throwable e) {
                Log.e(TAG, "setContext", e);
            }
        }
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
                    t.dismissListener.onDismiss();
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

    // https://github.com/PureWriter/ToastCompat
    public static class SafeToastContext extends ContextWrapper {
        public SafeToastContext(@NonNull Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return new ApplicationContextWrapper(getBaseContext().getApplicationContext());
        }

        public static final class ApplicationContextWrapper extends ContextWrapper {
            public ApplicationContextWrapper(@NonNull Context base) {
                super(base);
            }

            @Override
            public Object getSystemService(@NonNull String name) {
                if (Context.WINDOW_SERVICE.equals(name)) {
                    // noinspection ConstantConditions
                    return new WindowManagerWrapper((WindowManager) getBaseContext().getSystemService(name));
                }
                return super.getSystemService(name);
            }
        }

        public static final class WindowManagerWrapper implements WindowManager {
            private final @NonNull
            WindowManager base;

            public WindowManagerWrapper(@NonNull WindowManager base) {
                this.base = base;
            }

            @Override
            public Display getDefaultDisplay() {
                return base.getDefaultDisplay();
            }

            @Override
            public void removeViewImmediate(View view) {
                base.removeViewImmediate(view);
            }

            @Override
            public void addView(View view, ViewGroup.LayoutParams params) {
                try {
                    Log.d(TAG, "WindowManager's addView(view, params) has been hooked.");
                    base.addView(view, params);
                } catch (BadTokenException e) {
                    Log.i(TAG, e.getMessage());
                } catch (Throwable throwable) {
                    Log.e(TAG, "[addView]", throwable);
                }
            }

            @Override
            public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
                base.updateViewLayout(view, params);
            }

            @Override
            public void removeView(View view) {
                base.removeView(view);
            }
        }
    }

    public Toast(Context context, android.widget.Toast t, int d, CharSequence m) {
        this.context = context;
        this.toast = t;
        this.d = d;
        this.m = m;
        setContext(toast.getView(), new SafeToastContext(context));
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener l) {
        dismissListener = l;
    }

    public Toast center() {
        View v = toast.getView();
        TextView t = (TextView) v.findViewById(android.R.id.message);
        if (t != null)
            t.setGravity(Gravity.CENTER);
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
            dismissListener.onDismiss();
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
        Runnable show = new Runnable() { // show system toast
            @Override
            public void run() {
                context = context.getApplicationContext(); // toast can crash internally if activity context used
                View v = toast.getView();
                ViewParent p = v.getParent();
                if (p != null) // second show same view (after exception)
                    ((ViewGroup) p).removeView(v);
                FrameLayout f = new FrameLayout(context) {
                    @Override
                    protected void onDetachedFromWindow() {
                        super.onDetachedFromWindow();
                        if (dismissListener != null)
                            dismissListener.onDismiss();
                    }
                };
                f.addView(v);
                toast.setView(f);
                setContext(f, new SafeToastContext(context));
                toast.show();
            }
        };
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode()) { // locked, system toast not appearing on locked screen use Activity
            if (w != null) {
                w.dismiss();
                w = null;
            }
            if (context instanceof Activity) {
                View v = toast.getView();
                try {
                    Activity a = (Activity) context;
                    if (a.isFinishing())
                        throw new WindowManager.BadTokenException("window finishing");
                    int ww = context.getResources().getDisplayMetrics().widthPixels;
                    int hh = context.getResources().getDisplayMetrics().heightPixels;
                    v.measure(View.MeasureSpec.makeMeasureSpec(ww, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.AT_MOST));
                    w = new PopupWindow(v, v.getMeasuredWidth(), v.getMeasuredHeight(), false);
                    w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                    w.setAnimationStyle(android.R.style.Animation_Toast);
                    View d = a.getWindow().getDecorView(); // for window token only
                    w.showAtLocation(d, Gravity.BOTTOM, 0, hh / 6);
                    handler.removeCallbacks(hide);
                    handler.postDelayed(hide, getDuration());
                } catch (WindowManager.BadTokenException e) { // happens in onCreate when screen is locked (and about to be unlocked)
                    Log.d(TAG, "unable to use activity", e);
                    w = null; // nothing to dismiss, dismiss may crash due to IllegalArgumentException
                    show.run();
                }
            } else { // from Application / Service
                show.run();
            }
        } else { // not locked, show no problems
            show.run();
        }
    }
}
