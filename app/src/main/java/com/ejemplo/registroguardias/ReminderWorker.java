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
            QuerySnapshot snapshot = Tasks.await(
                FirebaseFirestore.getInstance().collection("personal").get());
            int pending = 0;
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                if (Boolean.TRUE.equals(document.getBoolean("retirado"))) continue;
                if (Boolean.FALSE.equals(document.getBoolean("activo"))) continue;
                if ("Dentro".equals(document.getString("estado"))) pending++;
            }
            if (pending == 0) {
                cancelNotification();
                return Result.success();
            }
            showNotification(pending);
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
    }

    private void showNotification(int pending) {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        createChannel(context);
        Intent intent = new Intent(context, AccessActivity.class)
            .putExtra(AccessActivity.EXTRA_OPEN_INSIDE_FILTER, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String content = pending == 1
            ? context.getString(R.string.notification_content_one)
            : context.getString(R.string.notification_content_many, pending);
        String bigText = pending == 1
            ? context.getString(R.string.notification_big_text_one)
            : context.getString(R.string.notification_big_text_many, pending);
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
