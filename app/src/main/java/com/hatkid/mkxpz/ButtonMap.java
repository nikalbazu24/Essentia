package com.hatkid.mkxpz;

import android.content.Context;
import android.view.KeyEvent;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which controller button does what. Every button can send a PC key to the game or
 * run an Essentia action (turbo, Auto Fire, screenshot, keyboard, menu).
 * Select is not listed: it's the shortcut button (Select + Start / R1 / L1).
 */
public final class ButtonMap
{
    private static final String KEY = "button_map_v1";
    private static String sKey = KEY;
    private static String scope(Context c) {
        if (c instanceof android.app.Activity) {
            String path = ((android.app.Activity)c).getIntent().getStringExtra(MainActivity.EXTRA_GAME_PATH);
            if (path != null && !path.isEmpty()) {
                try {
                    byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(path.getBytes("UTF-8"));
                    StringBuilder key = new StringBuilder(KEY + ":");
                    for (byte b : bytes) key.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                    return key.toString();
                } catch (Exception e) { throw new IllegalStateException(e); }
            }
        }
        return KEY;
    }

    /** Physical buttons that can be changed, in the order shown to the user. */
    public static final int[] BUTTONS = {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START,
    };

    /** Button names as printed on most controllers. */
    public static String buttonName(int keycode)
    {
        switch (keycode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            default: return KeyEvent.keyCodeToString(keycode).replace("KEYCODE_", "");
        }
    }

    /** What a button can do. */
    public static final class Action
    {
        public final String id;
        /** PC key sent to the game, or 0 for an Essentia action. */
        public final int keycode;
        /** Short key name for the manual ("Enter"), or null for Essentia actions. */
        public final String keyName;
        public final int labelRes;
        public final int useRes;

        Action(String id, int keycode, String keyName, int labelRes, int useRes)
        {
            this.id = id;
            this.keycode = keycode;
            this.keyName = keyName;
            this.labelRes = labelRes;
            this.useRes = useRes;
        }

        public boolean isKey() { return keycode != 0; }
    }

    public static final Action[] ACTIONS = {
        new Action("enter", KeyEvent.KEYCODE_ENTER, "Enter", R.string.btn_enter, R.string.ctl_use_a),
        new Action("esc", KeyEvent.KEYCODE_ESCAPE, "Esc", R.string.btn_esc, R.string.ctl_use_b),
        new Action("shift", KeyEvent.KEYCODE_SHIFT_LEFT, "Shift", R.string.btn_shift, R.string.ctl_use_x),
        new Action("a", KeyEvent.KEYCODE_A, "A", R.string.btn_a, R.string.ctl_use_y),
        new Action("q", KeyEvent.KEYCODE_Q, "Q", R.string.btn_q, R.string.btn_use_q),
        new Action("w", KeyEvent.KEYCODE_W, "W", R.string.btn_w, R.string.btn_use_w),
        new Action("s", KeyEvent.KEYCODE_S, "S", R.string.btn_s, R.string.ctl_use_l2),
        new Action("d", KeyEvent.KEYCODE_D, "D", R.string.btn_d, R.string.ctl_use_r2),
        new Action("ctrl", KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl", R.string.btn_ctrl, R.string.btn_use_ctrl),
        new Action("turbo", 0, null, R.string.btn_turbo, R.string.btn_turbo),
        new Action("turbo_hold", 0, null, R.string.btn_turbo_hold, R.string.btn_turbo_hold),
        new Action("autofire", 0, null, R.string.btn_autofire, R.string.btn_use_autofire),
        new Action("shot", 0, null, R.string.btn_shot, R.string.btn_shot),
        new Action("keyboard", 0, null, R.string.btn_keyboard, R.string.btn_use_keyboard),
        new Action("menu", 0, null, R.string.btn_menu, R.string.ctl_use_menu),
        new Action("none", 0, null, R.string.btn_none, R.string.btn_none),
    };

    private static final Map<Integer, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_A, "enter");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_B, "esc");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_X, "shift");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_Y, "a");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_L1, "q");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_R1, "w");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_L2, "s");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_R2, "d");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_THUMBL, "s");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_THUMBR, "d");
        DEFAULTS.put(KeyEvent.KEYCODE_BUTTON_START, "esc");
    }

    private static final Map<Integer, String> sMap = new LinkedHashMap<>(DEFAULTS);

    private ButtonMap() { }

    public static Action action(String id)
    {
        for (Action a : ACTIONS) if (a.id.equals(id)) return a;
        return null;
    }

    /** The action of a controller button, or null if it isn't one of the buttons. */
    public static synchronized Action forButton(int keycode)
    {
        String id = sMap.get(keycode);
        return id == null ? null : action(id);
    }

    public static synchronized void load(Context c)
    {
        sMap.clear();
        sMap.putAll(DEFAULTS);
        sKey = scope(c);
        try {
            android.content.SharedPreferences prefs = c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE);
            String json = prefs.getString(sKey, prefs.getString(KEY, null));
            if (json == null) return;
            JSONObject o = new JSONObject(json);
            for (int b : BUTTONS) {
                String id = o.optString(String.valueOf(b), null);
                if (id != null && action(id) != null) sMap.put(b, id);
            }
        } catch (Exception ignored) { }
    }

    public static synchronized void set(Context c, int button, String actionId)
    {
        sMap.put(button, actionId);
        save(c);
    }

    public static synchronized void reset(Context c)
    {
        c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE).edit().remove(scope(c)).commit();
        load(c);
    }

    /** Nintendo-style controllers: A/B and X/Y swapped. */
    public static synchronized void nintendoLayout(Context c)
    {
        sMap.clear();
        sMap.putAll(DEFAULTS);
        sMap.put(KeyEvent.KEYCODE_BUTTON_A, "esc");
        sMap.put(KeyEvent.KEYCODE_BUTTON_B, "enter");
        sMap.put(KeyEvent.KEYCODE_BUTTON_X, "a");
        sMap.put(KeyEvent.KEYCODE_BUTTON_Y, "shift");
        save(c);
    }

    private static void save(Context c)
    {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<Integer, String> e : sMap.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
            c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE).edit()
                .putString(scope(c), o.toString()).commit();
        } catch (Exception ignored) { }
    }
}
