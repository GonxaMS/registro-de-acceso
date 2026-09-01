package com.ejemplo.registroguardias;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCorrectionsActivity extends Activity {
    private static final String ADMIN_USER = "Admin";
    private static final Locale LOCALE = new Locale("es", "AR");

    private final List<AdminPerson> people = new ArrayList<>();
    private FirebaseFirestore database;
    private ListenerRegistration peopleListener;
    private AdminPerson selectedPerson;
    private DailyMovements daily = new DailyMovements();
    private String selectedDate;
    private boolean loading;

    private TextView subtitle;
    private TextView personButton;
    private TextView dateButton;
    private TextView dayTitle;
    private TextView entryText;
    private TextView exitText;
    private Button entryButton;
    private Button exitButton;
    private Button removeEntryButton;
    private Button removeExitButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_corrections);
        applyWindowInsets();
        subtitle = findViewById(R.id.txtAdminSubtitle);
        personButton = findViewById(R.id.btnAdminPerson);
        dateButton = findViewById(R.id.btnAdminDate);
        dayTitle = findViewById(R.id.txtAdminDayTitle);
        entryText = findViewById(R.id.txtAdminEntry);
        exitText = findViewById(R.id.txtAdminExit);
        entryButton = findViewById(R.id.btnAdminEntry);
        exitButton = findViewById(R.id.btnAdminExit);
        removeEntryButton = findViewById(R.id.btnAdminRemoveEntry);
        removeExitButton = findViewById(R.id.btnAdminRemoveExit);
        findViewById(R.id.btnAdminBack).setOnClickListener(view -> finish());
        personButton.setOnClickListener(view -> choosePerson());
        dateButton.setOnClickListener(view -> chooseDate());
        entryButton.setOnClickListener(view -> chooseTime("Ingreso"));
        exitButton.setOnClickListener(view -> chooseTime("Salida"));
        removeEntryButton.setOnClickListener(view -> confirmCancellation("Ingreso", daily.entry));
        removeExitButton.setOnClickListener(view -> confirmCancellation("Salida", daily.exit));

        selectedDate = today();
        updateSelectionLabels();
        setActionsEnabled(false);
        database = FirebaseFirestore.getInstance();
        AdminAccess.check(database, (allowed, uid) -> {
            if (!allowed) {
                Toast.makeText(this, "Este dispositivo no tiene permiso de administrador\nUID: " + uid,
                    Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            listenForPeople();
        });
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.adminCorrectionsRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void listenForPeople() {
        subtitle.setText("Cargando operarios…");
        peopleListener = database.collection("personal").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showMessage("No se pudo cargar", friendlyError(error));
                return;
            }
            if (snapshot == null) return;
            String selectedId = selectedPerson == null ? "" : selectedPerson.id;
            people.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Boolean removed = document.getBoolean("retirado");
                if (removed != null && removed) continue;
                people.add(new AdminPerson(
                    document.getId(), document.getString("nombre"), document.getString("estado"),
                    document.getString("ultimoMovimiento"), document.getString("fecha"),
                    document.getString("hora")
                ));
            }
            Collections.sort(people, (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));

            selectedPerson = findPerson(selectedId);
            if (selectedPerson == null) selectedPerson = firstPendingPerson();
            if (selectedPerson == null && !people.isEmpty()) selectedPerson = people.get(0);
            if (selectedPerson != null && isPending(selectedPerson)) selectedDate = selectedPerson.date;

            int pending = 0;
            for (AdminPerson person : people) if (isPending(person)) pending++;
            subtitle.setText(pending == 0 ? "Sin jornadas pendientes"
                : pending + (pending == 1 ? " jornada pendiente" : " jornadas pendientes"));
            updateSelectionLabels();
            loadDailyMovements();
        });
    }

    private AdminPerson findPerson(String id) {
        for (AdminPerson person : people) if (person.id.equals(id)) return person;
        return null;
    }

    private AdminPerson firstPendingPerson() {
        for (AdminPerson person : people) if (isPending(person)) return person;
        return null;
    }

    private boolean isPending(AdminPerson person) {
        return "Dentro".equals(person.state) && isPreviousDate(person.date);
    }

    private void choosePerson() {
        if (people.isEmpty()) {
            showMessage("Sin operarios", "No hay operarios disponibles para corregir.");
            return;
        }
        String[] options = new String[people.size()];
        for (int index = 0; index < people.size(); index++) {
            AdminPerson person = people.get(index);
            options[index] = person.name + (isPending(person)
                ? "  ·  pendiente desde " + person.date : "");
        }
        new AlertDialog.Builder(this)
            .setTitle("Seleccionar operario")
            .setItems(options, (dialog, index) -> {
                selectedPerson = people.get(index);
                if (isPending(selectedPerson)) selectedDate = selectedPerson.date;
                updateSelectionLabels();
                loadDailyMovements();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void chooseDate() {
        Calendar initial = calendarFor(selectedDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.clear();
            chosen.set(year, month, day);
            selectedDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(chosen.getTime());
            updateSelectionLabels();
            loadDailyMovements();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle("Fecha del movimiento");
        dialog.show();
    }

    private void updateSelectionLabels() {
        personButton.setText(selectedPerson == null ? "Seleccionar operario" : selectedPerson.name);
        dateButton.setText(readableDate(selectedDate));
        dayTitle.setText(selectedPerson == null ? "Registro del día"
            : "Registro de " + selectedPerson.name);
    }

    private void loadDailyMovements() {
        if (selectedPerson == null || !isValidDate(selectedDate)) {
            daily = new DailyMovements();
            renderDaily();
            return;
        }
        loading = true;
        setActionsEnabled(false);
        entryText.setText("Ingreso  buscando…");
        exitText.setText("Salida     buscando…");
        database.collection("movimientos").whereEqualTo("personalId", selectedPerson.id).get()
            .addOnSuccessListener(snapshot -> {
                daily = effectiveMovements(snapshot.getDocuments(), selectedDate);
                if (daily.entry == null && selectedDate.equals(selectedPerson.date)
                    && "Dentro".equals(selectedPerson.state)
                    && "Ingreso".equals(selectedPerson.lastMovement)
                    && validTime(selectedPerson.time)) {
                    daily.fallbackEntryTime = selectedPerson.time.substring(0, 5);
                }
                loading = false;
                renderDaily();
            })
            .addOnFailureListener(error -> {
                loading = false;
                renderDaily();
                showMessage("No se pudo leer el registro", friendlyError(error));
            });
    }

    private DailyMovements effectiveMovements(List<DocumentSnapshot> documents, String date) {
        DailyMovements result = new DailyMovements();
        HashSet<String> replaced = new HashSet<>();
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
            if ("Ingreso".equals(type)) result.entry = newer(result.entry, document);
            else if ("Salida".equals(type)) result.exit = newer(result.exit, document);
        }
        return result;
    }

    private void renderDaily() {
        String entry = daily.entry == null ? daily.fallbackEntryTime : shownTime(daily.entry);
        String exit = daily.exit == null ? "" : shownTime(daily.exit);
        entryText.setText(entry.isEmpty() ? "Ingreso  sin registrar" : "Ingreso  " + entry
            + (daily.entry == null
                ? "  ·  recuperado del estado  ·  sin ID"
                : "  ·  ID " + daily.entry.getId()));
        exitText.setText(exit.isEmpty() ? "Salida     sin registrar"
            : "Salida     " + exit + "  ·  ID " + daily.exit.getId());
        entryButton.setText(daily.entry == null ? "Agregar ingreso" : "Corregir ingreso");
        exitButton.setText(daily.exit == null ? "Agregar salida" : "Corregir salida");
        setActionsEnabled(!loading && selectedPerson != null && isValidDate(selectedDate));
    }

    private void setActionsEnabled(boolean enabled) {
        entryButton.setEnabled(enabled);
        exitButton.setEnabled(enabled);
        removeEntryButton.setEnabled(enabled && daily.entry != null);
        removeExitButton.setEnabled(enabled && daily.exit != null);
    }

    private void chooseTime(String type) {
        if (loading || selectedPerson == null) return;
        DocumentSnapshot existing = "Ingreso".equals(type) ? daily.entry : daily.exit;
        String existingTime = existing == null ? "" : shownTime(existing);
        if (existingTime.isEmpty() && "Ingreso".equals(type)) existingTime = daily.fallbackEntryTime;
        int initial = minutes(existingTime);
        if (initial < 0) {
            Calendar now = Calendar.getInstance();
            initial = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        }
        new TimePickerDialog(this, (picker, hour, minute) -> {
            String time = String.format(Locale.US, "%02d:%02d", hour, minute);
            if (!validOrder(type, time)) return;
            confirmSave(type, existing, time);
        }, initial / 60, initial % 60, true).show();
    }

    private boolean validOrder(String type, String time) {
        int selected = minutes(time);
        String entry = daily.entry == null ? daily.fallbackEntryTime : shownTime(daily.entry);
        String exit = daily.exit == null ? "" : shownTime(daily.exit);
        if ("Ingreso".equals(type) && !exit.isEmpty() && selected >= minutes(exit)) {
            showMessage("Hora incorrecta", "El ingreso debe ser anterior a la salida.");
            return false;
        }
        if ("Salida".equals(type) && !entry.isEmpty() && selected <= minutes(entry)) {
            showMessage("Hora incorrecta", "La salida debe ser posterior al ingreso.");
            return false;
        }
        return true;
    }

    private void confirmSave(String type, DocumentSnapshot previous, String time) {
        String action = previous == null ? "Agregar" : "Corregir";
        String detail = action + " " + type.toLowerCase(LOCALE) + " de " + selectedPerson.name
            + " el " + selectedDate + " a las " + time + ".";
        if ("Salida".equals(type) && isPending(selectedPerson)
            && selectedDate.equals(selectedPerson.date)) {
            detail += " Esto cerrará la jornada pendiente y permitirá un nuevo ingreso.";
        }
        new AlertDialog.Builder(this)
            .setTitle(action + " " + type.toLowerCase(LOCALE))
            .setMessage(detail)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (dialog, which) -> saveMovement(type, previous, time))
            .show();
    }

    private void saveMovement(String type, DocumentSnapshot previous, String time) {
        setActionsEnabled(false);
        toast("Guardando corrección…");
        AdminPerson chosenPerson = selectedPerson;
        String chosenDate = selectedDate;
        DocumentReference personReference = database.collection("personal").document(chosenPerson.id);
        DocumentReference metaReference = database.collection("meta").document("config");

        database.runTransaction(transaction -> {
            DocumentSnapshot currentPerson = transaction.get(personReference);
            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteMovimiento");
            String movementId = String.format(Locale.US, "M%06d", next);
            Person movementPerson = new Person(chosenPerson.id, chosenPerson.name,
                currentPerson.getString("estado"), currentPerson.getString("ultimoMovimiento"),
                currentPerson.getString("fecha"));
            Map<String, Object> movement = baseMovement(
                movementId, movementPerson, type, chosenDate, time, ADMIN_USER);
            movement.put("esAjusteAdmin", true);
            if (previous != null) {
                movement.put("esCorreccion", true);
                movement.put("reemplazaA", previous.getId());
            }
            transaction.set(database.collection("movimientos").document(movementId), movement);
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimiento", next + 1), SetOptions.merge());

            String currentDate = value(currentPerson.getString("fecha"));
            String currentMovement = value(currentPerson.getString("ultimoMovimiento"));
            String currentState = value(currentPerson.getString("estado"));
            boolean updateCurrent = false;
            if (chosenDate.equals(currentDate)) {
                if ("Salida".equals(type) && "Dentro".equals(currentState)) updateCurrent = true;
                else if (type.equals(currentMovement)) updateCurrent = true;
            }
            if (updateCurrent) {
                Map<String, Object> update = new HashMap<>();
                update.put("estado", "Ingreso".equals(type) ? "Dentro" : "Fuera");
                update.put("ultimoMovimiento", type);
                update.put("fecha", chosenDate);
                update.put("hora", time);
                update.put("actualizado", FieldValue.serverTimestamp());
                transaction.update(personReference, update);
            }
            return new SaveResult(movementId);
        }).addOnSuccessListener(result -> {
            toast(type + " del " + chosenDate + " guardado a las " + time);
            loadDailyMovements();
        }).addOnFailureListener(error -> {
            renderDaily();
            showMessage("No se pudo guardar", friendlyError(error));
        });
    }

    private void confirmCancellation(String type, DocumentSnapshot target) {
        if (loading || selectedPerson == null || target == null) return;
        if ("Ingreso".equals(type) && daily.exit != null) {
            showMessage("No se puede quitar el ingreso",
                "Primero debes quitar la salida de ese día, porque depende del ingreso.");
            return;
        }
        String detail = "Quitar el " + type.toLowerCase(LOCALE) + " de "
            + selectedPerson.name + " del " + selectedDate + " a las " + shownTime(target)
            + ". El movimiento original conservará su ID y quedará anulado por un nuevo movimiento.";
        new AlertDialog.Builder(this)
            .setTitle("Quitar " + type.toLowerCase(LOCALE))
            .setMessage(detail)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, quitar", (dialog, which) -> saveCancellation(type, target))
            .show();
    }

    private void saveCancellation(String type, DocumentSnapshot target) {
        setActionsEnabled(false);
        toast("Quitando " + type.toLowerCase(LOCALE) + "…");
        AdminPerson chosenPerson = selectedPerson;
        String chosenDate = selectedDate;
        DocumentReference personReference = database.collection("personal").document(chosenPerson.id);
        DocumentReference metaReference = database.collection("meta").document("config");

        database.runTransaction(transaction -> {
            DocumentSnapshot currentPerson = transaction.get(personReference);
            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteMovimiento");
            String cancellationId = String.format(Locale.US, "M%06d", next);
            Person movementPerson = new Person(chosenPerson.id, chosenPerson.name,
                currentPerson.getString("estado"), currentPerson.getString("ultimoMovimiento"),
                currentPerson.getString("fecha"));
            Map<String, Object> cancellation = baseMovement(cancellationId, movementPerson,
                "Anulacion" + type, chosenDate, shownTime(target), ADMIN_USER);
            cancellation.put("esAjusteAdmin", true);
            cancellation.put("anulaA", target.getId());
            transaction.set(database.collection("movimientos").document(cancellationId), cancellation);
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimiento", next + 1), SetOptions.merge());

            String currentDate = value(currentPerson.getString("fecha"));
            String currentMovement = value(currentPerson.getString("ultimoMovimiento"));
            boolean updateCurrent = chosenDate.equals(currentDate) && type.equals(currentMovement);
            if (updateCurrent) {
                Map<String, Object> update = new HashMap<>();
                if ("Ingreso".equals(type)) {
                    update.put("estado", "Fuera");
                    update.put("ultimoMovimiento", "");
                    update.put("fecha", "");
                    update.put("hora", "");
                } else {
                    String entryTime = daily.entry == null ? daily.fallbackEntryTime : shownTime(daily.entry);
                    update.put("estado", "Dentro");
                    update.put("ultimoMovimiento", "Ingreso");
                    update.put("fecha", chosenDate);
                    update.put("hora", entryTime);
                }
                update.put("actualizado", FieldValue.serverTimestamp());
                transaction.update(personReference, update);
            }
            return new SaveResult(cancellationId);
        }).addOnSuccessListener(result -> {
            toast(type + " del " + chosenDate + " fue quitado");
            loadDailyMovements();
        }).addOnFailureListener(error -> {
            renderDaily();
            showMessage("No se pudo quitar", friendlyError(error));
        });
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

    private static DocumentSnapshot newer(DocumentSnapshot current, DocumentSnapshot candidate) {
        if (current == null) return candidate;
        Timestamp currentTime = current.getTimestamp("creado");
        Timestamp candidateTime = candidate.getTimestamp("creado");
        if (currentTime != null && candidateTime != null) {
            return candidateTime.compareTo(currentTime) > 0 ? candidate : current;
        }
        return candidate.getId().compareTo(current.getId()) > 0 ? candidate : current;
    }

    private static String shownTime(DocumentSnapshot document) {
        String value = document == null ? "" : document.getString("hora");
        return validTime(value) ? value.substring(0, 5) : "";
    }

    private static boolean validTime(String value) {
        return value != null && value.matches("[0-2][0-9]:[0-5][0-9].*");
    }

    private static int minutes(String value) {
        try {
            String[] parts = value.substring(0, 5).split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static long nextNumber(DocumentSnapshot config, String field) {
        Long stored = config.getLong(field);
        return stored == null ? 1L : stored;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String today() {
        Calendar calendar = Calendar.getInstance();
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(calendar.getTime());
    }

    private static Calendar calendarFor(String date) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date);
            if (parsed != null) calendar.setTime(parsed);
        } catch (ParseException ignored) {}
        return calendar;
    }

    private static String readableDate(String date) {
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date);
            if (parsed != null) {
                String text = new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", LOCALE).format(parsed);
                return text.substring(0, 1).toUpperCase(LOCALE) + text.substring(1);
            }
        } catch (Exception ignored) {}
        return "Seleccionar fecha";
    }

    private static boolean isPreviousDate(String date) {
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date);
            if (parsed == null) return false;
            Calendar chosen = Calendar.getInstance();
            chosen.setTime(parsed);
            startOfDay(chosen);
            Calendar today = Calendar.getInstance();
            startOfDay(today);
            return chosen.before(today);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isValidDate(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            format.setLenient(false);
            Date parsed = format.parse(date);
            return parsed != null && format.format(parsed).equals(date);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void startOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static String friendlyError(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException && current.getMessage() != null) {
                return current.getMessage().replace("java.lang.IllegalStateException: ", "");
            }
            current = current.getCause();
        }
        String message = error.getMessage() == null ? "" : error.getMessage().toUpperCase(Locale.ROOT);
        if (message.contains("UNAVAILABLE") || message.contains("NETWORK") || message.contains("TIMEOUT")) {
            return "No hay conexión. Intenta nuevamente.";
        }
        if (message.contains("PERMISSION_DENIED")) {
            return "No tienes permiso para realizar esta acción.";
        }
        return "No se pudo completar la operación. Intenta nuevamente.";
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
        super.onDestroy();
    }

    private static final class AdminPerson {
        final String id;
        final String name;
        final String state;
        final String lastMovement;
        final String date;
        final String time;

        AdminPerson(String id, String name, String state, String lastMovement, String date, String time) {
            this.id = id;
            this.name = value(name);
            this.state = value(state);
            this.lastMovement = value(lastMovement);
            this.date = value(date);
            this.time = value(time);
        }
    }

    private static final class DailyMovements {
        DocumentSnapshot entry;
        DocumentSnapshot exit;
        String fallbackEntryTime = "";
    }

    private static final class SaveResult {
        final String movementId;

        SaveResult(String movementId) {
            this.movementId = movementId;
        }
    }
}


