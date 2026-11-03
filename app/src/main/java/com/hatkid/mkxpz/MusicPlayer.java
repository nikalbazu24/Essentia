package com.hatkid.mkxpz;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * Essentia's own music in the library and settings (an original chiptune loop,
 * made with tools/compose_theme.py). It stops while a game runs, when the app
 * goes to the background or when other audio starts, and can be turned off.
 */
public final class MusicPlayer
{
    private static MediaPlayer sPlayer;
    private static int sVisible = 0;
    private static float sLevel = 0f;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static AudioFocusRequest sFocus;
    private static boolean sHasFocus = false;

    private MusicPlayer() { }

    public static boolean enabled(Context c) { return EssentiaSettings.flag(c, EssentiaSettings.MUSIC, true); }

    /** 0..100 */
    public static int volume(Context c) { return EssentiaSettings.musicVolume(c); }

    /** An Essentia screen became visible. */
    public static void resume(Context c)
    {
        sVisible++;
        sHandler.removeCallbacks(sStop);
        if (enabled(c)) start(c.getApplicationContext());
    }

    /** An Essentia screen was left. Music stops unless another one opens right away. */
    public static void pause()
    {
        sVisible = Math.max(0, sVisible - 1);
        if (sVisible == 0) {
            sHandler.removeCallbacks(sStop);
            sHandler.postDelayed(sStop, 400);
        }
    }

    /** Stops immediately (a game is about to start). */
    public static void stopNow()
    {
        sHandler.removeCallbacks(sStop);
        release();
    }

    /** Settings changed (on/off, volume). */
    public static void refresh(Context c)
    {
        if (!enabled(c)) { release(); return; }
        if (sVisible > 0) start(c.getApplicationContext());
        if (sPlayer != null) fadeTo(volume(c) / 100f);
    }

    private static final Runnable sStop = () -> fadeOutAndRelease();

    private static void start(Context c)
    {
        if (sPlayer != null) {
            if (!sPlayer.isPlaying()) sPlayer.start();
            fadeTo(volume(c) / 100f);
            return;
        }
        if (!requestFocus(c)) return;
        try {
            sPlayer = MediaPlayer.create(c, R.raw.essentia_theme);
            if (sPlayer == null) return;
            sPlayer.setLooping(true);
            sLevel = 0f;
            sPlayer.setVolume(0f, 0f);
            sPlayer.start();
            fadeTo(volume(c) / 100f);
        } catch (Exception e) {
            release();
        }
    }

    private static boolean requestFocus(Context c)
    {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return true;
        AudioManager.OnAudioFocusChangeListener l = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                sHasFocus = false;
                release();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (sPlayer != null) sPlayer.setVolume(sLevel * 0.3f, sLevel * 0.3f);
            }
        };
        int r;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(l)
                .build();
            r = am.requestAudioFocus(sFocus);
        } else {
            r = am.requestAudioFocus(l, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        sHasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return sHasFocus;
    }

    // smooth volume changes
    private static float sTarget = 0f;
    private static Runnable sAfterFade;
    private static final Runnable sFadeStep = new Runnable() {
        @Override public void run() {
            if (sPlayer == null) return;
            float step = 0.05f;
            if (Math.abs(sTarget - sLevel) <= step) sLevel = sTarget;
            else sLevel += sTarget > sLevel ? step : -step;
            try { sPlayer.setVolume(sLevel, sLevel); } catch (Exception ignored) { }
            if (sLevel != sTarget) {
                sHandler.postDelayed(this, 40);
            } else if (sAfterFade != null) {
                Runnable r = sAfterFade;
                sAfterFade = null;
                r.run();
            }
        }
    };

    private static void fadeTo(float target)
    {
        sTarget = target;
        sAfterFade = null;
        sHandler.removeCallbacks(sFadeStep);
        sHandler.post(sFadeStep);
    }

    private static void fadeOutAndRelease()
    {
        if (sPlayer == null) return;
        fadeTo(0f);
        sAfterFade = MusicPlayer::release;
    }

    private static void release()
    {
        sHandler.removeCallbacks(sFadeStep);
        sAfterFade = null;
        if (sPlayer != null) {
            try { sPlayer.stop(); } catch (Exception ignored) { }
            sPlayer.release();
            sPlayer = null;
        }
        sLevel = 0f;
    }
}
