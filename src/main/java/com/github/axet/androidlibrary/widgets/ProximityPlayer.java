package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.v7.media.MediaRouter;

import java.lang.reflect.Method;

public class ProximityPlayer extends ProximityShader {

    public int streamType;

    public static boolean isDeviceMountedSpeaker(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.isWiredHeadsetOn())
            return false;
        if (am.isBluetoothScoOn())
            return false;
        if (am.isBluetoothA2dpOn())
            return false;
        return MediaRouter.getInstance(context).getDefaultRoute().isDeviceSpeaker(); // device mounted or usb speaker
    }

    public static boolean isDeviceMountedSpeaker(Context context, Object o) {
        if (o instanceof AudioTrack) {
            try {
                Class<?> AudioTrackClass = Class.forName("android.media.AudioTrack");
                Method m = AudioTrackClass.getMethod("getRoutedDevice");
                Object ad = m.invoke(o);
                Class<?> AudioDeviceInfoClass = Class.forName("android.media.AudioDeviceInfo"); // API23+
                Method getType = AudioDeviceInfoClass.getMethod("getType");
                int type = (int) getType.invoke(ad);
                return type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
            } catch (Exception ignore) {
            }
        }
        if (o instanceof MediaPlayer) { // API28+
            try {
                Class<?> AudioRoutingClass = Class.forName("android.media.AudioRouting"); // API24+
                Method m = AudioRoutingClass.getMethod("getRoutedDevice");
                Object ad = m.invoke(o);
                Class<?> AudioDeviceInfoClass = Class.forName("android.media.AudioDeviceInfo"); // API23+
                Method getType = AudioDeviceInfoClass.getMethod("getType");
                int type = (int) getType.invoke(ad);
                return type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
            } catch (Exception ignore) {
            }
        }
        return isDeviceMountedSpeaker(context);
    }

    public ProximityPlayer(Context context) {
        super(context);
        streamType = AudioManager.STREAM_MUSIC;
    }

    @Override
    public void create() {
        super.create();
    }

    @Override
    public void onNear() {
        super.onNear();
        turnScreenOff();
        prepare(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    public void onFar() {
        super.onFar();
        turnScreenOn();
        prepare(AudioManager.STREAM_MUSIC);
    }

    public void prepare(int next) {
        if (!isDeviceMountedSpeaker(context))
            next = AudioManager.STREAM_MUSIC;
        if (next != streamType) {
            streamType = next;
            prepare();
        }
    }

    public void prepare() {
    }
}
