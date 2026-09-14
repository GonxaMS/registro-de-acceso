package com.ejemplo.registroguardias;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPreferences {
    static final String PREFS_NAME = "registro_guardias";
    static final String START_SCREEN_KEY = "start_screen";
    static final String START_PERSONAL = "personal";
    static final String START_KEYS = "keys";
    static final String REMINDERS_ENABLED_KEY = "reminders_enabled";
    static final String REMINDER_HOUR_KEY = "reminder_hour";
    static final String REMINDER_MINUTE_KEY = "reminder_minute";
    static final String VIBRATION_ENABLED_KEY = "vibration_enabled";
    static final String LARGE_TEXT_KEY = "large_text";
    static final String HIGH_CONTRAST_KEY = "high_contrast";
    static final int DEFAULT_REMINDER_HOUR = 21;
    static final int DEFAULT_REMINDER_MINUTE = 0;

    private AppPreferences() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static boolean remindersEnabled(Context context) {
        return get(context).getBoolean(REMINDERS_ENABLED_KEY, true);
    }

    static int reminderHour(Context context) {
        return get(context).getInt(REMINDER_HOUR_KEY, DEFAULT_REMINDER_HOUR);
    }

    static int reminderMinute(Context context) {
        return get(context).getInt(REMINDER_MINUTE_KEY, DEFAULT_REMINDER_MINUTE);
    }

    static boolean vibrationEnabled(Context context) {
        return get(context).getBoolean(VIBRATION_ENABLED_KEY, true);
    }

    static boolean largeTextEnabled(Context context) {
        return get(context).getBoolean(LARGE_TEXT_KEY, false);
    }

    static boolean highContrastEnabled(Context context) {
        return get(context).getBoolean(HIGH_CONTRAST_KEY, false);
    }
}
