package com.hatkid.mkxpz.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.hatkid.mkxpz.R;
import java.util.Locale;

/** Touch keyboard that leaves the engine's game window active. */
public final class GameKeyboard extends ScrollView {
    public interface Input {
        void press(int key, String text, boolean shift);
    }

    private final LinearLayout rows;
    private final Input input;
    private final Runnable close;
    private boolean upperCase;
    private boolean symbols;

    public GameKeyboard(Context context, Input input, Runnable close) {
        super(context);
        this.input = input;
        this.close = close;
        setFillViewport(false);
        setBackground(Ui.rounded(context, Ui.BG, 18));
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(context, 6);
        rows.setPadding(padding, padding, padding, padding);
        addView(rows);
        render();
    }

    private void render() {
        rows.removeAllViews();
        LinearLayout header = row();
        key(header, getContext().getString(R.string.btn_keyboard), 2, null);
        key(header, getContext().getString(R.string.close), 1, close);

        if (symbols) {
            letters("1234567890");
            letters("áéíóúñü");
        } else {
            letters("qwertyuiop");
            letters("asdfghjkl");
            letters("zxcvbnm");
        }

        LinearLayout bottom = row();
        key(bottom, upperCase ? "⇧ ✓" : "⇧", 1, () -> { upperCase = !upperCase; render(); })
            .setContentDescription(getContext().getString(R.string.keyboard_caps));
        key(bottom, symbols ? "ABC" : "123 ñ", 1, () -> { symbols = !symbols; render(); })
            .setContentDescription(getContext().getString(symbols ? R.string.keyboard_letters : R.string.keyboard_symbols));
        key(bottom, getContext().getString(R.string.keyboard_space), 2,
            () -> input.press(KeyEvent.KEYCODE_SPACE, " ", false));
        key(bottom, "⌫", 1, () -> input.press(KeyEvent.KEYCODE_DEL, null, false))
            .setContentDescription(getContext().getString(R.string.keyboard_delete));
        key(bottom, "↵", 1, () -> input.press(KeyEvent.KEYCODE_ENTER, null, false))
            .setContentDescription(getContext().getString(R.string.keyboard_enter));

        LinearLayout navigation = row();
        key(navigation, "Esc", 1, () -> input.press(KeyEvent.KEYCODE_ESCAPE, null, false));
        key(navigation, "←", 1, () -> input.press(KeyEvent.KEYCODE_DPAD_LEFT, null, false));
        key(navigation, "↑", 1, () -> input.press(KeyEvent.KEYCODE_DPAD_UP, null, false));
        key(navigation, "↓", 1, () -> input.press(KeyEvent.KEYCODE_DPAD_DOWN, null, false));
        key(navigation, "→", 1, () -> input.press(KeyEvent.KEYCODE_DPAD_RIGHT, null, false));
    }

    private void letters(String characters) {
        LinearLayout row = row();
        for (char character : characters.toCharArray()) {
            String text = String.valueOf(character);
            if (upperCase) text = text.toUpperCase(Locale.ROOT);
            String value = text;
            int code = KeyEvent.KEYCODE_UNKNOWN;
            if (character >= 'a' && character <= 'z') code = KeyEvent.KEYCODE_A + character - 'a';
            if (character >= '0' && character <= '9') code = KeyEvent.KEYCODE_0 + character - '0';
            int keyCode = code;
            key(row, value, 1, () -> input.press(keyCode, value, upperCase && Character.isLetter(character)));
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        rows.addView(row);
        return row;
    }

    private android.view.View key(LinearLayout row, String label, int weight, Runnable action) {
        Context context = getContext();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), weight);
        lp.setMargins(Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2));
        if (action == null) {
            android.widget.TextView title = Ui.text(context, label, 16, Ui.TEXT, true);
            title.setGravity(Gravity.CENTER);
            row.addView(title, lp);
            return title;
        }
        Button button = Ui.button(context, label, false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(v -> action.run());
        row.addView(button, lp);
        return button;
    }
}
