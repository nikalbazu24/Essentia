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
    private LinearLayout mMapRows;
    private LinearLayout mManual;

    /** One row per button: "A  ·  Confirm (Enter)"; tap to change it. */
    private void refreshMap()
    {
        ButtonMap.load(this);
        mMapRows.removeAllViews();
        for (int b : ButtonMap.BUTTONS) {
            ButtonMap.Action a = ButtonMap.forButton(b);
            Button row = Ui.button(this, ButtonMap.buttonName(b) + "   \u2192   "
                + (a != null ? getString(a.labelRes) : "?"), false);
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            row.setOnClickListener(v -> chooseAction(b));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(this, 4);
            mMapRows.addView(row, lp);
        }
        mManual.removeAllViews();
        mManual.addView(ControlsGuide.buildTable(this));
    }

    private void chooseAction(int button)
    {
        String[] labels = new String[ButtonMap.ACTIONS.length];
        int checked = -1;
        ButtonMap.Action current = ButtonMap.forButton(button);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(ButtonMap.ACTIONS[i].labelRes);
            if (current != null && current.id.equals(ButtonMap.ACTIONS[i].id)) checked = i;
        }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.btn_map_choose, ButtonMap.buttonName(button)))
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                ButtonMap.set(this, button, ButtonMap.ACTIONS[which].id);
                d.dismiss();
                refreshMap();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

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

        // Button map (which button does what)
        TextView mapTitle = Ui.text(this, getString(R.string.btn_map_title), 18, Ui.ACCENT_LIGHT, true);
        mapTitle.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 2));
        list.addView(mapTitle);
        TextView mapDesc = Ui.text(this, getString(R.string.btn_map_desc), 14, Ui.TEXT_DIM, false);
        mapDesc.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
        list.addView(mapDesc);
        mMapRows = new LinearLayout(this);
        mMapRows.setOrientation(LinearLayout.VERTICAL);
        list.addView(mMapRows);
        LinearLayout presets = new LinearLayout(this);
        Button nintendo = Ui.button(this, getString(R.string.btn_map_nintendo), false);
        nintendo.setOnClickListener(v -> { ButtonMap.nintendoLayout(this); refreshMap(); });
        Button reset = Ui.button(this, getString(R.string.btn_map_reset), false);
        reset.setOnClickListener(v -> { ButtonMap.reset(this); refreshMap(); });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        plp.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
        presets.addView(nintendo, plp);
        presets.addView(reset, new LinearLayout.LayoutParams(plp));
        list.addView(presets);

        // Manual
        TextView manual = Ui.text(this, getString(R.string.ctl_manual_title), 18, Ui.ACCENT_LIGHT, true);
        manual.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 6));
        list.addView(manual);
        mManual = new LinearLayout(this);
        mManual.setOrientation(LinearLayout.VERTICAL);
        list.addView(mManual);
        refreshMap();

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
            ButtonMap.Action act = ButtonMap.forButton(code);
            String key = act != null ? getString(act.labelRes)
                : mapped < 0 ? getString(R.string.ctl_test_essentia)
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

    @Override
    protected void onResume()
    {
        super.onResume();
        MusicPlayer.resume(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        MusicPlayer.pause();
    }
}
