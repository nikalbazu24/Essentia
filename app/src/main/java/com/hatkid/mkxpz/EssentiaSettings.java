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

    /** Second screen while playing: touch panel, black, or not used. */
    public static final String GAME2_PANEL = "panel";
    public static final String GAME2_BLACK = "black";
    public static final String GAME2_OFF = "off";
    private static final String KEY_GAME2 = "game_second_screen";

    public static String gameSecondScreen(Context c) { return prefs(c).getString(KEY_GAME2, GAME2_PANEL); }
    public static void setGameSecondScreen(Context c, String v) { prefs(c).edit().putString(KEY_GAME2, v).commit(); }

    private static final String KEY_OSC_MODE = "osc_mode";
    private static final String KEY_OSC_OPACITY = "osc_opacity";
    private static final String KEY_OSC_SCALE = "osc_scale";
    private static final String KEY_DUAL = "dual_screen";
    private static final String KEY_SMOOTH = "smooth_scaling";
    private static final String KEY_VIEW = "library_view";
    private static final String KEY_SORT = "library_sort";
    private static final String KEY_SAVES = "save_location";

    public static final String VIEW_SHELF = "shelf";
    public static final String VIEW_GRID = "grid";
    public static final String VIEW_BIG = "big";
    public static final String VIEW_LIST = "list";

    public static final String SORT_RECENT = "recent";
    public static final String SORT_AZ = "az";

    /** Saves inside each game's folder (same place as JoiPlay). */
    public static final String SAVES_GAME = "game";
    /** Saves in /storage/emulated/0/Essentia/Partidas/<game>. */
    public static final String SAVES_ESSENTIA = "essentia";

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

    public static String viewMode(Context c) { return prefs(c).getString(KEY_VIEW, VIEW_SHELF); }
    public static void setViewMode(Context c, String v) { prefs(c).edit().putString(KEY_VIEW, v).commit(); }

    public static String sortMode(Context c) { return prefs(c).getString(KEY_SORT, SORT_RECENT); }
    public static void setSortMode(Context c, String v) { prefs(c).edit().putString(KEY_SORT, v).commit(); }

    public static String saveLocation(Context c) { return prefs(c).getString(KEY_SAVES, SAVES_GAME); }
    public static void setSaveLocation(Context c, String v) { prefs(c).edit().putString(KEY_SAVES, v).commit(); }

    /** Screen format: game shape + black bars / + blurred sides / stretched / wider game. */
    public static final String SCREEN_ORIGINAL = "original";
    public static final String SCREEN_AMBIENT = "ambient";
    public static final String SCREEN_STRETCH = "stretch";
    public static final String SCREEN_WIDE = "wide";
    /** Safe widescreen (default): wide map, menus and battles in their own shape with soft sides. */
    public static final String SCREEN_WIDE_SAFE = "wide_safe";
    private static final String KEY_SCREEN = "screen_mode";

    public static String screenMode(Context c)
    {
        SharedPreferences p = prefs(c);
        // the full widescreen was the default before: move to the safe one once
        if (!p.getBoolean("screen_mode_v2", false)) {
            SharedPreferences.Editor e = p.edit().putBoolean("screen_mode_v2", true);
            if (SCREEN_WIDE.equals(p.getString(KEY_SCREEN, SCREEN_WIDE))) e.putString(KEY_SCREEN, SCREEN_WIDE_SAFE);
            e.commit();
        }
        return p.getString(KEY_SCREEN, SCREEN_WIDE_SAFE);
    }

    public static boolean isWide(String mode) { return SCREEN_WIDE.equals(mode) || SCREEN_WIDE_SAFE.equals(mode); }

    /** Picture filter: sharp pixels, smooth, or old TV lines. */
    public static final String FILTER_SHARP = "sharp";
    public static final String FILTER_SMOOTH = "smooth";
    public static final String FILTER_CRT = "crt";
    private static final String KEY_FILTER = "picture_filter";
    public static String filter(Context c) { return prefs(c).getString(KEY_FILTER, smoothScaling(c) ? FILTER_SMOOTH : FILTER_SHARP); }
    public static void setFilter(Context c, String v)
    {
        prefs(c).edit().putString(KEY_FILTER, v).commit();
        setSmoothScaling(c, FILTER_SMOOTH.equals(v));
    }
    /** Messages appear at once (Pokémon Essentials text speed at its fastest). */
    public static final String FAST_TEXT = "fast_text";
    /** Skip the splash screens before the title. */
    public static final String SKIP_INTRO = "skip_intro";
    /** Back up saves to the database after playing. */
    public static final String CLOUD_BACKUP = "cloud_backup";
    public static void setScreenMode(Context c, String v) { prefs(c).edit().putString(KEY_SCREEN, v).commit(); }

    /** Game screen orientation: horizontal (default), vertical (game on top, controls below) or both. */
    public static final String ORIENT_LANDSCAPE = "landscape";
    public static final String ORIENT_PORTRAIT = "portrait";
    public static final String ORIENT_AUTO = "auto";
    private static final String KEY_ORIENT = "game_orientation";
    public static String orientation(Context c) { return prefs(c).getString(KEY_ORIENT, ORIENT_LANDSCAPE); }
    public static void setOrientation(Context c, String v) { prefs(c).edit().putString(KEY_ORIENT, v).commit(); }

    // Performance and diagnostics (booleans)
    public static final String PERF_FAST_LOAD = "perf_fast_load";
    public static final String PERF_LOAD_TIMES = "perf_load_times";
    public static final String PERF_SHOW_FPS = "perf_show_fps";
    public static final String PERF_FRAMESKIP = "perf_frameskip";
    public static final String PERF_VSYNC = "perf_vsync";
    public static final String PERF_INTEGER = "perf_integer_scale";
    public static final String PERF_SUSTAINED = "perf_sustained";
    public static final String PERF_60HZ = "perf_60hz";
    public static final String DEBUG_INPUT = "debug_input";
    /** Essentia's own music in the library (on by default). */
    public static final String MUSIC = "app_music";
    private static final String KEY_MUSIC_VOLUME = "app_music_volume";
    public static int musicVolume(Context c) { return prefs(c).getInt(KEY_MUSIC_VOLUME, 45); }
    public static void setMusicVolume(Context c, int v) { prefs(c).edit().putInt(KEY_MUSIC_VOLUME, v).apply(); }

    /** Offer new official versions of games (PokéUpdater) before opening them. */
    public static final String AUTO_UPDATE = "auto_game_updates";
    /** Move diagonally when two directions are held (off by default). */
    public static final String DIAGONAL = "diagonal_movement";
    /** Open the last played game when Essentia starts (off by default). */
    public static final String RESUME_LAST = "resume_last_game";
    /** Look for new games in the folders already added, when Essentia starts. */
    public static final String RESCAN = "rescan_folders";

    private static final String KEY_ROOTS = "game_roots";
    public static java.util.Set<String> gameRoots(Context c)
    {
        return new java.util.HashSet<>(prefs(c).getStringSet(KEY_ROOTS, new java.util.HashSet<>()));
    }
    public static void addGameRoot(Context c, String root)
    {
        java.util.Set<String> s = gameRoots(c);
        if (s.add(root)) prefs(c).edit().putStringSet(KEY_ROOTS, s).apply();
    }

    /** Short vibration when touching the on-screen controls. */
    public static final String HAPTICS = "touch_haptics";

    /** Turbo speed (2..4x), switched on from the panel or with Select + R1. */
    private static final String KEY_TURBO = "turbo_speed";
    public static int turboSpeed(Context c) { return prefs(c).getInt(KEY_TURBO, 3); }
    public static void setTurboSpeed(Context c, int v) { prefs(c).edit().putInt(KEY_TURBO, v).commit(); }

    /** Ruby engine: modern (Ruby 3.1) or classic (Ruby 1.8, for very old games). */
    public static final String ENGINE_MODERN = "modern";
    public static final String ENGINE_CLASSIC = "classic";
    /** Automatic (default): modern; if the game turns out to need Ruby 1.8, classic from then on. */
    public static final String ENGINE_AUTO = "";

    private static SharedPreferences learned(Context c)
    {
        return c.getSharedPreferences("essentia_engine_auto", Context.MODE_PRIVATE);
    }

    /** The automatic engine found that this game needs the classic engine. */
    /** (Remembered per version of Essentia: after an update the modern engine is tried again.) */
    public static boolean learnedClassic(Context c, String path)
    {
        try {
            return learned(c).getInt(path, 0) == BuildConfig.VERSION_CODE;
        } catch (ClassCastException e) {
            return false;
        }
    }

    public static void setLearnedClassic(Context c, String path, boolean v)
    {
        if (v) learned(c).edit().putInt(path, BuildConfig.VERSION_CODE).commit();
        else learned(c).edit().remove(path).commit();
    }

    /** Folder with the RPG Maker RTP (Essentia/RTP/XP, VX, VXAce). */
    public static java.io.File rtpRoot()
    {
        return new java.io.File(android.os.Environment.getExternalStorageDirectory(), "Essentia/RTP");
    }

    public static boolean flag(Context c, String key, boolean def) { return prefs(c).getBoolean(key, def); }
    public static void setFlag(Context c, String key, boolean v) { prefs(c).edit().putBoolean(key, v).commit(); }

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
