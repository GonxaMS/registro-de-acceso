package com.ejemplo.registroguardias;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;

public final class SettingsActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4101;

    private SwitchCompat remindersSwitch;
    private SwitchCompat vibrationSwitch;
    private SwitchCompat largeTextSwitch;
    private SwitchCompat highContrastSwitch;
    private TextView reminderTime;
    private TextView startScreen;
    private TextView themeAutomatic;
    private TextView themeLight;
    private TextView themeDark;
    private TextView updateStatus;
    private Button updateButton;
    private View adminButton;
    private UpdateManager.UpdateInfo updateInfo;
    private boolean updateInProgress;

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        AccessibilityMode.apply(this);
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        AccessibilityMode.applyTo(this, findViewById(android.R.id.content));
        applyWindowInsets();

        findViewById(R.id.btnSettingsBack).setOnClickListener(view -> finish());
        themeAutomatic = findViewById(R.id.btnSettingsThemeAuto);
        themeLight = findViewById(R.id.btnSettingsThemeLight);
        themeDark = findViewById(R.id.btnSettingsThemeDark);
        themeAutomatic.setOnClickListener(view -> selectTheme(ThemeMode.AUTOMATIC));
        themeLight.setOnClickListener(view -> selectTheme(ThemeMode.LIGHT));
        themeDark.setOnClickListener(view -> selectTheme(ThemeMode.DARK));

        startScreen = findViewById(R.id.btnSettingsStartScreen);
        startScreen.setOnClickListener(view -> chooseStartScreen());
        findViewById(R.id.btnSettingsLogout).setOnClickListener(view -> confirmLogout());

        remindersSwitch = findViewById(R.id.switchSettingsReminders);
        remindersSwitch.setChecked(AppPreferences.remindersEnabled(this));
        remindersSwitch.setOnCheckedChangeListener((button, checked) -> setRemindersEnabled(checked));
        vibrationSwitch = findViewById(R.id.switchSettingsVibration);
        vibrationSwitch.setChecked(AppPreferences.vibrationEnabled(this));
        vibrationSwitch.setOnCheckedChangeListener((button, checked) ->
            AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.VIBRATION_ENABLED_KEY, checked).apply());

        largeTextSwitch = findViewById(R.id.switchSettingsLargeText);
        largeTextSwitch.setChecked(AppPreferences.largeTextEnabled(this));
        largeTextSwitch.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.LARGE_TEXT_KEY, checked).apply();
            recreate();
        });
        highContrastSwitch = findViewById(R.id.switchSettingsHighContrast);
        highContrastSwitch.setChecked(AppPreferences.highContrastEnabled(this));
        highContrastSwitch.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.HIGH_CONTRAST_KEY, checked).apply();
            recreate();
        });

        reminderTime = findViewById(R.id.btnSettingsReminderTime);
        reminderTime.setOnClickListener(view -> chooseReminderTime());

        updateStatus = findViewById(R.id.settingsUpdateStatus);
        updateButton = findViewById(R.id.btnSettingsCheckUpdate);
        updateButton.setOnClickListener(view -> checkOrDownloadUpdate());

        adminButton = findViewById(R.id.btnSettingsAdmin);
        adminButton.setOnClickListener(view ->
            startActivity(new Intent(this, AdminDashboardActivity.class)));

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
        int mode = ThemeMode.savedMode(this);
        themeAutomatic.setSelected(mode == ThemeMode.AUTOMATIC);
        themeLight.setSelected(mode == ThemeMode.LIGHT);
        themeDark.setSelected(mode == ThemeMode.DARK);
        boolean keys = AppPreferences.START_KEYS.equals(
            AppPreferences.get(this).getString(AppPreferences.START_SCREEN_KEY,
                AppPreferences.START_PERSONAL));
        startScreen.setText(getString(R.string.settings_start_screen,
            getString(keys ? R.string.settings_start_keys : R.string.settings_start_personal)));
        reminderTime.setText(getString(R.string.settings_reminder_time,
            AppPreferences.reminderHour(this), AppPreferences.reminderMinute(this)));
        TextView version = findViewById(R.id.settingsVersionCurrent);
        version.setText(getString(R.string.settings_version_current, BuildConfig.VERSION_NAME));
        updateStatus.setText("");
        updateButton.setText(R.string.settings_update_check);
    }

    private void selectTheme(int mode) {
        ThemeMode.setMode(this, mode);
        render();
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

    private void confirmLogout() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_logout_title)
            .setMessage(R.string.settings_logout_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_logout, (dialog, which) -> {
                AppPreferences.get(this).edit()
                    .remove(AccessActivity.USER_NAME_KEY).apply();
                Intent intent = new Intent(this, SetupActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
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

    private void checkOrDownloadUpdate() {
        if (updateInProgress) return;
        if (updateInfo != null && updateInfo.isUpdateAvailable()) {
            downloadUpdate();
            return;
        }
        updateInProgress = true;
        updateButton.setEnabled(false);
        updateStatus.setText(R.string.settings_update_checking);
        UpdateManager.check(this, new UpdateManager.CheckCallback() {
            @Override public void onComplete(UpdateManager.UpdateInfo info) {
                if (isFinishing()) return;
                updateInProgress = false;
                updateButton.setEnabled(true);
                if (info.isUpdateAvailable()) {
                    updateInfo = info;
                    updateStatus.setText(getString(R.string.settings_update_available,
                        info.latestVersion));
                    updateButton.setText(R.string.settings_update_button);
                } else {
                    updateInfo = null;
                    updateStatus.setText(R.string.settings_update_up_to_date);
                    updateButton.setText(R.string.settings_update_check);
                }
            }

            @Override public void onError(Exception error) {
                if (isFinishing()) return;
                updateInProgress = false;
                updateButton.setEnabled(true);
                updateInfo = null;
                updateStatus.setText(R.string.settings_update_failed);
                updateButton.setText(R.string.settings_update_check);
            }
        });
    }

    private void downloadUpdate() {
        UpdateManager.UpdateInfo info = updateInfo;
        if (info == null) return;
        updateInProgress = true;
        updateButton.setEnabled(false);
        updateStatus.setText(getString(R.string.settings_update_downloading,
            info.latestVersion));
        UpdateManager.download(this, info, new UpdateManager.DownloadCallback() {
            @Override public void onProgress(int percent) {
                // El estado mantiene el texto simple y solo muestra la versión destino.
            }

            @Override public void onComplete(File apkFile) {
                if (isFinishing()) return;
                updateInProgress = false;
                updateButton.setEnabled(true);
                installApk(apkFile);
            }

            @Override public void onError(Exception error) {
                if (isFinishing()) return;
                updateInProgress = false;
                updateButton.setEnabled(true);
                updateStatus.setText(R.string.settings_update_download_failed);
            }
        });
    }

    private void installApk(File apkFile) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
            startActivity(permissionIntent);
            updateStatus.setText(R.string.settings_update_install_permission);
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(this,
            BuildConfig.APPLICATION_ID + ".fileprovider", apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(installIntent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                       int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (!granted) {
            remindersSwitch.setChecked(false);
            AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.REMINDERS_ENABLED_KEY, false).apply();
            ReminderScheduler.schedule(this);
            Toast.makeText(this, R.string.settings_notifications_disabled, Toast.LENGTH_LONG).show();
        }
    }
}
