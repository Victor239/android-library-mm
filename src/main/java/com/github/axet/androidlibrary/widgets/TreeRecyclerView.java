package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class TreeRecyclerView extends RecyclerView {

    public OnToggleListener toggleListener;
    public MotionEvent last;

    public static class TreeHolder extends ViewHolder {
        public TreeHolder(View itemView) {
            super(itemView);
        }
    }

    public static class TreeAdapter<T extends TreeHolder> extends Adapter<T> {
        public TreeListView.TreeNode root = new TreeListView.TreeNode();
        public ArrayList<TreeListView.TreeNode> items = new ArrayList<>();

        public TreeAdapter() {
        }

        public void load() {
            items.clear();
            load(root);
            notifyDataSetChanged();
        }

        public void load(TreeListView.TreeNode tt) {
            for (TreeListView.TreeNode t : tt.nodes) {
                items.add(t);
                if (t.expanded)
                    load(t);
            }
        }

        public int expand(TreeListView.TreeNode n) {
            int count = 0;
            int pos = items.indexOf(n);
            notifyItemChanged(pos); // update expand / collaps icons
            pos = pos + 1;
            for (TreeListView.TreeNode t : n.nodes) {
                items.add(pos, t);
                notifyItemInserted(pos);
                pos++;
                count++;
                if (t.expanded) {
                    int e = expand(t);
                    pos += e;
                    count += e;
                }
            }
            return count;
        }

        public void collapse(TreeListView.TreeNode n) {
            int pos = items.indexOf(n);
            notifyItemChanged(pos); // update expand / collaps icons
            pos = pos + 1;
            for (TreeListView.TreeNode t : n.nodes) {
                if (t.expanded) // if item were expanded, collapse it
                    collapse(t);
                items.remove(pos);
                notifyItemRemoved(pos);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public TreeListView.TreeNode getItem(int position) {
            return items.get(position);
        }

        @Override
        public T onCreateViewHolder(ViewGroup parent, int viewType) {
            return null;
        }

        @Override
        public void onBindViewHolder(T holder, int position) {
        }
    }

    public interface OnToggleListener {
        void onItemToggled(View view, ViewHolder h);
    }

    public TreeRecyclerView(Context context) {
        super(context);
        create();
    }

    public TreeRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public TreeRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public void create() {
        setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public void setOnToggleListener(OnToggleListener l) {
        toggleListener = l;
    }

    public boolean performItemClick(View view, ViewHolder h) {
        TreeAdapter a = (TreeAdapter) getAdapter();
        TreeListView.TreeNode n = a.getItem(h.getAdapterPosition());
        if (!n.nodes.isEmpty()) { // is folder
            n.expanded = !n.expanded;
            if (n.expanded)
                a.expand(n);
            else
                a.collapse(n);
            if (toggleListener != null)
                toggleListener.onItemToggled(view, h);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (super.onInterceptTouchEvent(e))
            return true;
        View child = findChildViewUnder((int) e.getX(), (int) e.getY());
        if (child != null) {
            if (child.hasFocusable() && child.dispatchTouchEvent(e))
                return false; // pass touch event to the checkboxes
            ViewHolder h = findContainingViewHolder(child);
            TreeAdapter a = (TreeAdapter) getAdapter();
            TreeListView.TreeNode n = a.getItem(h.getAdapterPosition());
            if (!n.nodes.isEmpty()) // is folder
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last = MotionEvent.obtain(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                if (last != null && ev.getX() == last.getX() && ev.getY() == last.getY()) {
                    View child = findChildViewUnder((int) ev.getX(), (int) ev.getY());
                    if (child != null) {
                        if (child.hasFocusable() && child.dispatchTouchEvent(ev))
                            return false; // pass touch event to the checkboxes
                        ViewHolder h = findContainingViewHolder(child);
                        performItemClick(child, h);
                        return true;
                    }
                }
                break;
        }
        return super.onTouchEvent(ev);
    }
}
