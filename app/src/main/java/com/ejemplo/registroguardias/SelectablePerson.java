package com.ejemplo.registroguardias;

final class SelectablePerson {
    final String id;
    final String name;
    final boolean hidden;

    SelectablePerson(String id, String name, boolean hidden) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.hidden = hidden;
    }

    String label() {
        return hidden ? name + " (oculto)" : name;
    }
}
