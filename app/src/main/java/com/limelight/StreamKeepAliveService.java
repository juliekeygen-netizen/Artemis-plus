package com.limelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Foreground-service lifetime anchor for the experimental Keep Connection Alive mode.
 * The stream transport remains owned by Game; this service only keeps Android informed that
 * Artemis is actively communicating with the host while the Activity is backgrounded.
 */
public class StreamKeepAliveService extends Service {
    private static final String CHANNEL_ID = "artemis_background_stream";
    private static final int NOTIFICATION_ID = 0x4152;

    public static boolean start(Context context) {
        try {
            Intent intent = new Intent(context, StreamKeepAliveService.class);
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException e) {
            LimeLog.warning("Unable to start Keep Alive foreground service: " + e.getMessage());
            return false;
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, StreamKeepAliveService.class));
        } catch (RuntimeException e) {
            LimeLog.warning("Unable to stop Keep Alive foreground service: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background streaming",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps an active Artemis stream connected while the app is in the background");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Artemis stream active")
                .setContentText("Keeping the PC connection alive in the background")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            LimeLog.warning("Unable to enter foreground for Keep Alive: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
