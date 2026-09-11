package com.ejemplo.registroguardias;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

final class ReminderScheduler {
    static final String WORK_NAME = "registro_guardias_exit_reminder";

    private ReminderScheduler() {}

    static void schedule(Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (!AppPreferences.remindersEnabled(context)) {
            manager.cancelUniqueWork(WORK_NAME);
            return;
        }

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            ReminderWorker.class, 24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilReminder(context), TimeUnit.MILLISECONDS)
            .build();
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    static void reschedule(Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (!AppPreferences.remindersEnabled(context)) {
            manager.cancelUniqueWork(WORK_NAME);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            ReminderWorker.class, 24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilReminder(context), TimeUnit.MILLISECONDS)
            .build();
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    private static long delayUntilReminder(Context context) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, AppPreferences.reminderHour(context));
        target.set(Calendar.MINUTE, AppPreferences.reminderMinute(context));
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1);
        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}
