package ir.mehranlatifi83.salemzi.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.net.Uri;
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

import ir.mehranlatifi83.salemzi.R;
import ir.mehranlatifi83.salemzi.manager.ScheduleManager;
import ir.mehranlatifi83.salemzi.receiver.SleepScheduleReceiver;
import ir.mehranlatifi83.salemzi.service.SleepVpnService;
import ir.mehranlatifi83.salemzi.service.WakeAlarmService;
import ir.mehranlatifi83.salemzi.util.JalaliCalendar;
import ir.mehranlatifi83.salemzi.util.TimePickerHelper;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS                = "helth_prefs";
    private static final String KEY_SLEEP_ACTIVE     = "sleep_active";
    private static final String KEY_OB_DND_SHOWN     = "onboarding_dnd_shown";
    private static final String KEY_OB_OVERLAY_SHOWN = "onboarding_overlay_shown";
    private static final String KEY_PRIVACY_ACCEPTED = "privacy_policy_accepted";

    private MaterialButton   btnToggle;
    private TextView         textStatus;
    private MaterialCardView cardCircle;
    private ImageView        iconSleep;
    private TextView         textSleepTime;
    private TextView         textWakeTime;
    private TextView         textScheduleHint;
    private MaterialSwitch   switchSchedule;
    private TextView         textLockMode;
    private TextView         textOverlayStatus;
    private TextView         textSoundName;

    private boolean isSleepActive        = false;
    private boolean pendingScheduleEnable = false;

    private final ActivityResultLauncher<Intent> vpnLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (pendingScheduleEnable) {
                        pendingScheduleEnable = false;
                        ScheduleManager.setScheduleEnabled(this, true);
                        updateScheduleUI();
                    } else {
                        startSleepMode();
                    }
                } else {
                    pendingScheduleEnable = false;
                }
            });

    private final ActivityResultLauncher<Intent> ringtoneLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra(
                            android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    saveAlarmSoundUri(uri != null ? uri.toString() : null);
                }
            });

    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        saveAlarmSoundUri(uri.toString());
                    }
                }
            });

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupBottomNav();
        setupScheduleCard();
        requestNotificationPermissionIfNeeded();
        showPrivacyPolicyIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isSleepActive = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SLEEP_ACTIVE, false);
        updateSleepUI();
        updateScheduleUI();
        updateOverlayUI();
        checkOnboarding();
    }

    // ─── View wiring ─────────────────────────────────────────────────────────

    private void bindViews() {
        btnToggle         = findViewById(R.id.btn_toggle);
        textStatus        = findViewById(R.id.text_status);
        cardCircle        = findViewById(R.id.card_circle);
        iconSleep         = findViewById(R.id.icon_sleep);
        textSleepTime     = findViewById(R.id.text_sleep_time);
        textWakeTime      = findViewById(R.id.text_wake_time);
        textScheduleHint  = findViewById(R.id.text_schedule_hint);
        switchSchedule    = findViewById(R.id.switch_schedule);
        textLockMode      = findViewById(R.id.text_lock_mode);
        textOverlayStatus = findViewById(R.id.text_overlay_status);
        textSoundName     = findViewById(R.id.text_sound_name);

        ((TextView) findViewById(R.id.text_date)).setText(buildLocalizedDate());

        isSleepActive = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SLEEP_ACTIVE, false);

        updateSleepUI();
        updateScheduleUI();
        updateLockModeUI();
        updateOverlayUI();
        updateSoundUI();

        btnToggle.setOnClickListener(v -> toggleSleepMode());
        findViewById(R.id.row_lock_mode).setOnClickListener(v -> toggleLockMode());
        findViewById(R.id.row_overlay).setOnClickListener(v -> onOverlayRowTapped());
        findViewById(R.id.row_sound).setOnClickListener(v -> showSoundPickerDialog());
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_sleep);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_water) {
                startActivity(new Intent(this, WaterActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return true;
            }
            return true;
        });
    }

    private void setupScheduleCard() {
        findViewById(R.id.card_schedule).setOnClickListener(v -> showSleepTimePicker());
        switchSchedule.setOnCheckedChangeListener((btn, checked) -> onScheduleSwitchChanged(checked));
    }

    // ─── Sleep mode toggle ───────────────────────────────────────────────────

    private void toggleSleepMode() {
        if (isSleepActive) {
            stopSleepMode();
        } else {
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) vpnLauncher.launch(vpnIntent);
            else                   startSleepMode();
        }
    }

    private void startSleepMode() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted()) {
            showDndPermissionDialog();
            return;
        }

        startForegroundService(new Intent(this, SleepVpnService.class));
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_SILENT);

        isSleepActive = true;
        persistSleepState();
        saveSleepStartTime();
        updateSleepUI();

        SleepScheduleReceiver.showSleepNotification(this);
    }

    private void stopSleepMode() {
        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        isSleepActive = false;
        persistSleepState();
        updateSleepUI();
    }

    private void persistSleepState() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SLEEP_ACTIVE, isSleepActive).apply();
    }

    private void saveSleepStartTime() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(SleepLockActivity.KEY_SLEEP_START, System.currentTimeMillis()).apply();
    }

    // ─── Schedule time pickers ───────────────────────────────────────────────

    private void showSleepTimePicker() {
        int[] saved = ScheduleManager.getSleepTime(this);
        int h = saved != null ? saved[0] : 23;
        int m = saved != null ? saved[1] : 0;
        TimePickerHelper.show(getSupportFragmentManager(), this,
                getString(R.string.picker_sleep_title), h, m, (hour, min) -> {
                    ScheduleManager.saveSleepTime(this, hour, min);
                    showWakeTimePicker();
                });
    }

    private void showWakeTimePicker() {
        int[] saved = ScheduleManager.getWakeTime(this);
        int h = saved != null ? saved[0] : 7;
        int m = saved != null ? saved[1] : 0;
        TimePickerHelper.show(getSupportFragmentManager(), this,
                getString(R.string.picker_wake_title), h, m, (hour, min) -> {
                    ScheduleManager.saveWakeTime(this, hour, min);
                    if (ScheduleManager.isScheduleEnabled(this)) {
                        ScheduleManager.scheduleSleepAlarm(this);
                        ScheduleManager.scheduleWakeAlarm(this);
                        ScheduleManager.scheduleSleepReminderAlarm(this);
                    }
                    updateScheduleUI();
                });
    }

    private void onScheduleSwitchChanged(boolean checked) {
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
        if (checked) {
            // Pre-authorize VPN so it works when the schedule fires automatically at night.
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) {
                pendingScheduleEnable = true;
                vpnLauncher.launch(vpnIntent);
                return;
            }
        }
        ScheduleManager.setScheduleEnabled(this, checked);
        updateScheduleUI();
    }

    // ─── UI state ────────────────────────────────────────────────────────────

    private void updateSleepUI() {
        int primary = ContextCompat.getColor(this, R.color.colorPrimary);
        int muted   = ContextCompat.getColor(this, R.color.colorOnSurfaceVariant);
        int surface = ContextCompat.getColor(this, R.color.colorSurface);

        if (isSleepActive) {
            textStatus.setText(R.string.status_active);
            textStatus.setTextColor(primary);
            btnToggle.setText(R.string.btn_disable_sleep);
            cardCircle.setStrokeColor(primary);
            iconSleep.setImageTintList(ColorStateList.valueOf(primary));
        } else {
            textStatus.setText(R.string.status_inactive);
            textStatus.setTextColor(muted);
            btnToggle.setText(R.string.btn_enable_sleep);
            cardCircle.setStrokeColor(surface);
            iconSleep.setImageTintList(ColorStateList.valueOf(muted));
        }
    }

    private void updateScheduleUI() {
        int[] sleep = ScheduleManager.getSleepTime(this);
        int[] wake  = ScheduleManager.getWakeTime(this);
        boolean on  = ScheduleManager.isScheduleEnabled(this);

        textSleepTime.setText(sleep != null ? fmt(sleep[0], sleep[1]) : getString(R.string.schedule_not_set));
        textWakeTime.setText(wake   != null ? fmt(wake[0],  wake[1])  : getString(R.string.schedule_not_set));

        switchSchedule.setOnCheckedChangeListener(null);
        switchSchedule.setChecked(on);
        switchSchedule.setOnCheckedChangeListener((btn, checked) -> onScheduleSwitchChanged(checked));

        textScheduleHint.setText(!ScheduleManager.hasSchedule(this)
                ? R.string.tap_to_set
                : on ? R.string.schedule_active : R.string.schedule_inactive);
    }

    private String fmt(int h, int m) {
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private void toggleLockMode() {
        String[] labels = {
            getString(R.string.challenge_simple),
            getString(R.string.challenge_memory),
            getString(R.string.challenge_math)
        };
        String[] keys = {
            SleepLockActivity.CHALLENGE_SIMPLE,
            SleepLockActivity.CHALLENGE_MEMORY,
            SleepLockActivity.CHALLENGE_MATH
        };
        String current = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(SleepLockActivity.PREF_CHALLENGE, SleepLockActivity.CHALLENGE_SIMPLE);
        int checkedItem = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) { checkedItem = i; break; }

        new AlertDialog.Builder(this)
                .setTitle(R.string.challenge_select_title)
                .setSingleChoiceItems(labels, checkedItem, (d, which) -> {
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString(SleepLockActivity.PREF_CHALLENGE, keys[which]).apply();
                    updateLockModeUI();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ─── Privacy policy ──────────────────────────────────────────────────────

    private void showPrivacyPolicyIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)) return;

        String lang = Locale.getDefault().getLanguage();
        String file = "fa".equals(lang) ? "privacy_policy_fa.html" : "privacy_policy_en.html";

        android.webkit.WebView webView = new android.webkit.WebView(this);
        webView.loadUrl("file:///android_asset/" + file);

        new AlertDialog.Builder(this)
                .setTitle(R.string.privacy_policy_title)
                .setView(webView)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, (d, w) ->
                        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, true).apply())
                .setNegativeButton(R.string.decline, (d, w) -> finishAffinity())
                .show();
    }

    // ─── Onboarding ──────────────────────────────────────────────────────────

    private void checkOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        NotificationManager nm  = getSystemService(NotificationManager.class);

        if (!nm.isNotificationPolicyAccessGranted() && !prefs.getBoolean(KEY_OB_DND_SHOWN, false)) {
            prefs.edit().putBoolean(KEY_OB_DND_SHOWN, true).apply();
            showDndOnboardingDialog();
            return;
        }

        boolean dndHandled = nm.isNotificationPolicyAccessGranted()
                || prefs.getBoolean(KEY_OB_DND_SHOWN, false);
        if (dndHandled
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)
                && !prefs.getBoolean(KEY_OB_OVERLAY_SHOWN, false)) {
            prefs.edit().putBoolean(KEY_OB_OVERLAY_SHOWN, true).apply();
            showOverlayOnboardingDialog();
        }
    }

    private void showDndOnboardingDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dnd_dialog_title)
                .setMessage(R.string.dnd_onboarding_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                .setNegativeButton(R.string.later, (d, w) -> checkOnboarding())
                .setCancelable(false)
                .show();
    }

    private void showOverlayOnboardingDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_dialog_title)
                .setMessage(R.string.overlay_onboarding_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()))))
                .setNegativeButton(R.string.later, null)
                .setCancelable(false)
                .show();
    }

    private void onOverlayRowTapped() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_dialog_title)
                .setMessage(R.string.overlay_dialog_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateOverlayUI() {
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        textOverlayStatus.setText(granted ? R.string.overlay_on : R.string.overlay_off);
    }

    private void updateLockModeUI() {
        String mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(SleepLockActivity.PREF_CHALLENGE, SleepLockActivity.CHALLENGE_SIMPLE);
        int res;
        switch (mode) {
            case SleepLockActivity.CHALLENGE_MEMORY: res = R.string.challenge_memory; break;
            case SleepLockActivity.CHALLENGE_MATH:   res = R.string.challenge_math;   break;
            default:                                 res = R.string.challenge_simple;  break;
        }
        textLockMode.setText(res);
    }

    // ─── Sound picker ─────────────────────────────────────────────────────────

    private void showSoundPickerDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_sound_title)
                .setItems(new CharSequence[]{
                        getString(R.string.choose_system_ringtone),
                        getString(R.string.choose_audio_file)
                }, (dialog, which) -> {
                    if (which == 0) openSystemRingtonePicker();
                    else            openFilePicker();
                })
                .show();
    }

    private void openSystemRingtonePicker() {
        String saved = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(WakeAlarmService.PREF_SOUND_URI, null);
        Intent intent = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                android.media.RingtoneManager.TYPE_ALARM);
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        if (saved != null) {
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    Uri.parse(saved));
        }
        ringtoneLauncher.launch(intent);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        fileLauncher.launch(intent);
    }

    private void saveAlarmSoundUri(String uriStr) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(WakeAlarmService.PREF_SOUND_URI, uriStr).apply();
        updateSoundUI();
    }

    private void updateSoundUI() {
        String saved = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(WakeAlarmService.PREF_SOUND_URI, null);
        if (saved == null) {
            textSoundName.setText(R.string.sound_default);
            return;
        }
        Uri uri = Uri.parse(saved);
        android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, uri);
        String title = r != null ? r.getTitle(this) : null;
        textSoundName.setText(title != null ? title : getString(R.string.sound_custom_file));
    }

    // ─── Permission dialogs ──────────────────────────────────────────────────

    private void showDndPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dnd_dialog_title)
                .setMessage(R.string.dnd_dialog_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false)
                .show();
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
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (!nm.canUseFullScreenIntent()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.fullscreen_permission_title)
                        .setMessage(R.string.fullscreen_permission_message)
                        .setPositiveButton(R.string.go_to_settings, (d, w) ->
                                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        }
    }

    // ─── Date ────────────────────────────────────────────────────────────────

    private String buildLocalizedDate() {
        Calendar cal  = Calendar.getInstance();
        String   lang = java.util.Locale.getDefault().getLanguage();
        if ("fa".equals(lang)) {
            String[] days   = {"یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه","شنبه"};
            String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                               "مهر","آبان","آذر","دی","بهمن","اسفند"};
            int[] j = JalaliCalendar.toJalali(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
            return days[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    + "،  " + j[2] + " " + months[j[1] - 1] + " " + j[0];
        }
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault())
                + ",  "
                + cal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())
                + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + cal.get(Calendar.YEAR);
    }
}
