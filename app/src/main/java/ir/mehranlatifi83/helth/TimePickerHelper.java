package ir.mehranlatifi83.helth;

import android.content.Context;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Locale;

/**
 * Time picker with two switchable modes:
 *  - Scroll mode (default): two NumberPicker spinners, no keyboard
 *  - Clock mode:            MaterialTimePicker circular clock face
 */
public class TimePickerHelper {

    public interface OnTimeSetListener {
        void onTimeSet(int hour, int minute);
    }

    /** Show the picker starting in scroll (NumberPicker) mode. */
    public static void show(FragmentManager fm, Context ctx, String title,
                            int initialHour, int initialMin,
                            OnTimeSetListener listener) {
        showScrollMode(fm, ctx, title, initialHour, initialMin, listener);
    }

    // ── Scroll mode (NumberPicker) ────────────────────────────────────────────

    private static void showScrollMode(FragmentManager fm, Context ctx, String title,
                                       int initialHour, int initialMin,
                                       OnTimeSetListener listener) {
        int pad = dp(ctx, 24);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);

        // Hour : Minute pickers row
        LinearLayout pickersRow = new LinearLayout(ctx);
        pickersRow.setOrientation(LinearLayout.HORIZONTAL);
        pickersRow.setGravity(Gravity.CENTER);
        pickersRow.setPadding(pad, dp(ctx, 8), pad, 0);

        NumberPicker npHour = makeNumberPicker(ctx, 0, 23, initialHour);
        TextView colon = new TextView(ctx);
        colon.setText(":");
        colon.setTextSize(28);
        colon.setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0);
        NumberPicker npMin = makeNumberPicker(ctx, 0, 59, initialMin);

        pickersRow.addView(npHour);
        pickersRow.addView(colon);
        pickersRow.addView(npMin);

        // Toggle button
        TextView toggleBtn = new TextView(ctx);
        toggleBtn.setText("🕐  حالت ساعت دایره‌ای");
        toggleBtn.setTextSize(13);
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setPadding(0, dp(ctx, 4), 0, dp(ctx, 12));

        content.addView(pickersRow);
        content.addView(toggleBtn);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        listener.onTimeSet(npHour.getValue(), npMin.getValue()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        toggleBtn.setOnClickListener(v -> {
            dialog.dismiss();
            showClockMode(fm, ctx, title, npHour.getValue(), npMin.getValue(), listener);
        });

        dialog.show();
    }

    // ── Clock mode (MaterialTimePicker) ───────────────────────────────────────

    private static void showClockMode(FragmentManager fm, Context ctx, String title,
                                      int initialHour, int initialMin,
                                      OnTimeSetListener listener) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .setHour(initialHour)
                .setMinute(initialMin)
                .setTitleText(title)
                .build();

        picker.addOnPositiveButtonClickListener(v ->
                listener.onTimeSet(picker.getHour(), picker.getMinute()));

        picker.show(fm, "clock_picker");

        // After the dialog view is created, intercept the internal mode-toggle button
        // so tapping the keyboard icon goes to our scroll mode instead of typing mode.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.view.View root = picker.getView();
            if (root == null) return;
            // Resolve the resource ID by name to avoid a hard dependency on the
            // internal layout of Material Components.
            // Resources from libraries are merged into the app's package at build time
            int btnId = root.getResources().getIdentifier(
                    "material_timepicker_mode_button", "id",
                    ctx.getPackageName());
            // Fallback: try the material library package directly
            if (btnId == 0) {
                btnId = root.getResources().getIdentifier(
                        "material_timepicker_mode_button", "id",
                        "com.google.android.material");
            }
            android.view.View modeBtn = (btnId != 0) ? root.findViewById(btnId) : null;
            // Second fallback: traverse the view tree for an ImageButton in the content area
            if (modeBtn == null) modeBtn = findModeButton(root);
            if (modeBtn == null) return;
            modeBtn.setOnClickListener(v -> {
                int h = picker.getHour();
                int m = picker.getMinute();
                picker.dismiss();
                showScrollMode(fm, ctx, title, h, m, listener);
            });
        }, 300);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static NumberPicker makeNumberPicker(Context ctx, int min, int max, int value) {
        NumberPicker np = new NumberPicker(ctx);
        np.setMinValue(min);
        np.setMaxValue(max);
        np.setValue(value);
        np.setWrapSelectorWheel(true);
        np.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        np.invalidate();
        return np;
    }

    /**
     * Traverses the picker's view tree to find the mode-toggle ImageButton.
     * It's the only ImageButton that is NOT inside the dialog button bar.
     */
    private static android.view.View findModeButton(android.view.View root) {
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof android.widget.ImageButton
                    || child.getClass().getSimpleName().contains("CheckableImageButton")) {
                return child;
            }
            android.view.View found = findModeButton(child);
            if (found != null) return found;
        }
        return null;
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
