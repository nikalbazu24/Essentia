package com.hatkid.mkxpz;

import android.app.*;
import android.content.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;

/** Only the moderated public feed is read. Private error reports never enter this cache. */
public final class Community {
    private Community(){}
    public static void refresh(Context c){
        try{
            long last=c.getSharedPreferences("community",0).getLong("time",0);
            if(System.currentTimeMillis()-last<60*60*1000)return;
            String base=Cloud.url(c);URL url=new URL(base+(base.contains("?")?"&":"?")+"action=compatibility");
            HttpURLConnection con=(HttpURLConnection)url.openConnection();con.setConnectTimeout(5000);con.setReadTimeout(5000);
            JSONObject result;
            try(InputStream in=con.getInputStream()){result=new JSONObject(new String(SaveTransaction.read(in,512*1024),"UTF-8"));}
            finally{con.disconnect();}
            if(!result.optBoolean("ok")||!result.has("compatibility"))return;
            c.getSharedPreferences("community",0).edit().putString("feed",result.getJSONArray("compatibility").toString())
                .putLong("time",System.currentTimeMillis()).apply();
        }catch(Exception ignored){}
    }
    public static String summary(Context c,String id){
        StringBuilder text=new StringBuilder();
        try{
            JSONArray rows=new JSONArray(c.getSharedPreferences("community",0).getString("feed","[]"));int count=0;
            for(int i=0;i<rows.length()&&count<6;i++){
                JSONObject row=rows.getJSONObject(i);if(!id.equals(row.optString("game")))continue;count++;
                JSONObject votes=row.getJSONObject("votes");
                text.append("\nVersión ").append(row.optString("version","sin indicar")).append(" · ").append(row.optString("engine","auto"))
                    .append("\nPerfecto: ").append(votes.optInt("perfecto")).append(" · Jugable: ").append(votes.optInt("jugable"))
                    .append(" · Con fallas: ").append(votes.optInt("con_fallas")).append(" · No abre: ").append(votes.optInt("no_abre"))
                    .append("\n").append(row.optInt("reports")).append(" aportes · ").append(row.optDouble("hours",0)).append(" h registradas por participantes");
                String note=row.optString("note","");if(!note.isEmpty())text.append("\n").append(note);
                text.append("\n");
            }
        }catch(Exception ignored){}
        return text.length()==0?"Todavía no hay aportes comunitarios aprobados para esta ficha. La compatibilidad del catálogo aparece arriba.":
            "Compatibilidad de la comunidad · aportes revisados\n"+text+"Las horas registradas no garantizan ausencia de errores. Cada versión y motor se muestran por separado.";
    }
    public static void contribute(Activity a,Catalog.Game game){
        Library.Entry installed=Catalog.installed(a,game);
        LinearLayout box=new LinearLayout(a);box.setOrientation(1);int pad=16;box.setPadding(pad,pad,pad,pad);
        String[] labels={"Perfecto","Jugable","Con fallas","No abre"},values={"perfecto","jugable","con_fallas","no_abre"};
        Spinner state=new Spinner(a);state.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels));box.addView(state);
        EditText version=com.hatkid.mkxpz.ui.Ui.input(a);version.setHint("Versión del juego que probaste");version.setText(installed==null?game.version:Catalog.localVersion(a,new File(installed.path)));box.addView(version);
        EditText note=com.hatkid.mkxpz.ui.Ui.input(a);note.setHint("Qué funciona y qué falla (solo información del juego)");note.setMaxLines(4);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});box.addView(note);
        TextView privacy=new TextView(a);privacy.setText("El aporte incluye juego, versión, motor y horas registradas por Essentia. Podrá mostrarse públicamente tras revisión. No adjunta scripts, partidas, claves ni datos del dispositivo.");box.addView(privacy);
        new AlertDialog.Builder(a).setTitle("Aportar compatibilidad · "+game.title).setView(box)
            .setNegativeButton("Cancelar",null).setPositiveButton("Enviar para revisión",(d,w)->{
                String chosen=values[state.getSelectedItemPosition()],v=version.getText().toString().trim(),n=note.getText().toString().trim();
                new Thread(()->{
                    String message;
                    try{
                        JSONObject body=new JSONObject().put("action","compatibility_submit").put("game",game.id).put("version",v)
                            .put("engine",installed==null?"sin indicar":installed.engine).put("state",chosen).put("note",n)
                            .put("hours",installed==null?0:GameStats.quick(a,installed.path).playMs/3600000.0);
                        Cloud.post(a,body,20000);message="Aporte recibido. Aparecerá cuando sea revisado.";
                    }catch(Exception e){message="No se pudo enviar el aporte. Inténtalo más tarde.";}
                    String result=message;a.runOnUiThread(()->{if(!a.isFinishing())Toast.makeText(a,result,Toast.LENGTH_LONG).show();});
                },"EssentiaCompatibility").start();
            }).show();
    }
}
