package com.ejemplo.registroguardias;

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

    String label() {
        if (keyOnly) return name + " (solo llaves)";
        return hidden ? name + " (oculto)" : name;
    }
}