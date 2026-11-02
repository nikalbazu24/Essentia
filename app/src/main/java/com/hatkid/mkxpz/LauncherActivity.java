package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.File;

/**
 * Entry point for frontends (Díptico, ES-DE, Daijishō...):
 *   io.github.nikalbazu24.essentia/com.hatkid.mkxpz.LauncherActivity
 *
 * The game comes as a file path or URI in the intent data, or as a string extra such
 * as "path", "game" or "rom". It may point to the game folder or any file inside it
 * (Game.exe, Game.ini...). Without a game, it opens the library.
 */
public class LauncherActivity extends Activity
{
    private static final String TAG = "Essentia[Launcher]";
    private static final int REQ_ALL_FILES = 1;

    /** Extra keys accepted from frontends (checked in this order). */
    private static final String[] PATH_EXTRAS = {
        "path", "game", "rom", "ROM", "file", "folder", "gamePath", "game_path",
        "filePath", "file_path", "romPath", "rom_path"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) continueLaunch();
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        setIntent(intent);
        continueLaunch();
    }

    private void continueLaunch()
    {
        if (!Games.hasAllFilesAccess()) {
            Games.askAllFilesAccess(this, REQ_ALL_FILES, this::finish);
            return;
        }

        String requested = pathFromIntent(getIntent());
        if (requested == null) {
            startActivity(new Intent(this, LibraryActivity.class));
            finish();
            return;
        }

        Log.i(TAG, "Path from caller: " + requested);
        File dir = Games.resolveGameFolder(requested);
        if (dir == null) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.err_not_a_game, requested))
                .setCancelable(false)
                .setPositiveButton(R.string.close, (d, w) -> finish())
                .show();
            return;
        }

        Games.startGame(this, dir, true);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ALL_FILES) {
            if (Games.hasAllFilesAccess()) continueLaunch();
            else finish();
        }
    }

    private String pathFromIntent(Intent intent)
    {
        if (intent == null) return null;

        if (intent.getExtras() != null) {
            for (String key : PATH_EXTRAS) {
                Object value = intent.getExtras().get(key);
                String p = null;
                if (value instanceof String) p = Games.pathFromString(this, (String) value);
                else if (value instanceof Uri) p = Games.pathFromUri(this, (Uri) value);
                if (p != null) return p;
            }
        }

        Uri data = intent.getData();
        return data != null ? Games.pathFromUri(this, data) : null;
    }
}
