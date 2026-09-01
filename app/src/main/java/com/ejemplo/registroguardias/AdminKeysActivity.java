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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdminKeysActivity extends Activity {
    private static final String ADMIN_USER = "Admin";
    private static final Locale LOCALE = new Locale("es", "AR");

    private final List<KeyItem> keys = new ArrayList<>();
    private final List<SelectablePerson> people = new ArrayList<>();
    private final List<DocumentSnapshot> movements = new ArrayList<>();
    private FirebaseFirestore database;
    private ListenerRegistration keysListener;
    private ListenerRegistration personalListener;
    private ListenerRegistration keyPeopleListener;
    private boolean loadErrorDialogVisible;
    private final Set<String> retriedLoads = new HashSet<>();
    private final Set<String> reportedLoads = new HashSet<>();
    private KeyItem selectedKey;
    private String selectedDate;
    private boolean loading;
    private TextView keyButton;
    private TextView dateButton;
    private TextView movementText;
    private Button addTakeButton;
    private Button addReturnButton;
    private Button editButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_keys);
        applyWindowInsets();
        database = FirebaseFirestore.getInstance();
        keyButton = findViewById(R.id.btnAdminKey);
        dateButton = findViewById(R.id.btnAdminKeyDate);
        movementText = findViewById(R.id.txtAdminKeyMovements);
        addTakeButton = findViewById(R.id.btnAdminAddTake);
        addReturnButton = findViewById(R.id.btnAdminAddReturn);
        editButton = findViewById(R.id.btnAdminEditKeyMovement);
        selectedDate = today();
        dateButton.setText(readableDate(selectedDate));
        findViewById(R.id.btnAdminKeysBack).setOnClickListener(view -> finish());
        keyButton.setOnClickListener(view -> chooseKey());
        dateButton.setOnClickListener(view -> chooseDate());
        addTakeButton.setOnClickListener(view -> choosePerson("Retiro"));
        addReturnButton.setOnClickListener(view -> choosePerson("Devolucion"));
        editButton.setOnClickListener(view -> chooseMovement());
        setActionsEnabled(false);
        AdminAccess.check(database, (allowed, uid) -> {
            if (!allowed) {
                Toast.makeText(this, "Este dispositivo no tiene permiso de administrador\nUID: " + uid,
                    Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            listenForKeys();
            listenForPeople();
        });
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.adminKeysRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void listenForKeys() {
        if (keysListener != null) keysListener.remove();
        keysListener = database.collection("llaves").addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                if (error != null) {
                    showLoadError("Admin · Llaves", "No se pudieron cargar las llaves", error,
                        this::listenForKeys);
                }
                return;
            }
            String selectedId = selectedKey == null ? "" : selectedKey.id;
            keys.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Boolean active = document.getBoolean("activo");
                if (active != null && !active) continue;
                keys.add(new KeyItem(document.getId(), document.getString("nombre"),
                    document.getString("estado"), document.getString("quienTiene"),
                    document.getString("fechaRetiro"), document.getString("horaRetiro")));
            }
            Collections.sort(keys, (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));
            selectedKey = findKey(selectedId);
            if (selectedKey == null && !keys.isEmpty()) selectedKey = keys.get(0);
            keyButton.setText(selectedKey == null ? "Seleccionar llave" : selectedKey.name);
            loadMovements();
        });
    }

    private void listenForPeople() {
        if (personalListener != null) personalListener.remove();
        personalListener = database.collection("personal").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showLoadError("Admin · Personal", "No se pudo cargar el personal", error,
                    this::listenForPeople);
                return;
            }
            if (snapshot == null) return;
            people.removeIf(person -> !person.keyOnly);
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Boolean removed = document.getBoolean("retirado");
                if (removed != null && removed) continue;
                people.add(new SelectablePerson(document.getId(), document.getString("nombre"), false));
            }
            sortPeople();
        });
        if (keyPeopleListener != null) keyPeopleListener.remove();
        keyPeopleListener = database.collection("operariosLlaves").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showLoadError("Admin · Operarios de llaves",
                    "No se pudieron cargar los operarios de llaves", error,
                    this::listenForPeople);
                return;
            }
            if (snapshot == null) return;
            people.removeIf(person -> person.keyOnly);
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                String name = value(document.getString("nombre"));
                if (!name.isEmpty() && !containsPerson(name)) {
                    people.add(new SelectablePerson("", name, false, true));
                }
            }
            sortPeople();
        });
    }

    private boolean containsPerson(String name) {
        for (SelectablePerson person : people) if (person.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    private void sortPeople() {
        Collections.sort(people, (left, right) ->
            String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));
    }

    private KeyItem findKey(String id) {
        for (KeyItem key : keys) if (key.id.equals(id)) return key;
        return null;
    }

    private void chooseKey() {
        if (keys.isEmpty()) return;
        String[] options = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) options[i] = keys.get(i).name;
        new AlertDialog.Builder(this).setTitle("Seleccionar llave")
            .setItems(options, (dialog, index) -> {
                selectedKey = keys.get(index);
                keyButton.setText(selectedKey.name);
                loadMovements();
            }).setNegativeButton("Cancelar", null).show();
    }

    private void chooseDate() {
        Calendar initial = calendarFor(selectedDate);
        new DatePickerDialog(this, (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.clear();
            chosen.set(year, month, day);
            selectedDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(chosen.getTime());
            dateButton.setText(readableDate(selectedDate));
            loadMovements();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH))
            .show();
    }

    private void loadMovements() {
        if (selectedKey == null) {
            movements.clear();
            renderMovements();
            return;
        }
        loading = true;
        setActionsEnabled(false);
        movementText.setText("Buscando movimientos…");
        database.collection("movimientosLlaves").whereEqualTo("llaveId", selectedKey.id).get()
            .addOnSuccessListener(snapshot -> {
                movements.clear();
                HashSet<String> replaced = new HashSet<>();
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    if (!selectedDate.equals(document.getString("fecha"))) continue;
                    String replacement = document.getString("reemplazaA");
                    String cancellation = document.getString("anulaA");
                    if (replacement != null) replaced.add(replacement);
                    if (cancellation != null) replaced.add(cancellation);
                }
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    if (!selectedDate.equals(document.getString("fecha"))
                        || replaced.contains(document.getId())) continue;
                    String type = document.getString("movimiento");
                    if ("Retiro".equals(type) || "Devolucion".equals(type)) movements.add(document);
                }
                Collections.sort(movements, Comparator.comparing(AdminKeysActivity::sortKey));
                loading = false;
                renderMovements();
            }).addOnFailureListener(error -> {
                loading = false;
                renderMovements();
                showMessage("No se pudieron leer los movimientos", friendlyError(error));
            });
    }

    private void renderMovements() {
        if (selectedKey == null) movementText.setText("Selecciona una llave");
        else if (movements.isEmpty()) movementText.setText("Sin movimientos el " + selectedDate);
        else {
            StringBuilder text = new StringBuilder("Movimientos del ").append(selectedDate);
            for (DocumentSnapshot movement : movements) {
                text.append("\n\n").append(value(movement.getString("movimiento")))
                    .append(" · ").append(shownTime(movement))
                    .append(" · ").append(value(movement.getString("persona")))
                    .append("\nID ").append(movement.getId());
            }
            movementText.setText(text);
        }
        setActionsEnabled(!loading && selectedKey != null && isValidDate(selectedDate));
    }

    private void setActionsEnabled(boolean enabled) {
        addTakeButton.setEnabled(enabled);
        addReturnButton.setEnabled(enabled);
        editButton.setEnabled(enabled && !movements.isEmpty());
    }

    private void choosePerson(String type) {
        if (selectedKey == null || people.isEmpty()) {
            showMessage("Sin personas", "No hay personas disponibles para asignar al movimiento.");
            return;
        }
        String[] options = new String[people.size()];
        for (int i = 0; i < people.size(); i++) options[i] = people.get(i).label();
        new AlertDialog.Builder(this).setTitle("Persona del " + type.toLowerCase(LOCALE))
            .setItems(options, (dialog, index) -> chooseTime(type, people.get(index), null))
            .setNegativeButton("Cancelar", null).show();
    }

    private void chooseMovement() {
        String[] options = new String[movements.size()];
        for (int i = 0; i < movements.size(); i++) {
            DocumentSnapshot movement = movements.get(i);
            options[i] = movement.getString("movimiento") + " · " + shownTime(movement)
                + " · " + movement.getString("persona") + " · ID " + movement.getId();
        }
        new AlertDialog.Builder(this).setTitle("Seleccionar movimiento")
            .setItems(options, (dialog, index) -> chooseAction(movements.get(index)))
            .setNegativeButton("Cancelar", null).show();
    }

    private void chooseAction(DocumentSnapshot movement) {
        new AlertDialog.Builder(this).setTitle("Movimiento " + movement.getId())
            .setItems(new String[]{"Corregir hora", "Quitar movimiento"}, (dialog, index) -> {
                if (index == 0) {
                    SelectablePerson person = new SelectablePerson(
                        value(movement.getString("personaId")), value(movement.getString("persona")), false);
                    chooseTime(value(movement.getString("movimiento")), person, movement);
                } else confirmCancellation(movement);
            }).setNegativeButton("Cancelar", null).show();
    }

    private void chooseTime(String type, SelectablePerson person, DocumentSnapshot previous) {
        int initial = minutes(previous == null ? "" : shownTime(previous));
        if (initial < 0) {
            Calendar now = Calendar.getInstance();
            initial = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        }
        new TimePickerDialog(this, (picker, hour, minute) -> {
            String time = String.format(Locale.US, "%02d:%02d", hour, minute);
            confirmSave(type, person, previous, time);
        }, initial / 60, initial % 60, true).show();
    }

    private void confirmSave(String type, SelectablePerson person, DocumentSnapshot previous,
                             String time) {
        String action = previous == null ? "Agregar" : "Corregir";
        new AlertDialog.Builder(this).setTitle(action + " " + type.toLowerCase(LOCALE))
            .setMessage(action + " " + type.toLowerCase(LOCALE) + " de " + selectedKey.name
                + " el " + selectedDate + " a las " + time + " · " + person.name + ".")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (dialog, which) ->
                saveMovement(type, person, previous, time)).show();
    }

    private void saveMovement(String type, SelectablePerson person, DocumentSnapshot previous,
                              String time) {
        setActionsEnabled(false);
        KeyItem key = selectedKey;
        String date = selectedDate;
        DocumentReference meta = database.collection("meta").document("config");
        database.runTransaction(transaction -> {
            DocumentSnapshot config = transaction.get(meta);
            long next = nextNumber(config, "siguienteMovimientoLlave");
            String id = String.format(Locale.US, "L%06d", next);
            Map<String, Object> movement = baseMovement(id, key, type, person, date, time);
            movement.put("esAjusteAdmin", true);
            if (previous != null) {
                movement.put("esCorreccion", true);
                movement.put("reemplazaA", previous.getId());
            }
            transaction.set(database.collection("movimientosLlaves").document(id), movement);
            transaction.set(meta, Collections.singletonMap("siguienteMovimientoLlave", next + 1),
                SetOptions.merge());
            return id;
        }).addOnSuccessListener(id -> {
            toast(type + " guardado correctamente");
            loadMovements();
        }).addOnFailureListener(error -> {
            renderMovements();
            showMessage("No se pudo guardar", friendlyError(error));
        });
    }

    private void confirmCancellation(DocumentSnapshot target) {
        new AlertDialog.Builder(this).setTitle("Quitar movimiento")
            .setMessage("Quitar " + target.getString("movimiento") + " de " + selectedKey.name
                + " del " + selectedDate + " · ID " + target.getId() + "?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, quitar", (dialog, which) -> saveCancellation(target)).show();
    }

    private void saveCancellation(DocumentSnapshot target) {
        setActionsEnabled(false);
        KeyItem key = selectedKey;
        String date = selectedDate;
        String targetType = value(target.getString("movimiento"));
        SelectablePerson person = new SelectablePerson(value(target.getString("personaId")),
            value(target.getString("persona")), false);
        DocumentReference meta = database.collection("meta").document("config");
        database.runTransaction(transaction -> {
            DocumentSnapshot config = transaction.get(meta);
            long next = nextNumber(config, "siguienteMovimientoLlave");
            String id = String.format(Locale.US, "L%06d", next);
            Map<String, Object> cancellation = baseMovement(id, key,
                "Anulacion" + targetType, person, date, shownTime(target));
            cancellation.put("esAjusteAdmin", true);
            cancellation.put("anulaA", target.getId());
            transaction.set(database.collection("movimientosLlaves").document(id), cancellation);
            transaction.set(meta, Collections.singletonMap("siguienteMovimientoLlave", next + 1),
                SetOptions.merge());
            return id;
        }).addOnSuccessListener(id -> {
            toast("Movimiento quitado correctamente");
            loadMovements();
        }).addOnFailureListener(error -> {
            renderMovements();
            showMessage("No se pudo quitar", friendlyError(error));
        });
    }

    private static Map<String, Object> baseMovement(String id, KeyItem key, String type,
                                                     SelectablePerson person, String date, String time) {
        Map<String, Object> movement = new HashMap<>();
        movement.put("movimientoId", id);
        movement.put("llaveId", key.id);
        movement.put("llaveNombre", key.name);
        movement.put("movimiento", type);
        movement.put("personaId", person.id);
        movement.put("persona", person.name);
        if ("Retiro".equals(type)) {
            movement.put("quienRetiraId", person.id);
            movement.put("quienRetira", person.name);
        } else if ("Devolucion".equals(type)) {
            movement.put("quienDevuelveId", person.id);
            movement.put("quienDevuelve", person.name);
        }
        movement.put("fecha", date);
        movement.put("hora", time);
        movement.put("usuario", ADMIN_USER);
        movement.put("creado", FieldValue.serverTimestamp());
        return movement;
    }

    private static String sortKey(DocumentSnapshot document) {
        String time = value(document.getString("hora"));
        Timestamp created = document.getTimestamp("creado");
        return time + "|" + (created == null ? "" : created.toDate().getTime()) + "|" + document.getId();
    }

    private static String shownTime(DocumentSnapshot document) {
        String time = document == null ? "" : value(document.getString("hora"));
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }

    private static int minutes(String time) {
        try {
            String[] parts = time.substring(0, 5).split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception ignored) { return -1; }
    }

    private static String today() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date());
    }

    private static Calendar calendarFor(String date) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date);
            if (parsed != null) calendar.setTime(parsed);
        } catch (Exception ignored) {}
        return calendar;
    }

    private static String readableDate(String date) {
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date);
            String text = new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", LOCALE).format(parsed);
            return text.substring(0, 1).toUpperCase(LOCALE) + text.substring(1);
        } catch (Exception ignored) { return "Seleccionar fecha"; }
    }

    private static boolean isValidDate(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            format.setLenient(false);
            Date parsed = format.parse(date);
            return parsed != null && format.format(parsed).equals(date);
        } catch (Exception ignored) { return false; }
    }

    private static long nextNumber(DocumentSnapshot config, String field) {
        Long stored = config.getLong(field);
        return stored == null ? 1L : stored;
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static String friendlyError(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toUpperCase(Locale.ROOT);
        if (message.contains("PERMISSION_DENIED")) return "Firebase rechazó el cambio por permisos.";
        if (message.contains("UNAVAILABLE") || message.contains("NETWORK")) return "No hay conexión.";
        return "No se pudo completar la operación.";
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Aceptar", null).show());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showLoadError(String source, String title, Exception error, Runnable retry) {
        if (retriedLoads.contains(source) && reportedLoads.add(source)) {
            AppErrorReporter.report(database, source, error);
        }
        if (loadErrorDialogVisible || isFinishing()) return;
        loadErrorDialogVisible = true;
        runOnUiThread(() -> {
            if (isFinishing()) {
                loadErrorDialogVisible = false;
                return;
            }
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(friendlyError(error) + "\n\nLos datos anteriores seguirán visibles.")
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Reintentar", (ignored, which) -> {
                    loadErrorDialogVisible = false;
                    retriedLoads.add(source);
                    retry.run();
                })
                .create();
            dialog.setOnDismissListener(ignored -> loadErrorDialogVisible = false);
            dialog.show();
        });
    }

    @Override protected void onDestroy() {
        if (keysListener != null) keysListener.remove();
        if (personalListener != null) personalListener.remove();
        if (keyPeopleListener != null) keyPeopleListener.remove();
        super.onDestroy();
    }
}
