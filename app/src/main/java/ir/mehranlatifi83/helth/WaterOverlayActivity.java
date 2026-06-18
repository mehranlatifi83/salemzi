package ir.mehranlatifi83.helth;

import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class WaterOverlayActivity extends AppCompatActivity {

    private static final long AUTO_DISMISS_MS = 60_000; // auto-dismiss after 60 seconds

    private CountDownTimer autoTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over the lock screen and turn the screen on if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_water_overlay);

        int slot = getIntent().getIntExtra(WaterReminderReceiver.EXTRA_SLOT, 0);
        setupMessage(slot);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });

        MaterialButton btnDone = findViewById(R.id.btn_water_done);
        btnDone.setOnClickListener(v -> finish());

        startAutoTimer();
    }

    private void setupMessage(int slot) {
        int[] titleRes = {
            R.string.water_reminder_title_0, R.string.water_reminder_title_1,
            R.string.water_reminder_title_2, R.string.water_reminder_title_3,
            R.string.water_reminder_title_4, R.string.water_reminder_title_5,
            R.string.water_reminder_title_6, R.string.water_reminder_title_7,
        };
        int[] textRes = {
            R.string.water_reminder_text_0, R.string.water_reminder_text_1,
            R.string.water_reminder_text_2, R.string.water_reminder_text_3,
            R.string.water_reminder_text_4, R.string.water_reminder_text_5,
            R.string.water_reminder_text_6, R.string.water_reminder_text_7,
        };

        int safe = (slot >= 0 && slot < titleRes.length) ? slot : 0;
        ((TextView) findViewById(R.id.text_overlay_title)).setText(titleRes[safe]);
        ((TextView) findViewById(R.id.text_overlay_body)).setText(textRes[safe]);
    }

    private void startAutoTimer() {
        autoTimer = new CountDownTimer(AUTO_DISMISS_MS, AUTO_DISMISS_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() { finish(); }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoTimer != null) autoTimer.cancel();
    }

}
