package com.ejemplo.registroguardias;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AccessActivity extends Activity implements PeopleAdapter.Actions {
    static final String PREFS_NAME = "registro_guardias";
    static final String USER_NAME_KEY = "operator_user_name";
    static final String SHEETS_WEB_URL = "https://docs.google.com/spreadsheets/d/1qh4aP9Hot-tAO5i7fEfzNTqQ2YK71xFkwgrm7uuDIcw/edit";

    private final List<Person> visiblePeople = new ArrayList<>();
    private final List<Person> hiddenPeople = new ArrayList<>();
    private final List<Person> removedPeople = new ArrayList<>();
    private final List<Person> filteredPeople = new ArrayList<>();
    private final SheetsClient sheets = new SheetsClient();

    private FirebaseFirestore database;
    private FirebaseAuth authentication;
    private ListenerRegistration peopleListener;
    private ListenerRegistration keysListener;
    private SharedPreferences preferences;
    private EditText search;
    private TextView count;
    private TextView borrowedKeys;
    private PeopleAdapter adapter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (currentUser().isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        applyWindowInsets();
        search = findViewById(R.id.inputSearch);
        count = findViewById(R.id.txtCount);
        borrowedKeys = findViewById(R.id.txtBorrowedKeys);
        adapter = new PeopleAdapter(this, filteredPeople, this);
        adapter.setToday(today());
        ((ListView) findViewById(R.id.listPeople)).setAdapter(adapter);
        ((TextView) findViewById(R.id.txtToday)).setText(
            new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "AR")).format(new Date())
        );
        findViewById(R.id.btnMenu).setOnClickListener(this::showMainMenu);
        findViewById(R.id.btnSheetsShortcut).setOnClickListener(view -> openSheets());
        findViewById(R.id.btnKeysShortcut).setOnClickListener(view ->
            startActivity(new Intent(this, KeysActivity.class)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filterPeople(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        count.setText("Conectando con Firebase…");
        authentication = FirebaseAuth.getInstance();
        database = FirebaseFirestore.getInstance();
        if (authentication.getCurrentUser() != null) startListeners();
        else authentication.signInAnonymously()
            .addOnSuccessListener(result -> startListeners())
            .addOnFailureListener(error -> showMessage("Sin acceso", "No se pudo iniciar Firebase: " + cleanError(error)));
    }

    private void openSheets() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHEETS_WEB_URL)));
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.mainRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void startListeners() {
        listenForPeople();
        listenForBorrowedKeys();
    }

    private void listenForPeople() {
        peopleListener = database.collection("personal").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showMessage("Error de Firebase", cleanError(error));
                return;
            }
            if (snapshot == null) return;
            visiblePeople.clear();
            hiddenPeople.clear();
            removedPeople.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Person person = new Person(
                    document.getId(), document.getString("nombre"), document.getString("estado"),
                    document.getString("ultimoMovimiento"), document.getString("fecha")
                );
                Boolean active = document.getBoolean("activo");
                Boolean removed = document.getBoolean("retirado");
                if (removed != null && removed) removedPeople.add(person);
                else if (active == null || active) visiblePeople.add(person);
                else hiddenPeople.add(person);
            }
            Comparator<Person> byName = (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name);
            Collections.sort(visiblePeople, byName);
            Collections.sort(hiddenPeople, byName);
            Collections.sort(removedPeople, byName);
            filterPeople(search.getText().toString());
        });
    }

    private void listenForBorrowedKeys() {
        keysListener = database.collection("llaves").addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                borrowedKeys.setText("Llaves prestadas: --");
                return;
            }
            int borrowed = 0;
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Boolean active = document.getBoolean("activo");
                if (active != null && !active) continue;
                if ("Prestada".equals(document.getString("estado"))) borrowed++;
            }
            borrowedKeys.setText("Llaves prestadas: " + borrowed);
        });
    }

    private void filterPeople(String query) {
        filteredPeople.clear();
        String normalized = query.trim().toLowerCase(Locale.getDefault());
        for (Person person : visiblePeople) {
            if (person.name.toLowerCase(Locale.getDefault()).contains(normalized)
                || person.id.toLowerCase(Locale.getDefault()).contains(normalized)) {
                filteredPeople.add(person);
            }
        }
        int inside = 0;
        for (Person person : filteredPeople) if ("Dentro".equals(person.state)) inside++;
        count.setText(filteredPeople.size()
            + (filteredPeople.size() == 1 ? " operario" : " operarios")
            + " · " + inside + " dentro");
        adapter.notifyDataSetChanged();
    }

    @Override public void onMovement(Person person, String type) {
        boolean entry = "Ingreso".equals(type);
        String date = today();
        if (entry && "Dentro".equals(person.state)) {
            showMessage("No se puede registrar", person.name + " ya está dentro");
            return;
        }
        if (!entry && "Fuera".equals(person.state)) {
            showMessage("No se puede registrar", "Primero debes registrar el ingreso de " + person.name);
            return;
        }
        if (entry && date.equals(person.date) && "Salida".equals(person.lastMovement)) {
            showMessage("No se puede registrar", person.name + " ya completó el ingreso y la salida de hoy");
            return;
        }

        String time = currentTime();
        String newState = entry ? "Dentro" : "Fuera";
        String registeredBy = currentUser();
        DocumentReference personReference = database.collection("personal").document(person.id);
        DocumentReference metaReference = database.collection("meta").document("config");
        toast("Registrando…");

        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(personReference);
            DocumentSnapshot config = transaction.get(metaReference);
            String state = current.getString("estado");
            String lastDate = current.getString("fecha");
            String lastMovement = current.getString("ultimoMovimiento");
            if (entry && "Dentro".equals(state)) throw new IllegalStateException(person.name + " ya está dentro");
            if (!entry && !"Dentro".equals(state)) {
                throw new IllegalStateException("Primero debes registrar el ingreso de " + person.name);
            }
            if (entry && date.equals(lastDate) && "Salida".equals(lastMovement)) {
                throw new IllegalStateException(person.name + " ya completó el ingreso y la salida de hoy");
            }

            long next = nextNumber(config, "siguienteMovimiento");
            String movementId = String.format(Locale.US, "M%06d", next);
            DocumentReference movementReference = database.collection("movimientos").document(movementId);

            Map<String, Object> personUpdate = new HashMap<>();
            personUpdate.put("estado", newState);
            personUpdate.put("ultimoMovimiento", type);
            personUpdate.put("fecha", date);
            personUpdate.put("hora", time);
            personUpdate.put("actualizado", FieldValue.serverTimestamp());
            transaction.update(personReference, personUpdate);

            Map<String, Object> movement = baseMovement(movementId, person, type, date, time, registeredBy);
            transaction.set(movementReference, movement);
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimiento", next + 1), SetOptions.merge());
            return movementId;
        }).addOnSuccessListener(ignored -> {
            showMessage("Registro correcto", type + " registrado a las " + time + " por " + registeredBy);
            sheets.mirrorMovement(person, time, type, date);
        }).addOnFailureListener(error -> showMessage("No se puede registrar", cleanError(error)));
    }

    private static Map<String, Object> baseMovement(String id, Person person, String type,
                                                     String date, String time, String user) {
        Map<String, Object> movement = new HashMap<>();
        movement.put("movimientoId", id);
        movement.put("personalId", person.id);
        movement.put("nombre", person.name);
        movement.put("movimiento", type);
        movement.put("fecha", date);
        movement.put("hora", time);
        movement.put("creado", FieldValue.serverTimestamp());
        movement.put("usuario", user);
        return movement;
    }

    @Override public void onOptions(View anchor, Person person) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Modificar hora");
        menu.getMenu().add("Quitar ingreso de hoy");
        menu.getMenu().add("Quitar salida de hoy");
        menu.getMenu().add("Quitar operario");
        menu.getMenu().add("Ocultar operario");
        menu.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            if (option.startsWith("Modificar")) loadTodayMovements(person);
            else if (option.startsWith("Quitar ingreso")) startCancellation(person, "Ingreso");
            else if (option.startsWith("Quitar salida")) startCancellation(person, "Salida");
            else if (option.startsWith("Quitar operario")) confirmRemove(person);
            else confirmHide(person);
            return true;
        });
        menu.show();
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Agregar operario");
        menu.getMenu().add("Mostrar operarios ocultos");
        menu.getMenu().add("Registro de llaves");
        menu.getMenu().add("Cambiar usuario");
        menu.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            if (option.startsWith("Agregar")) showAddDialog();
            else if (option.startsWith("Mostrar")) showHiddenPeople();
            else if (option.startsWith("Registro")) startActivity(new Intent(this, KeysActivity.class));
            else showChangeUserDialog();
            return true;
        });
        menu.show();
    }

    private void showAddDialog() {
        EditText input = dialogInput("Nombre completo", "");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Agregar operario")
            .setView(padded(input))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError("Escribe un nombre");
                    return;
                }
                for (Person person : visiblePeople) {
                    if (person.name.equalsIgnoreCase(name)) {
                        input.setError("Este operario ya existe");
                        return;
                    }
                }
                dialog.dismiss();
                addPerson(name);
            }));
        dialog.show();
    }

    private void addPerson(String name) {
        for (Person person : hiddenPeople) {
            if (person.name.equalsIgnoreCase(name)) {
                database.collection("personal").document(person.id).update("activo", true, "retirado", false)
                    .addOnSuccessListener(ignored -> {
                        toast(person.name + " volvió a la lista");
                        sheets.syncPerson("mostrar", person);
                    })
                    .addOnFailureListener(error -> showMessage("No se pudo mostrar", cleanError(error)));
                return;
            }
        }

        for (Person person : removedPeople) {
            if (person.name.equalsIgnoreCase(name)) {
                showMessage("Operario quitado",
                    person.name + " fue quitado de la lista y no puede restaurarse desde la app.");
                return;
            }
        }

        DocumentReference metaReference = database.collection("meta").document("config");
        database.runTransaction(transaction -> {
            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteId");
            String id = String.format(Locale.US, "P%04d", next);
            Person person = new Person(id, name, "Fuera", "", "");
            Map<String, Object> data = new HashMap<>();
            data.put("nombre", name);
            data.put("estado", "Fuera");
            data.put("ultimoMovimiento", "");
            data.put("fecha", "");
            data.put("hora", "");
            data.put("activo", true);
            data.put("retirado", false);
            data.put("actualizado", FieldValue.serverTimestamp());
            transaction.set(database.collection("personal").document(id), data);
            transaction.set(metaReference, Collections.singletonMap("siguienteId", next + 1), SetOptions.merge());
            return person;
        }).addOnSuccessListener(person -> {
            toast(person.name + " fue agregado");
            sheets.syncPerson("agregar", person);
        }).addOnFailureListener(error -> showMessage("No se pudo agregar", cleanError(error)));
    }

    private void confirmHide(Person person) {
        if ("Dentro".equals(person.state)) {
            showMessage("No se puede ocultar", "Primero debes registrar la salida de " + person.name);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Ocultar operario")
            .setMessage("¿Quieres ocultar a " + person.name + " de la lista?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, ocultar", (dialog, which) ->
                database.collection("personal").document(person.id).update("activo", false, "retirado", false)
                    .addOnSuccessListener(ignored -> {
                        toast("Operario oculto");
                        sheets.syncPerson("ocultar", person);
                    })
                    .addOnFailureListener(error -> showMessage("No se pudo ocultar", cleanError(error))))
            .show();
    }

    private void showHiddenPeople() {
        if (hiddenPeople.isEmpty()) {
            toast("No hay operarios ocultos");
            return;
        }
        String[] names = new String[hiddenPeople.size()];
        for (int index = 0; index < names.length; index++) names[index] = hiddenPeople.get(index).name;
        new AlertDialog.Builder(this)
            .setTitle("Mostrar operarios ocultos")
            .setItems(names, (dialog, index) -> {
                Person person = hiddenPeople.get(index);
                database.collection("personal").document(person.id).update("activo", true, "retirado", false)
                    .addOnSuccessListener(ignored -> {
                        toast(person.name + " volvió a la lista");
                        sheets.syncPerson("mostrar", person);
                    })
                    .addOnFailureListener(error -> showMessage("No se pudo mostrar", cleanError(error)));
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void confirmRemove(Person person) {
        if ("Dentro".equals(person.state)) {
            showMessage("No se puede quitar", "Primero debes registrar la salida de " + person.name);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Quitar operario")
            .setMessage("Quieres quitar a " + person.name
                + " de la lista? Esta accion no se podra restaurar desde la app.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Si, quitar", (dialog, which) ->
                database.collection("personal").document(person.id)
                    .update("activo", false, "retirado", true, "actualizado", FieldValue.serverTimestamp())
                    .addOnSuccessListener(ignored -> toast("Operario quitado"))
                    .addOnFailureListener(error -> showMessage("No se pudo quitar", cleanError(error))))
            .show();
    }

    private void showChangeUserDialog() {
        EditText input = dialogInput("Nombre del usuario", currentUser());
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Cambiar usuario")
            .setMessage("Los próximos movimientos quedarán registrados con este nombre.")
            .setView(padded(input))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.length() < 2) {
                    input.setError("Escribe el nombre del usuario");
                    return;
                }
                preferences.edit().putString(USER_NAME_KEY, name).apply();
                dialog.dismiss();
                toast("Usuario cambiado a " + name);
            }));
        dialog.show();
    }

    private static final class DailyMovements {
        DocumentSnapshot entry;
        DocumentSnapshot exit;
    }

    private void loadTodayMovements(Person person) {
        String date = today();
        toast("Buscando horarios de hoy…");
        database.collection("movimientos").whereEqualTo("personalId", person.id).get()
            .addOnSuccessListener(snapshot -> {
                DailyMovements daily = new DailyMovements();
                List<DocumentSnapshot> documents = snapshot.getDocuments();
                java.util.HashSet<String> replaced = new java.util.HashSet<>();
                for (DocumentSnapshot document : documents) {
                    if (!date.equals(document.getString("fecha"))) continue;
                    String replacedId = document.getString("reemplazaA");
                    String cancelledId = document.getString("anulaA");
                    if (replacedId != null) replaced.add(replacedId);
                    if (cancelledId != null) replaced.add(cancelledId);
                }
                for (DocumentSnapshot document : documents) {
                    if (!date.equals(document.getString("fecha")) || replaced.contains(document.getId())) continue;
                    String type = document.getString("movimiento");
                    if ("Ingreso".equals(type)) daily.entry = newer(daily.entry, document);
                    else if ("Salida".equals(type)) daily.exit = newer(daily.exit, document);
                }
                showMovementChoice(person, daily, date);
            })
            .addOnFailureListener(error -> showMessage("No se pudieron leer los horarios", cleanError(error)));
    }

    private void startCancellation(Person person, String type) {
        String date = today();
        toast("Verificando registro de hoy…");
        database.collection("movimientos").whereEqualTo("personalId", person.id).get()
            .addOnSuccessListener(snapshot -> {
                DailyMovements daily = effectiveMovements(snapshot.getDocuments(), date);
                DocumentSnapshot target = "Ingreso".equals(type) ? daily.entry : daily.exit;
                if (target == null) {
                    showMessage("No se puede quitar", "No hay " + type.toLowerCase(Locale.getDefault())
                        + " registrado hoy para " + person.name);
                    return;
                }
                if ("Ingreso".equals(type) && daily.exit != null) {
                    showMessage("No se puede quitar el ingreso",
                        "Primero debes quitar la salida de hoy, porque depende de ese ingreso.");
                    return;
                }
                if ("Salida".equals(type) && daily.entry == null) {
                    showMessage("No se puede quitar la salida", "No se encontró el ingreso de hoy.");
                    return;
                }
                confirmCancellation(person, type, target, daily, date);
            })
            .addOnFailureListener(error -> showMessage("No se pudieron leer los horarios", cleanError(error)));
    }

    private DailyMovements effectiveMovements(List<DocumentSnapshot> documents, String date) {
        DailyMovements daily = new DailyMovements();
        java.util.HashSet<String> replaced = new java.util.HashSet<>();
        for (DocumentSnapshot document : documents) {
            if (!date.equals(document.getString("fecha"))) continue;
            String replacedId = document.getString("reemplazaA");
            String cancelledId = document.getString("anulaA");
            if (replacedId != null) replaced.add(replacedId);
            if (cancelledId != null) replaced.add(cancelledId);
        }
        for (DocumentSnapshot document : documents) {
            if (!date.equals(document.getString("fecha")) || replaced.contains(document.getId())) continue;
            String type = document.getString("movimiento");
            if ("Ingreso".equals(type)) daily.entry = newer(daily.entry, document);
            else if ("Salida".equals(type)) daily.exit = newer(daily.exit, document);
        }
        return daily;
    }

    private void confirmCancellation(Person person, String type, DocumentSnapshot target,
                                     DailyMovements daily, String date) {
        new AlertDialog.Builder(this)
            .setTitle("Quitar " + type.toLowerCase(Locale.getDefault()))
            .setMessage("¿Quieres quitar el " + type.toLowerCase(Locale.getDefault()) + " de "
                + person.name + " registrado a las " + shownTime(target) + "?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, quitar", (dialog, which) ->
                saveCancellation(person, type, target, daily, date))
            .show();
    }

    private void saveCancellation(Person person, String type, DocumentSnapshot target,
                                  DailyMovements daily, String date) {
        toast("Quitando " + type.toLowerCase(Locale.getDefault()) + "…");
        DocumentReference metaReference = database.collection("meta").document("config");
        DocumentReference personReference = database.collection("personal").document(person.id);
        String registeredBy = currentUser();
        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(personReference);
            if ("Ingreso".equals(type) && !"Dentro".equals(current.getString("estado"))) {
                throw new IllegalStateException("El ingreso ya no puede quitarse");
            }
            if ("Salida".equals(type) && (!"Fuera".equals(current.getString("estado"))
                || !"Salida".equals(current.getString("ultimoMovimiento")))) {
                throw new IllegalStateException("La salida ya no puede quitarse");
            }
            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteMovimiento");
            String cancellationId = String.format(Locale.US, "M%06d", next);
            Map<String, Object> cancellation = baseMovement(cancellationId, person,
                "Anulacion" + type, date, currentTime(), registeredBy);
            cancellation.put("anulaA", target.getId());
            transaction.set(database.collection("movimientos").document(cancellationId), cancellation);
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimiento", next + 1), SetOptions.merge());

            Map<String, Object> personUpdate = new HashMap<>();
            if ("Ingreso".equals(type)) {
                personUpdate.put("estado", "Fuera");
                personUpdate.put("ultimoMovimiento", "");
                personUpdate.put("fecha", "");
                personUpdate.put("hora", "");
            } else {
                personUpdate.put("estado", "Dentro");
                personUpdate.put("ultimoMovimiento", "Ingreso");
                personUpdate.put("fecha", date);
                personUpdate.put("hora", shownTime(daily.entry));
            }
            personUpdate.put("actualizado", FieldValue.serverTimestamp());
            transaction.update(personReference, personUpdate);
            return cancellationId;
        }).addOnSuccessListener(ignored -> {
            showMessage("Registro quitado", type + " de hoy fue quitado por " + registeredBy + ".");
            sheets.cancelMovement(person, type, date);
        }).addOnFailureListener(error -> showMessage("No se pudo quitar", cleanError(error)));
    }
    private static DocumentSnapshot newer(DocumentSnapshot current, DocumentSnapshot candidate) {
        if (current == null) return candidate;
        Timestamp currentTime = current.getTimestamp("creado");
        Timestamp candidateTime = candidate.getTimestamp("creado");
        if (currentTime != null && candidateTime != null) {
            return candidateTime.compareTo(currentTime) > 0 ? candidate : current;
        }
        return candidate.getId().compareTo(current.getId()) > 0 ? candidate : current;
    }

    private void showMovementChoice(Person person, DailyMovements daily, String date) {
        if (daily.entry == null && daily.exit == null) {
            showMessage("Sin horarios para modificar",
                "Todavía no hay ingresos ni salidas registrados hoy para " + person.name);
            return;
        }
        if (daily.entry != null && daily.exit != null) {
            String[] options = {
                "Hora de ingreso · " + shownTime(daily.entry),
                "Hora de salida · " + shownTime(daily.exit)
            };
            new AlertDialog.Builder(this)
                .setTitle("Modificar hora de " + person.name)
                .setItems(options, (dialog, index) -> {
                    if (index == 0) openTimePicker(person, "Ingreso", daily.entry, daily, date);
                    else openTimePicker(person, "Salida", daily.exit, daily, date);
                })
                .setNegativeButton("Cancelar", null)
                .show();
            return;
        }
        String type = daily.entry != null ? "Ingreso" : "Salida";
        DocumentSnapshot movement = daily.entry != null ? daily.entry : daily.exit;
        openTimePicker(person, type, movement, daily, date);
    }

    private void openTimePicker(Person person, String type, DocumentSnapshot movement,
                                DailyMovements daily, String date) {
        int initial = minutes(shownTime(movement));
        if (initial < 0) {
            java.util.Calendar now = java.util.Calendar.getInstance();
            initial = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        }
        new TimePickerDialog(this, (picker, hour, minute) -> {
            int selected = hour * 60 + minute;
            if ("Ingreso".equals(type) && daily.exit != null
                && selected >= minutes(shownTime(daily.exit))) {
                showMessage("Hora incorrecta", "El ingreso debe ser anterior a la salida");
                return;
            }
            if ("Salida".equals(type) && daily.entry != null
                && selected <= minutes(shownTime(daily.entry))) {
                showMessage("Hora incorrecta", "La salida debe ser posterior al ingreso");
                return;
            }
            String newTime = String.format(Locale.US, "%02d:%02d", hour, minute);
            confirmTimeChange(person, type, movement, date, newTime);
        }, initial / 60, initial % 60, true).show();
    }

    private void confirmTimeChange(Person person, String type, DocumentSnapshot previous,
                                   String date, String newTime) {
        new AlertDialog.Builder(this)
            .setTitle("Confirmar nueva hora")
            .setMessage(type + " de " + person.name + " a las " + newTime)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (dialog, which) ->
                saveCorrectedTime(person, type, previous, date, newTime))
            .show();
    }

    private void saveCorrectedTime(Person person, String type, DocumentSnapshot previous,
                                   String date, String newTime) {
        toast("Guardando corrección…");
        DocumentReference metaReference = database.collection("meta").document("config");
        String registeredBy = currentUser();
        database.runTransaction(transaction -> {
            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteMovimiento");
            String correctionId = String.format(Locale.US, "M%06d", next);
            DocumentReference correctionReference = database.collection("movimientos").document(correctionId);
            Map<String, Object> correction = baseMovement(
                correctionId, person, type, date, newTime, registeredBy
            );
            correction.put("esCorreccion", true);
            correction.put("reemplazaA", previous.getId());
            transaction.set(correctionReference, correction);
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimiento", next + 1), SetOptions.merge());
            if (date.equals(person.date) && type.equals(person.lastMovement)) {
                transaction.update(database.collection("personal").document(person.id),
                    "hora", newTime, "actualizado", FieldValue.serverTimestamp());
            }
            return correctionId;
        }).addOnSuccessListener(ignored -> {
            showMessage("Hora modificada", type + " actualizado a las " + newTime + " por "
                + registeredBy + ". El registro anterior quedó reemplazado.");
            sheets.mirrorMovement(person, newTime, type, date);
        }).addOnFailureListener(error -> showMessage("No se pudo modificar la hora", cleanError(error)));
    }

    private static String shownTime(DocumentSnapshot document) {
        String value = document.getString("hora");
        return value == null || value.length() < 5 ? "--:--" : value.substring(0, 5);
    }

    private static int minutes(String value) {
        try {
            String[] parts = value.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String currentUser() {
        return preferences.getString(USER_NAME_KEY, "").trim();
    }

    private static long nextNumber(DocumentSnapshot config, String field) {
        Long stored = config.getLong(field);
        return stored == null ? 1L : stored;
    }

    private static String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanError(Exception error) {
        String message = error.getMessage();
        return message == null ? "Operación rechazada"
            : message.replace("java.lang.IllegalStateException: ", "");
    }

    private static String today() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
    }

    private static String currentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private EditText dialogInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        return input;
    }

    private LinearLayout padded(View content) {
        LinearLayout container = new LinearLayout(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(padding, 0, padding, 0);
        container.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return container;
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
            .setTitle(title).setMessage(message).setPositiveButton("Aceptar", null).show());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        if (peopleListener != null) peopleListener.remove();
        if (keysListener != null) keysListener.remove();
        sheets.close();
        super.onDestroy();
    }
}
