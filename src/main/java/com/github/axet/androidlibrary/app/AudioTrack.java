package com.github.axet.androidlibrary.app;

import android.os.Handler;

import java.lang.reflect.Method;

public class AudioTrack extends android.media.AudioTrack {
    int markerInFrames = -1;
    int periodInFrames = 1000;
    Handler handler = new Handler();
    Runnable playInterval;
    OnPlaybackPositionUpdateListener listener;
    public long playStart = 0;

    // old phones bug.
    // http://stackoverflow.com/questions/27602492
    //
    // with MODE_STATIC setNotificationMarkerPosition not called
    public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) throws IllegalArgumentException {
        super(streamType, sampleRateInHz, channelConfig, audioFormat, getMinSize(sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes), MODE_STREAM);
    }

    // AudioTrack unable to play shorter then 'min' size of data, fill it with zeros
    public static int getMinSize(int sampleRate, int c, int audioFormat, int b) {
        int min = android.media.AudioTrack.getMinBufferSize(sampleRate, c, audioFormat);
        if (b < min)
            b = min;
        return b;
    }

    void update() {
        if (listener == null)
            return;
        if (playStart <= 0)
            return;

        int mark = 0;
        try {
            mark = getNotificationMarkerPosition();
        } catch (IllegalStateException ignore) { // Unable to retrieve AudioTrack pointer for getMarkerPosition()
        }

        if (mark <= 0 && markerInFrames >= 0) { // some old bugged phones unable to set markers
            handler.removeCallbacks(playInterval);
            playInterval = new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    if (markerInFrames >= 0) {
                        long playEnd = playStart + markerInFrames * 1000 / getSampleRate();
                        if (now >= playEnd) {
                            listener.onMarkerReached(AudioTrack.this);
                            return;
                        }
                    }
                    listener.onPeriodicNotification(AudioTrack.this);
                    long update = periodInFrames * 1000 / getSampleRate();

                    long end;
                    try {
                        Method m = getClass().getDeclaredMethod("getNativeFrameCount");
                        m.setAccessible(true);
                        int len = (int) m.invoke(this) * 1000 / getSampleRate();
                        end = len * 2 - (now - playStart);
                    } catch (Exception e) { // use 1 sec delay
                        end = 1000;
                    }
                    if (update > end)
                        update = end;

                    handler.postDelayed(playInterval, update);
                }
            };
            playInterval.run();
        } else {
            handler.removeCallbacks(playInterval);
            playInterval = null;
        }
    }

    @Override
    public void release() {
        super.release();
        if (playInterval != null) {
            handler.removeCallbacks(playInterval);
            playInterval = null;
        }
    }

    @Override
    public void play() throws IllegalStateException {
        super.play();
        playStart = System.currentTimeMillis();
        update();
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
        this.listener = listener;
        update();
        super.setPlaybackPositionUpdateListener(listener);
    }

    @Override
    public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener listener, Handler handler) {
        this.listener = listener;
        if (handler != null)
            this.handler = handler;
        update();
        super.setPlaybackPositionUpdateListener(listener, handler);
    }

}
