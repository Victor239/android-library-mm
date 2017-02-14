package com.github.axet.androidlibrary.sound;

import android.os.Handler;

public class FadeVolume implements Runnable {
    Handler handler = new Handler();

    public int step = 0;
    public int steps = 50;
    public int delay = 100;
    // we start from startVolume, rest - how much we should increase
    public float rest = 0;
    public float startVolume = 0;
    public float inc;

    public FadeVolume(int sec, int inc) {
        this(inc < 0 ? 1 : 0, sec, inc);
    }

    public FadeVolume(float startVolume, int sec, int inc) {
        this.startVolume = startVolume;
        this.inc = inc;
        this.steps = (sec / delay);
        this.rest = 1f - startVolume;
    }

    public void stop() {
        handler.removeCallbacks(this);
    }

    @Override
    public void run() {
        handler.removeCallbacks(this);

        float log1 = (float) (Math.log(steps - step) / Math.log(steps));
        // volume 0..1
        float vol = 1 - log1;

        // actual volume
        float restvol = startVolume + inc * rest * vol;

        step(restvol);

        step++;

        if (step >= steps) {
            // should be clear anyway
            handler.removeCallbacks(this);
            done();
            return;
        }

        handler.postDelayed(this, delay);
    }

    public void step(float vol) {
    }

    public void done() {
    }
}
