package ir.mehranlatifi83.helth;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS           = "helth_prefs";
    private static final String KEY_SLEEP_ACTIVE = "sleep_active";

    private MaterialButton    btnToggle;
    private TextView          textStatus;
    private MaterialCardView  cardCircle;
    private ImageView         iconSleep;
    private TextView          textSleepTime;
    private TextView          textWakeTime;
    private TextView          textScheduleHint;
    private MaterialSwitch    switchSchedule;

    private boolean isSleepActive = false;

    private final ActivityResultLauncher<Intent> vpnLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> { if (result.getResultCode() == RESULT_OK) startSleepMode(); }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle        = findViewById(R.id.btn_toggle);
        textStatus       = findViewById(R.id.text_status);
        cardCircle       = findViewById(R.id.card_circle);
        iconSleep        = findViewById(R.id.icon_sleep);
        textSleepTime    = findViewById(R.id.text_sleep_time);
        textWakeTime     = findViewById(R.id.text_wake_time);
        textScheduleHint = findViewById(R.id.text_schedule_hint);
        switchSchedule   = findViewById(R.id.switch_schedule);
        TextView textDate = findViewById(R.id.text_date);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        textDate.setText(getPersianDate());
        bottomNav.setSelectedItemId(R.id.nav_sleep);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        isSleepActive = prefs.getBoolean(KEY_SLEEP_ACTIVE, false);
        updateSleepUI();
        updateScheduleUI();

        btnToggle.setOnClickListener(v -> toggleSleepMode());

        cardCircle.setOnClickListener(v -> {
            // reserved for future detail screen
        });

        // کارت برنامه خواب — باز کردن تایم‌پیکر
        findViewById(R.id.card_schedule).setOnClickListener(v -> showSleepTimePicker());

        // سوییچ برنامه خواب
        switchSchedule.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !ScheduleManager.hasSchedule(this)) {
                switchSchedule.setChecked(false);
                showSleepTimePicker();
                return;
            }
            if (checked && !ScheduleManager.canScheduleExact(this)) {
                switchSchedule.setChecked(false);
                showAlarmPermissionDialog();
                return;
            }
            ScheduleManager.setScheduleEnabled(this, checked);
            updateScheduleUI();
        });

        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // sync state بعد از برگشت از lock screen
        isSleepActive = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SLEEP_ACTIVE, false);
        updateSleepUI();
        updateScheduleUI();
    }

    // ─── Sleep mode ──────────────────────────────────────────────────────────

    private void toggleSleepMode() {
        if (isSleepActive) {
            stopSleepMode();
        } else {
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) vpnLauncher.launch(vpnIntent);
            else startSleepMode();
        }
    }

    private void startSleepMode() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted()) {
            showDndPermissionDialog();
            return;
        }
        startService(new Intent(this, SleepVpnService.class));
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_SILENT);
        isSleepActive = true;
        saveState();
        updateSleepUI();
        SleepLockActivity.launch(this);
    }

    private void stopSleepMode() {
        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        isSleepActive = false;
        saveState();
        updateSleepUI();
    }

    private void saveState() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SLEEP_ACTIVE, isSleepActive).apply();
    }

    // ─── Schedule time pickers ───────────────────────────────────────────────

    private void showSleepTimePicker() {
        int[] saved = ScheduleManager.getSleepTime(this);
        int h = saved != null ? saved[0] : 23;
        int m = saved != null ? saved[1] : 0;

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h).setMinute(m)
                .setTitleText(R.string.picker_sleep_title)
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            ScheduleManager.saveSleepTime(this, picker.getHour(), picker.getMinute());
            showWakeTimePicker();
        });
        picker.show(getSupportFragmentManager(), "sleep_picker");
    }

    private void showWakeTimePicker() {
        int[] saved = ScheduleManager.getWakeTime(this);
        int h = saved != null ? saved[0] : 7;
        int m = saved != null ? saved[1] : 0;

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h).setMinute(m)
                .setTitleText(R.string.picker_wake_title)
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            ScheduleManager.saveWakeTime(this, picker.getHour(), picker.getMinute());
            // اگه برنامه فعال بود، آلارم‌ها رو آپدیت کن
            if (ScheduleManager.isScheduleEnabled(this)) {
                ScheduleManager.scheduleSleepAlarm(this);
                ScheduleManager.scheduleWakeAlarm(this);
            }
            updateScheduleUI();
        });
        picker.show(getSupportFragmentManager(), "wake_picker");
    }

    // ─── UI updates ──────────────────────────────────────────────────────────

    private void updateSleepUI() {
        int primary  = ContextCompat.getColor(this, R.color.colorPrimary);
        int variant  = ContextCompat.getColor(this, R.color.colorOnSurfaceVariant);
        int surface  = ContextCompat.getColor(this, R.color.colorSurface);

        if (isSleepActive) {
            textStatus.setText(R.string.status_active);
            textStatus.setTextColor(primary);
            btnToggle.setText(R.string.btn_disable_sleep);
            cardCircle.setStrokeColor(primary);
            iconSleep.setImageTintList(ColorStateList.valueOf(primary));
        } else {
            textStatus.setText(R.string.status_inactive);
            textStatus.setTextColor(variant);
            btnToggle.setText(R.string.btn_enable_sleep);
            cardCircle.setStrokeColor(surface);
            iconSleep.setImageTintList(ColorStateList.valueOf(variant));
        }
    }

    private void updateScheduleUI() {
        int[] sleep = ScheduleManager.getSleepTime(this);
        int[] wake  = ScheduleManager.getWakeTime(this);
        boolean enabled = ScheduleManager.isScheduleEnabled(this);

        textSleepTime.setText(sleep != null ? formatTime(sleep[0], sleep[1])
                : getString(R.string.schedule_not_set));
        textWakeTime.setText(wake != null ? formatTime(wake[0], wake[1])
                : getString(R.string.schedule_not_set));

        switchSchedule.setOnCheckedChangeListener(null); // موقتاً برای جلوگیری از loop
        switchSchedule.setChecked(enabled);
        switchSchedule.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !ScheduleManager.hasSchedule(this)) {
                switchSchedule.setChecked(false);
                showSleepTimePicker();
                return;
            }
            if (checked && !ScheduleManager.canScheduleExact(this)) {
                switchSchedule.setChecked(false);
                showAlarmPermissionDialog();
                return;
            }
            ScheduleManager.setScheduleEnabled(this, checked);
            updateScheduleUI();
        });

        if (!ScheduleManager.hasSchedule(this)) {
            textScheduleHint.setText(R.string.tap_to_set);
        } else if (enabled) {
            textScheduleHint.setText(R.string.schedule_active);
        } else {
            textScheduleHint.setText(R.string.schedule_inactive);
        }
    }

    private String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    // ─── Permissions & dialogs ───────────────────────────────────────────────

    private void showDndPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dnd_dialog_title)
                .setMessage(R.string.dnd_dialog_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false).show();
    }

    private void showAlarmPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.alarm_permission_title)
                .setMessage(R.string.alarm_permission_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                    }
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    // ─── Date ────────────────────────────────────────────────────────────────

    private String getPersianDate() {
        Calendar cal = Calendar.getInstance();
        String[] days   = {"یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"};
        String[] months = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                           "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        String day = days[cal.get(Calendar.DAY_OF_WEEK) - 1];
        int[] j = JalaliCalendar.toJalali(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        return day + "،  " + j[2] + " " + months[j[1] - 1] + " " + j[0];
    }
}
