package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.graphics.PointF;
import androidx.recyclerview.widget.LinearSmoothScroller;

public class TopAlwaysSmoothScroller extends LinearSmoothScroller {
    public TopAlwaysSmoothScroller(Context context) {
        super(context);
    }

    @Override
    protected int getVerticalSnapPreference() {
        return SNAP_TO_START;
    }
}
