package com.github.axet.androidlibrary.widgets;

import android.support.v7.widget.RecyclerView;

public interface WrapperRecyclerAdapter<T extends RecyclerView.ViewHolder> {

    RecyclerView.Adapter<T> getWrappedAdapter();

    int getWrappedPosition(int pos);

}
