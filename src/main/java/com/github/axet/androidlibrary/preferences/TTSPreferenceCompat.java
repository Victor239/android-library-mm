package com.github.axet.androidlibrary.preferences;

import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import android.support.v7.preference.ListPreference;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.github.axet.androidlibrary.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TTSPreferenceCompat extends ListPreference {
    CharSequence defSummary;
    ArrayList<CharSequence> text;
    ArrayList<CharSequence> value;

    public static void addLocale(HashSet<Locale> list, Locale l) {
        for (Locale m : list) {
            if (m.toString().equals(l.toString()))
                return;
        }
        list.add(l);
    }

    public static Locale toLocale(String str) { // use LocaleUtils.toLocale
        String[] ss = str.split("_");
        if (ss.length == 3)
            return new Locale(ss[0], ss[1], ss[2]);
        else if (ss.length == 2)
            return new Locale(ss[0], ss[1]);
        else
            return new Locale(ss[0]);
    }

    public static HashSet<Locale> getInputLanguages(Context context) {
        HashSet<Locale> list = new HashSet<>();
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            LocaleList ll = LocaleList.getDefault();
            for (int i = 0; i < ll.size(); i++)
                addLocale(list, ll.get(i));
        }
        if (Build.VERSION.SDK_INT >= 11) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                List<InputMethodInfo> ims = imm.getEnabledInputMethodList();
                for (InputMethodInfo m : ims) {
                    List<InputMethodSubtype> ss = imm.getEnabledInputMethodSubtypeList(m, true);
                    for (InputMethodSubtype s : ss) {
                        if (s.getMode().equals("keyboard")) {
                            if (Build.VERSION.SDK_INT >= 24 && !s.getLanguageTag().isEmpty())
                                addLocale(list, Locale.forLanguageTag(s.getLanguageTag()));
                            else
                                addLocale(list, toLocale(s.getLanguageTag()));
                        }
                    }
                }
            }
        }
        return list;
    }

    public static String formatLocale(Locale l) {
        String n = l.getDisplayLanguage();
        String v = l.toString();
        String t;
        if (n == null || n.isEmpty() || n.equals(v))
            t = v;
        else
            t = String.format("%s (%s)", n, v);
        return t;
    }

    public TTSPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public TTSPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public TTSPreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
        defSummary = getSummary();
        text = new ArrayList<>();
        value = new ArrayList<>();
        text.add(getContext().getString(R.string.system_default));
        value.add("");
        Set<Locale> ll = null;
        if (ll == null)
            ll = getInputLanguages(getContext());
        if (ll == null) {
            ll = new HashSet<>();
            ll.add(Locale.US);
        }
        for (Locale l : ll) {
            text.add(formatLocale(l));
            value.add(l.toString());
        }
        setEntries(text.toArray(new CharSequence[0]));
        setEntryValues(value.toArray(new CharSequence[0]));
        setDefaultValue("");
    }

    @Override
    public void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        setSummary(getEntry());
    }

    @Override
    public void onClick() {
        super.onClick();
    }

    @Override
    protected void notifyChanged() {
        super.notifyChanged();
        setSummary(getEntry());
    }
}
