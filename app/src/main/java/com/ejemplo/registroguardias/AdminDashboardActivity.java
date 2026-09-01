package com.ejemplo.registroguardias;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

public final class AdminDashboardActivity extends Activity {
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
                Toast.makeText(this, "Este dispositivo no tiene permiso de administrador",
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
            String readable = new SimpleDateFormat("MMMM 'de' yyyy", LOCALE).format(date);
            monthButton.setText("Mes: " + readable.substring(0, 1).toUpperCase(LOCALE)
                + readable.substring(1));
        } catch (Exception ignored) {
            monthButton.setText("Seleccionar mes");
        }
    }

    private void listenForRebuildRequest() {
        rebuildListener = database.collection("comandosAdmin").document("rehacerPlanillas")
            .addSnapshotListener((document, error) -> {
                if (error != null || document == null || !document.exists()) {
                    rebuildButton.setText("Rehacer planillas");
                    return;
                }
                String state = value(document.getString("estado"));
                if ("Pendiente".equals(state)) rebuildButton.setText("Solicitud pendiente…");
                else if ("Procesando".equals(state)) rebuildButton.setText("Procesando planillas…");
                else if ("Error".equals(state)) rebuildButton.setText("Reintentar reconstrucción");
                else rebuildButton.setText("Rehacer planillas");
            });
    }

    private void listenForSynchronization() {
        syncStatusListener = database.collection("sincronizacion").document("sheets")
            .addSnapshotListener((document, error) -> {
                if (error != null) {
                    syncStatus.setText("No se pudo consultar el estado de Sheets");
                    return;
                }
                if (document == null || !document.exists()) {
                    syncStatus.setText("Sincronización todavía no instalada");
                    return;
                }
                String state = value(document.getString("estado"));
                String lastError = value(document.getString("ultimoError"));
                Timestamp lastRun = document.getTimestamp("ultimaEjecucion");
                String when = lastRun == null ? "sin fecha"
                    : new SimpleDateFormat("dd/MM/yyyy HH:mm", LOCALE).format(lastRun.toDate());
                String text = (state.isEmpty() ? "Sin estado" : state)
                    + "\nÚltima ejecución: " + when;
                if (!lastError.isEmpty()) text += "\nÚltimo error: " + lastError;
                syncStatus.setText(text);
            });

        syncErrorsListener = database.collection("erroresSincronizacion")
            .orderBy("ocurrido", Query.Direction.DESCENDING).limit(10)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    syncErrors.setText("No se pudo consultar el registro de fallos.");
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
                    syncErrors.setText("No se pudieron consultar los errores de la app.");
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
        syncErrors.setText(unresolved == 0 ? "Sin errores pendientes."
            : unresolved + (unresolved == 1 ? " error pendiente\n\n" : " errores pendientes\n\n")
                + text);
        resolveErrorsButton.setEnabled(unresolved > 0);
    }

    private int appendPendingErrors(StringBuilder text, List<DocumentSnapshot> documents) {
        int unresolved = 0;
        for (DocumentSnapshot document : documents) {
            if (Boolean.TRUE.equals(document.getBoolean("resuelto"))) continue;
            unresolved++;
            Timestamp occurred = document.getTimestamp("ocurrido");
            String when = occurred == null ? "Sin fecha"
                : new SimpleDateFormat("dd/MM HH:mm", LOCALE).format(occurred.toDate());
            if (text.length() > 0) text.append("\n\n");
            text.append(when).append(" · ").append(value(document.getString("origen")))
                .append("\n").append(value(document.getString("mensaje")));
        }
        return unresolved;
    }

    private void confirmResolveErrors() {
        new AlertDialog.Builder(this)
            .setTitle("Resolver errores anteriores")
            .setMessage("Dejarán de aparecer como pendientes, pero permanecerán guardados como historial.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Marcar como resueltos", (dialog, which) -> resolveErrors())
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
                        toast("No hay errores pendientes");
                        return;
                    }
                    batch.commit()
                        .addOnSuccessListener(ignored -> toast(resolved + " errores marcados como resueltos"))
                        .addOnFailureListener(error -> showMessage("No se pudieron resolver", friendlyError(error)));
                }).addOnFailureListener(error ->
                    showMessage("No se pudieron consultar los errores", friendlyError(error))))
            .addOnFailureListener(error -> showMessage("No se pudieron consultar los errores", friendlyError(error)));
    }

    private void confirmRebuildSheets() {
        new AlertDialog.Builder(this)
            .setTitle("Rehacer planillas")
            .setMessage("Se volverán a crear las planillas de personal y llaves del mes seleccionado usando los datos de Firebase.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Solicitar", (dialog, which) -> requestSheetsRebuild())
            .show();
    }

    private void requestSheetsRebuild() {
        Map<String, Object> request = new HashMap<>();
        request.put("estado", "Pendiente");
        request.put("fechaMes", selectedMonth);
        request.put("solicitadoPor", ADMIN_USER);
        request.put("solicitado", FieldValue.serverTimestamp());
        database.collection("comandosAdmin").document("rehacerPlanillas").set(request)
            .addOnSuccessListener(ignored -> toast("Solicitud enviada. Las planillas se reconstruirán pronto"))
            .addOnFailureListener(error -> showMessage("No se pudo solicitar", friendlyError(error)));
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }

    private static String friendlyError(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toUpperCase(Locale.ROOT);
        if (message.contains("UNAVAILABLE") || message.contains("NETWORK") || message.contains("TIMEOUT")) {
            return "No hay conexión. Intenta nuevamente.";
        }
        if (message.contains("PERMISSION_DENIED")) return "No tienes permiso para realizar esta acción.";
        return "No se pudo completar la operación. Intenta nuevamente.";
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Aceptar", null).show());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        if (syncStatusListener != null) syncStatusListener.remove();
        if (syncErrorsListener != null) syncErrorsListener.remove();
        if (appErrorsListener != null) appErrorsListener.remove();
        if (rebuildListener != null) rebuildListener.remove();
        super.onDestroy();
    }
}
