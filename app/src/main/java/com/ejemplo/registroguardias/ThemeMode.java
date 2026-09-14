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
        AppCompatDelegate.setDefaultNightMode(toNightMode(savedMode(context)));
    }

    static String menuLabel(Context context) {
        return context.getString(R.string.appearance_menu_label, label(context, savedMode(context)));
    }

    static int savedMode(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        int mode = preferences.getInt(KEY, AUTOMATIC);
        return mode < AUTOMATIC || mode > DARK ? AUTOMATIC : mode;
    }

    static void setMode(Context context, int mode) {
        if (mode < AUTOMATIC || mode > DARK) mode = AUTOMATIC;
        AppPreferences.get(context).edit().putInt(KEY, mode).apply();
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    static void showChooser(Activity activity) {
        String[] options = {
            activity.getString(R.string.appearance_automatic),
            activity.getString(R.string.appearance_light),
            activity.getString(R.string.appearance_dark)
        };
        new android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.appearance_title)
            .setSingleChoiceItems(options, savedMode(activity), (dialog, which) -> {
                dialog.dismiss();
                setMode(activity, which);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private static int toNightMode(int mode) {
        if (mode == LIGHT) return AppCompatDelegate.MODE_NIGHT_NO;
        if (mode == DARK) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private static String label(Context context, int mode) {
        if (mode == LIGHT) return context.getString(R.string.appearance_label_light);
        if (mode == DARK) return context.getString(R.string.appearance_label_dark);
        return context.getString(R.string.appearance_label_automatic);
    }

    static void reset(Context context) {
        AppPreferences.get(context).edit().remove(KEY).apply();
        apply(context);
    }
}
