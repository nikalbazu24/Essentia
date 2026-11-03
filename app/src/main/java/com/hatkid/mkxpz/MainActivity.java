package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.storage.StorageManager;
import android.os.storage.OnObbStateChangeListener;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.util.DisplayMetrics;
import java.util.Locale;
import java.io.File;

import org.libsdl.app.SDLActivity;
import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadConfig;

public class MainActivity extends SDLActivity
{
    // This activity inherits from SDLActivity activity.
    // Put your Java-side stuff here.

    private static final String TAG = "mkxp-z[Activity]";

    /** Intent extra with the absolute path of the game folder (set by LauncherActivity). */
    public static final String EXTRA_GAME_PATH = "essentia.GAME_PATH";
    /** Folder for this game's saves (System.data_directory). */
    public static final String EXTRA_DATA_DIR = "essentia.DATA_DIR";
    /** Game title, for the loading screen. */
    public static final String EXTRA_TITLE = "essentia.TITLE";
    /** "true" for the experimental 16:9 mode. */
    public static final String EXTRA_WIDESCREEN = "essentia.WIDESCREEN";
    private static final String GAME_PATH_DEFAULT = Environment.getExternalStorageDirectory() + "/mkxp-z";
    private static String GAME_PATH = GAME_PATH_DEFAULT;
    private static String OBB_MAIN_FILENAME;
    private static boolean DEBUG = false;

    protected boolean mStarted = false;

    protected static Handler mMainHandler;
    protected static StorageManager mStorageManager;
    protected static Vibrator mVibrator;

    protected static TextView tvFps;

    // In-screen gamepad
    private final Gamepad mGamepad = new Gamepad();
    private boolean mGamepadInvisible = false;
    private String mOscMode = EssentiaSettings.OSC_AUTO;

    private void runSDLThread()
    {
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
        }

        mStarted = true;

        // Run (resume) native SDL thread
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    OnObbStateChangeListener obbListener = new OnObbStateChangeListener()
    {
        @Override
        public void onObbStateChange(String path, int state)
        {
            super.onObbStateChange(path, state);

            Log.v(TAG, "OBB state of " + path + " changed to " + state);

            switch (state)
            {
                case OnObbStateChangeListener.MOUNTED:
                    String obbPath = mStorageManager.getMountedObbPath(path);
                    Log.v(TAG, "OBB " + path + " is mounted to " + obbPath);
                    GAME_PATH = obbPath;
                    break;

                case OnObbStateChangeListener.UNMOUNTED:
                    Log.v(TAG, "OBB " + path + " is unmounted");
                    GAME_PATH = GAME_PATH_DEFAULT;
                    break;

                default:
                    Log.e(TAG, "Failed to mount OBB " + path + ": Got state " + state);
                    break;
            }

            runSDLThread();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == 110) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                // Close the App because the User did not allow the all files access permission to be used.
                mSingleton.finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Essentia: the game folder comes from LauncherActivity. It must be set before
        // SDL starts the native thread, which reads GAME_PATH through JNI.
        String requestedPath = getIntent() != null ? getIntent().getStringExtra(EXTRA_GAME_PATH) : null;
        if (requestedPath != null && !requestedPath.isEmpty()) {
            GAME_PATH = requestedPath;
        }

        // Essentia: settings read by the native engine through the environment
        try {
            Intent in = getIntent();
            String dataDir = in != null ? in.getStringExtra(EXTRA_DATA_DIR) : null;
            boolean wide = in != null && in.getBooleanExtra(EXTRA_WIDESCREEN, false);
            android.system.Os.setenv("ESSENTIA_SMOOTH_SCALING",
                EssentiaSettings.smoothScaling(this) ? "1" : "0", true);
            android.system.Os.setenv("ESSENTIA_DATA_DIR", dataDir != null ? dataDir : "", true);
            android.system.Os.setenv("ESSENTIA_WIDESCREEN", wide ? "1" : "0", true);
            // The phone's motion sensor must not count as "controller 0", or the engine
            // ignores the real controller (and its sticks).
            android.system.Os.setenv("SDL_ACCELEROMETER_AS_JOYSTICK", "0", true);
        } catch (Exception e) {
            Log.w(TAG, "Could not pass settings to the engine: " + e);
        }

        super.onCreate(savedInstanceState);

        mMainHandler = new Handler(getMainLooper());

        mStorageManager = (StorageManager) getSystemService(STORAGE_SERVICE);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Get main OBB filepath
        final String obbPrefix = "main"; // "main", "patch"
        final int obbVersion = 1;
        OBB_MAIN_FILENAME = getObbDir() + "/" + obbPrefix + "." + obbVersion + "." + getPackageName() + ".obb";

        // Get Debug flag
        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            DEBUG = actInfo.metaData.getBoolean("mkxp_debug");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to set debug flag: " + e);
            e.printStackTrace();
        }

        // Check for all files access permission (Android 11+).
        // Essentia: LauncherActivity asks for it; if it is missing, go back there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(this, LauncherActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return;
            }
        }

        // Setup in-screen gamepad (Essentia: mode, size and opacity from settings)
        mOscMode = EssentiaSettings.oscMode(this);
        if (EssentiaSettings.OSC_NEVER.equals(mOscMode)) {
            mGamepadInvisible = true;
        } else if (EssentiaSettings.OSC_ALWAYS.equals(mOscMode)) {
            mGamepadInvisible = false;
        } else {
            mGamepadInvisible = isAndroidTV() || isChromebook()
                || EssentiaSettings.hasPhysicalController(this);
        }

        GamepadConfig gpadConfig = new GamepadConfig();
        gpadConfig.opacity = EssentiaSettings.oscOpacity(this);
        gpadConfig.scale = EssentiaSettings.oscScale(this);
        mGamepad.init(gpadConfig, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);

        if (mLayout != null && !EssentiaSettings.OSC_NEVER.equals(mOscMode)) {
            mGamepad.attachTo(this, mLayout);
        }

        // Setup FPS textview
        tvFps = new TextView(this);
        tvFps.setTextSize((8 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT)));
        tvFps.setTextColor(Color.argb(96, 255, 255, 255));
        tvFps.setVisibility(View.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 16, 0, 0);
        tvFps.setLayoutParams(params);

        mLayout.addView(tvFps);

        // Essentia: loading screen until the game draws its first frame
        String title = getIntent() != null ? getIntent().getStringExtra(EXTRA_TITLE) : null;
        mLoading = new com.hatkid.mkxpz.ui.LoadingScreen(this, title != null ? title : "", GAME_PATH);
        mLayout.addView(mLoading, new android.widget.RelativeLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        if (!mStarted) {
            // Check for main OBB file
            if (new File(OBB_MAIN_FILENAME).exists()) {
                Log.v(TAG, "Main OBB file found, starting with main OBB mount");

                // Try to mount main OBB file
                mStorageManager.mountObb(OBB_MAIN_FILENAME, null, obbListener);
            } else {
                Log.v(TAG, "Main OBB file not found, starting without main OBB mount");

                // Run from default game directory
                runSDLThread();
            }
        } else {
            // onStart: Resume SDL thread
            runSDLThread();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // HACK: Exiting the JVM (process) since Ruby does not likes when we
        // trying to re-initialize Ruby VM in mkxp-z (JNI native library)
        // that leads to segmentation fault, even we have cleanup the Ruby VM.
        System.exit(0);
    }

    // Essentia: exit menu (Android Back button, or Select + Start)
    private boolean mSelectHeld = false;
    private boolean mStartSwallowed = false;
    private AlertDialog mExitDialog = null;

    private void showExitDialog()
    {
        if (mExitDialog != null && mExitDialog.isShowing())
            return;

        String[] items = {
            getString(R.string.menu_resume),
            getString(R.string.menu_controls),
            getString(R.string.menu_exit)
        };
        mExitDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.menu_title)
            .setItems(items, (d, which) -> {
                if (which == 1) {
                    com.hatkid.mkxpz.ui.ControlsGuide.showDialog(this);
                } else if (which == 2) {
                    confirmExit();
                }
            })
            .create();
        mExitDialog.show();
    }

    private void confirmExit()
    {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.exit_confirm, (d, w) -> finish())
            .setNegativeButton(R.string.exit_cancel, (d, w) -> d.dismiss())
            .show();
    }

    // Essentia: loading screen
    private static com.hatkid.mkxpz.ui.LoadingScreen mLoading;

    /** Called by the engine (graphics.cpp) when the game loop starts. */
    @SuppressWarnings("unused")
    private static void onGameReady()
    {
        if (mMainHandler != null) {
            mMainHandler.post(() -> {
                if (mLoading != null) mLoading.finish();
            });
        }
    }

    @Override
    public void onBackPressed()
    {
        showExitDialog();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        int code = evt.getKeyCode();
        boolean down = evt.getAction() == KeyEvent.ACTION_DOWN;

        // Android Back (navigation bar, Thor's back key...) -> exit menu
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down && !evt.isCanceled())
                showExitDialog();
            return true;
        }

        // Select + Start -> exit menu (Start is not sent to the game in that case)
        if (code == KeyEvent.KEYCODE_BUTTON_SELECT) {
            mSelectHeld = down;
        } else if (code == KeyEvent.KEYCODE_BUTTON_START) {
            if (down && mSelectHeld) {
                mStartSwallowed = true;
                showExitDialog();
                return true;
            }
            if (!down && mStartSwallowed) {
                mStartSwallowed = false;
                return true;
            }
        }

        if (
            code != KeyEvent.KEYCODE_VOLUME_UP &&
            code != KeyEvent.KEYCODE_VOLUME_DOWN &&
            code != KeyEvent.KEYCODE_VOLUME_MUTE &&
            code != KeyEvent.KEYCODE_HEADSETHOOK
        ) {
            // Hide gamepad view on key events when visible (only in "auto" mode)
            if (!mGamepadInvisible && EssentiaSettings.OSC_AUTO.equals(mOscMode)) {
                mGamepad.hideView();
                mGamepadInvisible = true;
            }
        }

        if (mGamepad.processGamepadEvent(evt))
            return true;

        return super.dispatchKeyEvent(evt);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt)
    {
        // Show gamepad view on touch when hidden (not in "never" mode)
        if (mGamepadInvisible && !EssentiaSettings.OSC_NEVER.equals(mOscMode)) {
            mGamepad.showView();
            mGamepadInvisible = false;
        }

        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent evt)
    {
        // Essentia: the d-pad hat and the left stick become arrow keys here, and the
        // event also continues to SDL, so the engine's own controller support (left
        // stick = arrows) works too. Both paths press the same arrow, so nothing doubles.
        mGamepad.processDPadEvent(evt);
        mGamepad.processStickEvent(evt);
        return super.dispatchGenericMotionEvent(evt);
    }

    /**
     * This method is for arguments for launching native mkxp-z.
     * 
     * @return arguments for the mkxp-z
     */
    @Override
    protected String[] getArguments()
    {
        String[] args;

        if (DEBUG) {
            // Arguments in Debug mode
            args = new String[] { "debug" };
        } else {
            // Arguments in normal mode
            args = new String[] {};
        }

        return args;
    }

    /**
     * This static method is used in native mkxp-z. (see eventthread.cpp)
     * This method updates text with given FPS count to FPS TextView in Activity.
     */
    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unused")
    private static void updateFPSText(int num)
    {
        mMainHandler.post(() -> tvFps.setText(num + " FPS"));
    }

    /**
     * This static method is used in native mkxp-z. (see eventthread.cpp)
     * This method sets the visibility of FPS TextView in Activity.
     */
    @SuppressWarnings("unused")
    private static void setFPSVisibility(boolean visible)
    {
        mMainHandler.post(() -> {
            if (visible)
                tvFps.setVisibility(View.VISIBLE);
            else
                tvFps.setVisibility(View.INVISIBLE);
        });
    }

    /**
     * This static method is used in native mkxp-z. (see systemImpl.cpp)
     * This method returns a string of current device locale tag. (e.g. "en_US")
     * 
     * @return string of locale tag
     */
    @SuppressWarnings("unused")
    private static String getSystemLanguage()
    {
        return Locale.getDefault().toString();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method returns a boolean indicating that the device has a vibrator or not.
     * 
     * @return boolean
     */
    @SuppressWarnings("unused")
    private static boolean hasVibrator()
    {
        return mVibrator.hasVibrator();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method makes device vibrating with given milliseconds duration.
     * 
     * @param duration milliseconds duration of vibration
     */
    @SuppressWarnings("unused")
    private static void vibrate(int duration)
    {
        if (duration >= 10000) {
            duration = 10000;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            mVibrator.vibrate(duration);
        }
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method turns off the current device vibration.
     */
    @SuppressWarnings("unused")
    private static void vibrateStop()
    {
        mVibrator.cancel();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method returns a boolean indicating the app is in multi window mode or not.
     * (Multi-window mode supports from Android 7.0 Nougat (API 24) and higher.)
     * 
     * @param activity current MainActivity instance
     * @return boolean
     */
    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}