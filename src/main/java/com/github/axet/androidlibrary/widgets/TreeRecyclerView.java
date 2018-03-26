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

    public static class TreeNode {
        public TreeNode parent;
        public boolean selected = false;
        public boolean expanded = false;
        public Object tag;
        public int level;
        public ArrayList<TreeNode> nodes = new ArrayList<>();

        public TreeNode() {
            level = -1;
        }

        public TreeNode(TreeNode p) {
            parent = p;
            level = p.level + 1;
        }

        public TreeNode(Object tag) {
            this.tag = tag;
        }

        public TreeNode(TreeNode p, Object tag) {
            this(p);
            this.tag = tag;
        }
    }

    public static class TreeHolder extends ViewHolder {
        public TreeHolder(View itemView) {
            super(itemView);
        }
    }

    public static class TreeAdapter<T extends TreeHolder> extends Adapter<T> {
        public TreeNode root = new TreeNode();
        public ArrayList<TreeNode> items = new ArrayList<>();

        public TreeAdapter() {
        }

        public void load() {
            items.clear();
            load(root);
            notifyDataSetChanged();
        }

        public void load(TreeNode tt) {
            for (TreeNode t : tt.nodes) {
                items.add(t);
                if (t.expanded)
                    load(t);
            }
        }

        public int expand(TreeNode n) {
            int count = 0;
            int pos = items.indexOf(n);
            notifyItemChanged(pos); // update expand / collaps icons
            pos = pos + 1;
            for (TreeNode t : n.nodes) {
                items.add(pos, t);
                notifyItemInserted(pos);
                pos++;
                count++;
                if (t.expanded)
                    pos += expand(t);
            }
            return count;
        }

        public void collapse(TreeNode n) {
            int pos = items.indexOf(n);
            notifyItemChanged(pos); // update expand / collaps icons
            pos = pos + 1;
            for (TreeNode t : n.nodes) {
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

        public TreeNode getItem(int position) {
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
        TreeNode n = a.getItem(h.getAdapterPosition());
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
        if (toggleListener == null)
            return false;
        View child = findChildViewUnder((int) e.getX(), (int) e.getY());
        if (child != null) {
            ViewHolder h = findContainingViewHolder(child);
            TreeAdapter a = (TreeAdapter) getAdapter();
            TreeNode n = a.getItem(h.getAdapterPosition());
            if (!n.nodes.isEmpty()) // is folder
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean b = super.onTouchEvent(ev);
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
                        ViewHolder h = findContainingViewHolder(child);
                        performItemClick(child, h);
                        return true;
                    }
                }
                break;
        }
        return b;
    }
}
