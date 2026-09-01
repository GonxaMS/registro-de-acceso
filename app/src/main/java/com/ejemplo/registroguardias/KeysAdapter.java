package com.ejemplo.registroguardias;

import android.graphics.Color;
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
    }

    private final KeysActivity activity;
    private final List<KeyItem> keys;
    private final Actions actions;

    KeysAdapter(KeysActivity activity, List<KeyItem> keys, Actions actions) {
        this.activity = activity;
        this.keys = keys;
        this.actions = actions;
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
        boolean borrowed = "Prestada".equals(key.state);
        boolean pending = actions.isKeyMovementPending(key);
        holder.name.setText(key.name);
        holder.status.setText(borrowed ? "● Prestada" : "✓ Disponible");
        holder.status.setTextColor(Color.parseColor(borrowed ? "#B25A00" : "#1B7F4B"));
        holder.detail.setText(borrowed
            ? "La tiene " + key.holder + " desde " + key.date + " " + key.time
            : "Lista para retirar");

        setEnabled(holder.take, !pending && !borrowed);
        setEnabled(holder.returnKey, !pending && borrowed);
        holder.take.setOnClickListener(view -> actions.onKeyMovement(key, "Retiro"));
        holder.returnKey.setOnClickListener(view -> actions.onKeyMovement(key, "Devolucion"));
        holder.options.setOnClickListener(view -> actions.onKeyOptions(view, key));
        return recycled;
    }

    private static void setEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(1f);
        button.setTextColor(Color.parseColor(enabled ? "#FFFFFF" : "#8B98AA"));
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
