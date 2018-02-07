package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.DataSetObserver;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class TreeListView extends ListView {

    public OnToggleListener toggleListener;

    public static class TreeNode {
        public boolean selected = false;
        public boolean expanded = false;
        public Object tag;
        public ArrayList<TreeNode> nodes = new ArrayList<>();

        public TreeNode() {
        }

        public TreeNode(Object tag) {
            this.tag = tag;
        }
    }

    public static class TreeAdapter implements ListAdapter {
        public DataSetObserver listener;
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

        void notifyDataSetChanged() {
            if (listener == null)
                return;
            listener.onChanged();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            listener = observer;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            listener = null;
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

        @Override
        public int getItemViewType(int position) {
            return 1;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return items.isEmpty();
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
}
