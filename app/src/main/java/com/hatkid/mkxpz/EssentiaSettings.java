package com.hatkid.mkxpz;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.view.InputDevice;

/** Essentia's user settings (shared by the library, the settings screen and the game). */
public final class EssentiaSettings
{
    public static final String PREFS = "essentia";

    /** On-screen controls: shown when no physical controller is connected. */
    public static final String OSC_AUTO = "auto";
    /** On-screen controls: always shown. */
    public static final String OSC_ALWAYS = "always";
    /** On-screen controls: never shown. */
    public static final String OSC_NEVER = "never";

    public static final String DUAL_AUTO = "auto";
    public static final String DUAL_OFF = "off";

    private static final String KEY_OSC_MODE = "osc_mode";
    private static final String KEY_OSC_OPACITY = "osc_opacity";
    private static final String KEY_OSC_SCALE = "osc_scale";
    private static final String KEY_DUAL = "dual_screen";
    private static final String KEY_SMOOTH = "smooth_scaling";

    private EssentiaSettings() { }

    private static SharedPreferences prefs(Context c)
    {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String oscMode(Context c) { return prefs(c).getString(KEY_OSC_MODE, OSC_AUTO); }
    public static void setOscMode(Context c, String v) { prefs(c).edit().putString(KEY_OSC_MODE, v).commit(); }

    /** 10..100 % */
    public static int oscOpacity(Context c) { return prefs(c).getInt(KEY_OSC_OPACITY, 40); }
    public static void setOscOpacity(Context c, int v) { prefs(c).edit().putInt(KEY_OSC_OPACITY, v).commit(); }

    /** 60..160 % */
    public static int oscScale(Context c) { return prefs(c).getInt(KEY_OSC_SCALE, 100); }
    public static void setOscScale(Context c, int v) { prefs(c).edit().putInt(KEY_OSC_SCALE, v).commit(); }

    public static String dualScreen(Context c) { return prefs(c).getString(KEY_DUAL, DUAL_AUTO); }
    public static void setDualScreen(Context c, String v) { prefs(c).edit().putString(KEY_DUAL, v).commit(); }

    /** Smooth (bilinear) scaling instead of sharp pixels. */
    public static boolean smoothScaling(Context c) { return prefs(c).getBoolean(KEY_SMOOTH, false); }
    public static void setSmoothScaling(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_SMOOTH, v).commit(); }

    /** True when a physical controller (gamepad / joystick) is connected. */
    public static boolean hasPhysicalController(Context c)
    {
        InputManager im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
        int[] ids = im != null ? im.getInputDeviceIds() : InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                return true;
            }
        }
        return false;
    }
}
