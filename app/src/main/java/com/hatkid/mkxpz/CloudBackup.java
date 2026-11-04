package com.hatkid.mkxpz;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Save backups in Essentia's database (the owner's Google Drive): after playing,
 * changed saves are uploaded; any of the last 10 backups of a game can be
 * brought back (the current saves are kept aside first).
 */
public final class CloudBackup
{
    private static final String TAG = "Essentia[Backup]";
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private CloudBackup() { }

    /** Runs a backup in the background after a game session (from the game screen). */
    public static void afterSession(Context c, String path)
    {
        if (path == null || !EssentiaSettings.flag(c, EssentiaSettings.CLOUD_BACKUP, false)
            || !Cloud.backupsReady(c)) return;
        try {
            c.startService(new Intent(c, Worker.class).putExtra("path", path));
        } catch (Exception e) {
            Log.w(TAG, "start: " + e);
        }
    }

    private static String titleOf(Context c, String path)
    {
        Library.Entry e = Library.find(c, path);
        return e != null ? e.displayTitle() : Games.readTitle(new File(path));
    }

    private static List<File> saves(Context c, String path)
    {
        return Saves.findIn(Saves.dirFor(c, new File(path), titleOf(c, path)));
    }

    private static String fingerprint(List<File> files)
    {
        files = new ArrayList<>(files);
        java.util.Collections.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        StringBuilder sb = new StringBuilder();
        for (File f : files) sb.append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');
        return sb.toString();
    }

    /** Uploads the game's saves if they changed. Returns true if something was sent. */
    public static boolean backup(Context c, String path, boolean force) throws Exception
    {
        List<File> files = saves(c, path);
        java.util.Collections.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        if (files.isEmpty()) return false;
        SharedPreferences p = c.getSharedPreferences("essentia_backups", Context.MODE_PRIVATE);
        String fp = fingerprint(files);
        if (!force && fp.equals(p.getString(path, ""))) return false;
        JSONArray arr = new JSONArray();
        long total = 0;
        for (File f : files) {
            total += f.length();
            if (total > MAX_BYTES) throw new java.io.IOException("Las partidas superan 20 MB; no se subió una copia incompleta");
            JSONObject o = new JSONObject();
            o.put("name", f.getName());
            o.put("data", Base64.encodeToString(SaveTransaction.read(f), Base64.NO_WRAP));
            arr.put(o);
        }
        JSONObject body = new JSONObject();
        body.put("action", "backup_put");
        body.put("key", Cloud.backupKey(c));
        body.put("game", titleOf(c, path));
        body.put("files", arr);
        if (!fp.equals(fingerprint(saves(c, path))))
            throw new java.io.IOException("Las partidas cambiaron durante la copia. Cierra el juego y vuelve a intentarlo.");
        Cloud.post(c, body, 90000);
        p.edit().putString(path, fp).putLong(path + ":time", System.currentTimeMillis()).apply();
        return true;
    }

    public static long lastBackup(Context c, String path)
    {
        return c.getSharedPreferences("essentia_backups", Context.MODE_PRIVATE).getLong(path + ":time", 0);
    }

    /** The game's backups, newest first: [id, files summary]. */
    public static List<String[]> list(Context c, String path) throws Exception
    {
        return list(c, path, 45000);
    }

    public static List<String[]> list(Context c, String path, int timeout) throws Exception
    {
        JSONObject body = new JSONObject();
        body.put("action", "backup_list");
        body.put("key", Cloud.backupKey(c));
        body.put("game", titleOf(c, path));
        JSONArray arr = Cloud.post(c, body, timeout).getJSONArray("backups");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONArray files = o.getJSONArray("files");
            StringBuilder names = new StringBuilder();
            for (int j = 0; j < files.length(); j++) {
                if (j > 0) names.append(", ");
                names.append(files.getJSONObject(j).getString("name"));
            }
            out.add(new String[] { o.getString("id"), names.toString() });
        }
        return out;
    }

    /** Brings a backup back; the current saves are copied to Essentia/Respaldos first. */
    public static int restore(Context c, String path, String id) throws Exception
    {
        JSONObject body = new JSONObject();
        body.put("action", "backup_get");
        body.put("key", Cloud.backupKey(c));
        body.put("game", titleOf(c, path));
        body.put("id", id);
        JSONArray files = Cloud.post(c, body, 90000).getJSONArray("files");
        String title = titleOf(c, path);
        File target = Saves.dirFor(c, new File(path), title);
        java.util.Map<String, byte[]> payload = new java.util.LinkedHashMap<>();
        long total = 0;
        for (int i = 0; i < files.length(); i++) {
            JSONObject o = files.getJSONObject(i);
            String name = o.getString("name");
            SaveTransaction.child(target, name);
            if (!Saves.looksLikeSave(name) || payload.containsKey(name))
                throw new java.io.IOException("El respaldo contiene un archivo no válido");
            String encoded = o.getString("data");
            if (encoded.length() > MAX_BYTES * 4 / 3 + 8) throw new java.io.IOException("Respaldo demasiado grande");
            byte[] data = Base64.decode(encoded, Base64.DEFAULT);
            total += data.length;
            if (total > MAX_BYTES) throw new java.io.IOException("Respaldo demasiado grande");
            payload.put(name, data);
        }
        synchronized (UpdateService.class) {
            if (GameRemoval.busy() || UpdateService.hasWork() || Games.isGameRunning(c))
                throw new java.io.IOException("Cierra el juego y espera a que terminen las instalaciones");
            int count = SaveTransaction.restore(target, new File(Environment.getExternalStorageDirectory(),
                "Essentia/Respaldos/" + Saves.safeName(title)), payload);
            c.getSharedPreferences("essentia_backups", Context.MODE_PRIVATE).edit()
                .putLong(path + ":time", System.currentTimeMillis()).apply();
            return count;
        }
    }

    /** "20261103-174512" → readable date. */
    public static String readable(String id)
    {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(in.parse(id));
        } catch (Exception e) {
            return id;
        }
    }

    private static void copy(File from, File to) throws Exception
    {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) > 0) out.write(b, 0, r);
        }
    }

    private static byte[] readAll(File f) throws Exception
    {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) > 0) buf.write(b, 0, r);
            return buf.toByteArray();
        }
    }

    /** Background service (Essentia's main process) that does the upload. */
    public static final class Worker extends Service
    {
        @Override
        public int onStartCommand(Intent intent, int flags, int startId)
        {
            final String path = intent != null ? intent.getStringExtra("path") : null;
            new Thread(() -> {
                try {
                    if (path != null) backup(getApplicationContext(), path, false);
                } catch (Exception e) {
                    Log.w(TAG, "backup: " + e);
                }
                stopSelf(startId);
            }, "EssentiaBackup").start();
            return START_NOT_STICKY;
        }

        @Override public IBinder onBind(Intent intent) { return null; }
    }
}
