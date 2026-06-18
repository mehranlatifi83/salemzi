package ir.mehranlatifi83.helth;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class SleepLockActivity extends AppCompatActivity {

    private static final String PREFS          = "helth_prefs";
    private static final String KEY_LOCK_MODE  = "lock_exit_mode";
    static final         String MODE_MATH      = "math";
    static final         String MODE_TIMED     = "timed";

    // Math mode views
    private LinearLayout      sectionMath;
    private TextView          textMathProblem;
    private TextInputEditText editAnswer;
    private TextInputLayout   inputLayoutAnswer;
    private TextView          textError;

    // Timed mode views
    private LinearLayout  sectionTimed;
    private TextView      textCountdown;
    private TextView      textNoExitHint;
    private TextView      textCountdownLabel;
    private MaterialButton btnConfirmAwake;

    // Clock views (shared)
    private TextView textClock;
    private TextView textLockDate;

    private int  mathAnswer;
    private int  wrongCount = 0;
    private boolean timedMode;
    private boolean exitCalled = false;

    private CountDownTimer countDownTimer;
    private final Handler  clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick    = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

    // ─── Entry point ─────────────────────────────────────────────────────────

    /** Launches the lock screen. Safe to call from any Activity context. */
    public static void launch(Context ctx) {
        Intent i = new Intent(ctx, SleepLockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ctx.startActivity(i);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupWindowFlags();
        setContentView(R.layout.activity_sleep_lock);
        hideSystemBars();
        blockBackButton();

        textClock          = findViewById(R.id.text_clock);
        textLockDate       = findViewById(R.id.text_lock_date);
        sectionMath        = findViewById(R.id.section_math);
        sectionTimed       = findViewById(R.id.section_timed);
        textCountdown      = findViewById(R.id.text_countdown);
        textNoExitHint     = findViewById(R.id.text_no_exit_hint);
        textCountdownLabel = findViewById(R.id.text_countdown_label);
        btnConfirmAwake    = findViewById(R.id.btn_confirm_awake);

        timedMode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_LOCK_MODE, MODE_MATH).equals(MODE_TIMED);

        updateClock();

        if (timedMode) {
            setupTimedMode();
        } else {
            setupMathMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.post(clockTick);
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        // Safety net: only stop the alarm service if exitSleepMode() was never called
        // (e.g., system killed the activity). Avoids a double-stop when the user exits
        // normally, since exitSleepMode() already sent ACTION_DISMISS.
        if (!exitCalled) WakeAlarmService.stop(this);
    }

    // ─── Mode setup ──────────────────────────────────────────────────────────

    private void setupMathMode() {
        sectionMath.setVisibility(View.VISIBLE);
        sectionTimed.setVisibility(View.GONE);

        textMathProblem   = findViewById(R.id.text_math_problem);
        editAnswer        = findViewById(R.id.edit_answer);
        inputLayoutAnswer = findViewById(R.id.input_layout_answer);
        textError         = findViewById(R.id.text_error);
        MaterialButton btnConfirm = findViewById(R.id.btn_confirm);

        generateNewProblem();

        btnConfirm.setOnClickListener(v -> checkAnswer());
        editAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkAnswer();
                return true;
            }
            return false;
        });
    }

    private void setupTimedMode() {
        sectionMath.setVisibility(View.GONE);
        sectionTimed.setVisibility(View.VISIBLE);
        startCountdown();
    }

    // ─── Window / immersive mode ─────────────────────────────────────────────

    private void setupWindowFlags() {
        int flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Recommended API for showing over the lock screen since API 27
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                   | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        }

        getWindow().addFlags(flags);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
    }

    /** Registers an OnBackPressedCallback that does nothing, preventing
     *  the user from dismissing the lock screen via the back gesture. */
    private void blockBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { /* intentionally empty */ }
        });
    }

    /** Intercepts hardware keys. Home and recents cannot be truly blocked
     *  by apps, but returning true here suppresses the key event within
     *  our activity, preventing unintended interactions. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─── Clock ───────────────────────────────────────────────────────────────

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        textClock.setText(String.format(Locale.getDefault(),
                "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)));
        textLockDate.setText(buildPersianDate(cal));
    }

    private String buildPersianDate(Calendar cal) {
        String[] days   = {"یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه","شنبه"};
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                           "مهر","آبان","آذر","دی","بهمن","اسفند"};
        int[] j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        return days[cal.get(Calendar.DAY_OF_WEEK) - 1]
                + "،  " + j[2] + " " + months[j[1] - 1];
    }

    // ─── Countdown (timed mode) ───────────────────────────────────────────────

    private void startCountdown() {
        int[] wake = ScheduleManager.getWakeTime(this);
        if (wake == null) {
            textCountdown.setText(R.string.no_wake_time_set);
            return;
        }

        long wakeMs    = nextWakeMs(wake[0], wake[1]);
        long remaining = wakeMs - System.currentTimeMillis();

        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long ms) {
                long h = ms / 3_600_000;
                long m = (ms % 3_600_000) / 60_000;
                long s = (ms % 60_000) / 1_000;
                textCountdown.setText(
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
            }
            @Override
            public void onFinish() {
                textCountdown.setText("00:00:00");
                onWakeTimeReached();
            }
        }.start();
    }

    /** Called when the countdown reaches zero. Plays the alarm and shows the confirm button. */
    private void onWakeTimeReached() {
        WakeAlarmService.start(this);
        textNoExitHint.setText(R.string.wake_time_reached);
        textCountdownLabel.setVisibility(View.GONE);
        btnConfirmAwake.setVisibility(View.VISIBLE);
        // exitSleepMode() handles stopping the alarm and all cleanup
        btnConfirmAwake.setOnClickListener(v -> exitSleepMode());
    }

    private long nextWakeMs(int hour, int min) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    // ─── Math captcha ────────────────────────────────────────────────────────

    /** Generates a multi-step arithmetic problem. Difficulty scales with wrong attempts.
     *
     *  Level 0 (0–1 wrong): 2-digit × 1-digit  →  e.g. "34 × 7 = ؟"
     *    Requires real mental multiplication; impossible to solve on autopilot.
     *
     *  Level 1 (2–3 wrong): chained  →  e.g. "(8 × 7) + 23 = ؟"
     *    Forces working memory: compute product first, then add/subtract.
     *
     *  Level 2 (4+ wrong): double multiplication  →  e.g. "(6 × 8) + (4 × 7) = ؟"
     *    Requires holding two intermediate results simultaneously. */
    private void generateNewProblem() {
        Random rnd = new Random();
        String problem;

        int difficulty = Math.min(wrongCount / 2, 2);
        switch (difficulty) {
            case 0: {
                int a = 12 + rnd.nextInt(29); // 12–40
                int b = 3  + rnd.nextInt(7);  // 3–9
                mathAnswer = a * b;
                problem = a + " × " + b + " = ؟";
                break;
            }
            case 1: {
                int a    = 3  + rnd.nextInt(7);  // 3–9
                int b    = 3  + rnd.nextInt(7);  // 3–9
                int c    = 11 + rnd.nextInt(29); // 11–39
                int base = a * b;
                if (rnd.nextBoolean() || base <= c) {
                    mathAnswer = base + c;
                    problem = "(" + a + " × " + b + ") + " + c + " = ؟";
                } else {
                    mathAnswer = base - c;
                    problem = "(" + a + " × " + b + ") - " + c + " = ؟";
                }
                break;
            }
            default: {
                int a = 3 + rnd.nextInt(7); // 3–9
                int b = 3 + rnd.nextInt(7); // 3–9
                int c = 3 + rnd.nextInt(6); // 3–8
                int d = 3 + rnd.nextInt(6); // 3–8
                mathAnswer = a * b + c * d;
                problem = "(" + a + " × " + b + ") + (" + c + " × " + d + ") = ؟";
                break;
            }
        }

        textMathProblem.setText(problem);
        editAnswer.setText("");
        textError.setVisibility(View.INVISIBLE);
        inputLayoutAnswer.setError(null);
    }

    private void checkAnswer() {
        String raw = editAnswer.getText() == null ? "" : editAnswer.getText().toString().trim();
        if (raw.isEmpty()) return;

        try {
            if (Integer.parseInt(raw) == mathAnswer) {
                exitSleepMode();
            } else {
                wrongCount++;
                onWrongAnswer();
            }
        } catch (NumberFormatException e) {
            wrongCount++;
            onWrongAnswer();
        }
    }

    private void onWrongAnswer() {
        String msg;
        if      (wrongCount < 3) msg = "اشتباهه! دوباره امتحان کن";
        else if (wrongCount < 6) msg = "نه! بذار بخوابی 😴  (" + wrongCount + " بار اشتباه)";
        else                     msg = "برو بخواب! " + wrongCount + " بار اشتباه زدی 🌙";

        textError.setText(msg);
        textError.setVisibility(View.VISIBLE);
        editAnswer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
        editAnswer.setText("");

        // Generate a fresh problem after a short delay so the user reads the message
        editAnswer.postDelayed(this::generateNewProblem, 900);
    }

    // ─── Exit ────────────────────────────────────────────────────────────────

    private void exitSleepMode() {
        exitCalled = true;
        // Stop the wake alarm FIRST (sends ACTION_DISMISS → player.reset() immediately)
        WakeAlarmService.stop(this);

        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();

        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        // Cancel lingering sleep notification (posted by SleepScheduleReceiver)
        getSystemService(NotificationManager.class).cancel(2);

        finish();
    }
}
