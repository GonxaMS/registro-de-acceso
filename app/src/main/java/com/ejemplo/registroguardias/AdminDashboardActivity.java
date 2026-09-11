package com.ejemplo.registroguardias;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminDashboardActivity extends AppCompatActivity {
    private static final String ADMIN_USER = "Admin";
    private static final Locale LOCALE = new Locale("es", "AR");

    private final List<DocumentSnapshot> synchronizationFailures = new ArrayList<>();
    private final List<DocumentSnapshot> appFailures = new ArrayList<>();

    private FirebaseFirestore database;
    private ListenerRegistration syncStatusListener;
    private ListenerRegistration syncErrorsListener;
    private ListenerRegistration appErrorsListener;
    private ListenerRegistration rebuildListener;
    private TextView syncStatus;
    private TextView syncErrors;
    private TextView monthButton;
    private Button rebuildButton;
    private Button resolveErrorsButton;
    private String selectedMonth;

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        super.onCreate(state);
        setContentView(R.layout.activity_admin_dashboard);
        applyWindowInsets();

        syncStatus = findViewById(R.id.txtDashboardSyncStatus);
        syncErrors = findViewById(R.id.txtDashboardSyncErrors);
        monthButton = findViewById(R.id.btnDashboardMonth);
        rebuildButton = findViewById(R.id.btnDashboardRebuildSheets);
        resolveErrorsButton = findViewById(R.id.btnDashboardResolveErrors);
        selectedMonth = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        updateMonthLabel();

        findViewById(R.id.btnAdminDashboardBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnDashboardPeople).setOnClickListener(view ->
            startActivity(new Intent(this, AdminCorrectionsActivity.class)));
        findViewById(R.id.btnDashboardKeys).setOnClickListener(view ->
            startActivity(new Intent(this, AdminKeysActivity.class)));
        findViewById(R.id.btnDashboardDevices).setOnClickListener(view ->
            startActivity(new Intent(this, AdminDevicesActivity.class)));
        monthButton.setOnClickListener(view -> chooseMonth());
        rebuildButton.setOnClickListener(view -> confirmRebuildSheets());
        resolveErrorsButton.setOnClickListener(view -> confirmResolveErrors());

        database = FirebaseFirestore.getInstance();
        AdminAccess.check(database, (allowed, uid) -> {
            if (!allowed) {
                Toast.makeText(this, getString(R.string.admin_permission_required),
                    Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            listenForSynchronization();
            listenForRebuildRequest();
        });
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.adminDashboardRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void chooseMonth() {
        Calendar selected = Calendar.getInstance();
        try {
            selected.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedMonth));
        } catch (Exception ignored) {
            selected.setTime(new Date());
        }
        new DatePickerDialog(this, (picker, year, month, day) -> {
            selectedMonth = String.format(Locale.US, "%04d-%02d-01", year, month + 1);
            updateMonthLabel();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), 1).show();
    }

    private void updateMonthLabel() {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedMonth);
            String readable = new SimpleDateFormat(getString(R.string.date_format_month), LOCALE)
                .format(date);
            monthButton.setText(getString(R.string.admin_month_label,
                readable.substring(0, 1).toUpperCase(LOCALE) + readable.substring(1)));
        } catch (Exception ignored) {
            monthButton.setText(R.string.admin_select_month);
        }
    }

    private void listenForRebuildRequest() {
        rebuildListener = database.collection("comandosAdmin").document("rehacerPlanillas")
            .addSnapshotListener((document, error) -> {
                if (error != null || document == null || !document.exists()) {
                    rebuildButton.setText(R.string.admin_rebuild_default);
                    return;
                }
                String state = value(document.getString("estado"));
                if ("Pendiente".equals(state)) rebuildButton.setText(R.string.admin_rebuild_pending);
                else if ("Procesando".equals(state)) rebuildButton.setText(R.string.admin_rebuild_processing);
                else if ("Error".equals(state)) rebuildButton.setText(R.string.admin_rebuild_retry);
                else rebuildButton.setText(R.string.admin_rebuild_default);
            });
    }

    private void listenForSynchronization() {
        syncStatusListener = database.collection("sincronizacion").document("sheets")
            .addSnapshotListener((document, error) -> {
                if (error != null) {
                    syncStatus.setText(R.string.admin_sync_status_failed);
                    return;
                }
                if (document == null || !document.exists()) {
                    syncStatus.setText(R.string.admin_sync_not_installed);
                    return;
                }
                String state = value(document.getString("estado"));
                String lastError = value(document.getString("ultimoError"));
                Timestamp lastRun = document.getTimestamp("ultimaEjecucion");
                String when = lastRun == null ? getString(R.string.admin_sync_no_date)
                    : new SimpleDateFormat(getString(R.string.date_format_timestamp), LOCALE)
                        .format(lastRun.toDate());
                String text = getString(R.string.admin_sync_state,
                    state.isEmpty() ? getString(R.string.admin_sync_no_state) : state,
                    getString(R.string.admin_sync_last_run, when));
                if (!lastError.isEmpty()) {
                    text += "\n" + getString(R.string.admin_sync_last_error, lastError);
                }
                syncStatus.setText(text);
            });

        syncErrorsListener = database.collection("erroresSincronizacion")
            .orderBy("ocurrido", Query.Direction.DESCENDING).limit(10)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    syncErrors.setText(R.string.admin_sync_errors_failed);
                    return;
                }
                synchronizationFailures.clear();
                if (snapshot != null) synchronizationFailures.addAll(snapshot.getDocuments());
                renderPendingErrors();
            });

        appErrorsListener = database.collection("erroresApp")
            .orderBy("ocurrido", Query.Direction.DESCENDING).limit(10)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    syncErrors.setText(R.string.admin_app_errors_failed);
                    return;
                }
                appFailures.clear();
                if (snapshot != null) appFailures.addAll(snapshot.getDocuments());
                renderPendingErrors();
            });
    }

    private void renderPendingErrors() {
        StringBuilder text = new StringBuilder();
        int unresolved = appendPendingErrors(text, appFailures)
            + appendPendingErrors(text, synchronizationFailures);
        syncErrors.setText(unresolved == 0 ? getString(R.string.admin_no_pending_errors)
            : getString(unresolved == 1 ? R.string.admin_pending_errors_one
                : R.string.admin_pending_errors_many, unresolved) + text);
        resolveErrorsButton.setEnabled(unresolved > 0);
    }

    private int appendPendingErrors(StringBuilder text, List<DocumentSnapshot> documents) {
        int unresolved = 0;
        for (DocumentSnapshot document : documents) {
            if (Boolean.TRUE.equals(document.getBoolean("resuelto"))) continue;
            unresolved++;
            Timestamp occurred = document.getTimestamp("ocurrido");
            String when = occurred == null ? getString(R.string.admin_error_no_date)
                : new SimpleDateFormat(getString(R.string.date_format_short_timestamp), LOCALE)
                    .format(occurred.toDate());
            if (text.length() > 0) text.append("\n\n");
            text.append(getString(R.string.admin_errors_log_date, when,
                value(document.getString("origen")), value(document.getString("mensaje"))));
        }
        return unresolved;
    }

    private void confirmResolveErrors() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.admin_resolve_title)
            .setMessage(R.string.admin_resolve_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.admin_mark_resolved, (dialog, which) -> resolveErrors())
            .show();
    }

    private void resolveErrors() {
        resolveErrorsButton.setEnabled(false);
        database.collection("erroresSincronizacion").whereEqualTo("resuelto", false).get()
            .addOnSuccessListener(syncSnapshot -> database.collection("erroresApp")
                .whereEqualTo("resuelto", false).get().addOnSuccessListener(appSnapshot -> {
                    WriteBatch batch = database.batch();
                    int count = 0;
                    for (DocumentSnapshot document : syncSnapshot.getDocuments()) {
                        if (count >= 500) break;
                        batch.update(document.getReference(), "resuelto", true);
                        count++;
                    }
                    for (DocumentSnapshot document : appSnapshot.getDocuments()) {
                        if (count >= 500) break;
                        batch.update(document.getReference(), "resuelto", true);
                        count++;
                    }
                    final int resolved = count;
                    if (resolved == 0) {
                        toast(getString(R.string.admin_no_errors_pending));
                        return;
                    }
                    batch.commit()
                        .addOnSuccessListener(ignored -> toast(getString(R.string.admin_resolved_count,
                            resolved)))
                        .addOnFailureListener(error -> showMessage(getString(R.string.admin_resolve_failed),
                            friendlyError(error)));
                }).addOnFailureListener(error ->
                    showMessage(getString(R.string.admin_errors_query_failed), friendlyError(error))))
            .addOnFailureListener(error -> showMessage(getString(R.string.admin_errors_query_failed),
                friendlyError(error)));
    }

    private void confirmRebuildSheets() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.admin_rebuild_title)
            .setMessage(R.string.admin_rebuild_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.admin_request, (dialog, which) -> requestSheetsRebuild())
            .show();
    }

    private void requestSheetsRebuild() {
        Map<String, Object> request = new HashMap<>();
        request.put("estado", "Pendiente");
        request.put("fechaMes", selectedMonth);
        request.put("solicitadoPor", ADMIN_USER);
        request.put("solicitado", FieldValue.serverTimestamp());
        database.collection("comandosAdmin").document("rehacerPlanillas").set(request)
            .addOnSuccessListener(ignored -> toast(getString(R.string.admin_request_sent)))
            .addOnFailureListener(error -> showMessage(getString(R.string.admin_request_failed),
                friendlyError(error)));
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toUpperCase(Locale.ROOT);
        if (message.contains("UNAVAILABLE") || message.contains("NETWORK") || message.contains("TIMEOUT")) {
            return getString(R.string.error_no_connection_retry);
        }
        if (message.contains("PERMISSION_DENIED")) return getString(R.string.error_no_permission);
        return getString(R.string.error_operation_failed);
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.dialog_accept, null).show());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (database == null || !NetworkMonitor.isAvailable(this)) return;
        AdminAccess.checkRole(database, (allowed, role) -> {
            if (isFinishing() || AdminAccess.ADMIN.equals(role)) return;
            if (AdminAccess.BLOCKED.equals(role)) {
                startActivity(new Intent(this, BlockedActivity.class));
            }
            finish();
        });
    }

    @Override protected void onDestroy() {
        if (syncStatusListener != null) syncStatusListener.remove();
        if (syncErrorsListener != null) syncErrorsListener.remove();
        if (appErrorsListener != null) appErrorsListener.remove();
        if (rebuildListener != null) rebuildListener.remove();
        super.onDestroy();
    }
}
