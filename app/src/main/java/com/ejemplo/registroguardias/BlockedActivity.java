package com.ejemplo.registroguardias;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public final class BlockedActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        ThemeMode.apply(this);
        AccessibilityMode.apply(this);
        super.onCreate(state);
        setContentView(R.layout.activity_blocked);
        AccessibilityMode.applyTo(this, findViewById(android.R.id.content));
        View root = findViewById(R.id.blockedRoot);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(24), insets.getSystemWindowInsetTop(), dp(24),
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        findViewById(R.id.btnBlockedRetry).setOnClickListener(view -> {
            startActivity(new Intent(this, AccessActivity.class));
            finish();
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
