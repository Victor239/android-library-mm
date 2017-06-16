package com.github.axet.androidlibrary.sound;

import android.annotation.TargetApi;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
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
    int sampleRate;
    int audioFormat;
    int channelConfig;

    // AudioTrack unable to play shorter then 'min' size of data, fill it with zeros
    public static int getMinSize(int sampleRate, int c, int audioFormat, int b) {
        int min = android.media.AudioTrack.getMinBufferSize(sampleRate, c, audioFormat);
        if (b < min)
            b = min;
        return b;
    }

    // streamType AudioManager#STREAM_MUSIC
    // usage AudioAttributes#USAGE_MEDIA
    // ct AudioAttributes#CONTENT_TYPE_MUSIC
    public static AudioTrack create(int streamType, int usage, int ct, AudioBuffer buffer) {
        return create(streamType, usage, ct, buffer, buffer.getBytesMin());
    }

    public static AudioTrack create(int streamType, int usage, int ct, AudioBuffer buffer, int len) {
        if (Build.VERSION.SDK_INT >= 21) {
            AudioAttributes a = new AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(ct)
                    .build();
            return new AudioTrack(a, buffer, len);
        } else {
            return new AudioTrack(streamType, buffer, len);
        }
    }

    public static class AudioBuffer {
        public int sampleRate;
        public int channelConfig; // AudioFormat.CHANNEL_OUT_MONO or AudioFormat.CHANNEL_OUT_STEREO
        public int audioFormat;
        public short[] buffer; // buffer including zeros (to fill minimum size)
        public int len; // buffer length
        public int pos; // write AudioTrack pos

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
            this.buffer = new short[len];
        }

        public AudioBuffer(int sampleRate, int c, int audioFormat) {
            this.sampleRate = sampleRate;
            this.channelConfig = c;
            this.audioFormat = audioFormat;
            this.len = getMinSize(sampleRate, c, audioFormat, 0);
            if (len <= 0)
                throw new RuntimeException("unable to initialize audio");
            this.buffer = new short[len];
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

        @TargetApi(21)
        public AudioFormat getAudioFormat() {
            AudioFormat.Builder builder = new AudioFormat.Builder();
            builder.setEncoding(Sound.DEFAULT_AUDIOFORMAT);
            builder.setSampleRate(sampleRate);
            return builder.build();
        }

        public int getBytesLen() {
            return buffer.length * SHORT_SIZE;
        }

        public int getBytesMin() {
            return getMinSize(sampleRate, channelConfig, audioFormat, getBytesLen());
        }
    }

    // old phones bug.
    // http://stackoverflow.com/questions/27602492
    //
    // with MODE_STATIC setNotificationMarkerPosition not called
    public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) throws IllegalArgumentException {
        super(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, MODE_STREAM);
        this.sampleRate = sampleRateInHz;
        this.audioFormat = audioFormat;
        this.channelConfig = channelConfig;
    }

    public AudioTrack(int streamType, AudioBuffer buffer) throws IllegalArgumentException {
        this(streamType, buffer, buffer.getBytesMin());
    }

    public AudioTrack(int streamType, AudioBuffer buffer, int len) throws IllegalArgumentException {
        super(streamType, buffer.sampleRate, buffer.channelConfig, buffer.audioFormat, len, MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (getState() != STATE_INITIALIZED)
            throw new RuntimeException("Unable initialize AudioTrack");
        write(buffer);
        this.sampleRate = buffer.sampleRate;
        this.audioFormat = buffer.audioFormat;
        this.channelConfig = buffer.channelConfig;
    }

    @TargetApi(21)
    public AudioTrack(AudioAttributes a, AudioBuffer buffer) throws IllegalArgumentException {
        this(a, buffer, buffer.getBytesMin());
    }

    @TargetApi(21)
    public AudioTrack(AudioAttributes a, AudioBuffer buffer, int len) throws IllegalArgumentException {
        super(a, buffer.getAudioFormat(), len, MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (getState() != STATE_INITIALIZED)
            throw new RuntimeException("Unable initialize AudioTrack");
        write(buffer);
        this.sampleRate = buffer.sampleRate;
        this.audioFormat = buffer.audioFormat;
        this.channelConfig = buffer.channelConfig;
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

    void fillZeros() {
        int b = len * SHORT_SIZE;
        b = getMinSize(sampleRate, channelConfig, audioFormat, b);
        if (b <= 0)
            throw new RuntimeException("unable to get min size");
        int blen = b / SHORT_SIZE;
        int diff = blen - len;
        if (diff > 0) {
            short[] buf = new short[diff];
            write(buf, 0, buf.length);
            len += diff / getChannelCount();
            frames += diff;
        }
    }

    @Override
    public void play() throws IllegalStateException {
        fillZeros();
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

    @Override
    public int write(short[] audioData, int offsetInShorts, int sizeInShorts) {
        int out = super.write(audioData, offsetInShorts, sizeInShorts);
        this.len += out / getChannelCount();
        this.frames += out;
        return out;
    }

    public int write(AudioBuffer buffer) {
        int out = write(buffer, buffer.pos, buffer.len - buffer.pos);
        if (out < 0)
            return out;
        buffer.pos += out;
        return out;
    }

    public int write(AudioBuffer buffer, int pos, int len) {
        return write(buffer.buffer, pos, len);
    }
}
