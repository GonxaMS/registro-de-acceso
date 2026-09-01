package com.ejemplo.registroguardias;

import android.app.Activity;
import android.app.AlertDialog;
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

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class KeysActivity extends Activity implements KeysAdapter.Actions {
    private final List<KeyItem> visibleKeys = new ArrayList<>();
    private final List<KeyItem> hiddenKeys = new ArrayList<>();
    private final List<KeyItem> filteredKeys = new ArrayList<>();
    private final List<SelectablePerson> personalPeople = new ArrayList<>();
    private final List<SelectablePerson> keyOnlyPeople = new ArrayList<>();
    private final List<SelectablePerson> selectablePeople = new ArrayList<>();

    private FirebaseFirestore database;
    private ListenerRegistration keysListener;
    private ListenerRegistration peopleListener;
    private ListenerRegistration keyPeopleListener;
    private boolean loadErrorDialogVisible;
    private final Set<String> retriedLoads = new HashSet<>();
    private final Set<String> reportedLoads = new HashSet<>();
    private final Set<String> pendingMovements = new HashSet<>();
    private SharedPreferences preferences;
    private EditText search;
    private TextView count;
    private KeysAdapter adapter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(AccessActivity.PREFS_NAME, MODE_PRIVATE);
        setContentView(R.layout.activity_keys);
        applyWindowInsets();

        search = findViewById(R.id.inputKeySearch);
        count = findViewById(R.id.txtKeyCount);
        adapter = new KeysAdapter(this, filteredKeys, this);
        ((ListView) findViewById(R.id.listKeys)).setAdapter(adapter);
        ((TextView) findViewById(R.id.txtKeysToday)).setText(
            new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "AR")).format(new Date())
        );
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnKeyMenu).setOnClickListener(this::showMainMenu);
        findViewById(R.id.btnKeySheets).setOnClickListener(view -> openSheets());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filterKeys(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        count.setText("Cargando llaves…");
        database = FirebaseFirestore.getInstance();
        AdminAccess.checkRole(database, (admin, role) -> {
            if (AdminAccess.BLOCKED.equals(role)) {
                startActivity(new Intent(this, BlockedActivity.class));
                finish();
                return;
            }
            listenForKeys();
            listenForPeople();
            listenForKeyOnlyPeople();
        });
    }

    private void openSheets() {
        if (BuildConfig.USE_FIREBASE_EMULATOR || AccessActivity.SHEETS_WEB_URL.trim().isEmpty()) {
            toast("La planilla no está disponible");
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AccessActivity.SHEETS_WEB_URL)));
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.keysRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void listenForKeys() {
        if (keysListener != null) keysListener.remove();
        keysListener = database.collection("llaves").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showLoadError("Llaves", "No se pudieron cargar las llaves", error, this::listenForKeys);
                return;
            }
            if (snapshot == null) return;
            visibleKeys.clear();
            hiddenKeys.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                KeyItem key = new KeyItem(
                    document.getId(), document.getString("nombre"), document.getString("estado"),
                    document.getString("quienTiene"), document.getString("fechaRetiro"),
                    document.getString("horaRetiro")
                );
                Boolean active = document.getBoolean("activo");
                if (active == null || active) visibleKeys.add(key);
                else hiddenKeys.add(key);
            }
            Comparator<KeyItem> byName = (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name);
            Collections.sort(visibleKeys, byName);
            Collections.sort(hiddenKeys, byName);
            filterKeys(search.getText().toString());
        });
    }

    private void listenForPeople() {
        if (peopleListener != null) peopleListener.remove();
        peopleListener = database.collection("personal").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showLoadError("Personal para llaves", "No se pudo cargar el personal", error,
                    this::listenForPeople);
                return;
            }
            if (snapshot == null) return;
            personalPeople.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Boolean removed = document.getBoolean("retirado");
                if (removed != null && removed) continue;
                Boolean active = document.getBoolean("activo");
                personalPeople.add(new SelectablePerson(
                    document.getId(), document.getString("nombre"), active != null && !active
                ));
            }
            rebuildSelectablePeople();
        });
    }

    private void listenForKeyOnlyPeople() {
        if (keyPeopleListener != null) keyPeopleListener.remove();
        keyPeopleListener = database.collection("operariosLlaves")
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    showLoadError("Operarios de llaves",
                        "No se pudieron cargar los operarios de llaves", error,
                        this::listenForKeyOnlyPeople);
                    return;
                }
                if (snapshot == null) return;
                keyOnlyPeople.clear();
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    String name = document.getString("nombre");
                    if (name != null && !name.trim().isEmpty()) {
                        keyOnlyPeople.add(new SelectablePerson("", name, false, true));
                    }
                }
                rebuildSelectablePeople();
            });
    }

    private void rebuildSelectablePeople() {
        selectablePeople.clear();
        selectablePeople.addAll(personalPeople);
        for (SelectablePerson keyPerson : keyOnlyPeople) {
            boolean duplicate = false;
            for (SelectablePerson person : personalPeople) {
                if (person.name.equalsIgnoreCase(keyPerson.name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) selectablePeople.add(keyPerson);
        }
        Collections.sort(selectablePeople, (left, right) ->
            String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));
    }

    private void filterKeys(String query) {
        filteredKeys.clear();
        String normalized = query.trim().toLowerCase(Locale.getDefault());
        for (KeyItem key : visibleKeys) {
            if (key.name.toLowerCase(Locale.getDefault()).contains(normalized)) {
                filteredKeys.add(key);
            }
        }
        int borrowed = 0;
        for (KeyItem key : filteredKeys) if ("Prestada".equals(key.state)) borrowed++;
        count.setText(filteredKeys.size()
            + (filteredKeys.size() == 1 ? " llave" : " llaves")
            + " · " + borrowed + " prestadas");
        adapter.notifyDataSetChanged();
    }

    @Override public void onKeyMovement(KeyItem key, String type) {
        showPersonSelector(key, type);
    }

    private void showPersonSelector(KeyItem key, String type) {
        boolean take = "Retiro".equals(type);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        EditText input = dialogInput("APELLIDO NOMBRE (ej. PÉREZ JUAN)", take ? "" : key.holder);
        input.setSelectAllOnFocus(true);
        ListView list = new ListView(this);
        list.setDividerHeight(1);
        List<SelectablePerson> filtered = new ArrayList<>();
        android.widget.ArrayAdapter<String> peopleAdapter =
            new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(peopleAdapter);
        Runnable refresh = () -> {
            filtered.clear();
            peopleAdapter.clear();
            String query = input.getText().toString().trim().toLowerCase(Locale.getDefault());
            for (SelectablePerson person : selectablePeople) {
                if (person.name.toLowerCase(Locale.getDefault()).contains(query)) {
                    filtered.add(person);
                    peopleAdapter.add(person.label());
                }
            }
            peopleAdapter.notifyDataSetChanged();
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                refresh.run();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        content.addView(list, new LinearLayout.LayoutParams(-1, dp(320)));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle((take ? "Quien retira " : "Quien devuelve ") + key.name)
            .setMessage("Elige de la lista o escribe APELLIDO primero y NOMBRE después.")
            .setView(padded(content))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Usar nombre escrito", null)
            .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filtered.size()) return;
            SelectablePerson person = filtered.get(position);
            dialog.dismiss();
            saveKeyMovement(key, type, person);
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String writtenName = keyPersonName(input.getText().toString());
                if (writtenName.length() < 2) {
                    input.setError("Escribe apellido y nombre");
                    return;
                }
                if (!writtenName.contains(" ")) {
                    input.setError("Escribe APELLIDO primero y NOMBRE después");
                    return;
                }
                if (writtenName.length() > 120) {
                    input.setError("El nombre es demasiado largo");
                    return;
                }
                SelectablePerson chosen = null;
                for (SelectablePerson person : selectablePeople) {
                    if (person.name.equalsIgnoreCase(writtenName)) {
                        chosen = person;
                        break;
                    }
                }
                if (chosen == null) chosen = new SelectablePerson("", writtenName, false, true);
                dialog.dismiss();
                saveKeyMovement(key, type, chosen);
            }));
        dialog.show();
        refresh.run();
    }

    private void saveKeyMovement(KeyItem key, String type, SelectablePerson person) {
        if (!pendingMovements.add(key.id)) return;
        adapter.notifyDataSetChanged();
        boolean take = "Retiro".equals(type);
        String date = today();
        String time = currentTime();
        String registeredBy = currentUser();
        DocumentReference keyReference = database.collection("llaves").document(key.id);
        DocumentReference metaReference = database.collection("meta").document("config");
        DocumentReference keyPersonReference = person.id.isEmpty()
            ? database.collection("operariosLlaves").document(keyPersonId(person.name)) : null;
        toast(take ? "Registrando retiro..." : "Registrando devolucion...");

        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(keyReference);
            String state = current.getString("estado");
            if (take && "Prestada".equals(state)) {
                throw new IllegalStateException("La llave ya está prestada a " + current.getString("quienTiene"));
            }
            if (!take && !"Prestada".equals(state)) {
                throw new IllegalStateException("La llave ya esta disponible");
            }

            DocumentSnapshot config = transaction.get(metaReference);
            DocumentSnapshot storedKeyPerson = keyPersonReference == null
                ? null : transaction.get(keyPersonReference);
            long next = nextNumber(config, "siguienteMovimientoLlave");
            String movementId = String.format(Locale.US, "L%06d", next);
            DocumentReference movementReference = database.collection("movimientosLlaves").document(movementId);

            Map<String, Object> keyUpdate = new HashMap<>();
            keyUpdate.put("estado", take ? "Prestada" : "Disponible");
            keyUpdate.put("quienTiene", take ? person.name : "");
            keyUpdate.put("quienTieneId", take ? person.id : "");
            keyUpdate.put("fechaRetiro", take ? date : "");
            keyUpdate.put("horaRetiro", take ? time : "");
            keyUpdate.put("ultimoMovimiento", type);
            keyUpdate.put("ultimoMovimientoId", movementId);
            keyUpdate.put("ultimaFecha", date);
            keyUpdate.put("ultimaHora", time);
            keyUpdate.put("actualizado", FieldValue.serverTimestamp());
            transaction.update(keyReference, keyUpdate);

            Map<String, Object> movement = new HashMap<>();
            movement.put("movimientoId", movementId);
            movement.put("llaveId", key.id);
            movement.put("llaveNombre", key.name);
            movement.put("movimiento", type);
            movement.put("personaId", person.id);
            movement.put("persona", person.name);
            if (take) {
                movement.put("quienRetiraId", person.id);
                movement.put("quienRetira", person.name);
            } else {
                movement.put("quienDevuelveId", person.id);
                movement.put("quienDevuelve", person.name);
            }
            movement.put("fecha", date);
            movement.put("hora", time);
            movement.put("usuario", registeredBy);
            movement.put("creado", FieldValue.serverTimestamp());
            transaction.set(movementReference, movement);
            if (keyPersonReference != null) {
                if (storedKeyPerson != null && storedKeyPerson.exists()) {
                    transaction.update(keyPersonReference, "actualizado", FieldValue.serverTimestamp());
                } else {
                    Map<String, Object> keyPerson = new HashMap<>();
                    keyPerson.put("nombre", person.name);
                    keyPerson.put("actualizado", FieldValue.serverTimestamp());
                    transaction.set(keyPersonReference, keyPerson);
                }
            }
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimientoLlave", next + 1), SetOptions.merge());
            return movementId;
        }).addOnSuccessListener(movementId -> {
            finishKeyMovement(key.id);
            toast((take ? person.name + " retiró " : person.name + " devolvió ") + key.name
                + " a las " + time);
        }).addOnFailureListener(error -> {
            finishKeyMovement(key.id);
            showMessage("No se pudo registrar", friendlyError(error));
        });
    }

    @Override public boolean isKeyMovementPending(KeyItem key) {
        return pendingMovements.contains(key.id);
    }

    private void finishKeyMovement(String keyId) {
        pendingMovements.remove(keyId);
        adapter.notifyDataSetChanged();
    }

    @Override public void onKeyOptions(View anchor, KeyItem key) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Ocultar llave");
        menu.setOnMenuItemClickListener(item -> {
            confirmHide(key);
            return true;
        });
        menu.show();
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Agregar llave");
        menu.getMenu().add("Mostrar llaves ocultas");
        menu.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            if (option.startsWith("Agregar")) showAddDialog();
            else showHiddenKeys();
            return true;
        });
        menu.show();
    }

    private void showAddDialog() {
        EditText input = dialogInput("Nombre o identificacion de la llave", "");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Agregar llave")
            .setView(padded(input))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError("Escribe apellido y nombre");
                    return;
                }
                for (KeyItem key : visibleKeys) {
                    if (key.name.equalsIgnoreCase(name)) {
                        input.setError("Esta llave ya existe");
                        return;
                    }
                }
                dialog.dismiss();
                addKey(name);
            }));
        dialog.show();
    }

    private void addKey(String name) {
        for (KeyItem key : hiddenKeys) {
            if (key.name.equalsIgnoreCase(name)) {
                database.collection("llaves").document(key.id).update("activo", true)
                    .addOnSuccessListener(ignored -> toast(key.name + " volvio a la lista"))
                    .addOnFailureListener(error -> showMessage("No se pudo mostrar", friendlyError(error)));
                return;
            }
        }

        DocumentReference metaReference = database.collection("meta").document("config");
        database.runTransaction(transaction -> {
            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteLlave");
            String id = String.format(Locale.US, "K%04d", next);
            Map<String, Object> data = new HashMap<>();
            data.put("nombre", name);
            data.put("estado", "Disponible");
            data.put("quienTiene", "");
            data.put("quienTieneId", "");
            data.put("fechaRetiro", "");
            data.put("horaRetiro", "");
            data.put("ultimoMovimiento", "");
            data.put("ultimoMovimientoId", "");
            data.put("ultimaFecha", "");
            data.put("ultimaHora", "");
            data.put("activo", true);
            data.put("actualizado", FieldValue.serverTimestamp());
            transaction.set(database.collection("llaves").document(id), data);
            transaction.set(metaReference, Collections.singletonMap("siguienteLlave", next + 1), SetOptions.merge());
            return name;
        }).addOnSuccessListener(ignored -> toast(name + " fue agregada"))
            .addOnFailureListener(error -> showMessage("No se pudo agregar", friendlyError(error)));
    }

    private void confirmHide(KeyItem key) {
        if ("Prestada".equals(key.state)) {
            showMessage("No se puede ocultar", "Primero deben devolver " + key.name);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Ocultar llave")
            .setMessage("Quieres ocultar " + key.name + " de la lista?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Si, ocultar", (dialog, which) ->
                database.collection("llaves").document(key.id).update("activo", false)
                    .addOnSuccessListener(ignored -> toast("Llave oculta"))
                    .addOnFailureListener(error -> showMessage("No se pudo ocultar", friendlyError(error))))
            .show();
    }

    private void showHiddenKeys() {
        if (hiddenKeys.isEmpty()) {
            toast("No hay llaves ocultas");
            return;
        }
        String[] names = new String[hiddenKeys.size()];
        for (int index = 0; index < names.length; index++) names[index] = hiddenKeys.get(index).name;
        new AlertDialog.Builder(this)
            .setTitle("Mostrar llaves ocultas")
            .setItems(names, (dialog, index) -> {
                KeyItem key = hiddenKeys.get(index);
                database.collection("llaves").document(key.id).update("activo", true)
                    .addOnSuccessListener(ignored -> toast(key.name + " volvio a la lista"))
                    .addOnFailureListener(error -> showMessage("No se pudo mostrar", friendlyError(error)));
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private String currentUser() {
        return preferences.getString(AccessActivity.USER_NAME_KEY, "").trim();
    }

    private static long nextNumber(DocumentSnapshot config, String field) {
        Long stored = config.getLong(field);
        return stored == null ? 1L : stored;
    }

    private static String keyPersonName(String value) {
        return cleanName(value).toUpperCase(new Locale("es", "AR"));
    }

    private static String keyPersonId(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("E");
            for (int index = 0; index < 12; index++) {
                id.append(String.format(Locale.US, "%02x", digest[index] & 0xff));
            }
            return id.toString();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo guardar el nombre");
        }
    }
    private static String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
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
        int padding = dp(20);
        container.setPadding(padding, 0, padding, 0);
        container.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return container;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
            .setTitle(title).setMessage(message).setPositiveButton("Aceptar", null).show());
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        if (keysListener != null) keysListener.remove();
        if (peopleListener != null) peopleListener.remove();
        if (keyPeopleListener != null) keyPeopleListener.remove();
        super.onDestroy();
    }
}
