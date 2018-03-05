package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class TreeListView extends ListView {

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

    public static class TreeAdapter extends BaseAdapter {
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

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public TreeNode getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return null;
        }
    }

    public interface OnToggleListener {
        void onItemToggled(View view, int position, long id);
    }

    public TreeListView(Context context) {
        super(context);
    }

    public TreeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TreeListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public TreeListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        super.setOnItemClickListener(listener);
    }

    public void setOnToggleListener(OnToggleListener l) {
        toggleListener = l;
    }

    @Override
    public boolean performItemClick(View view, int position, long id) {
        TreeAdapter a = (TreeAdapter) getAdapter();
        TreeNode n = a.getItem(position);
        if (!n.nodes.isEmpty()) {
            n.expanded = !n.expanded;
            a.load();
            if (toggleListener != null)
                toggleListener.onItemToggled(view, position, id);
        }
        return super.performItemClick(view, position, id);
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
                    int first = getFirstVisiblePosition();
                    int motionPosition = pointToPosition((int) ev.getX(), (int) ev.getY());
                    View child = getChildAt(motionPosition - first);
                    if (child != null && child.hasFocusable()) {
                        if (performItemClick(child, motionPosition, getAdapter().getItemId(motionPosition)))
                            return true;
                    }
                }
                break;
        }
        return b;
    }
}
