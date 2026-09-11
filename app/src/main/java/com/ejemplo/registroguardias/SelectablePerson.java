package com.ejemplo.registroguardias;

import android.content.Context;

final class SelectablePerson {
    final String id;
    final String name;
    final boolean hidden;
    final boolean keyOnly;

    SelectablePerson(String id, String name, boolean hidden) {
        this(id, name, hidden, false);
    }

    SelectablePerson(String id, String name, boolean hidden, boolean keyOnly) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.hidden = hidden;
        this.keyOnly = keyOnly;
    }

    String label(Context context) {
        if (keyOnly) return context.getString(R.string.only_keys_label, name);
        return hidden ? context.getString(R.string.hidden_person_label, name) : name;
    }

    /** Kept for model tests; production UI uses {@link #label(Context)}. */
    String label() {
        if (keyOnly) return name + " (solo llaves)";
        return hidden ? name + " (oculto)" : name;
    }
}
