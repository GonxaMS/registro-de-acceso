package com.ejemplo.registroguardias;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.HashSet;
import java.util.Set;

public final class AccessActivity extends AppCompatActivity implements PeopleAdapter.Actions {
    private static final String PEOPLE_FILTER_ALL = "all";
    private static final String PEOPLE_FILTER_INSIDE = "inside";
    private static final String PEOPLE_FILTER_OUTSIDE = "outside";
    static final String PREFS_NAME = "registro_guardias";
    static final String USER_NAME_KEY = "operator_user_name";
    static final String SHEETS_WEB_URL = BuildConfig.SHEETS_WEB_URL;
    static final String EXTRA_OPEN_INSIDE_FILTER = "open_inside_filter";

    private final List<Person> visiblePeople = new ArrayList<>();
    private final List<Person> hiddenPeople = new ArrayList<>();
    private final List<Person> removedPeople = new ArrayList<>();
    private final List<Person> filteredPeople = new ArrayList<>();
    private final Set<String> pendingMovements = new HashSet<>();

    private FirebaseFirestore database;
    private FirebaseAuth authentication;
    private ListenerRegistration peopleListener;
    private ListenerRegistration keysListener;
    private SharedPreferences preferences;
    private EditText search;
    private TextView count;
    private TextView borrowedKeys;
    private TextView peopleFilterAll;
    private TextView peopleFilterInside;
    private TextView peopleFilterOutside;
    private TextView offlineBanner;
    private PeopleAdapter adapter;
    private NetworkMonitor networkMonitor;
    private boolean networkAvailable = true;
    private String peopleFilter = PEOPLE_FILTER_ALL;

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (currentUser().isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        boolean openInsideFilter = getIntent().getBooleanExtra(EXTRA_OPEN_INSIDE_FILTER, false);
        if (state == null && !openInsideFilter && AppPreferences.START_KEYS.equals(
            preferences.getString(AppPreferences.START_SCREEN_KEY, AppPreferences.START_PERSONAL))) {
            startActivity(new Intent(this, KeysActivity.class));
            finish();
            return;
        }
        if (openInsideFilter) {
            peopleFilter = PEOPLE_FILTER_INSIDE;
        }

        setContentView(R.layout.activity_main);
        applyWindowInsets();
        search = findViewById(R.id.inputSearch);
        count = findViewById(R.id.txtCount);
        borrowedKeys = findViewById(R.id.txtBorrowedKeys);
        peopleFilterAll = findViewById(R.id.filterPeopleAll);
        peopleFilterInside = findViewById(R.id.filterPeopleInside);
        peopleFilterOutside = findViewById(R.id.filterPeopleOutside);
        offlineBanner = findViewById(R.id.offlineBanner);
        adapter = new PeopleAdapter(this, filteredPeople, this);
        adapter.setToday(today());
        ((ListView) findViewById(R.id.listPeople)).setAdapter(adapter);
        ((TextView) findViewById(R.id.txtToday)).setText(
            new SimpleDateFormat(getString(R.string.date_format_full), new Locale("es", "AR"))
                .format(new Date())
        );
        findViewById(R.id.btnMenu).setOnClickListener(this::showMainMenu);
        findViewById(R.id.btnSheetsShortcut).setOnClickListener(view -> openSheets());
        findViewById(R.id.btnKeysShortcut).setOnClickListener(view ->
            startActivity(new Intent(this, KeysActivity.class)));
        peopleFilterAll.setOnClickListener(view -> setPeopleFilter(PEOPLE_FILTER_ALL));
        peopleFilterInside.setOnClickListener(view -> setPeopleFilter(PEOPLE_FILTER_INSIDE));
        peopleFilterOutside.setOnClickListener(view -> setPeopleFilter(PEOPLE_FILTER_OUTSIDE));
        updatePeopleFilterButtons();
        networkMonitor = new NetworkMonitor(this, this::updateNetworkState);
        networkMonitor.start();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filterPeople(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        count.setText(R.string.loading_people);
        authentication = FirebaseAuth.getInstance();
        database = FirebaseFirestore.getInstance();
        if (authentication.getCurrentUser() != null) startListeners();
        else authentication.signInAnonymously()
            .addOnSuccessListener(result -> startListeners())
            .addOnFailureListener(error -> showMessage(getString(R.string.error_no_connection_title),
                friendlyError(error)));
    }

    private void openSheets() {
        if (BuildConfig.USE_FIREBASE_EMULATOR || SHEETS_WEB_URL.trim().isEmpty()) {
            toast(getString(R.string.sheet_unavailable));
            return;
        }
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
        registerDevice();
    }

    private void registerDevice() {
        if (authentication.getCurrentUser() == null) return;
        String uid = authentication.getCurrentUser().getUid();
        Map<String, Object> device = new HashMap<>();
        device.put("nombre", currentUser());
        device.put("actualizado", FieldValue.serverTimestamp());
        DocumentReference reference = database.collection("dispositivos").document(uid);
        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(reference);
            if (current.exists()) transaction.update(reference, device);
            else {
                device.put("estado", AdminAccess.BLOCKED);
                transaction.set(reference, device);
            }
            return null;
        }).addOnCompleteListener(task -> AdminAccess.checkRole(database, (allowed, role) -> {
            if (AdminAccess.BLOCKED.equals(role)) {
                startActivity(new Intent(this, BlockedActivity.class));
                finish();
                return;
            }
            listenForPeople();
            listenForBorrowedKeys();
        }));
    }

    private void listenForPeople() {
        peopleListener = database.collection("personal").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                showMessage(getString(R.string.error_load_failed), friendlyError(error));
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
                borrowedKeys.setText(R.string.borrowed_keys_unavailable);
                return;
            }
            int borrowed = 0;
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Boolean active = document.getBoolean("activo");
                if (active != null && !active) continue;
                if ("Prestada".equals(document.getString("estado"))) borrowed++;
            }
            borrowedKeys.setText(getString(R.string.borrowed_keys_count, borrowed));
        });
    }

    private void filterPeople(String query) {
        filteredPeople.clear();
        String normalized = query.trim().toLowerCase(Locale.getDefault());
        for (Person person : visiblePeople) {
            boolean matchesState = PEOPLE_FILTER_ALL.equals(peopleFilter)
                || (PEOPLE_FILTER_INSIDE.equals(peopleFilter) && "Dentro".equals(person.state))
                || (PEOPLE_FILTER_OUTSIDE.equals(peopleFilter) && "Fuera".equals(person.state));
            if (matchesState && person.name.toLowerCase(Locale.getDefault()).contains(normalized)) {
                filteredPeople.add(person);
            }
        }
        int inside = 0;
        for (Person person : filteredPeople) if ("Dentro".equals(person.state)) inside++;
        count.setText(filteredPeople.size() == 1
            ? getString(R.string.people_summary_one, inside)
            : getString(R.string.people_summary_many, filteredPeople.size(), inside));
        adapter.notifyDataSetChanged();
    }

    private void setPeopleFilter(String filter) {
        peopleFilter = filter;
        updatePeopleFilterButtons();
        filterPeople(search.getText().toString());
    }

    private void updatePeopleFilterButtons() {
        peopleFilterAll.setSelected(PEOPLE_FILTER_ALL.equals(peopleFilter));
        peopleFilterInside.setSelected(PEOPLE_FILTER_INSIDE.equals(peopleFilter));
        peopleFilterOutside.setSelected(PEOPLE_FILTER_OUTSIDE.equals(peopleFilter));
    }

    private void updateNetworkState(boolean available) {
        runOnUiThread(() -> {
            networkAvailable = available;
            offlineBanner.setVisibility(available ? View.GONE : View.VISIBLE);
            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }

    @Override public void onMovement(Person person, String type) {
        if (!networkAvailable) {
            toast(getString(R.string.offline_operations_blocked));
            return;
        }
        if (!pendingMovements.add(person.id)) return;
        adapter.notifyDataSetChanged();
        boolean entry = "Ingreso".equals(type);
        String date = today();
        if (entry && "Dentro".equals(person.state)) {
            finishMovement(person.id);
            showMessage(getString(R.string.error_register_failed),
                getString(R.string.person_already_inside, person.name));
            return;
        }
        if (!entry && "Fuera".equals(person.state)) {
            finishMovement(person.id);
            showMessage(getString(R.string.error_register_failed),
                getString(R.string.person_missing_entry, person.name));
            return;
        }
        if (entry && date.equals(person.date) && "Salida".equals(person.lastMovement)) {
            finishMovement(person.id);
            showMessage(getString(R.string.error_register_failed),
                getString(R.string.person_completed_today, person.name));
            return;
        }

        String time = currentTime();
        String newState = entry ? "Dentro" : "Fuera";
        String registeredBy = currentUser();
        DocumentReference personReference = database.collection("personal").document(person.id);
        DocumentReference metaReference = database.collection("meta").document("config");
        toast(getString(R.string.registering));

        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(personReference);
            DocumentSnapshot config = transaction.get(metaReference);
            String state = current.getString("estado");
            String lastDate = current.getString("fecha");
            String lastMovement = current.getString("ultimoMovimiento");
            if (entry && "Dentro".equals(state)) {
                throw new IllegalStateException(getString(R.string.person_already_inside, person.name));
            }
            if (!entry && !"Dentro".equals(state)) {
                throw new IllegalStateException(getString(R.string.person_missing_entry, person.name));
            }
            if (entry && date.equals(lastDate) && "Salida".equals(lastMovement)) {
                throw new IllegalStateException(getString(R.string.person_completed_today, person.name));
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
        }).addOnSuccessListener(movementId -> {
            finishMovement(person.id);
            adapter.highlightPerson(person.id);
            successHaptic();
            toast(getString(R.string.movement_registered, type, time, registeredBy));
        }).addOnFailureListener(error -> {
            finishMovement(person.id);
            showMessage(getString(R.string.error_register_failed), friendlyError(error));
        });
    }

    @Override public boolean isMovementPending(Person person) {
        return pendingMovements.contains(person.id);
    }

    @Override public boolean isNetworkAvailable() {
        return networkAvailable;
    }

    private void finishMovement(String personId) {
        pendingMovements.remove(personId);
        adapter.notifyDataSetChanged();
    }

    private void successHaptic() {
        if (!AppPreferences.vibrationEnabled(this)) return;
        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
        if (!networkAvailable) {
            toast(getString(R.string.offline_operations_blocked));
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.menu_modify_time);
        menu.getMenu().add(R.string.menu_cancel_entry);
        menu.getMenu().add(R.string.menu_cancel_exit);
        menu.getMenu().add(R.string.menu_remove_person);
        menu.getMenu().add(R.string.menu_hide_person);
        menu.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            if (option.equals(getString(R.string.menu_modify_time))) loadTodayMovements(person);
            else if (option.equals(getString(R.string.menu_cancel_entry))) startCancellation(person, "Ingreso");
            else if (option.equals(getString(R.string.menu_cancel_exit))) startCancellation(person, "Salida");
            else if (option.equals(getString(R.string.menu_remove_person))) confirmRemove(person);
            else confirmHide(person);
            return true;
        });
        menu.show();
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.menu_add_person);
        menu.getMenu().add(R.string.menu_show_hidden_people);
        menu.getMenu().add(R.string.menu_settings);
        menu.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            if (option.equals(getString(R.string.menu_add_person))) showAddDialog();
            else if (option.equals(getString(R.string.menu_show_hidden_people))) showHiddenPeople();
            else if (option.equals(getString(R.string.menu_settings))) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });
        menu.show();
    }

    private void showAddDialog() {
        EditText input = dialogInput(getString(R.string.person_name_hint), "");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.add_person_title)
            .setView(padded(input))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_add, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError(getString(R.string.enter_person_name));
                    return;
                }
                for (Person person : visiblePeople) {
                    if (person.name.equalsIgnoreCase(name)) {
                        input.setError(getString(R.string.person_already_exists));
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
                        toast(getString(R.string.person_returned_to_list, person.name));
                    })
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_show_failed),
                        friendlyError(error)));
                return;
            }
        }

        for (Person person : removedPeople) {
            if (person.name.equalsIgnoreCase(name)) {
                showMessage(getString(R.string.person_removed),
                    getString(R.string.person_removed_message, person.name));
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
            toast(getString(R.string.person_added, person.name));
        }).addOnFailureListener(error -> showMessage(getString(R.string.error_add_failed),
            friendlyError(error)));
    }

    private void confirmHide(Person person) {
        if ("Dentro".equals(person.state)) {
            showMessage(getString(R.string.error_hide_failed),
                getString(R.string.person_cannot_hide, person.name));
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.hide_person_title)
            .setMessage(getString(R.string.hide_person_message, person.name))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.confirm_hide, (dialog, which) ->
                database.collection("personal").document(person.id).update("activo", false, "retirado", false)
                    .addOnSuccessListener(ignored -> {
                        toast(getString(R.string.person_hidden));
                    })
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_hide_failed),
                        friendlyError(error))))
            .show();
    }

    private void showHiddenPeople() {
        if (hiddenPeople.isEmpty()) {
            toast(getString(R.string.no_hidden_people));
            return;
        }
        String[] names = new String[hiddenPeople.size()];
        for (int index = 0; index < names.length; index++) names[index] = hiddenPeople.get(index).name;
        new AlertDialog.Builder(this)
            .setTitle(R.string.show_hidden_people_title)
            .setItems(names, (dialog, index) -> {
                Person person = hiddenPeople.get(index);
                database.collection("personal").document(person.id).update("activo", true, "retirado", false)
                    .addOnSuccessListener(ignored -> {
                        toast(getString(R.string.person_returned_to_list, person.name));
                    })
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_show_failed),
                        friendlyError(error)));
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void confirmRemove(Person person) {
        if ("Dentro".equals(person.state)) {
            showMessage(getString(R.string.error_remove_failed),
                getString(R.string.person_cannot_remove, person.name));
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.remove_person_title)
            .setMessage(getString(R.string.remove_person_message, person.name))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.confirm_remove, (dialog, which) ->
                database.collection("personal").document(person.id)
                    .update("activo", false, "retirado", true, "actualizado", FieldValue.serverTimestamp())
                    .addOnSuccessListener(ignored -> toast(getString(R.string.person_removed)))
                    .addOnFailureListener(error -> showMessage(getString(R.string.error_remove_failed),
                        friendlyError(error))))
            .show();
    }

    private void showChangeUserDialog() {
        EditText input = dialogInput(getString(R.string.settings_user_name_hint), currentUser());
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.change_user_title)
            .setMessage(R.string.change_user_message)
            .setView(padded(input))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.length() < 2) {
                    input.setError(getString(R.string.enter_user_name));
                    return;
                }
                preferences.edit().putString(USER_NAME_KEY, name).apply();
                dialog.dismiss();
                toast(getString(R.string.user_changed, name));
            }));
        dialog.show();
    }

    private static final class DailyMovements {
        DocumentSnapshot entry;
        DocumentSnapshot exit;
    }

    private void loadTodayMovements(Person person) {
        String date = today();
        toast(getString(R.string.searching_today_movements));
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
            .addOnFailureListener(error -> showMessage(getString(R.string.error_read_schedules),
                friendlyError(error)));
    }

    private void startCancellation(Person person, String type) {
        String date = today();
        toast(getString(R.string.checking_today_record));
        database.collection("movimientos").whereEqualTo("personalId", person.id).get()
            .addOnSuccessListener(snapshot -> {
                DailyMovements daily = effectiveMovements(snapshot.getDocuments(), date);
                DocumentSnapshot target = "Ingreso".equals(type) ? daily.entry : daily.exit;
                if (target == null) {
                    showMessage(getString(R.string.error_remove_failed),
                        getString(R.string.no_movement_today,
                            type.toLowerCase(Locale.getDefault()), person.name));
                    return;
                }
                if ("Ingreso".equals(type) && daily.exit != null) {
                    showMessage(getString(R.string.cannot_remove_entry),
                        getString(R.string.remove_entry_first));
                    return;
                }
                if ("Salida".equals(type) && daily.entry == null) {
                    showMessage(getString(R.string.cannot_remove_exit),
                        getString(R.string.missing_entry_today));
                    return;
                }
                confirmCancellation(person, type, target, daily, date);
            })
            .addOnFailureListener(error -> showMessage(getString(R.string.error_read_schedules),
                friendlyError(error)));
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
            .setTitle(getString(R.string.remove_movement_title,
                type.toLowerCase(Locale.getDefault())))
            .setMessage(getString(R.string.remove_movement_message,
                type.toLowerCase(Locale.getDefault()), person.name, shownTime(target)))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.confirm_remove, (dialog, which) ->
                saveCancellation(person, type, target, daily, date))
            .show();
    }

    private void saveCancellation(Person person, String type, DocumentSnapshot target,
                                  DailyMovements daily, String date) {
        toast(getString(R.string.removing_movement, type.toLowerCase(Locale.getDefault())));
        DocumentReference metaReference = database.collection("meta").document("config");
        DocumentReference personReference = database.collection("personal").document(person.id);
        String registeredBy = currentUser();
        database.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(personReference);
            if ("Ingreso".equals(type) && !"Dentro".equals(current.getString("estado"))) {
                throw new IllegalStateException(getString(R.string.cannot_remove_entry));
            }
            if ("Salida".equals(type) && (!"Fuera".equals(current.getString("estado"))
                || !"Salida".equals(current.getString("ultimoMovimiento")))) {
                throw new IllegalStateException(getString(R.string.cannot_remove_exit));
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
        }).addOnSuccessListener(cancellationId ->
            toast(getString(R.string.movement_removed_today, type, registeredBy)))
            .addOnFailureListener(error -> showMessage(getString(R.string.error_remove_failed),
                friendlyError(error)));
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
            showMessage(getString(R.string.no_schedule_to_modify),
                getString(R.string.no_today_schedules, person.name));
            return;
        }
        if (daily.entry != null && daily.exit != null) {
            String[] options = {
                getString(R.string.entry_time_label, shownTime(daily.entry)),
                getString(R.string.exit_time_label, shownTime(daily.exit))
            };
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.modify_time_title, person.name))
                .setItems(options, (dialog, index) -> {
                    if (index == 0) openTimePicker(person, "Ingreso", daily.entry, daily, date);
                    else openTimePicker(person, "Salida", daily.exit, daily, date);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
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
                showMessage(getString(R.string.invalid_time_title),
                    getString(R.string.invalid_entry_time));
                return;
            }
            if ("Salida".equals(type) && daily.entry != null
                && selected <= minutes(shownTime(daily.entry))) {
                showMessage(getString(R.string.invalid_time_title),
                    getString(R.string.invalid_exit_time));
                return;
            }
            String newTime = String.format(Locale.US, "%02d:%02d", hour, minute);
            confirmTimeChange(person, type, movement, date, newTime);
        }, initial / 60, initial % 60, true).show();
    }

    private void confirmTimeChange(Person person, String type, DocumentSnapshot previous,
                                   String date, String newTime) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.confirm_new_time)
            .setMessage(getString(R.string.new_time_message, type, person.name, newTime))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save, (dialog, which) ->
                saveCorrectedTime(person, type, previous, date, newTime))
            .show();
    }

    private void saveCorrectedTime(Person person, String type, DocumentSnapshot previous,
                                   String date, String newTime) {
        toast(getString(R.string.saving_correction));
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
        }).addOnSuccessListener(correctionId ->
            toast(getString(R.string.movement_updated, type, newTime, registeredBy)))
            .addOnFailureListener(error -> showMessage(getString(R.string.error_modify_failed),
                friendlyError(error)));
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
        int padding = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(padding, 0, padding, 0);
        container.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return container;
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
            .setTitle(title).setMessage(message).setPositiveButton(R.string.dialog_accept, null).show());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (database == null || authentication == null
            || authentication.getCurrentUser() == null || !networkAvailable) return;
        AdminAccess.checkRole(database, (allowed, role) -> {
            if (isFinishing()) return;
            if (AdminAccess.BLOCKED.equals(role)) {
                startActivity(new Intent(this, BlockedActivity.class));
                finish();
                return;
            }
        });
    }

    @Override protected void onDestroy() {
        if (peopleListener != null) peopleListener.remove();
        if (keysListener != null) keysListener.remove();
        if (networkMonitor != null) networkMonitor.stop();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_INSIDE_FILTER, false) && search != null) {
            setPeopleFilter(PEOPLE_FILTER_INSIDE);
        }
    }
}
