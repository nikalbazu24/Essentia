package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hatkid.mkxpz.ui.Ui;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/**
 * "Send report": after a game stops with an error (or from the game's options),
 * sends the error, the game's details and, if wanted, its scripts to Essentia's
 * database, where they can be read to fix the problem.
 */
public class ReportActivity extends com.hatkid.mkxpz.ui.ControllerActivity
{
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_ERROR = "error";

    /** Opens the report screen for a game (error may be null: manual report). */
    public static void open(Context c, String path, String error)
    {
        Intent i = new Intent(c, ReportActivity.class);
        i.putExtra(EXTRA_PATH, path);
        i.putExtra(EXTRA_ERROR, error);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        String error = getIntent().getStringExtra(EXTRA_ERROR);
        if (path == null) { finish(); return; }
        Diagnostics.error(this,path,error);
        Library.Entry e = Library.find(this, path);
        String title = e != null ? e.displayTitle() : Games.readTitle(new File(path));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p,p,p,p);box.setBackgroundColor(Ui.BG);
        android.widget.Button back=Ui.button(this,"‹ Volver",false);back.setOnClickListener(v->finish());box.addView(back);
        box.addView(Ui.text(this,"Reportar un problema",26,Ui.TEXT,true));
        box.addView(Ui.text(this,title,18,Ui.ACCENT_LIGHT,true));

        TextView info = Ui.text(this, getString(error != null ? R.string.rep_crash_text : R.string.rep_manual_text),
            14, Ui.TEXT_DIM, false);
        box.addView(info);
        if (error != null) {
            String first = error.split("\n\n")[0];
            TextView err = Ui.text(this, first.length() > 400 ? first.substring(0, 400) + "…" : first, 13, Ui.TEXT, false);
            err.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
            box.addView(err);
        }
        EditText note = new EditText(this);
        note.setHint(R.string.rep_note_hint);
        note.setMinLines(4);
        note.setTextColor(Ui.TEXT);note.setHintTextColor(Ui.TEXT_DIM);
        box.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        CheckBox scripts = new CheckBox(this);
        scripts.setText(R.string.rep_include_scripts);
        scripts.setChecked(false);
        scripts.setTextColor(Ui.TEXT);
        box.addView(scripts);

        boolean cloud = Cloud.configured(this);
        box.addView(Ui.text(this,"El informe se envía solo al tocar Enviar o al confirmar con qué app compartirlo. Adjuntar scripts es opcional.",14,Ui.TEXT_DIM,false));
        if(Diagnostics.enabled(this)) {
            TextView diagnostic=Ui.text(this,Diagnostics.summary(this,path),13,Ui.TEXT_DIM,false);
            diagnostic.setPadding(p,p,p,p);diagnostic.setBackground(Ui.rounded(this,Ui.SURFACE,14));box.addView(diagnostic);
        }
        android.widget.Button submit=Ui.button(this,getString(cloud?R.string.rep_send:R.string.rep_share),true);
        submit.setOnClickListener(v->{submit.setEnabled(false);send(path,title,error,note.getText().toString(),scripts.isChecked(),cloud);});
        box.addView(submit);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.addView(box);setContentView(scroll);
    }

    private void send(String path, String title, String error, String note, boolean withScripts, boolean cloud)
    {
        Context app = getApplicationContext();
        Toast.makeText(app, R.string.rep_sending, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = false;
            String id = "";
            File scriptsFile = null;
            try {
                File dir = new File(path);
                if (withScripts) {
                    scriptsFile = new File(ShareProvider.folder(app), Saves.safeName(title) + " - Scripts.rxdata");
                    if (!GameScripts.extract(dir, scriptsFile)) scriptsFile = null;
                }
                GameProfile prof = GameProfile.of(app, dir);
                Library.Entry e = Library.find(app, path);
                JSONObject r = new JSONObject();
                r.put("action", "report");
                r.put("essentia", Cloud.essentiaVersion(app));
                r.put("device", Cloud.device());
                r.put("android", android.os.Build.VERSION.RELEASE);
                r.put("game", title);
                String v = GameUpdater.localVersion(dir);
                r.put("gameVersion", v == null ? "" : v);
                r.put("kind", prof.label());
                r.put("engine", e != null && !e.engine.isEmpty() ? e.engine
                    : (EssentiaSettings.learnedClassic(app, path) ? "auto→classic" : "auto"));
                r.put("screen", e != null && !e.screenMode.isEmpty() ? e.screenMode : EssentiaSettings.screenMode(app));
                String summary = error == null ? "(reporte manual)" : error.split("\n\n")[0];
                r.put("error", (note.trim().isEmpty() ? "" : "Nota: " + note.trim() + "\n") + summary);
                r.put("detail", (error == null ? "" : error) + (Diagnostics.enabled(app) ? "\n" + Diagnostics.summary(app,path) : ""));
                if (cloud) {
                    if (scriptsFile != null && scriptsFile.length() < 12L * 1024 * 1024) {
                        r.put("scripts", Base64.encodeToString(readAll(scriptsFile), Base64.NO_WRAP));
                    }
                    id = Cloud.post(app, r, 60000).optString("id", "");
                    ok = true;
                } else {
                    // no database: the report goes through the share menu
                    File txt = new File(ShareProvider.folder(app), Saves.safeName(title) + " - reporte.txt");
                    try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(txt), "UTF-8")) {
                        w.write(r.toString(2));
                    }
                    final File share = txt;
                    runOnUiThread(() -> ShareProvider.share(this, share, getString(R.string.rep_share)));
                    ok = true;
                }
            } catch (Exception ex) {
                ok = false;
            }
            final boolean sent = ok;
            final String reportId = id;
            runOnUiThread(() -> {
                if (cloud) {
                    Toast.makeText(app, sent ? getString(R.string.rep_sent, reportId) : getString(R.string.rep_failed),
                        Toast.LENGTH_LONG).show();
                }
                finish();
            });
        }, "EssentiaReport").start();
    }

    private static byte[] readAll(File f) throws Exception
    {
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) > 0) out.write(b, 0, r);
            return out.toByteArray();
        }
    }
}
