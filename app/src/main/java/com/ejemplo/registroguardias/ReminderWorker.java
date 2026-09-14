package com.ejemplo.registroguardias;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public final class ReminderWorker extends Worker {
    private static final String CHANNEL_ID = "exit_reminders";
    private static final int NOTIFICATION_ID = 7301;
    private static final int TEST_NOTIFICATION_ID = 7302;

    public ReminderWorker(Context context, WorkerParameters parameters) {
        super(context, parameters);
    }

    @Override public Result doWork() {
        if (!AppPreferences.remindersEnabled(getApplicationContext())) {
            cancelNotification();
            return Result.success();
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            cancelNotification();
            return Result.success();
        }
        try {
            QuerySnapshot peopleSnapshot = Tasks.await(
                FirebaseFirestore.getInstance().collection("personal").get());
            QuerySnapshot keysSnapshot = Tasks.await(
                FirebaseFirestore.getInstance().collection("llaves").get());
            int pendingPeople = countPendingPeople(peopleSnapshot);
            int pendingKeys = countPendingKeys(keysSnapshot);
            if (pendingPeople == 0 && pendingKeys == 0) {
                cancelNotification();
                return Result.success();
            }
            showNotification(getApplicationContext(), pendingPeople, pendingKeys);
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
    }

    static boolean showTestNotification(Context context) {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        createChannel(context);
        Intent intent = new Intent(context, AccessActivity.class)
            .putExtra(AccessActivity.EXTRA_OPEN_INSIDE_FILTER, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, TEST_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_test_content))
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notification_test_big_text)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification.build());
        return true;
    }

    private static void showNotification(Context context, int pendingPeople, int pendingKeys) {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        createChannel(context);
        Intent intent = new Intent(context,
            pendingPeople > 0 ? AccessActivity.class : KeysActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (pendingPeople > 0) intent.putExtra(AccessActivity.EXTRA_OPEN_INSIDE_FILTER, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String content = reminderContent(context, pendingPeople, pendingKeys);
        String bigText = context.getString(R.string.notification_with_review, content);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(content)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(bigText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
    }

    private static String reminderContent(Context context, int pendingPeople, int pendingKeys) {
        if (pendingPeople > 0 && pendingKeys > 0) {
            return context.getString(R.string.notification_content_both, pendingPeople, pendingKeys);
        }
        if (pendingPeople > 0) {
            return pendingPeople == 1
                ? context.getString(R.string.notification_content_people_one)
                : context.getString(R.string.notification_content_people_many, pendingPeople);
        }
        return pendingKeys == 1
            ? context.getString(R.string.notification_content_keys_one)
            : context.getString(R.string.notification_content_keys_many, pendingKeys);
    }

    private static int countPendingPeople(QuerySnapshot snapshot) {
        int pending = 0;
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            if (Boolean.TRUE.equals(document.getBoolean("retirado"))) continue;
            if (Boolean.FALSE.equals(document.getBoolean("activo"))) continue;
            if ("Dentro".equals(document.getString("estado"))) pending++;
        }
        return pending;
    }

    private static int countPendingKeys(QuerySnapshot snapshot) {
        int pending = 0;
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            if (Boolean.FALSE.equals(document.getBoolean("activo"))) continue;
            if ("Prestada".equals(document.getString("estado"))) pending++;
        }
        return pending;
    }

    private void cancelNotification() {
        NotificationManagerCompat.from(getApplicationContext()).cancel(NOTIFICATION_ID);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
