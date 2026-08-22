package com.ejemplo.registroguardias;

final class KeyItem {
    final String id;
    final String name;
    final String state;
    final String holder;
    final String date;
    final String time;

    KeyItem(String id, String name, String state, String holder, String date, String time) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.state = "Prestada".equals(state) ? "Prestada" : "Disponible";
        this.holder = holder == null ? "" : holder;
        this.date = date == null ? "" : date;
        this.time = time == null ? "" : time;
    }
}
