package com.ejemplo.registroguardias;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
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
    private View adminButton;

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
        findViewById(R.id.btnSettingsUser).setOnClickListener(view -> chooseUserName());
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
        adminButton = findViewById(R.id.btnSettingsAdmin);
        adminButton.setOnClickListener(view ->
            startActivity(new Intent(this, AdminDashboardActivity.class)));
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
        startScreen.setText(getString(R.string.settings_start_screen,
            getString(keys ? R.string.settings_start_keys : R.string.settings_start_personal)));
        reminderTime.setText(getString(R.string.settings_reminder_time,
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
        String[] options = {
            getString(R.string.settings_start_personal),
            getString(R.string.settings_start_keys)
        };
        boolean keys = AppPreferences.START_KEYS.equals(
            AppPreferences.get(this).getString(AppPreferences.START_SCREEN_KEY,
                AppPreferences.START_PERSONAL));
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_start_title)
            .setSingleChoiceItems(options, keys ? 1 : 0, (dialog, which) -> {
                AppPreferences.get(this).edit().putString(AppPreferences.START_SCREEN_KEY,
                    which == 1 ? AppPreferences.START_KEYS : AppPreferences.START_PERSONAL).apply();
                dialog.dismiss();
                render();
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void chooseUserName() {
        EditText input = new EditText(this);
        input.setHint(R.string.settings_user_name_hint);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(AppPreferences.get(this).getString(AccessActivity.USER_NAME_KEY, ""));
        int horizontalPadding = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
        input.setPadding(horizontalPadding, input.getPaddingTop(),
            horizontalPadding, input.getPaddingBottom());
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.settings_change_user_title)
            .setMessage(R.string.settings_change_user_message)
            .setView(input)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_save, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String name = cleanName(input.getText().toString());
                if (name.length() < 2) {
                    input.setError(R.string.setup_user_error);
                    return;
                }
                AppPreferences.get(this).edit()
                    .putString(AccessActivity.USER_NAME_KEY, name).apply();
                dialog.dismiss();
                Toast.makeText(this, getString(R.string.settings_user_changed, name),
                    Toast.LENGTH_SHORT).show();
            }));
        dialog.show();
    }

    private static String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset_title)
            .setMessage(R.string.settings_reset_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_reset, (dialog, which) -> {
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
                Toast.makeText(this, R.string.settings_restored, Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void loadAdminInfo() {
        TextView info = findViewById(R.id.settingsAdminInfo);
        AdminAccess.checkRole(FirebaseFirestore.getInstance(), (allowed, role) -> {
            if (isFinishing() || !AdminAccess.ADMIN.equals(role)) return;
            adminButton.setVisibility(View.VISIBLE);
            info.setVisibility(View.VISIBLE);
            info.setText(getString(R.string.settings_technical_info, BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE, getPackageName()));
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
            Toast.makeText(this, R.string.settings_notifications_disabled, Toast.LENGTH_LONG).show();
        }
    }
}
