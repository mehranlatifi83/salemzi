package ir.mehranlatifi83.helth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class WakeAlarmService extends Service {

    private static final String TAG        = "WakeAlarmService";
    private static final String CHANNEL_ID = "wake_alarm_channel";
    private static final int    NOTIF_ID   = 4;

    static final String ACTION_DISMISS = "ir.mehranlatifi83.helth.ACTION_DISMISS_WAKE";
    static final String PREF_SOUND_URI = "alarm_sound_uri";

    private MediaPlayer player;
    private boolean cleanupDone = false;

    // ─── Service lifecycle ───────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Always call startForeground immediately to satisfy Android 8+ requirements.
        // If this is a dismiss request we stop right after.
        ensureChannel();
        startForeground(NOTIF_ID, buildNotification());

        if (ACTION_DISMISS.equals(intent != null ? intent.getAction() : null)) {
            // Stop alarm immediately inside onStartCommand — don't wait for onDestroy.
            // This is the only reliable way to cut the sound synchronously.
            stopAlarm();
            cleanupDone = true;
            doFullSleepCleanup();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        playAlarm();
        // START_STICKY so the system restarts the service if it's killed while the
        // alarm is still ringing (e.g., due to memory pressure).
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Safety net: only clean up if onStartCommand's dismiss path didn't already do it.
        stopAlarm();
        if (!cleanupDone) doFullSleepCleanup();
        getSystemService(NotificationManager.class).cancel(NOTIF_ID);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─── Static helpers ───────────────────────────────────────────────────────

    /** Starts the alarm service. Safe to call from Activity or BroadcastReceiver. */
    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, WakeAlarmService.class));
    }

    /**
     * Stops the alarm service. Sends ACTION_DISMISS so the player is stopped
     * synchronously inside onStartCommand rather than waiting for onDestroy.
     */
    public static void stop(Context ctx) {
        // If service is running, this delivers the intent to onStartCommand which
        // stops the player immediately. If it's not running it starts and instantly stops.
        try {
            ctx.startForegroundService(
                    new Intent(ctx, WakeAlarmService.class).setAction(ACTION_DISMISS));
        } catch (Exception e) {
            // Fallback: just send a regular stopService
            ctx.stopService(new Intent(ctx, WakeAlarmService.class));
        }
    }

    // ─── Alarm sound ─────────────────────────────────────────────────────────

    private void playAlarm() {
        // Release any existing player first so a double-start (e.g., timed-mode countdown
        // and ACTION_WAKE alarm both firing at the same moment) does not leak a MediaPlayer.
        stopAlarm();
        Uri uri = resolveAlarmUri();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepareAsync();
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + "/" + extra);
                return false;
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to start alarm player", e);
        }
    }

    private void stopAlarm() {
        if (player != null) {
            try {
                // Null listeners first to prevent callbacks firing after release
                player.setOnPreparedListener(null);
                player.setOnErrorListener(null);
                // reset() is safe in every MediaPlayer state (including Preparing)
                // and stops audio immediately
                player.reset();
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }
    }

    private Uri resolveAlarmUri() {
        String saved = getSharedPreferences("helth_prefs", MODE_PRIVATE)
                .getString(PREF_SOUND_URI, null);
        if (saved != null) return Uri.parse(saved);
        Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return def != null ? def : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    // ─── Sleep state cleanup ─────────────────────────────────────────────────

    /**
     * Fully tears down sleep mode regardless of how the alarm is dismissed.
     * Idempotent — safe to call even if sleep mode is already inactive.
     */
    private void doFullSleepCleanup() {
        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();

        ((AudioManager) getSystemService(AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        getSharedPreferences("helth_prefs", MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        // Cancel the persistent sleep notification posted by SleepScheduleReceiver
        getSystemService(NotificationManager.class).cancel(2);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private Notification buildNotification() {
        Intent dismissIntent = new Intent(this, WakeAlarmService.class)
                .setAction(ACTION_DISMISS);
        // Use getForegroundService on API 26+ to avoid IllegalStateException when the
        // notification action is tapped while the app is in the background.
        PendingIntent dismissPi = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? PendingIntent.getForegroundService(this, 0, dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(this, 0, dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // fullScreenIntent ensures the notification appears prominently on lock screen
        // and fires even after the heads-up popup has slid away
        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this, 11,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.wake_alarm_notif_title))
                .setContentText(getString(R.string.wake_alarm_notif_text))
                .setSmallIcon(R.drawable.ic_sun)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPi, true)
                .addAction(0, getString(R.string.confirm_awake), dismissPi)
                .setContentIntent(dismissPi)
                .build();
    }

    private void ensureChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        // Sound is handled by MediaPlayer; silence the channel to avoid double audio
        ch.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
}
