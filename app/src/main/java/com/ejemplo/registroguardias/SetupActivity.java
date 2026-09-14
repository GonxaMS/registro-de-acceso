package com.ejemplo.registroguardias;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public final class SetupActivity extends AppCompatActivity {
    private SharedPreferences preferences;
    private EditText input;

    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        AccessibilityMode.apply(this);
        super.onCreate(state);
        preferences = getSharedPreferences(AccessActivity.PREFS_NAME, MODE_PRIVATE);
        if (!currentUser().isEmpty()) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_user_setup);
        AccessibilityMode.applyTo(this, findViewById(android.R.id.content));
        View root = findViewById(R.id.setupRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int horizontal = dp(24);
            view.setPadding(horizontal, insets.getSystemWindowInsetTop(), horizontal,
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        input = findViewById(R.id.inputUserName);
        findViewById(R.id.btnContinue).setOnClickListener(view -> saveAndContinue());
    }

    private String currentUser() {
        return preferences.getString(AccessActivity.USER_NAME_KEY, "").trim();
    }

    private void saveAndContinue() {
        String name = cleanName(input.getText().toString());
        if (name.length() < 2) {
            input.setError(getString(R.string.setup_user_error));
            return;
        }
        preferences.edit().putString(AccessActivity.USER_NAME_KEY, name).apply();
        openMain();
    }

    private void openMain() {
        startActivity(new Intent(this, AccessActivity.class));
        finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    @Override public void onBackPressed() {
        if (!currentUser().isEmpty()) super.onBackPressed();
    }
}
