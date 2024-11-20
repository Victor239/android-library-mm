package com.github.axet.androidlibrary.app;

import android.content.Context;
import android.text.format.DateFormat;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;

import com.github.axet.androidlibrary.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ScheduleTime {
    public static final int REPEAT_DELETE = -1;
    public static final int REPEAT_ONCE = 0;
    public static final int REPEAT_DAY = 1;
    public static final int REPEAT_MONTH_1 = 2;
    public static final int REPEAT_MONTH_2 = 3;
    public static final int REPEAT_MONTH_3 = 4;
    public static final int REPEAT_MONTH_4 = 5;
    public static final int REPEAT_MONTH_6 = 6;
    public static final int REPEAT_MONTH_12 = 7;
    public static final int REPEAT_WEEK = 8;
    public static final int REPEAT_6HOURS = 9;

    public static String getWeekName(Date d) {
        return new SimpleDateFormat("EE").format(d);
    }

    Context context;

    public boolean enabled;
    public int repeat;
    public long start; // start date
    public long next; // next occurence

    // make proper timezone shifts
    public int hour;
    public int min;

    public static class SpinnerItem {
        public int id;
        public String name;

        public SpinnerItem(int id, String n) {
            this.id = id;
            this.name = n;
        }

        public String toString() {
            return name;
        }
    }

    public static ArrayAdapter create(Context context) {
        ArrayAdapter a = new ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item);
        a.add(new SpinnerItem(REPEAT_DELETE, context.getString(R.string.repeat_delete)));
        a.add(new SpinnerItem(REPEAT_ONCE, context.getString(R.string.repeat_once)));
        a.add(new SpinnerItem(REPEAT_6HOURS, context.getString(R.string.repeat_6hours)));
        a.add(new SpinnerItem(REPEAT_DAY, context.getString(R.string.repeat_day)));
        a.add(new SpinnerItem(REPEAT_WEEK, context.getString(R.string.repeat_week)));
        a.add(new SpinnerItem(REPEAT_MONTH_1, context.getString(R.string.repeat_month_1)));
        a.add(new SpinnerItem(REPEAT_MONTH_2, context.getString(R.string.repeat_month_2)));
        a.add(new SpinnerItem(REPEAT_MONTH_3, context.getString(R.string.repeat_month_3)));
        a.add(new SpinnerItem(REPEAT_MONTH_4, context.getString(R.string.repeat_month_4)));
        a.add(new SpinnerItem(REPEAT_MONTH_6, context.getString(R.string.repeat_month_6)));
        a.add(new SpinnerItem(REPEAT_MONTH_12, context.getString(R.string.repeat_month_12)));
        return a;
    }

    public static int find(SpinnerAdapter a, int id) {
        for (int i = 0; i < a.getCount(); i++) {
            SpinnerItem s = (SpinnerItem) a.getItem(i);
            if (s.id == id)
                return i;
        }
        return -1;
    }

    public static SpinnerItem get(SpinnerAdapter a, int id) {
        int p = find(a, id);
        if (p == -1)
            return null;
        return (SpinnerItem) a.getItem(p);
    }

    public ScheduleTime(ScheduleTime s) {
        this.context = s.context;
        this.enabled = s.enabled;
        this.repeat = s.repeat;
        this.start = s.start;
        this.next = s.next;
        this.hour = s.hour;
        this.min = s.min;
    }

    public ScheduleTime(Context context) {
        this.context = context;
        this.enabled = false;
        setTime(System.currentTimeMillis());
    }

    public ScheduleTime(Context context, String json) {
        this.context = context;
        try {
            JSONObject o = new JSONObject(json);
            enabled = o.getBoolean("enabled");
            repeat = o.getInt("repeat");
            start = o.getLong("start");
            next = o.getLong("next");
            try {
                hour = o.getInt("hour");
                min = o.getInt("min");
            } catch (JSONException e) {
                setTime(start);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject save() {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", enabled);
            o.put("repeat", repeat);
            o.put("start", start);
            o.put("next", next);
            o.put("hour", hour);
            o.put("min", min);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void setTime(long l) {
        this.start = l;
        this.next = 0;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(l);
        hour = cal.get(Calendar.HOUR_OF_DAY);
        min = cal.get(Calendar.MINUTE);
        calculateNext();
    }

    // calculate when we should triggers next
    public void calculateNext() {
        Calendar cur = Calendar.getInstance();

        long next = this.next;
        if (next == 0)
            next = this.start;

        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(next);
        n.set(Calendar.HOUR_OF_DAY, hour); // reset hour,min = make proper timezone shifts
        n.set(Calendar.MINUTE, min);
        n.set(Calendar.SECOND, 0);
        n.set(Calendar.MILLISECOND, 0);

        while (n.before(cur)) {
            switch (repeat) {
                case REPEAT_DELETE:
                case REPEAT_ONCE:
                    if (this.next == 0) { // just set time, never fiers  before. calculate next date
                        n.add(Calendar.DAY_OF_MONTH, 1); // one day ahead
                    } else { // we already fired once disable
                        this.next = 0;
                        this.enabled = false;
                        return; // exit
                    }
                    break;
                case REPEAT_6HOURS:
                    n.add(Calendar.HOUR_OF_DAY, 6);
                    break;
                case REPEAT_DAY:
                    n.add(Calendar.DAY_OF_MONTH, 1);
                    break;
                case REPEAT_MONTH_1:
                    n.add(Calendar.MONTH, 1);
                    break;
                case REPEAT_MONTH_2:
                    n.add(Calendar.MONTH, 2);
                    break;
                case REPEAT_MONTH_3:
                    n.add(Calendar.MONTH, 3);
                    break;
                case REPEAT_MONTH_4:
                    n.add(Calendar.MONTH, 4);
                    break;
                case REPEAT_MONTH_6:
                    n.add(Calendar.MONTH, 6);
                    break;
                case REPEAT_MONTH_12:
                    n.add(Calendar.MONTH, 12);
                    break;
                case REPEAT_WEEK:
                    n.add(Calendar.DAY_OF_MONTH, 7);
                    break;
                default:
                    throw new RuntimeException("bad repeat");
            }
        }

        this.next = n.getTimeInMillis();
    }

    public void fired() {
        calculateNext();
        this.start = this.next; // will keep UI in sync. better to move start date.
    }

    public static String formatDate(long t) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(t);
        return String.format("%04d/%02d/%02d", start.get(Calendar.YEAR), 1 + start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
    }

    public String formatDate() {
        return formatDate(this.start);
    }

    public static String formatDateTime(long time) {
        return formatDate(time) + " " + formatTime(time);
    }

    public static String formatTime(long t) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(t);
        return String.format("%02d:%02d", start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE));
    }

    public static String formatTime(Context context, long t) {
        if (DateFormat.is24HourFormat(context)) {
            return formatTime(t);
        } else {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(t);
            SimpleDateFormat ff = new SimpleDateFormat("hh:mm a");
            return ff.format(start.getTime());
        }
    }

    public String formatTime() {
        return formatTime(context, this.start);
    }

    public String formatStatus() {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(this.start);

        ArrayAdapter a = create(context);
        String r = get(a, repeat).name;

        switch (repeat) {
            case REPEAT_6HOURS:
            case REPEAT_ONCE:
                return r + " at " + formatDate() + " " + formatTime();
            case REPEAT_DAY:
                return r + " at " + formatTime();
            case REPEAT_WEEK:
                r = getWeekName(start.getTime());
                return "Every " + r + " at " + formatTime();
            case REPEAT_MONTH_1:
            case REPEAT_MONTH_2:
            case REPEAT_MONTH_3:
            case REPEAT_MONTH_4:
            case REPEAT_MONTH_6:
            case REPEAT_MONTH_12:
                return r + String.format(" at %02d day ", start.get(Calendar.DAY_OF_MONTH)) + formatTime();
            default:
                return "UNKNOWN";
        }
    }
}
