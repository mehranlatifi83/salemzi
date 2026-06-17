package ir.mehranlatifi83.helth;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Schedules 8 water reminders evenly distributed between the user's wake and sleep times.
 * Falls back to 7:00–23:00 if no sleep schedule is configured.
 */
public class WaterReminderManager {

    static final String KEY_WATER_ON = "water_reminders_enabled";

    private static final String PREFS     = "helth_prefs";
    private static final int    REQ_BASE  = 200;
    static final int            COUNT     = 8;

    // ─── Enable / disable ────────────────────────────────────────────────────

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_WATER_ON, false);
    }

    public static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_WATER_ON, on).apply();
        if (on) scheduleAll(ctx);
        else    cancelAll(ctx);
    }

    // ─── Schedule computation ─────────────────────────────────────────────────

    /**
     * Returns 8 [hour, minute, index] triples evenly spread across awake hours.
     * Uses the app's configured sleep/wake times; falls back to 07:00–23:00.
     */
    public static List<int[]> computeReminderTimes(Context ctx) {
        int[] wake  = ScheduleManager.getWakeTime(ctx);
        int[] sleep = ScheduleManager.getSleepTime(ctx);

        int startMin = (wake  != null) ? wake[0]  * 60 + wake[1]  : 7 * 60;
        int endMin   = (sleep != null) ? sleep[0] * 60 + sleep[1] : 23 * 60;

        // If sleep is earlier than wake (e.g. midnight schedule), extend past midnight
        if (endMin <= startMin) endMin += 24 * 60;

        int span = endMin - startMin;

        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            // Evenly divide: first slot at 1/(COUNT+1), last at COUNT/(COUNT+1) of span
            int t = startMin + span * (i + 1) / (COUNT + 1);
            t = t % (24 * 60); // wrap past midnight
            list.add(new int[]{t / 60, t % 60, i});
        }
        return list;
    }

    // ─── Alarm scheduling ─────────────────────────────────────────────────────

    public static void scheduleAll(Context ctx) {
        cancelAll(ctx);
        List<int[]> slots = computeReminderTimes(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < slots.size(); i++) {
            int[] slot = slots.get(i);
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    nextTriggerMs(slot[0], slot[1]),
                    buildIntent(ctx, i, slot[0], slot[1]));
        }
    }

    public static void cancelAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < COUNT; i++) {
            am.cancel(buildCancelIntent(ctx, i));
        }
    }

    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE))
                    .canScheduleExactAlarms();
        }
        return true;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    static long nextTriggerMs(int hour, int min) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, min);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private static PendingIntent buildIntent(Context ctx, int slot, int h, int m) {
        Intent i = new Intent(ctx, WaterReminderReceiver.class)
                .setAction(WaterReminderReceiver.ACTION_WATER)
                .putExtra(WaterReminderReceiver.EXTRA_SLOT, slot)
                .putExtra(WaterReminderReceiver.EXTRA_HOUR, h)
                .putExtra(WaterReminderReceiver.EXTRA_MIN,  m);
        return PendingIntent.getBroadcast(ctx, REQ_BASE + slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent buildCancelIntent(Context ctx, int slot) {
        Intent i = new Intent(ctx, WaterReminderReceiver.class)
                .setAction(WaterReminderReceiver.ACTION_WATER);
        return PendingIntent.getBroadcast(ctx, REQ_BASE + slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
