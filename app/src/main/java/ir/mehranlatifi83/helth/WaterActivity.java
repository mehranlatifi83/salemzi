package ir.mehranlatifi83.helth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WaterActivity extends AppCompatActivity {

    private MaterialSwitch switchWater;
    private TextView       textWaterStatus;
    private TextView       textReminderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water);

        bindViews();
        setupBottomNav();
        setupSwitch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    // ─── View wiring ─────────────────────────────────────────────────────────

    private void bindViews() {
        switchWater      = findViewById(R.id.switch_water);
        textWaterStatus  = findViewById(R.id.text_water_status);
        textReminderList = findViewById(R.id.text_reminder_list);

        ((TextView) findViewById(R.id.text_water_date)).setText(buildPersianDate());
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_water);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_sleep) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return true;
            }
            return true;
        });
    }

    private void setupSwitch() {
        switchWater.setChecked(WaterReminderManager.isEnabled(this));
        switchWater.setOnCheckedChangeListener((btn, checked) -> {
            WaterReminderManager.setEnabled(this, checked);
            refreshUI();
        });
    }

    // ─── UI refresh ──────────────────────────────────────────────────────────

    private void refreshUI() {
        boolean enabled = WaterReminderManager.isEnabled(this);

        switchWater.setOnCheckedChangeListener(null);
        switchWater.setChecked(enabled);
        switchWater.setOnCheckedChangeListener((btn, checked) -> {
            WaterReminderManager.setEnabled(this, checked);
            refreshUI();
        });

        textWaterStatus.setText(enabled ? R.string.status_active : R.string.status_inactive);
        updateReminderList();
    }

    private void updateReminderList() {
        List<int[]> slots = WaterReminderManager.computeReminderTimes(this);
        StringBuilder sb  = new StringBuilder();
        for (int[] slot : slots) {
            sb.append(String.format(Locale.getDefault(), "%02d:%02d\n", slot[0], slot[1]));
        }
        textReminderList.setText(sb.toString().trim());
    }

    // ─── Date ────────────────────────────────────────────────────────────────

    private String buildPersianDate() {
        Calendar cal    = Calendar.getInstance();
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                           "مهر","آبان","آذر","دی","بهمن","اسفند"};
        int[] j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        return j[2] + " " + months[j[1] - 1] + " " + j[0];
    }
}
