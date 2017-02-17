package com.github.axet.androidlibrary.sound;

import android.content.Context;
import android.media.AudioManager;

public class Sound {
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
        if (soundMode != -1)
            return; // already silensed

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        soundMode = am.getRingerMode();

        if (soundMode == AudioManager.RINGER_MODE_SILENT) {
            // we already in SILENT mode. keep all unchanged.
            soundMode = -1;
            return;
        }

        am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
    }

    public void unsilent() {
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
}
