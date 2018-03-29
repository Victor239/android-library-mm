package com.github.axet.androidlibrary.widgets;

import android.support.v7.widget.RecyclerView;
import android.view.View;

public interface WrapperRecyclerAdapter<T extends RecyclerView.ViewHolder> {

    class ViewHolder extends RecyclerView.ViewHolder {
        public WrapperRecyclerAdapter adapter;

        public ViewHolder(View itemView) {
            super(itemView);
        }

        public int getAdapterPosition(RecyclerView.Adapter a) { // position may vary depends on who is calling
            int pos = getAdapterPosition();
            if (adapter == null)
                return pos;
            if (a instanceof WrapperRecyclerAdapter)
                return pos;
            return adapter.getWrappedPosition(pos);
        }
    }

    RecyclerView.Adapter<T> getWrappedAdapter();

    int getWrappedPosition(int pos);

}
