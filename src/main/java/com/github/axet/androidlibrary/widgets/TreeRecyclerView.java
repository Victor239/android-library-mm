package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.support.v4.view.ViewCompat;
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
        void onItemToggled(ViewHolder h);
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

    public boolean performItemClick(ViewHolder h) {
        Adapter a = getAdapter();
        int pos = h.getAdapterPosition();
        if (a instanceof HeaderRecyclerAdapter) {
            pos = ((HeaderRecyclerAdapter) a).getWrappedPosition(pos);
            a = ((HeaderRecyclerAdapter) a).getWrappedAdapter();
        }
        if (pos < 0)
            return false;
        TreeAdapter t = (TreeAdapter) a;
        TreeListView.TreeNode n = t.getItem(pos);
        if (!n.nodes.isEmpty()) { // is folder
            n.expanded = !n.expanded;
            if (n.expanded)
                t.expand(n);
            else
                t.collapse(n);
            if (toggleListener != null)
                toggleListener.onItemToggled(h);
            return true;
        }
        return false;
    }

    public ViewHolder findChildHolderUnder(float x, float y) { // findChildViewUnder can return not attached view, two animation view with same coords?
        final int count = getChildCount();
        ViewHolder last = null;
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final float translationX = ViewCompat.getTranslationX(child);
            final float translationY = ViewCompat.getTranslationY(child);
            if (x >= child.getLeft() + translationX &&
                    x <= child.getRight() + translationX &&
                    y >= child.getTop() + translationY &&
                    y <= child.getBottom() + translationY) {
                last = findContainingViewHolder(child);
                if (last.getAdapterPosition() != NO_POSITION)
                    return last;
            }
        }
        return last;
    }

    public static boolean hasFocusable(View v, float x, float y) { // has clicable view under coords
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            final int count = g.getChildCount();
            for (int i = count - 1; i >= 0; i--) {
                final View child = g.getChildAt(i);
                if (hasFocusable(child, x - v.getLeft() - v.getPaddingLeft(), y - v.getTop() - v.getPaddingTop()))
                    return true;
            }
        } else if (v.hasFocusable()) {
            final float translationX = ViewCompat.getTranslationX(v);
            final float translationY = ViewCompat.getTranslationY(v);
            if (x >= v.getLeft() + translationX &&
                    x <= v.getRight() + translationX &&
                    y >= v.getTop() + translationY &&
                    y <= v.getBottom() + translationY) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (super.onInterceptTouchEvent(e))
            return true;
        ViewHolder h = findChildHolderUnder(e.getX(), e.getY());
        if (h != null) {
            if (hasFocusable(h.itemView, e.getX(), e.getY()))
                return false; // pass touch events to the checkboxes
            int pos = h.getAdapterPosition();
            Adapter a = getAdapter();
            if (a instanceof HeaderRecyclerAdapter) {
                HeaderRecyclerAdapter s = (HeaderRecyclerAdapter) a;
                pos = s.getWrappedPosition(pos);
                a = s.getWrappedAdapter();
            }
            if (pos < 0)
                return false;
            TreeAdapter t = (TreeAdapter) a;
            TreeListView.TreeNode n = t.getItem(pos);
            if (!n.nodes.isEmpty()) // is folder
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last = MotionEvent.obtain(e);
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                if (last != null && e.getX() == last.getX() && e.getY() == last.getY()) {
                    ViewHolder h = findChildHolderUnder(e.getX(), e.getY());
                    if (h != null) {
                        if (hasFocusable(h.itemView, e.getX(), e.getY()))
                            return false; // pass touch event to the checkboxes
                        performItemClick(h);
                        return true;
                    }
                }
                break;
        }
        return super.onTouchEvent(e);
    }
}
