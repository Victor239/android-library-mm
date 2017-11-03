package com.github.axet.androidlibrary.widgets;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import com.github.axet.androidlibrary.R;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * &lt;com.github.axet.androidlibrary.widgets.AboutPreferenceCompat
 * app:html="@raw/about"
 * android:persistent="false" /&gt;
 */
public class AboutPreferenceCompat extends DialogPreference {
    int id;

    public static AlertDialog.Builder buildDialog(final Context context, int id) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View title = inflater.inflate(R.layout.about_title, null);
        TextView t = (TextView) title.findViewById(R.id.about_title_name);
        TextView v = (TextView) title.findViewById(R.id.about_title_version);

        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo a = pm.getApplicationInfo(context.getPackageName(), 0);
            t.setText(a.loadLabel(pm));
            PackageInfo pInfo = pm.getPackageInfo(context.getPackageName(), 0);
            String version = "v" + pInfo.versionName;
            v.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        WebViewCustom web = new WebViewCustom(context) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, final String url) {
                openUrl(getContext(), url);
                return true;
            }
        };
        web.getSettings().setBuiltInZoomControls(false);
        try {
            Resources res = context.getResources();
            InputStream is = res.openRawResource(id);
            String html = IOUtils.toString(is, Charset.defaultCharset());
            web.loadHtmlWithBaseURL("", html, "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCustomTitle(title);
        builder.setView(web);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        return builder;
    }

    public static void openUrl(final Context context, final String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(browserIntent);
    }

    public static void openUrlDialog(final Context context, final String url) {
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.open_browser);
        b.setMessage(R.string.are_you_sure);
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                openUrl(context, url);
                d.cancel();
            }
        });
        b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        AlertDialog d = b.create();
        d.show();
    }

    public static Dialog showDialog(final Context context, int id) {
        Dialog d = buildDialog(context, id).create();
        d.show();
        return d;
    }

    public static int getResourceId(Context context, String res) { // get resource id from String "R.raw.about" -> id
        try {
            String[] rr = res.split("\\."); // R.raw.about
            String name = rr[2];
            String type = rr[1];
            return context.getResources().getIdentifier(name, type, context.getPackageName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AboutPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create(attrs, defStyleAttr);
    }

    public AboutPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        create(attrs, 0);
    }

    public AboutPreferenceCompat(Context context) {
        this(context, null);
        create(null, 0);
    }

    void create(AttributeSet attrs, int defStyleAttr) {
        if (attrs != null) {
            final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.AboutPreferenceCompat, defStyleAttr, 0);
            id = a.getResourceId(R.styleable.AboutPreferenceCompat_html, -1);
        }
        setPersistent(false);
        try {
            String sum = "";
            PackageManager pm = getContext().getPackageManager();
            ApplicationInfo a = pm.getApplicationInfo(getContext().getPackageName(), PackageManager.GET_META_DATA);
            sum += a.loadLabel(pm);
            PackageInfo pInfo = pm.getPackageInfo(getContext().getPackageName(), PackageManager.GET_META_DATA);
            sum += " v" + pInfo.versionName;
            setSummary(sum);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        setTitle(getContext().getString(R.string.menu_about));
    }

    @Override
    public void onClick() {
        showDialog(getContext(), id);
    }

    public void setDialog(int id) {
        this.id = id;
    }
}
