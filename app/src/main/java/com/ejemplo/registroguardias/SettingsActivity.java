package com.ejemplo.registroguardias;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public final class SettingsActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4101;

    private SwitchCompat remindersSwitch;
    private SwitchCompat vibrationSwitch;
    private TextView reminderTime;
    private TextView startScreen;
    private TextView appearance;

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        applyWindowInsets();

        findViewById(R.id.btnSettingsBack).setOnClickListener(view -> finish());
        appearance = findViewById(R.id.btnSettingsAppearance);
        appearance.setOnClickListener(view -> ThemeMode.showChooser(this));
        startScreen = findViewById(R.id.btnSettingsStartScreen);
        startScreen.setOnClickListener(view -> chooseStartScreen());
        remindersSwitch = findViewById(R.id.switchSettingsReminders);
        remindersSwitch.setChecked(AppPreferences.remindersEnabled(this));
        remindersSwitch.setOnCheckedChangeListener((button, checked) -> setRemindersEnabled(checked));
        vibrationSwitch = findViewById(R.id.switchSettingsVibration);
        vibrationSwitch.setChecked(AppPreferences.vibrationEnabled(this));
        vibrationSwitch.setOnCheckedChangeListener((button, checked) ->
            AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.VIBRATION_ENABLED_KEY, checked).apply());
        reminderTime = findViewById(R.id.btnSettingsReminderTime);
        reminderTime.setOnClickListener(view -> chooseReminderTime());
        findViewById(R.id.btnSettingsReset).setOnClickListener(view -> confirmReset());
        render();
        loadAdminInfo();

        if (Build.VERSION.SDK_INT >= 33 && remindersSwitch.isChecked()
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.settingsRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(view.getPaddingLeft(), insets.getSystemWindowInsetTop(),
                view.getPaddingRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void render() {
        appearance.setText(ThemeMode.menuLabel(this));
        boolean keys = AppPreferences.START_KEYS.equals(
            AppPreferences.get(this).getString(AppPreferences.START_SCREEN_KEY,
                AppPreferences.START_PERSONAL));
        startScreen.setText("Pantalla inicial: " + (keys ? "Llaves" : "Personal"));
        reminderTime.setText(String.format(Locale.US, "Hora del recordatorio: %02d:%02d",
            AppPreferences.reminderHour(this), AppPreferences.reminderMinute(this)));
    }

    private void setRemindersEnabled(boolean enabled) {
        AppPreferences.get(this).edit()
            .putBoolean(AppPreferences.REMINDERS_ENABLED_KEY, enabled).apply();
        ReminderScheduler.reschedule(this);
        if (enabled && Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void chooseReminderTime() {
        new TimePickerDialog(this, (picker, hour, minute) -> {
            AppPreferences.get(this).edit()
                .putInt(AppPreferences.REMINDER_HOUR_KEY, hour)
                .putInt(AppPreferences.REMINDER_MINUTE_KEY, minute)
                .apply();
            ReminderScheduler.reschedule(this);
            render();
        }, AppPreferences.reminderHour(this), AppPreferences.reminderMinute(this), true).show();
    }

    private void chooseStartScreen() {
        String[] options = {"Personal", "Llaves"};
        boolean keys = AppPreferences.START_KEYS.equals(
            AppPreferences.get(this).getString(AppPreferences.START_SCREEN_KEY,
                AppPreferences.START_PERSONAL));
        new AlertDialog.Builder(this)
            .setTitle("Pantalla inicial")
            .setSingleChoiceItems(options, keys ? 1 : 0, (dialog, which) -> {
                AppPreferences.get(this).edit().putString(AppPreferences.START_SCREEN_KEY,
                    which == 1 ? AppPreferences.START_KEYS : AppPreferences.START_PERSONAL).apply();
                dialog.dismiss();
                render();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
            .setTitle("Restaurar preferencias")
            .setMessage("Se restaurarán la apariencia, la pantalla inicial y los recordatorios.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Restaurar", (dialog, which) -> {
                AppPreferences.get(this).edit()
                    .remove(AppPreferences.START_SCREEN_KEY)
                    .remove(AppPreferences.REMINDERS_ENABLED_KEY)
                    .remove(AppPreferences.REMINDER_HOUR_KEY)
                    .remove(AppPreferences.REMINDER_MINUTE_KEY)
                    .remove(AppPreferences.VIBRATION_ENABLED_KEY)
                    .apply();
                ThemeMode.reset(this);
                ReminderScheduler.reschedule(this);
                render();
                remindersSwitch.setChecked(AppPreferences.remindersEnabled(this));
                vibrationSwitch.setChecked(AppPreferences.vibrationEnabled(this));
                Toast.makeText(this, "Preferencias restauradas", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void loadAdminInfo() {
        TextView info = findViewById(R.id.settingsAdminInfo);
        AdminAccess.checkRole(FirebaseFirestore.getInstance(), (allowed, role) -> {
            if (isFinishing() || !AdminAccess.ADMIN.equals(role)) return;
            info.setVisibility(View.VISIBLE);
            info.setText("Información técnica\nVersión " + BuildConfig.VERSION_NAME
                + " · código " + BuildConfig.VERSION_CODE
                + "\nPaquete " + getPackageName());
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                       int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            remindersSwitch.setChecked(false);
            AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.REMINDERS_ENABLED_KEY, false).apply();
            ReminderScheduler.schedule(this);
            Toast.makeText(this, "Las notificaciones quedaron desactivadas", Toast.LENGTH_LONG).show();
        }
    }
}
