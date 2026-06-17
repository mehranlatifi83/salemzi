package ir.mehranlatifi83.helth;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

public class SleepScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_SLEEP = "ir.mehranlatifi83.helth.ACTION_SLEEP";
    public static final String ACTION_WAKE  = "ir.mehranlatifi83.helth.ACTION_WAKE";

    private static final String CHANNEL_ID = "schedule_channel";
    private static final int    NOTIF_SLEEP = 2;
    private static final int    NOTIF_WAKE  = 3;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SLEEP.equals(action)) {
            activateSleepMode(ctx);
            ScheduleManager.scheduleSleepAlarm(ctx); // reschedule for next day
        } else if (ACTION_WAKE.equals(action)) {
            deactivateSleepMode(ctx);
            ScheduleManager.scheduleWakeAlarm(ctx);  // reschedule for next day
        }
    }

    private void activateSleepMode(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);

        ctx.startForegroundService(new Intent(ctx, SleepVpnService.class));

        ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", true).apply();

        // If the user granted SYSTEM_ALERT_WINDOW, launch the activity directly over
        // whatever is currently on screen. Otherwise fall back to full-screen notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(ctx)) {
            SleepLockActivity.launch(ctx);
        }
        showSleepNotification(ctx);
    }

    private void deactivateSleepMode(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        ctx.stopService(new Intent(ctx, SleepVpnService.class));
        SleepVpnService.disconnect();

        ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        showWakeNotification(ctx);
    }

    /** Posts a high-priority notification with a full-screen intent that opens
     *  the sleep lock screen. Static so it can be called from MainActivity too —
     *  using a notification is the only reliable way to show an activity over
     *  any foreground app on Android 10+ (API 29+). */
    public static void showSleepNotification(Context ctx) {
        ensureChannel(ctx);

        PendingIntent lockScreenPi = PendingIntent.getActivity(
                ctx, 10,
                new Intent(ctx, SleepLockActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.notify(NOTIF_SLEEP, new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(ctx.getString(R.string.notif_sleep_time_title))
                .setContentText(ctx.getString(R.string.notif_sleep_time_text))
                .setSmallIcon(R.drawable.ic_moon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(lockScreenPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .build());
    }

    private void showWakeNotification(Context ctx) {
        ensureChannel(ctx);

        PendingIntent openApp = PendingIntent.getActivity(
                ctx, 0,
                new Intent(ctx, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.notify(NOTIF_WAKE, new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(ctx.getString(R.string.notif_wake_title))
                .setContentText(ctx.getString(R.string.notif_wake_text))
                .setSmallIcon(R.drawable.ic_sun)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build());
    }

    public static void ensureChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.channel_schedule_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
}
