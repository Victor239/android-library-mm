package com.github.axet.androidlibrary.sound;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.media.MediaRouter;

import com.github.axet.androidlibrary.widgets.SilencePreferenceCompat;

import java.lang.reflect.Method;
import java.util.Arrays;

public class Sound {

    public static final int DEFAULT_AUDIOFORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int DEFAULT_RATE = 16000;

    public static int[] RATES = new int[]{8000, 11025, 16000, 22050, 44100, 48000};

    public static final String ZEN_MODE = "zen_mode";
    public static final int ZEN_MODE_OFF = 0;
    public static final int ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1;
    public static final int ZEN_MODE_NO_INTERRUPTIONS = 2;
    public static final int ZEN_MODE_ALARMS = 3;

    public static final long LAST = 1000; // last delay

    public Context context;
    public Handler handler = new Handler();
    public int soundMode = -1;
    public long last; // last change, prevent spam
    public Runnable delayed;

    public static int getValidRecordRate(int in, int rate) {
        int i = Arrays.binarySearch(RATES, rate);
        if (i < 0) {
            i = -i - 2;
        }
        for (; i >= 0; i--) {
            int r = RATES[i];
            int bufferSize = AudioRecord.getMinBufferSize(r, in, DEFAULT_AUDIOFORMAT);
            if (bufferSize > 0) {
                return r;
            }
        }
        return -1;
    }

    public static int getValidAudioRate(int out, int rate) {
        int i = Arrays.binarySearch(RATES, rate);
        if (i < 0) {
            i = -i - 2;
        }
        for (; i >= 0; i--) {
            int r = RATES[i];
            int bufferSize = AudioTrack.getMinBufferSize(r, out, DEFAULT_AUDIOFORMAT);
            if (bufferSize > 0) {
                return r;
            }
        }
        return -1;
    }

    public Sound(Context context) {
        this.context = context;
    }

    public static float log1(float v, float m) {
        float log1 = (float) (Math.log(m - v) / Math.log(m));
        return 1 - log1;
    }

    public static float log1(float v) {
        return log1(v, 2);
    }

    boolean delaying(Runnable r) {
        long next = last + LAST;
        long last = System.currentTimeMillis();
        if (next > last) {
            handler.removeCallbacks(delayed);
            delayed = r;
            handler.postDelayed(delayed, next - last);
            return true;
        }
        this.last = last;
        return false;
    }

    public void silent() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!SilencePreferenceCompat.isNotificationPolicyAccessGranted(context))
                return;
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                silent();
            }
        };
        if (delaying(r))
            return;

        if (soundMode != -1)
            return; // already silensed

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        soundMode = am.getRingerMode();

        if (soundMode == AudioManager.RINGER_MODE_SILENT) { // we already in SILENT mode. keep all unchanged.
            soundMode = -1;
            return;
        }

        am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
    }

    public void unsilent() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!SilencePreferenceCompat.isNotificationPolicyAccessGranted(context))
                return;
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                unsilent();
            }
        };
        if (delaying(r))
            return;

        if (soundMode == -1)
            return; // already unsilensed

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int soundMode = am.getRingerMode();
        if (soundMode == AudioManager.RINGER_MODE_SILENT) {
            am.setRingerMode(this.soundMode);
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
        }

        this.soundMode = -1;
    }

    @TargetApi(17)
    public int getDNDMode() {
        ContentResolver resolver = context.getContentResolver();
        try {
            return Settings.Global.getInt(resolver, ZEN_MODE);
        } catch (Settings.SettingNotFoundException e) {
            return ZEN_MODE_OFF;
        }
    }

}
