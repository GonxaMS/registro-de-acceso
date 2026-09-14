package com.ejemplo.registroguardias;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdminDevicesActivity extends AppCompatActivity {
    private final List<Device> devices = new ArrayList<>();
    private final Map<String, Boolean> permissions = new HashMap<>();
    private FirebaseFirestore database;
    private ListenerRegistration devicesListener;
    private ListenerRegistration adminsListener;
    private LinearLayout container;
    private TextView status;
    private String ownUid = "";

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        AccessibilityMode.apply(this);
        super.onCreate(state);
        setContentView(R.layout.activity_admin_devices);
        AccessibilityMode.applyTo(this, findViewById(android.R.id.content));
        applyWindowInsets();
        container = findViewById(R.id.adminDevicesContainer);
        status = findViewById(R.id.txtAdminDevicesStatus);
        findViewById(R.id.btnAdminDevicesBack).setOnClickListener(view -> finish());
        database = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            ownUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        AdminAccess.check(database, (allowed, uid) -> {
            if (!allowed) {
                Toast.makeText(this, getString(R.string.admin_permission_required),
                    Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            listenForPermissions();
            listenForDevices();
        });
    }

    private void listenForPermissions() {
        adminsListener = database.collection("administradores").addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                status.setText(R.string.admin_permissions_load_failed);
                return;
            }
            permissions.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                permissions.put(document.getId(), Boolean.TRUE.equals(document.getBoolean("activo")));
            }
            render();
        });
    }

    private void listenForDevices() {
        devicesListener = database.collection("dispositivos").addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                status.setText(R.string.admin_devices_load_failed);
                return;
            }
            devices.clear();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                devices.add(new Device(document.getId(), value(document.getString("nombre")),
                    value(document.getString("estado"))));
            }
            Collections.sort(devices, (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));
            render();
        });
    }

    private void render() {
        container.removeAllViews();
        status.setText(devices.isEmpty() ? getString(R.string.admin_no_other_devices)
            : devices.size() == 1 ? getString(R.string.admin_device_count_one)
                : getString(R.string.admin_device_count_many, devices.size()));
        for (Device device : devices) {
            boolean own = ownUid.equals(device.uid);
            boolean enabled = Boolean.TRUE.equals(permissions.get(device.uid));
            String role = enabled ? AdminAccess.ADMIN
                : AdminAccess.NORMAL.equals(device.state) ? AdminAccess.NORMAL : AdminAccess.BLOCKED;
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            panelParams.setMargins(0, 0, 0, dp(12));
            panel.setLayoutParams(panelParams);
            panel.setBackgroundResource(R.drawable.panel);

            TextView name = new TextView(this);
            String deviceName = device.name.isEmpty() ? getString(R.string.admin_device_no_name)
                : device.name;
            name.setText(own ? getString(R.string.admin_device_name_self, deviceName) : deviceName);
            name.setTextColor(getColor(R.color.text));
            name.setTextSize(18);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            panel.addView(name);

            TextView uid = new TextView(this);
            uid.setText(getString(R.string.admin_device_uid_role, device.uid, roleLabel(role)));
            uid.setTextColor(getColor(enabled ? R.color.green
                : AdminAccess.BLOCKED.equals(role) ? R.color.gold_dark : R.color.muted));
            uid.setTextSize(14);
            uid.setPadding(0, dp(6), 0, dp(10));
            panel.addView(uid);

            Button action = new Button(this);
            action.setAllCaps(false);
            action.setText(own ? R.string.admin_self_permission : R.string.admin_change_permission);
            action.setEnabled(!own);
            action.setOnClickListener(view -> chooseRole(device, role));
            panel.addView(action);

            if (!own) {
                Button remove = new Button(this);
                remove.setAllCaps(false);
                remove.setText(R.string.admin_remove_device);
                remove.setTextColor(getColor(R.color.gold_dark));
                remove.setOnClickListener(view -> confirmRemove(device));
                panel.addView(remove);
            }
            container.addView(panel);
        }
    }

    private void confirmRemove(Device device) {
        String label = device.name.isEmpty() ? getString(R.string.admin_device_no_name) : device.name;
        new AlertDialog.Builder(this)
            .setTitle(R.string.admin_remove_device_title)
            .setMessage(getString(R.string.admin_remove_device_message, label, device.uid))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.admin_remove_device, (dialog, which) -> removeDevice(device))
            .show();
    }

    private void removeDevice(Device device) {
        if (ownUid.equals(device.uid)) return;
        database.runBatch(batch -> {
            batch.delete(database.collection("administradores").document(device.uid));
            batch.delete(database.collection("dispositivos").document(device.uid));
        })
            .addOnSuccessListener(ignored -> Toast.makeText(this,
                R.string.admin_device_removed, Toast.LENGTH_SHORT).show())
            .addOnFailureListener(error -> new AlertDialog.Builder(this)
                .setTitle(R.string.admin_remove_device_failed)
                .setMessage(R.string.admin_operation_rejected)
                .setPositiveButton(R.string.dialog_close, null)
                .show());
    }

    private void chooseRole(Device device, String currentRole) {
        String[] roles = {AdminAccess.ADMIN, AdminAccess.NORMAL, AdminAccess.BLOCKED};
        int selected = AdminAccess.ADMIN.equals(currentRole) ? 0
            : AdminAccess.NORMAL.equals(currentRole) ? 1 : 2;
        new AlertDialog.Builder(this)
            .setTitle(device.name.isEmpty() ? getString(R.string.admin_device_permission_title) : device.name)
            .setSingleChoiceItems(roles, selected, (dialog, which) -> {
                dialog.dismiss();
                confirmRole(device, roles[which]);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void confirmRole(Device device, String role) {
        String explanation = AdminAccess.ADMIN.equals(role)
            ? getString(R.string.admin_role_admin_explanation)
            : AdminAccess.NORMAL.equals(role)
                ? getString(R.string.admin_role_normal_explanation)
            : getString(R.string.admin_role_blocked_explanation);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_assign_permission_title, roleLabel(role)))
            .setMessage(getString(R.string.admin_assign_permission_message,
                device.name.isEmpty() ? device.uid : device.name, explanation))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.admin_confirm, (dialog, which) -> changeRole(device, role))
            .show();
    }

    private void changeRole(Device device, String role) {
        database.runTransaction(transaction -> {
            Map<String, Object> deviceUpdate = new HashMap<>();
            deviceUpdate.put("estado", AdminAccess.ADMIN.equals(role)
                ? AdminAccess.NORMAL : role);
            deviceUpdate.put("actualizado", FieldValue.serverTimestamp());
            transaction.update(database.collection("dispositivos").document(device.uid), deviceUpdate);

            Map<String, Object> adminUpdate = new HashMap<>();
            adminUpdate.put("activo", AdminAccess.ADMIN.equals(role));
            adminUpdate.put("nombre", device.name);
            adminUpdate.put("actualizado", FieldValue.serverTimestamp());
            transaction.set(database.collection("administradores").document(device.uid),
                adminUpdate, SetOptions.merge());
            return null;
        })
            .addOnSuccessListener(ignored -> Toast.makeText(this,
                getString(R.string.admin_permission_assigned, roleLabel(role)),
                Toast.LENGTH_SHORT).show())
            .addOnFailureListener(error -> new AlertDialog.Builder(this)
                .setTitle(R.string.admin_change_permission_failed)
                .setMessage(R.string.admin_operation_rejected)
                .setPositiveButton(R.string.dialog_close, null)
                .show());
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.adminDevicesRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String value(String value) { return value == null ? "" : value; }

    private String roleLabel(String role) {
        if (AdminAccess.ADMIN.equals(role)) return getString(R.string.admin_role_admin_label);
        if (AdminAccess.NORMAL.equals(role)) return getString(R.string.admin_role_normal_label);
        return getString(R.string.admin_role_blocked_label);
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
        if (devicesListener != null) devicesListener.remove();
        if (adminsListener != null) adminsListener.remove();
        super.onDestroy();
    }

    private static final class Device {
        final String uid;
        final String name;
        final String state;
        Device(String uid, String name, String state) {
            this.uid = uid;
            this.name = name;
            this.state = state;
        }
    }
}
