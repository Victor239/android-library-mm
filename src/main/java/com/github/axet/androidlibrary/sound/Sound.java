package com.github.axet.androidlibrary.sound;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.provider.Settings;

import com.github.axet.androidlibrary.widgets.SilencePreferenceCompat;

import java.util.Arrays;

public class Sound {

    public static final int DEFAULT_AUDIOFORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int DEFAULT_RATE = 16000;

    public static int[] RATES = new int[]{8000, 11025, 16000, 22050, 44100};

    public static final String ZEN_MODE = "zen_mode";
    public static final int ZEN_MODE_OFF = 0;
    public static final int ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1;
    public static final int ZEN_MODE_NO_INTERRUPTIONS = 2;
    public static final int ZEN_MODE_ALARMS = 3;

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

    protected Context context;
    protected int soundMode = -1;

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

    public void silent() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!SilencePreferenceCompat.isNotificationPolicyAccessGranted(context))
                return;
        }

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
