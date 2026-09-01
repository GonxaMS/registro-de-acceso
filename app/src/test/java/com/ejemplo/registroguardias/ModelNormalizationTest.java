package com.ejemplo.registroguardias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ModelNormalizationTest {
    @Test
    public void personPreservesInsideState() {
        Person person = new Person("P0001", "ANA", "Dentro", "Ingreso", "01/09/2026");
        assertEquals("Dentro", person.state);
    }

    @Test
    public void personConvertsUnknownStateToOutside() {
        Person person = new Person("P0001", "ANA", "Desconocido", null, null);
        assertEquals("Fuera", person.state);
        assertEquals("", person.lastMovement);
        assertEquals("", person.date);
    }

    @Test
    public void personConvertsNullNameToEmptyText() {
        assertEquals("", new Person("P0001", null, null, null, null).name);
    }

    @Test
    public void keyPreservesBorrowedState() {
        KeyItem key = new KeyItem("K0001", "PORTON", "Prestada", "ANA", "01/09/2026", "09:00");
        assertEquals("Prestada", key.state);
        assertEquals("ANA", key.holder);
    }

    @Test
    public void keyConvertsUnknownStateToAvailable() {
        KeyItem key = new KeyItem("K0001", null, "Desconocido", null, null, null);
        assertEquals("Disponible", key.state);
        assertEquals("", key.name);
        assertEquals("", key.holder);
        assertEquals("", key.date);
        assertEquals("", key.time);
    }

    @Test
    public void selectablePersonLabelsVisiblePerson() {
        SelectablePerson person = new SelectablePerson("P0001", "ANA", false);
        assertEquals("ANA", person.label());
        assertFalse(person.hidden);
        assertFalse(person.keyOnly);
    }

    @Test
    public void selectablePersonLabelsHiddenPerson() {
        SelectablePerson person = new SelectablePerson("P0001", "ANA", true);
        assertEquals("ANA (oculto)", person.label());
        assertTrue(person.hidden);
    }

    @Test
    public void selectablePersonLabelsKeyOnlyPersonFirst() {
        SelectablePerson person = new SelectablePerson(null, null, true, true);
        assertEquals(" (solo llaves)", person.label());
        assertEquals("", person.id);
        assertEquals("", person.name);
        assertTrue(person.keyOnly);
    }
}
