package com.ejemplo.registroguardias;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

final class PeopleAdapter extends BaseAdapter {
    interface Actions {
        void onMovement(Person person, String type);
        void onOptions(View anchor, Person person);
    }

    private final AccessActivity activity;
    private final List<Person> people;
    private final Actions actions;
    private String today = "";

    PeopleAdapter(AccessActivity activity, List<Person> people, Actions actions) {
        this.activity = activity;
        this.people = people;
        this.actions = actions;
    }

    void setToday(String value) {
        today = value;
    }

    @Override public int getCount() { return people.size(); }
    @Override public Person getItem(int position) { return people.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        Holder holder;
        if (recycled == null) {
            recycled = LayoutInflater.from(activity).inflate(R.layout.item_person, parent, false);
            holder = new Holder(recycled);
            recycled.setTag(holder);
        } else {
            holder = (Holder) recycled.getTag();
        }

        Person person = getItem(position);
        boolean inside = "Dentro".equals(person.state);
        boolean completed = !inside && today.equals(person.date) && "Salida".equals(person.lastMovement);

        holder.name.setText(person.name);
        holder.status.setText(inside ? "● Dentro" : completed ? "✓ Completado" : "● Fuera");
        holder.status.setTextColor(Color.parseColor(inside ? "#1B7F4B" : completed ? "#1769AA" : "#5E6C84"));

        setEnabled(holder.entry, !inside && !completed);
        setEnabled(holder.exit, inside);
        holder.entry.setOnClickListener(view -> actions.onMovement(person, "Ingreso"));
        holder.exit.setOnClickListener(view -> actions.onMovement(person, "Salida"));
        holder.options.setOnClickListener(view -> actions.onOptions(view, person));
        return recycled;
    }

    private static void setEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
    }

    private static final class Holder {
        final TextView name;
        final TextView status;
        final Button entry;
        final Button exit;
        final View options;

        Holder(View row) {
            name = row.findViewById(R.id.personName);
            status = row.findViewById(R.id.personStatus);
            entry = row.findViewById(R.id.btnEntry);
            exit = row.findViewById(R.id.btnExit);
            options = row.findViewById(R.id.btnEditTime);
        }
    }
}
