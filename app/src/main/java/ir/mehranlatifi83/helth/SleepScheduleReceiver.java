package ir.mehranlatifi83.helth;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import androidx.core.app.NotificationCompat;

public class SleepScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_SLEEP = "ir.mehranlatifi83.helth.ACTION_SLEEP";
    public static final String ACTION_WAKE  = "ir.mehranlatifi83.helth.ACTION_WAKE";
    private static final String CHANNEL_ID  = "schedule_channel";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SLEEP.equals(action)) {
            activateSleepMode(ctx);
            ScheduleManager.scheduleSleepAlarm(ctx); // فردا دوباره
        } else if (ACTION_WAKE.equals(action)) {
            deactivateSleepMode(ctx);
            ScheduleManager.scheduleWakeAlarm(ctx);  // فردا دوباره
        }
    }

    private void activateSleepMode(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);

        ctx.startForegroundService(new Intent(ctx, SleepVpnService.class));

        ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", true).apply();

        notify(ctx, R.string.notif_sleep_time_title, R.string.notif_sleep_time_text, 2);
        SleepLockActivity.launch(ctx);
    }

    private void deactivateSleepMode(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        ctx.stopService(new Intent(ctx, SleepVpnService.class));
        SleepVpnService.disconnect();

        ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        notify(ctx, R.string.notif_wake_title, R.string.notif_wake_text, 3);
    }

    private void notify(Context ctx, int titleRes, int textRes, int id) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.channel_schedule_name),
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(ch);

        PendingIntent openApp = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nm.notify(id, new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(ctx.getString(titleRes))
                .setContentText(ctx.getString(textRes))
                .setSmallIcon(R.drawable.ic_moon)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build());
    }
}
