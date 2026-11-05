package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.view.WindowManager;

/**
 * Base for Essentia's own screens: full-screen, dark, and usable with a controller.
 * A acts as "select" and B as "back", like in any console menu.
 */
public abstract class ControllerActivity extends Activity
{

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(com.hatkid.mkxpz.AppLanguage.wrap(base));
    }

    private int mAppearanceStamp;
    private String mLanguage;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Ui.applyTheme(this);
        setTheme(Ui.LIGHT?com.hatkid.mkxpz.R.style.AppThemeLight:com.hatkid.mkxpz.R.style.AppTheme);
        super.onCreate(savedInstanceState);
        mAppearanceStamp=Ui.themeStamp();
        mLanguage=com.hatkid.mkxpz.AppLanguage.selection(this);
        Window w = getWindow();
        w.getDecorView().setBackgroundColor(Ui.BG);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override protected void onResume() {
        super.onResume();Ui.applyTheme(this);
        if (!mLanguage.equals(com.hatkid.mkxpz.AppLanguage.selection(this))
            || (!(this instanceof com.hatkid.mkxpz.LibraryActivity) && mAppearanceStamp != Ui.themeStamp())) recreate();
    }

    @Override public void setContentView(View content) {
        super.setContentView(AppShell.wrap(this, content));
    }

    @Override public void setContentView(View content, ViewGroup.LayoutParams params) {
        setContentView(content);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    protected void hideSystemBars()
    {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    /** Extra controller shortcuts for a screen (X, Y, Start...). Return true if handled. */
    protected boolean onControllerButton(int keyCode)
    {
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e)
    {
        int code = e.getKeyCode();

        if (code == KeyEvent.KEYCODE_BUTTON_A) {
            return super.dispatchKeyEvent(translate(e, KeyEvent.KEYCODE_DPAD_CENTER));
        }
        if (code == KeyEvent.KEYCODE_BUTTON_B) {
            return super.dispatchKeyEvent(translate(e, KeyEvent.KEYCODE_BACK));
        }
        if (KeyEvent.isGamepadButton(code)) {
            if (e.getAction() == KeyEvent.ACTION_UP && onControllerButton(code)) return true;
            if (e.getAction() == KeyEvent.ACTION_DOWN && wantsButton(code)) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Whether {@link #onControllerButton} handles this button (so its press is swallowed). */
    protected boolean wantsButton(int keyCode)
    {
        return false;
    }

    private static KeyEvent translate(KeyEvent e, int newCode)
    {
        return new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), newCode,
            e.getRepeatCount(), e.getMetaState(), e.getDeviceId(), e.getScanCode(),
            e.getFlags(), e.getSource());
    }
}
