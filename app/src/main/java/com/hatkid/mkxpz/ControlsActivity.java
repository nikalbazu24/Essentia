package com.hatkid.mkxpz;

import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.ui.ControlsGuide;
import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.Ui;

import java.util.Locale;

/** Controls manual + a live controller tester (press buttons, move the sticks). */
public class ControlsActivity extends ControllerActivity
{
    private TextView mLastButton;
    private TextView mSticks;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);

        LinearLayout header = new LinearLayout(this);
        Button back = Ui.button(this, "‹  " + getString(R.string.set_back), false);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = Ui.text(this, getString(R.string.ctl_title), 24, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 14), 0, 0, 0);
        header.addView(title);
        list.addView(header);

        // Live tester
        TextView testTitle = Ui.text(this, getString(R.string.ctl_test_title), 18, Ui.ACCENT_LIGHT, true);
        testTitle.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 6));
        list.addView(testTitle);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(this, Ui.SURFACE, 14));
        int p = Ui.dp(this, 14);
        box.setPadding(p, p, p, p);
        mLastButton = Ui.text(this, getString(R.string.ctl_test_press), 17, Ui.GOLD, true);
        box.addView(mLastButton);
        mSticks = Ui.text(this, getString(R.string.ctl_test_sticks, "—"), 14, Ui.TEXT_DIM, false);
        mSticks.setPadding(0, Ui.dp(this, 6), 0, 0);
        box.addView(mSticks);
        list.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // Manual
        TextView manual = Ui.text(this, getString(R.string.ctl_manual_title), 18, Ui.ACCENT_LIGHT, true);
        manual.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 6));
        list.addView(manual);
        list.addView(ControlsGuide.buildTable(this));

        setContentView(scroll);
        back.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e)
    {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int mapped = Gamepad.mapControllerKey(code);
            String name = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
            String key = mapped < 0 ? getString(R.string.ctl_test_essentia)
                : KeyEvent.keyCodeToString(mapped).replace("KEYCODE_", "");
            mLastButton.setText(getString(R.string.ctl_test_result, name, key));
        }
        // B (and Android Back) still go back; everything else is only for testing
        if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK
            || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            return super.dispatchKeyEvent(e);
        }
        return KeyEvent.isGamepadButton(code) || super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e)
    {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            mSticks.setText(getString(R.string.ctl_test_sticks, String.format(Locale.ROOT,
                "X %.2f  Y %.2f   ·   Z %.2f  RZ %.2f   ·   HAT %.0f/%.0f",
                e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
                e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ),
                e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y))));
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }
}
