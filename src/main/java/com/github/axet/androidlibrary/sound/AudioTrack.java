package com.github.axet.androidlibrary.sound;

import android.media.AudioFormat;
import android.os.Handler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioTrack extends android.media.AudioTrack {
    public static int SHORT_SIZE = Short.SIZE / 8;

    int markerInFrames = -1;
    int periodInFrames = 1000;
    public long playStart = 0;
    Handler playbackHandler = new Handler();
    Runnable playbackUpdate;
    OnPlaybackPositionUpdateListener playbackListener;
    int len; // len in frames (stereo frames = len * 2)
    int frames; // frames written to audiotrack (including zeros, stereo frames = frames)

    public static class AudioBuffer {
        public int sampleRate;
        public int channelConfig; // AudioFormat.CHANNEL_OUT_MONO or AudioFormat.CHANNEL_OUT_STEREO
        public int audioFormat;
        public short[] buffer; // buffer including zeros (to fill minimum size)
        public int len; // buffer length

        public AudioBuffer(int sampleRate, int c, int audioFormat, short[] buf, int len) {
            this.sampleRate = sampleRate;
            this.channelConfig = c;
            this.audioFormat = audioFormat;
            this.len = len;
            this.buffer = buf;
        }

        public AudioBuffer(int sampleRate, int c, int audioFormat, int len) {
            this.sampleRate = sampleRate;
            this.channelConfig = c;
            this.audioFormat = audioFormat;
            this.len = len;

            int b = len * SHORT_SIZE;
            b = getMinSize(sampleRate, c, audioFormat, b);
            int blen = b / SHORT_SIZE;
            buffer = new short[blen];
        }

        public void write(short[] buf, int pos, int len) {
            System.arraycopy(buf, pos, buffer, 0, len);
        }

        public int getChannels() {
            switch (channelConfig) {
                case AudioFormat.CHANNEL_OUT_MONO:
                    return 1;
                case AudioFormat.CHANNEL_OUT_STEREO:
                    return 2;
                default:
                    throw new RuntimeException("unknown mode");
            }
        }
    }

    // old phones bug.
    // http://stackoverflow.com/questions/27602492
    //
    // with MODE_STATIC setNotificationMarkerPosition not called
    public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) throws IllegalArgumentException {
        super(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, MODE_STREAM);
    }

    public AudioTrack(int streamType, AudioBuffer buffer) throws IllegalArgumentException {
        super(streamType, buffer.sampleRate, buffer.channelConfig, buffer.audioFormat, buffer.buffer.length * SHORT_SIZE, MODE_STREAM);
        write(buffer);
    }

    // AudioTrack unable to play shorter then 'min' size of data, fill it with zeros
    public static int getMinSize(int sampleRate, int c, int audioFormat, int b) {
        int min = android.media.AudioTrack.getMinBufferSize(sampleRate, c, audioFormat);
        if (b < min)
            b = min;
        return b;
    }

    void playbackListenerUpdate() {
        if (playbackListener == null)
            return;
        if (playStart <= 0)
            return;

        int mark = 0;
        try {
            mark = getNotificationMarkerPosition();
        } catch (IllegalStateException ignore) { // Unable to retrieve AudioTrack pointer for getMarkerPosition()
        }

        if (mark <= 0 && markerInFrames >= 0) { // some old bugged phones unable to set markers
            playbackHandler.removeCallbacks(playbackUpdate);
            playbackUpdate = new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    if (markerInFrames >= 0) {
                        long playEnd = playStart + markerInFrames * 1000 / getSampleRate();
                        if (now >= playEnd) {
                            playbackListener.onMarkerReached(AudioTrack.this);
                            return;
                        }
                    }
                    playbackListener.onPeriodicNotification(AudioTrack.this);
                    long update = periodInFrames * 1000 / getSampleRate();

                    int len = getNativeFrameCount() * 1000 / getSampleRate(); // getNativeFrameCount() checking stereo fine
                    long end = len * 2 - (now - playStart);
                    if (update > end)
                        update = end;

                    playbackHandler.postDelayed(playbackUpdate, update);
                }
            };
            playbackUpdate.run();
        } else {
            playbackHandler.removeCallbacks(playbackUpdate);
            playbackUpdate = null;
        }
    }

    @Override
    public void release() {
        super.release();
        if (playbackUpdate != null) {
            playbackHandler.removeCallbacks(playbackUpdate);
            playbackUpdate = null;
        }
    }

    @Override
    public void play() throws IllegalStateException {
        super.play();
        playStart = System.currentTimeMillis();
        playbackListenerUpdate();
    }

    @Override
    public int setNotificationMarkerPosition(int markerInFrames) {  // do not check != AudioTrack.SUCCESS crash often
        this.markerInFrames = markerInFrames;
        return super.setNotificationMarkerPosition(markerInFrames);
    }

    @Override
    public int setPositionNotificationPeriod(int periodInFrames) {
        this.periodInFrames = periodInFrames;
        return super.setPositionNotificationPeriod(periodInFrames);
    }

    @Override
    public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener listener) {
        super.setPlaybackPositionUpdateListener(listener);
        this.playbackListener = listener;
        playbackListenerUpdate();
    }

    @Override
    public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener listener, Handler handler) {
        super.setPlaybackPositionUpdateListener(listener, handler);
        this.playbackListener = listener;
        if (handler != null) {
            this.playbackHandler.removeCallbacks(playbackUpdate);
            this.playbackHandler = handler;
        }
        playbackListenerUpdate();
    }

    public void write(AudioBuffer buffer) {
        write(buffer.buffer, 0, buffer.buffer.length);
        this.len += buffer.len / buffer.getChannels();
        this.frames += buffer.buffer.length;
    }
}
