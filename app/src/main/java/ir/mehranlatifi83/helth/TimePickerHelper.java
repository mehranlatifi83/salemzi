package ir.mehranlatifi83.helth;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.Locale;

/**
 * Custom time picker dialog with two switchable modes:
 *  - Keyboard mode: two numeric EditText fields (digits-only keyboard)
 *  - Scroll mode:   two NumberPicker spinners (no keyboard required)
 * The user can toggle between modes with the button in the dialog title bar.
 */
public class TimePickerHelper {

    public interface OnTimeSetListener {
        void onTimeSet(int hour, int minute);
    }

    private static final int MODE_KEYBOARD = 0;
    private static final int MODE_SCROLL   = 1;

    public static void show(Context ctx, String title,
                            int initialHour, int initialMin,
                            OnTimeSetListener listener) {
        // Mutable mode state
        int[] mode = {MODE_KEYBOARD};

        // ── Keyboard view ────────────────────────────────────────────────────
        LinearLayout keyboardView = new LinearLayout(ctx);
        keyboardView.setOrientation(LinearLayout.HORIZONTAL);
        keyboardView.setGravity(Gravity.CENTER);
        int hPad = dp(ctx, 24);
        keyboardView.setPadding(hPad, dp(ctx, 16), hPad, dp(ctx, 8));

        EditText etHour = makeEditText(ctx, initialHour);
        TextView colon  = new TextView(ctx);
        colon.setText(":");
        colon.setTextSize(32);
        colon.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
        EditText etMin = makeEditText(ctx, initialMin);

        keyboardView.addView(etHour);
        keyboardView.addView(colon);
        keyboardView.addView(etMin);

        // Auto-advance hour → minute after 2 digits
        etHour.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (s.length() == 2) etMin.requestFocus();
            }
        });

        // ── Scroll (NumberPicker) view ────────────────────────────────────────
        LinearLayout scrollView = new LinearLayout(ctx);
        scrollView.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.setGravity(Gravity.CENTER);
        scrollView.setPadding(hPad, dp(ctx, 8), hPad, dp(ctx, 8));
        scrollView.setVisibility(android.view.View.GONE);

        NumberPicker npHour = makeNumberPicker(ctx, 0, 23, initialHour);
        TextView colon2 = new TextView(ctx);
        colon2.setText(":");
        colon2.setTextSize(32);
        colon2.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
        NumberPicker npMin = makeNumberPicker(ctx, 0, 59, initialMin);

        scrollView.addView(npHour);
        scrollView.addView(colon2);
        scrollView.addView(npMin);

        // ── Container holding both views ─────────────────────────────────────
        FrameLayout container = new FrameLayout(ctx);
        container.addView(keyboardView);
        container.addView(scrollView);

        // ── Toggle button row ────────────────────────────────────────────────
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        TextView toggleBtn = new TextView(ctx);
        toggleBtn.setText("🕐  حالت چرخشی");
        toggleBtn.setTextSize(13);
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setPadding(0, 0, 0, dp(ctx, 12));
        toggleBtn.setOnClickListener(v -> {
            if (mode[0] == MODE_KEYBOARD) {
                // Switch to scroll — sync current keyboard values into NumberPickers
                npHour.setValue(parseField(etHour, 0, 23, initialHour));
                npMin.setValue(parseField(etMin, 0, 59, initialMin));
                keyboardView.setVisibility(android.view.View.GONE);
                scrollView.setVisibility(android.view.View.VISIBLE);
                toggleBtn.setText("⌨  حالت تایپ");
                mode[0] = MODE_SCROLL;
                hideKeyboard(ctx, etHour);
            } else {
                // Switch to keyboard — sync NumberPicker values into EditTexts
                etHour.setText(fmt(npHour.getValue()));
                etMin.setText(fmt(npMin.getValue()));
                scrollView.setVisibility(android.view.View.GONE);
                keyboardView.setVisibility(android.view.View.VISIBLE);
                toggleBtn.setText("🕐  حالت چرخشی");
                mode[0] = MODE_KEYBOARD;
            }
        });

        wrapper.addView(container);
        wrapper.addView(toggleBtn);

        // ── Dialog ───────────────────────────────────────────────────────────
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int h, m;
                    if (mode[0] == MODE_KEYBOARD) {
                        h = parseField(etHour, 0, 23, initialHour);
                        m = parseField(etMin,  0, 59, initialMin);
                    } else {
                        h = npHour.getValue();
                        m = npMin.getValue();
                    }
                    listener.onTimeSet(h, m);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        // Open keyboard on hour field after dialog is shown
        etHour.postDelayed(() -> {
            etHour.requestFocus();
            etHour.selectAll();
        }, 150);
    }

    // ── Widget factories ──────────────────────────────────────────────────────

    private static EditText makeEditText(Context ctx, int value) {
        EditText et = new EditText(ctx);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        et.setGravity(Gravity.CENTER);
        et.setTextSize(32);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        et.setText(fmt(value));
        et.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, 72),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        et.setLayoutParams(lp);
        return et;
    }

    private static NumberPicker makeNumberPicker(Context ctx, int min, int max, int value) {
        NumberPicker np = new NumberPicker(ctx);
        np.setMinValue(min);
        np.setMaxValue(max);
        np.setValue(value);
        np.setWrapSelectorWheel(true);
        // Format with leading zero
        np.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        // Force immediate re-render with formatter applied
        np.invalidate();
        return np;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int parseField(EditText et, int min, int max, int fallback) {
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String fmt(int value) {
        return String.format(Locale.getDefault(), "%02d", value);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static void hideKeyboard(Context ctx, android.view.View view) {
        InputMethodManager imm = (InputMethodManager)
                ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
