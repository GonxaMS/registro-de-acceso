package com.ejemplo.registroguardias;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

public final class KeysActivity extends AppCompatActivity implements KeysAdapter.Actions {
    private static final long DATA_LOAD_TIMEOUT_MS = 5_000L;
    private static final String KEYS_FILTER_ALL = "all";
    private static final String KEYS_FILTER_AVAILABLE = "available";
    private static final String KEYS_FILTER_BORROWED = "borrowed";
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
    private TextView keysFilterAll;
    private TextView keysFilterAvailable;
    private TextView keysFilterBorrowed;
    private TextView offlineBanner;
    private KeysAdapter adapter;
    private NetworkMonitor networkMonitor;
    private boolean networkAvailable = true;
    private String keysFilter = KEYS_FILTER_ALL;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable loadTimeout;
    private Runnable reconnectRetry;
    private AlertDialog loadErrorDialog;
    private boolean loadAttemptActive;
    private boolean keysLoaded;
    private boolean peopleLoaded;
    private boolean keyPeopleLoaded;
    private boolean roleCheckInProgress;

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        AccessibilityMode.apply(this);
        super.onCreate(state);
        preferences = getSharedPreferences(AccessActivity.PREFS_NAME, MODE_PRIVATE);
        setContentView(R.layout.activity_keys);
        AccessibilityMode.applyTo(this, findViewById(android.R.id.content));
        applyWindowInsets();

        search = findViewById(R.id.inputKeySearch);
        count = findViewById(R.id.txtKeyCount);
        keysFilterAll = findViewById(R.id.filterKeysAll);
        keysFilterAvailable = findViewById(R.id.filterKeysAvailable);
        keysFilterBorrowed = findViewById(R.id.filterKeysBorrowed);
        offlineBanner = findViewById(R.id.offlineBanner);
        adapter = new KeysAdapter(this, filteredKeys, this);
        ((ListView) findViewById(R.id.listKeys)).setAdapter(adapter);
        ((TextView) findViewById(R.id.txtKeysToday)).setText(
            new SimpleDateFormat(getString(R.string.date_format_full), new Locale("es", "AR"))
                .format(new Date())
        );
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnKeyMenu).setOnClickListener(this::showMainMenu);
        findViewById(R.id.btnKeySheets).setOnClickListener(view -> openSheets());
        keysFilterAll.setOnClickListener(view -> setKeysFilter(KEYS_FILTER_ALL));
        keysFilterAvailable.setOnClickListener(view -> setKeysFilter(KEYS_FILTER_AVAILABLE));
        keysFilterBorrowed.setOnClickListener(view -> setKeysFilter(KEYS_FILTER_BORROWED));
        updateKeysFilterButtons();
        networkMonitor = new NetworkMonitor(this, this::updateNetworkState);
        networkMonitor.start();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filterKeys(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        count.setText(R.string.loading_keys);
        database = FirebaseFirestore.getInstance();
        beginLoadAttempt();
        checkAccessAndLoad();
    }

    private void checkAccessAndLoad() {
        if (database == null || roleCheckInProgress || !loadAttemptActive || isFinishing()) return;
        roleCheckInProgress = true;
        AdminAccess.checkRoleWithError(database, (admin, role, error) -> {
            roleCheckInProgress = false;
            if (!loadAttemptActive || isFinishing()) return;
            if (error != null) {
                failLoad(error, getString(R.string.key_load_title));
                return;
            }
            if (AdminAccess.BLOCKED.equals(role)) {
                loadAttemptActive = false;
                cancelLoadTimeout();
                startActivity(new Intent(this, BlockedActivity.class));
                finish();
                return;
            }
            startDataListeners();
        });
    }

    private void beginLoadAttempt() {
        cancelLoadTimeout();
        removeDataListeners();
        loadAttemptActive = true;
        keysLoaded = false;
        peopleLoaded = false;
        keyPeopleLoaded = false;
        if (count != null) count.setText(R.string.loading_keys);
        loadTimeout = () -> {
            if (loadAttemptActive) {
                failLoad(new IllegalStateException(getString(R.string.load_timeout_message)),
                    getString(R.string.key_load_title));
            }
        };
        mainHandler.postDelayed(loadTimeout, DATA_LOAD_TIMEOUT_MS);
    }

    private void retryInitialLoad() {
        if (isFinishing() || database == null) return;
        dismissLoadErrorDialog();
        beginLoadAttempt();
        checkAccessAndLoad();
    }

    private void startDataListeners() {
        if (!loadAttemptActive || database == null || isFinishing()) return;
        removeDataListeners();
        listenForKeys();
        listenForPeople();
        listenForKeyOnlyPeople();
    }

    private void removeDataListeners() {
        if (keysListener != null) {
            keysListener.remove();
            keysListener = null;
        }
        if (peopleListener != null) {
            peopleListener.remove();
            peopleListener = null;
        }
        if (keyPeopleListener != null) {
            keyPeopleListener.remove();
            keyPeopleListener = null;
        }
    }

    private void openSheets() {
        if (BuildConfig.USE_FIREBASE_EMULATOR || AccessActivity.SHEETS_WEB_URL.trim().isEmpty()) {
            toast(getString(R.string.sheet_unavailable));
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
                failLoad(error, getString(R.string.key_load_failed));
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
            keysLoaded = true;
            finishLoadIfReady();
        });
    }

    private void listenForPeople() {
        if (peopleListener != null) peopleListener.remove();
        peopleListener = database.collection("personal").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                failLoad(error, getString(R.string.people_for_keys_failed));
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
            peopleLoaded = true;
            finishLoadIfReady();
        });
    }

    private void listenForKeyOnlyPeople() {
        if (keyPeopleListener != null) keyPeopleListener.remove();
        keyPeopleListener = database.collection("operariosLlaves")
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    failLoad(error, getString(R.string.key_workers_failed));
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
                keyPeopleLoaded = true;
                finishLoadIfReady();
            });
    }

    private void finishLoadIfReady() {
        if (!keysLoaded || !peopleLoaded || !keyPeopleLoaded) return;
        loadAttemptActive = false;
        cancelLoadTimeout();
        dismissLoadErrorDialog();
    }

    private void failLoad(Exception error, String title) {
        if (isFinishing()) return;
        loadAttemptActive = false;
        cancelLoadTimeout();
        removeDataListeners();
        showLoadError(title, title, error == null
            ? new IllegalStateException(getString(R.string.load_timeout_message)) : error,
            this::retryInitialLoad);
    }

    private void cancelLoadTimeout() {
        if (loadTimeout != null) {
            mainHandler.removeCallbacks(loadTimeout);
            loadTimeout = null;
        }
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
            boolean matchesState = KEYS_FILTER_ALL.equals(keysFilter)
                || (KEYS_FILTER_AVAILABLE.equals(keysFilter) && "Disponible".equals(key.state))
                || (KEYS_FILTER_BORROWED.equals(keysFilter) && "Prestada".equals(key.state));
            if (matchesState && key.name.toLowerCase(Locale.getDefault()).contains(normalized)) {
                filteredKeys.add(key);
            }
        }
        int borrowed = 0;
        for (KeyItem key : filteredKeys) if ("Prestada".equals(key.state)) borrowed++;
        count.setText(filteredKeys.size() == 1
            ? getString(R.string.keys_summary_one, borrowed)
            : getString(R.string.keys_summary_many, filteredKeys.size(), borrowed));
        adapter.notifyDataSetChanged();
    }

    private void setKeysFilter(String filter) {
        keysFilter = filter;
        updateKeysFilterButtons();
        filterKeys(search.getText().toString());
    }

    private void updateKeysFilterButtons() {
        keysFilterAll.setSelected(KEYS_FILTER_ALL.equals(keysFilter));
        keysFilterAvailable.setSelected(KEYS_FILTER_AVAILABLE.equals(keysFilter));
        keysFilterBorrowed.setSelected(KEYS_FILTER_BORROWED.equals(keysFilter));
    }

    @Override public void onKeyMovement(KeyItem key, String type) {
        if (!networkAvailable) {
            toast(getString(R.string.offline_operations_blocked));
            return;
        }
        showPersonSelector(key, type);
    }

    @Override public boolean isNetworkAvailable() {
        return networkAvailable;
    }

    private void updateNetworkState(boolean available) {
        runOnUiThread(() -> {
            boolean wasAvailable = networkAvailable;
            networkAvailable = available;
            offlineBanner.setVisibility(available ? View.GONE : View.VISIBLE);
            if (adapter != null) adapter.notifyDataSetChanged();
            if (available && !wasAvailable && database != null) {
                if (reconnectRetry != null) mainHandler.removeCallbacks(reconnectRetry);
                reconnectRetry = () -> {
                    reconnectRetry = null;
                    if (networkAvailable && !isFinishing()) retryInitialLoad();
                };
                mainHandler.postDelayed(reconnectRetry, 300L);
            }
        });
    }

    private void showPersonSelector(KeyItem key, String type) {
        boolean take = "Retiro".equals(type);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        EditText input = dialogInput(getString(R.string.key_person_name_hint),
            take ? "" : key.holder);
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
                    peopleAdapter.add(person.label(this));
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
            .setTitle(getString(take ? R.string.key_person_title_take : R.string.key_person_title_return,
                key.name))
            .setMessage(R.string.key_person_message)
            .setView(padded(content))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.key_written_name_button, null)
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
                    input.setError(getString(R.string.key_name_error));
                    return;
                }
                if (!writtenName.contains(" ")) {
                    input.setError(getString(R.string.key_name_order_error));
                    return;
                }
                if (writtenName.length() > 120) {
                    input.setError(getString(R.string.key_name_too_long));
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
        toast(getString(take ? R.string.registering_withdrawal : R.string.registering_return));

        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(keyReference);
            String state = current.getString("estado");
            if (take && "Prestada".equals(state)) {
                throw new IllegalStateException(getString(R.string.key_already_borrowed,
                    current.getString("quienTiene")));
            }
            if (!take && !"Prestada".equals(state)) {
                throw new IllegalStateException(getString(R.string.key_already_available));
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
            adapter.highlightKey(key.id);
            successHaptic();
            toast(getString(take ? R.string.key_movement_success_take
                : R.string.key_movement_success_return, person.name, key.name, time));
        }).addOnFailureListener(error -> {
            finishKeyMovement(key.id);
            showMessage(getString(R.string.key_register_failed), friendlyError(error));
        });
    }

    @Override public boolean isKeyMovementPending(KeyItem key) {
        return pendingMovements.contains(key.id);
    }

    private void finishKeyMovement(String keyId) {
        pendingMovements.remove(keyId);
        adapter.notifyDataSetChanged();
    }

    private void successHaptic() {
        if (!AppPreferences.vibrationEnabled(this)) return;
        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    @Override public void onKeyOptions(View anchor, KeyItem key) {
        if (!networkAvailable) {
            toast(getString(R.string.offline_operations_blocked));
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.menu_hide_key);
        menu.setOnMenuItemClickListener(item -> {
            confirmHide(key);
            return true;
        });
        menu.show();
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.menu_add_key);
        menu.getMenu().add(R.string.menu_show_hidden_keys);
        menu.getMenu().add(R.string.menu_settings);
        menu.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            if (option.equals(getString(R.string.menu_add_key))) showAddDialog();
            else if (option.equals(getString(R.string.menu_show_hidden_keys))) showHiddenKeys();
            else if (option.equals(getString(R.string.menu_settings))) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });
        menu.show();
    }

    private void showAddDialog() {
        EditText input = dialogInput(getString(R.string.key_name_or_id_hint), "");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.add_key_title)
            .setView(padded(input))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_add, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError(getString(R.string.key_name_required));
                    return;
                }
                for (KeyItem key : visibleKeys) {
                    if (key.name.equalsIgnoreCase(name)) {
                        input.setError(getString(R.string.key_already_exists));
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
                    .addOnSuccessListener(ignored -> toast(getString(R.string.key_hidden_restored,
                        key.name)))
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_show_failed),
                        friendlyError(error)));
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
        }).addOnSuccessListener(ignored -> toast(getString(R.string.key_add_confirmation, name)))
            .addOnFailureListener(error -> showMessage(getString(R.string.error_add_failed),
                friendlyError(error)));
    }

    private void confirmHide(KeyItem key) {
        if ("Prestada".equals(key.state)) {
            showMessage(getString(R.string.key_hide_not_allowed),
                getString(R.string.key_hide_blocked, key.name));
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.hide_key_title)
            .setMessage(getString(R.string.key_hide_confirmation, key.name))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.key_hide_confirm, (dialog, which) ->
                database.collection("llaves").document(key.id).update("activo", false)
                    .addOnSuccessListener(ignored -> toast(getString(R.string.key_hidden)))
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_hide_failed),
                        friendlyError(error))))
            .show();
    }

    private void showHiddenKeys() {
        if (hiddenKeys.isEmpty()) {
            toast(getString(R.string.no_hidden_keys));
            return;
        }
        String[] names = new String[hiddenKeys.size()];
        for (int index = 0; index < names.length; index++) names[index] = hiddenKeys.get(index).name;
        new AlertDialog.Builder(this)
            .setTitle(R.string.show_hidden_keys_title)
            .setItems(names, (dialog, index) -> {
                KeyItem key = hiddenKeys.get(index);
                database.collection("llaves").document(key.id).update("activo", true)
                    .addOnSuccessListener(ignored -> toast(getString(R.string.key_hidden_restored,
                        key.name)))
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_show_failed),
                        friendlyError(error)));
            })
            .setNegativeButton(R.string.dialog_cancel, null)
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

    private String keyPersonId(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("E");
            for (int index = 0; index < 12; index++) {
                id.append(String.format(Locale.US, "%02x", digest[index] & 0xff));
            }
            return id.toString();
        } catch (Exception error) {
            throw new IllegalStateException(getString(R.string.key_save_name_failed));
        }
    }
    private static String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String friendlyError(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException && current.getMessage() != null) {
                return current.getMessage().replace("java.lang.IllegalStateException: ", "");
            }
            current = current.getCause();
        }
        String message = error.getMessage() == null ? "" : error.getMessage().toUpperCase(Locale.ROOT);
        if (message.contains("UNAVAILABLE") || message.contains("NETWORK") || message.contains("TIMEOUT")) {
            return getString(R.string.error_no_connection_retry);
        }
        if (message.contains("PERMISSION_DENIED")) {
            return getString(R.string.error_no_permission);
        }
        return getString(R.string.error_operation_failed);
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
            .setTitle(title).setMessage(message).setPositiveButton(R.string.dialog_accept, null).show());
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
            loadErrorDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(friendlyError(error) + "\n\n" + getString(R.string.error_old_data_visible))
                .setNegativeButton(R.string.dialog_close, null)
                .setPositiveButton(R.string.dialog_retry, (ignored, which) -> {
                    loadErrorDialogVisible = false;
                    retriedLoads.add(source);
                    retry.run();
                })
                .create();
            loadErrorDialog.setOnDismissListener(ignored -> {
                loadErrorDialogVisible = false;
                loadErrorDialog = null;
            });
            loadErrorDialog.show();
        });
    }

    private void dismissLoadErrorDialog() {
        if (loadErrorDialog != null && loadErrorDialog.isShowing()) loadErrorDialog.dismiss();
        loadErrorDialog = null;
        loadErrorDialogVisible = false;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (database == null || !networkAvailable) return;
        AdminAccess.checkRoleWithError(database, (admin, role, error) -> {
            if (isFinishing()) return;
            if (error != null) return;
            if (AdminAccess.BLOCKED.equals(role)) {
                startActivity(new Intent(this, BlockedActivity.class));
                finish();
            }
        });
    }

    @Override protected void onDestroy() {
        loadAttemptActive = false;
        cancelLoadTimeout();
        if (reconnectRetry != null) mainHandler.removeCallbacks(reconnectRetry);
        removeDataListeners();
        if (networkMonitor != null) networkMonitor.stop();
        super.onDestroy();
    }
}
