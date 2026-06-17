package ir.mehranlatifi83.helth;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
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

    private TextView          textClock;
    private TextView          textLockDate;
    private TextView          textMathProblem;
    private TextInputEditText editAnswer;
    private TextInputLayout   inputLayoutAnswer;
    private TextView          textError;

    private int mathAnswer;
    private int wrongCount = 0;

    private final Handler   clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable  clockTick    = new Runnable() {
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

        textClock         = findViewById(R.id.text_clock);
        textLockDate      = findViewById(R.id.text_lock_date);
        textMathProblem   = findViewById(R.id.text_math_problem);
        editAnswer        = findViewById(R.id.edit_answer);
        inputLayoutAnswer = findViewById(R.id.input_layout_answer);
        textError         = findViewById(R.id.text_error);
        MaterialButton btnConfirm = findViewById(R.id.btn_confirm);

        updateClock();
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

    // ─── Math captcha ────────────────────────────────────────────────────────

    /** Generates a new arithmetic problem. Difficulty scales with wrong attempts:
     *  0–1 wrong  → simple addition / subtraction (results 20–80)
     *  2–3 wrong  → single-digit multiplication (3–10 × 3–10)
     *  4+ wrong   → harder multiplication (7–12 × 7–12) */
    private void generateNewProblem() {
        Random rnd = new Random();
        int a, b;
        String op;

        int difficulty = Math.min(wrongCount / 2, 2);
        switch (difficulty) {
            case 0:
                a = 10 + rnd.nextInt(40);
                b = 10 + rnd.nextInt(40);
                if (rnd.nextBoolean()) {
                    op = "+";  mathAnswer = a + b;
                } else {
                    if (a < b) { int t = a; a = b; b = t; }
                    op = "−";  mathAnswer = a - b;
                }
                break;
            case 1:
                a = 3 + rnd.nextInt(8);
                b = 3 + rnd.nextInt(8);
                op = "×";  mathAnswer = a * b;
                break;
            default:
                a = 7 + rnd.nextInt(6);
                b = 7 + rnd.nextInt(6);
                op = "×";  mathAnswer = a * b;
                break;
        }

        textMathProblem.setText(a + "  " + op + "  " + b + "  =  ؟");
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
        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();

        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        finish();
    }
}
