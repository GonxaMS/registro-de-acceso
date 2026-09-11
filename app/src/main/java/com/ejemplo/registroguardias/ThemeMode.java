package com.ejemplo.registroguardias;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

final class ThemeMode {
    static final int AUTOMATIC = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private static final String KEY = "theme_mode";

    private ThemeMode() {}

    static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(saved(context)));
    }

    static String menuLabel(Context context) {
        return "Apariencia: " + label(saved(context));
    }

    static void showChooser(Activity activity) {
        String[] options = {"Automático (según el teléfono)", "Modo claro", "Modo oscuro"};
        new android.app.AlertDialog.Builder(activity)
            .setTitle("Apariencia")
            .setSingleChoiceItems(options, saved(activity), (dialog, which) -> {
                AppPreferences.get(activity).edit().putInt(KEY, which).apply();
                dialog.dismiss();
                AppCompatDelegate.setDefaultNightMode(toNightMode(which));
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private static int saved(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        int mode = preferences.getInt(KEY, AUTOMATIC);
        return mode < AUTOMATIC || mode > DARK ? AUTOMATIC : mode;
    }

    private static int toNightMode(int mode) {
        if (mode == LIGHT) return AppCompatDelegate.MODE_NIGHT_NO;
        if (mode == DARK) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private static String label(int mode) {
        if (mode == LIGHT) return "claro";
        if (mode == DARK) return "oscuro";
        return "automático";
    }

    static void reset(Context context) {
        AppPreferences.get(context).edit().remove(KEY).apply();
        apply(context);
    }
}
