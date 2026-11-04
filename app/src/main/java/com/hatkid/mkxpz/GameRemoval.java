package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hatkid.mkxpz.ui.Ui;
import com.hatkid.mkxpz.ui.UpdateFlow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Explicit two-step, local-only game deletion. No files are touched by either confirmation. */
public final class GameRemoval
{
    // Use the update service's monitor so starting an update and reserving deletion are atomic.
    private static boolean sDeleting;

    private GameRemoval() { }

    public static boolean busy()
    {
        synchronized (UpdateService.class) { return sDeleting; }
    }

    public static void show(Activity activity, Library.Entry entry, Runnable changed)
    {
        if (busy()) { Toast.makeText(activity, R.string.delete_busy, Toast.LENGTH_LONG).show(); return; }
        File externalSaves = separateSaves(entry);
        boolean canDeleteSaves = externalSaves.isDirectory() && ownsSeparateSaves(activity, entry, externalSaves);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20);
        box.setPadding(pad, Ui.dp(activity, 10), pad, 0);
        TextView text = Ui.text(activity, activity.getString(R.string.delete_first_message,
            entry.displayTitle(), entry.path), 15, Ui.TEXT, false);
        text.setTextIsSelectable(true);
        box.addView(text);
        CheckBox saves = new CheckBox(activity);
        saves.setText(activity.getString(R.string.delete_separate_saves, externalSaves.getAbsolutePath()));
        saves.setTextColor(Ui.TEXT);
        saves.setChecked(false);
        if (canDeleteSaves) box.addView(saves);
        else if (externalSaves.isDirectory()) {
            box.addView(Ui.text(activity, activity.getString(R.string.delete_shared_saves), 13, Ui.TEXT_DIM, false));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);
        AlertDialog first = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.delete_first_title)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_continue, (dialog, which) ->
                confirmFinal(activity, entry, canDeleteSaves && saves.isChecked() ? externalSaves : null, changed))
            .create();
        safeDialog(first);
    }

    private static void confirmFinal(Activity activity, Library.Entry entry, File saves, Runnable changed)
    {
        String paths = entry.path + (saves == null ? "" : "\n" + saves.getAbsolutePath());
        AlertDialog last = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.delete_final_title)
            .setMessage(activity.getString(R.string.delete_final_message, entry.displayTitle(), paths))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_permanently, (dialog, which) -> remove(activity, entry, saves, changed))
            .create();
        safeDialog(last);
        // Repeated touch/controller presses must not immediately accept the second dialog.
        last.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (last.isShowing()) last.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        }, 1500);
    }

    private static void safeDialog(AlertDialog dialog)
    {
        UpdateFlow.controllerFriendly(dialog);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setFocusableInTouchMode(true);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    private static File separateSaves(Library.Entry entry)
    {
        return new File(Environment.getExternalStorageDirectory(), "Essentia/Partidas/" + Saves.safeName(entry.displayTitle()));
    }

    private static boolean ownsSeparateSaves(Context c, Library.Entry entry, File saves)
    {
        try {
            if (GameFiles.linked(saves) || GameFiles.contains(new File(entry.path), saves)
                || GameFiles.contains(saves, new File(entry.path))) return false;
            for (Library.Entry other : Library.load(c)) {
                if (other.path.equals(entry.path)) continue;
                if (Saves.safeName(other.displayTitle()).equalsIgnoreCase(saves.getName())
                    || Saves.safeName(Games.readTitle(new File(other.path))).equalsIgnoreCase(saves.getName())
                    || GameFiles.contains(saves, new File(other.path))
                    || GameFiles.contains(new File(other.path), saves)) return false;
            }
            return true;
        } catch (Exception ex) { return false; }
    }

    private static boolean engineRunning(Context c)
    {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return true;
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return true;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if ((c.getPackageName() + ":game").equals(process.processName)) return true;
        }
        return false;
    }

    private static List<File> volumes(Context c)
    {
        List<File> out = new ArrayList<>();
        out.add(Environment.getExternalStorageDirectory());
        for (File files : c.getExternalFilesDirs(null)) {
            if (files == null) continue;
            File android = files.getParentFile();
            for (int i = 0; i < 2 && android != null; i++) android = android.getParentFile();
            if (android != null && android.getName().equals("Android") && android.getParentFile() != null)
                out.add(android.getParentFile());
        }
        return out;
    }

    private static List<File> protectedFolders(Context c, List<File> volumes)
    {
        List<File> out = new ArrayList<>();
        String[] names = { "Android", "Download", "Downloads", "Documents", "DCIM", "Pictures", "Movies",
            "Music", "Podcasts", "Alarms", "Ringtones", "Notifications", "Essentia", "Essentia/Juegos",
            "Essentia/Partidas", "Essentia/Traducciones", "Essentia/Accesos" };
        for (File volume : volumes) for (String name : names) out.add(new File(volume, name));
        for (String path : EssentiaSettings.gameRoots(c)) {
            File folder = new File(path);
            if (!Games.isGameFolder(folder)) out.add(folder);
        }
        return out;
    }

    private static void remove(Activity activity, Library.Entry entry, File saves, Runnable changed)
    {
        Context app = activity.getApplicationContext();
        synchronized (UpdateService.class) {
            if (sDeleting || UpdateService.hasWork() || engineRunning(app)) {
                Toast.makeText(activity, R.string.delete_busy, Toast.LENGTH_LONG).show();
                return;
            }
            sDeleting = true;
        }
        AlertDialog progress = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(entry.displayTitle()).setMessage(R.string.delete_working).setCancelable(false).create();
        progress.show();
        new Thread(() -> {
            boolean ok = false;
            try {
                Library.Entry current = Library.find(app, entry.path);
                if (current == null || !current.displayTitle().equals(entry.displayTitle())) throw new IOException("Entry changed");
                List<File> volumes = volumes(app), protectedFolders = protectedFolders(app, volumes), others = new ArrayList<>();
                for (Library.Entry other : Library.load(app)) if (!other.path.equals(entry.path)) others.add(new File(other.path));
                for (File volume : volumes) {
                    if (GameFiles.contains(new File(volume, "Android"), new File(entry.path)))
                        throw new IOException("Android application storage is protected");
                }
                File game = GameFiles.validate(new File(entry.path), volumes, protectedFolders, others, true);
                File savesRoot = null;
                if (saves != null) {
                    if (!ownsSeparateSaves(app, current, saves) || !saves.equals(separateSaves(current)))
                        throw new IOException("Save ownership changed");
                    savesRoot = GameFiles.validate(saves, volumes, protectedFolders, others, false);
                }
                // Every selected directory must pass preflight before deleting the first file.
                GameFiles.delete(game);
                if (savesRoot != null) GameFiles.delete(savesRoot);
                Catalog.clearUpdate(app, entry.path);
                Library.remove(app, entry.path);
                if (Library.find(app, entry.path) != null) throw new IOException("Could not update library");
                ok = true;
            } catch (Exception ex) {
                android.util.Log.w("Essentia[Delete]", "Game deletion stopped", ex);
            } finally {
                synchronized (UpdateService.class) { sDeleting = false; }
            }
            final boolean removed = ok;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (progress.isShowing()) progress.dismiss();
                changed.run();
                if (removed) Toast.makeText(activity, R.string.delete_done, Toast.LENGTH_LONG).show();
                else new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.delete_failed_title).setMessage(R.string.delete_failed)
                    .setPositiveButton(android.R.string.ok, null).show();
            });
        }, "EssentiaDeleteGame").start();
    }
}
