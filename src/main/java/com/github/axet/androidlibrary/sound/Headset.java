package com.github.axet.androidlibrary.sound;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// TODO: Temporarily stubbed out for AndroidX migration - MediaSessionCompat requires additional dependencies
// Original implementation used androidx.media.session.MediaSessionCompat
public class Headset {
    public static String TAG = Headset.class.getSimpleName();

    // Stubbed constants
    public static long ACTIONS_MAIN = 0;
    public static long ACTIONS_SKIP = 0;

    public Object msc; // Changed from MediaSessionCompat to Object
    public long actions = ACTIONS_MAIN | ACTIONS_SKIP;
    public int flags = 0;

    public static void handleIntent(Headset headset, Intent intent) {
        if (headset == null)
            return;
        Log.d(TAG, "handleIntent stubbed - MediaSession support disabled");
    }

    public Headset() {
    }

    public void create(Context context, Class cls) {
        Log.d(TAG, "headset mediabutton support stubbed - MediaSession support disabled");
        // Stubbed out - MediaSessionCompat not available
    }

    public void setState(boolean playing) {
        // Stubbed out
    }

    public void onPlay() {
    }

    public void onPause() {
    }

    public void onStop() {
    }

    public void onSkipToNext() {
    }

    public void onSkipToPrevious() {
    }

    public void close() {
        Log.d(TAG, "headset mediabutton off (stubbed)");
        msc = null;
    }
}
