package ir.mehranlatifi83.helth;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

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

    private TextView         textClock;
    private TextView         textLockDate;
    private TextView         textMathProblem;
    private TextInputEditText editAnswer;
    private TextInputLayout  inputLayoutAnswer;
    private TextView         textError;

    private int  mathAnswer;
    private int  wrongCount = 0;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

    // ─── Launch helper ───────────────────────────────────────────────────────

    public static void launch(Context ctx) {
        Intent i = new Intent(ctx, SleepLockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
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

    // ─── Window setup ────────────────────────────────────────────────────────

    private void setupWindowFlags() {
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat ctrl =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
    }

    // ─── Block back & recent apps ────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        // بلاکه — کاربر نمی‌تونه با Back برگرده
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true; // بلاک
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─── Clock ───────────────────────────────────────────────────────────────

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        textClock.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
        textLockDate.setText(getPersianDate(cal));
    }

    private String getPersianDate(Calendar cal) {
        String[] days   = {"یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه","شنبه"};
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                           "مهر","آبان","آذر","دی","بهمن","اسفند"};
        String dayName = days[cal.get(Calendar.DAY_OF_WEEK) - 1];
        int[] j = JalaliCalendar.toJalali(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        return dayName + "،  " + j[2] + " " + months[j[1] - 1];
    }

    // ─── Math captcha ────────────────────────────────────────────────────────

    private void generateNewProblem() {
        Random rnd = new Random();
        int a, b;
        String op;

        // با هر بار اشتباه، مسئله سخت‌تر میشه
        int difficulty = Math.min(wrongCount / 2, 2);

        switch (difficulty) {
            case 0: // جمع/تفریق ساده
                a = 10 + rnd.nextInt(30);
                b = 10 + rnd.nextInt(30);
                if (rnd.nextBoolean()) {
                    op = "+";
                    mathAnswer = a + b;
                } else {
                    if (a < b) { int tmp = a; a = b; b = tmp; }
                    op = "−";
                    mathAnswer = a - b;
                }
                break;
            case 1: // ضرب تا ۱۰
                a = 3 + rnd.nextInt(8);
                b = 3 + rnd.nextInt(8);
                op = "×";
                mathAnswer = a * b;
                break;
            default: // ضرب بزرگ‌تر
                a = 7 + rnd.nextInt(6);
                b = 7 + rnd.nextInt(6);
                op = "×";
                mathAnswer = a * b;
                break;
        }

        textMathProblem.setText(a + "  " + op + "  " + b + "  =  ؟");
        editAnswer.setText("");
        textError.setVisibility(View.INVISIBLE);
        inputLayoutAnswer.setError(null);
    }

    private void checkAnswer() {
        String input = editAnswer.getText() == null ? "" : editAnswer.getText().toString().trim();
        if (input.isEmpty()) return;

        try {
            int typed = Integer.parseInt(input);
            if (typed == mathAnswer) {
                exitSleepMode();
            } else {
                wrongCount++;
                showWrongAnswer();
            }
        } catch (NumberFormatException e) {
            showWrongAnswer();
        }
    }

    private void showWrongAnswer() {
        String msg;
        if (wrongCount < 3) {
            msg = "اشتباهه! دوباره امتحان کن";
        } else if (wrongCount < 6) {
            msg = "نه! بذار بخوابی 😴  (" + wrongCount + " بار اشتباه)";
        } else {
            msg = "برو بخواب! " + wrongCount + " بار اشتباه زدی 🌙";
        }

        textError.setText(msg);
        textError.setVisibility(View.VISIBLE);

        // لرزش
        editAnswer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
        editAnswer.setText("");

        // مسئله جدید بده
        editAnswer.postDelayed(this::generateNewProblem, 800);
    }

    // ─── Exit ────────────────────────────────────────────────────────────────

    private void exitSleepMode() {
        // VPN
        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();

        // صدا
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        // state
        getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        finish();
    }
}
