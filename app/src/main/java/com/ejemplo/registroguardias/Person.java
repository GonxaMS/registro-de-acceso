package com.ejemplo.registroguardias;

final class Person {
    final String id;
    final String name;
    final String state;
    final String lastMovement;
    final String date;

    Person(String id, String name, String state, String lastMovement, String date) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.state = "Dentro".equals(state) ? "Dentro" : "Fuera";
        this.lastMovement = lastMovement == null ? "" : lastMovement;
        this.date = date == null ? "" : date;
    }
}
