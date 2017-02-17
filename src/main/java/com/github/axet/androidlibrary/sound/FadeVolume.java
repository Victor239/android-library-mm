package com.github.axet.androidlibrary.sound;

import android.os.Handler;

public class FadeVolume implements Runnable {
    public Handler handler = new Handler();

    public int step = 0;
    public int steps = 50;
    public int delay = 100;

    public FadeVolume(int sec) {
        steps = (sec / delay);
        if (steps < 1)
            steps = 1;
    }

    public void stop() {
        handler.removeCallbacks(this);
    }

    @Override
    public void run() {
        handler.removeCallbacks(this);

        float vol = Sound.log1(step, steps);

        if (!step(vol))
            return;

        step++;

        if (step >= steps) {
            // should be clear anyway
            handler.removeCallbacks(this);
            done();
            return;
        }

        handler.postDelayed(this, delay);
    }

    public boolean step(float vol) {
        return true;
    }

    public void done() {
    }
}
