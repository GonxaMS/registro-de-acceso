package com.ejemplo.registroguardias;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

final class AccessibilityMode {
    private static final float NORMAL_TEXT_SCALE = 1.0f;
    private static final float LARGE_TEXT_SCALE = 1.15f;

    private AccessibilityMode() {}

    static void apply(Context context) {
        float targetScale = AppPreferences.largeTextEnabled(context)
            ? LARGE_TEXT_SCALE : NORMAL_TEXT_SCALE;
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (Math.abs(configuration.fontScale - targetScale) < 0.01f) return;
        configuration.fontScale = targetScale;
        context.getResources().updateConfiguration(configuration,
            context.getResources().getDisplayMetrics());
    }

    static void applyTo(Context context, View root) {
        if (!AppPreferences.highContrastEnabled(context) || root == null) return;
        int normalText = ContextCompat.getColor(context, R.color.text);
        int normalMuted = ContextCompat.getColor(context, R.color.muted);
        int highContrastText = ContextCompat.getColor(context, R.color.high_contrast_text);
        int highContrastMuted = ContextCompat.getColor(context, R.color.high_contrast_muted);
        applyToView(root, normalText, normalMuted, highContrastText, highContrastMuted);
    }

    private static void applyToView(View view, int normalText, int normalMuted,
                                    int highContrastText, int highContrastMuted) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            int currentColor = textView.getCurrentTextColor();
            if (currentColor == normalText) textView.setTextColor(highContrastText);
            else if (currentColor == normalMuted) textView.setTextColor(highContrastMuted);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            applyToView(group.getChildAt(index), normalText, normalMuted,
                highContrastText, highContrastMuted);
        }
    }
}
