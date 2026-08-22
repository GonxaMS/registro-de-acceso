package com.ejemplo.registroguardias;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KeysActivity extends Activity implements KeysAdapter.Actions {
    private final List<KeyItem> visibleKeys = new ArrayList<>();
    private final List<KeyItem> hiddenKeys = new ArrayList<>();
    private final List<KeyItem> filteredKeys = new ArrayList<>();

    private FirebaseFirestore database;
    private ListenerRegistration keysListener;
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
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filterKeys(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        count.setText("Conectando con Firebase...");
        database = FirebaseFirestore.getInstance();
        listenForKeys();
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
        keysListener = database.collection("llaves").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showMessage("Error de Firebase", cleanError(error));
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

    private void filterKeys(String query) {
        filteredKeys.clear();
        String normalized = query.trim().toLowerCase(Locale.getDefault());
        for (KeyItem key : visibleKeys) {
            if (key.name.toLowerCase(Locale.getDefault()).contains(normalized)
                || key.id.toLowerCase(Locale.getDefault()).contains(normalized)) {
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
        if ("Retiro".equals(type)) showPersonDialog(key, type, "Quien se lleva la llave", "Retirar");
        else showPersonDialog(key, type, "Quien devuelve la llave", "Devolver");
    }

    private void showPersonDialog(KeyItem key, String type, String hint, String positive) {
        String defaultValue = "Devolucion".equals(type) && !key.holder.isEmpty() ? key.holder : "";
        EditText input = dialogInput(hint, defaultValue);
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(("Retiro".equals(type) ? "Retirar " : "Devolver ") + key.name)
            .setMessage("Usuario que registra: " + currentUser())
            .setView(padded(input))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(positive, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String person = cleanName(input.getText().toString());
                if (person.length() < 2) {
                    input.setError("Escribe un nombre");
                    return;
                }
                dialog.dismiss();
                saveKeyMovement(key, type, person);
            }));
        dialog.show();
    }

    private void saveKeyMovement(KeyItem key, String type, String person) {
        boolean take = "Retiro".equals(type);
        String date = today();
        String time = currentTime();
        String registeredBy = currentUser();
        DocumentReference keyReference = database.collection("llaves").document(key.id);
        DocumentReference metaReference = database.collection("meta").document("config");
        toast(take ? "Registrando retiro..." : "Registrando devolucion...");

        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(keyReference);
            String state = current.getString("estado");
            if (take && "Prestada".equals(state)) {
                throw new IllegalStateException("La llave ya esta prestada a " + current.getString("quienTiene"));
            }
            if (!take && !"Prestada".equals(state)) {
                throw new IllegalStateException("La llave ya esta disponible");
            }

            DocumentSnapshot config = transaction.get(metaReference);
            long next = nextNumber(config, "siguienteMovimientoLlave");
            String movementId = String.format(Locale.US, "L%06d", next);
            DocumentReference movementReference = database.collection("movimientosLlaves").document(movementId);

            Map<String, Object> keyUpdate = new HashMap<>();
            keyUpdate.put("estado", take ? "Prestada" : "Disponible");
            keyUpdate.put("quienTiene", take ? person : "");
            keyUpdate.put("fechaRetiro", take ? date : "");
            keyUpdate.put("horaRetiro", take ? time : "");
            keyUpdate.put("ultimoMovimiento", type);
            keyUpdate.put("ultimaFecha", date);
            keyUpdate.put("ultimaHora", time);
            keyUpdate.put("actualizado", FieldValue.serverTimestamp());
            transaction.update(keyReference, keyUpdate);

            Map<String, Object> movement = new HashMap<>();
            movement.put("movimientoId", movementId);
            movement.put("llaveId", key.id);
            movement.put("llaveNombre", key.name);
            movement.put("movimiento", type);
            movement.put("persona", person);
            if (take) movement.put("quienRetira", person);
            else movement.put("quienDevuelve", person);
            movement.put("fecha", date);
            movement.put("hora", time);
            movement.put("usuario", registeredBy);
            movement.put("creado", FieldValue.serverTimestamp());
            transaction.set(movementReference, movement);
            transaction.set(metaReference,
                Collections.singletonMap("siguienteMovimientoLlave", next + 1), SetOptions.merge());
            return movementId;
        }).addOnSuccessListener(ignored -> showMessage("Registro correcto",
            (take ? person + " retiro " : person + " devolvio ") + key.name
                + " a las " + time + "."))
            .addOnFailureListener(error -> showMessage("No se pudo registrar", cleanError(error)));
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
                    input.setError("Escribe un nombre");
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
                    .addOnFailureListener(error -> showMessage("No se pudo mostrar", cleanError(error)));
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
            data.put("fechaRetiro", "");
            data.put("horaRetiro", "");
            data.put("ultimoMovimiento", "");
            data.put("ultimaFecha", "");
            data.put("ultimaHora", "");
            data.put("activo", true);
            data.put("actualizado", FieldValue.serverTimestamp());
            transaction.set(database.collection("llaves").document(id), data);
            transaction.set(metaReference, Collections.singletonMap("siguienteLlave", next + 1), SetOptions.merge());
            return name;
        }).addOnSuccessListener(ignored -> toast(name + " fue agregada"))
            .addOnFailureListener(error -> showMessage("No se pudo agregar", cleanError(error)));
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
                    .addOnFailureListener(error -> showMessage("No se pudo ocultar", cleanError(error))))
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
                    .addOnFailureListener(error -> showMessage("No se pudo mostrar", cleanError(error)));
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

    private static String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanError(Exception error) {
        String message = error.getMessage();
        return message == null ? "Operacion rechazada"
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
        if (keysListener != null) keysListener.remove();
        super.onDestroy();
    }
}
