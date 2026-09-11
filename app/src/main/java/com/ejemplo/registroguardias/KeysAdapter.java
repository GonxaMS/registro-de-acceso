package com.ejemplo.registroguardias;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

final class KeysAdapter extends BaseAdapter {
    interface Actions {
        void onKeyMovement(KeyItem key, String type);
        void onKeyOptions(View anchor, KeyItem key);
        boolean isKeyMovementPending(KeyItem key);
        boolean isNetworkAvailable();
    }

    private final KeysActivity activity;
    private final List<KeyItem> keys;
    private final Actions actions;
    private final Handler highlightHandler = new Handler(Looper.getMainLooper());
    private String highlightedId = "";

    KeysAdapter(KeysActivity activity, List<KeyItem> keys, Actions actions) {
        this.activity = activity;
        this.keys = keys;
        this.actions = actions;
    }

    void highlightKey(String id) {
        highlightedId = id;
        notifyDataSetChanged();
        highlightHandler.postDelayed(() -> {
            if (id.equals(highlightedId)) {
                highlightedId = "";
                notifyDataSetChanged();
            }
        }, 1800L);
    }

    @Override public int getCount() { return keys.size(); }
    @Override public KeyItem getItem(int position) { return keys.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        Holder holder;
        if (recycled == null) {
            recycled = LayoutInflater.from(activity).inflate(R.layout.item_key, parent, false);
            holder = new Holder(recycled);
            recycled.setTag(holder);
        } else {
            holder = (Holder) recycled.getTag();
        }

        KeyItem key = getItem(position);
        recycled.setBackgroundTintList(key.id.equals(highlightedId)
            ? ColorStateList.valueOf(activity.getColor(R.color.highlight_key)) : null);
        boolean borrowed = "Prestada".equals(key.state);
        boolean pending = actions.isKeyMovementPending(key);
        boolean networkAvailable = actions.isNetworkAvailable();
        holder.name.setText(key.name);
        holder.status.setText(borrowed ? R.string.status_borrowed : R.string.status_available);
        holder.status.setTextColor(activity.getColor(
            borrowed ? R.color.gold_dark : R.color.green_dark));
        holder.detail.setText(borrowed
            ? activity.getString(R.string.key_holder_detail, key.holder, key.date, key.time)
            : activity.getString(R.string.key_ready_detail));

        setEnabled(holder.take, networkAvailable && !pending && !borrowed);
        setEnabled(holder.returnKey, networkAvailable && !pending && borrowed);
        holder.options.setEnabled(networkAvailable);
        holder.options.setAlpha(networkAvailable ? 1f : 0.45f);
        holder.take.setOnClickListener(view -> actions.onKeyMovement(key, "Retiro"));
        holder.returnKey.setOnClickListener(view -> actions.onKeyMovement(key, "Devolucion"));
        holder.options.setOnClickListener(view -> actions.onKeyOptions(view, key));
        return recycled;
    }

    private static void setEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(1f);
        button.setTextColor(button.getContext().getColor(
            enabled ? R.color.white : R.color.disabled_text));
    }

    private static final class Holder {
        final TextView name;
        final TextView status;
        final TextView detail;
        final Button take;
        final Button returnKey;
        final View options;

        Holder(View row) {
            name = row.findViewById(R.id.keyName);
            status = row.findViewById(R.id.keyStatus);
            detail = row.findViewById(R.id.keyDetail);
            take = row.findViewById(R.id.btnTakeKey);
            returnKey = row.findViewById(R.id.btnReturnKey);
            options = row.findViewById(R.id.btnKeyOptions);
        }
    }
}
