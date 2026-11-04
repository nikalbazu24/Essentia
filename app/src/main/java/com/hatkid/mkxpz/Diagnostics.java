package com.hatkid.mkxpz;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import org.json.JSONObject;
import android.util.AtomicFile;

/** Opt-in local measurements; files synchronize the separate game/library processes. */
public final class Diagnostics {
    private Diagnostics(){}
    public static boolean enabled(Context c){return c.getSharedPreferences("diagnostics",0).getBoolean("enabled",false);}
    public static File file(Context c,String path,String kind){return new File(GameProgress.file(c,path).getPath()+"."+kind);}
    private static void write(File file,String text){
        File temp=new File(file.getPath()+".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes("UTF-8"));out.getFD().sync();}
            if(!temp.renameTo(file))temp.delete();
        }catch(Exception ignored){temp.delete();}
    }
    private static String read(File file){
        try(InputStream in=new FileInputStream(file)){return new String(SaveTransaction.read(in,8192),"UTF-8");}
        catch(Exception e){return "";}
    }
    private static JSONObject session(Context c,String path) {
        try{return new JSONObject(read(file(c,path,"session")));}catch(Exception e){return new JSONObject();}
    }
    private static void saveSession(Context c,String path,JSONObject data) {
        AtomicFile file=new AtomicFile(file(c,path,"session"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(data.toString().getBytes("UTF-8"));file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);}
    }
    /** Called before SDL starts. No scripts, saved games, credentials or network requests. */
    public static synchronized void start(Context c,String path,String engine,String decision,String screen) {
        if(path==null||!enabled(c))return;
        try{
            JSONObject old=session(c,path);
            if(old.has("started")) {
                String previousError=read(file(c,path,"error"));
                write(file(c,path,"previous_session"),describe(c,old)
                    +(previousError.isEmpty()?"":"\nError del intento anterior:\n"+previousError));
            }
            JSONObject data=new JSONObject();data.put("started",System.currentTimeMillis());
            data.put("pid",android.os.Process.myPid());data.put("engine",engine);
            data.put("decision",decision==null?"unknown":decision);data.put("screen",screen==null?"unknown":screen);
            data.put("phase","before_sdl");data.put("first_frame",false);data.put("user_exit",false);
            saveSession(c,path,data);
            // A new run must never present measurements/errors from the previous run as current.
            for(String kind:new String[]{"load","fps","error"})file(c,path,kind).delete();
        }catch(Exception ignored){}
    }
    public static synchronized void event(Context c,String path,String event) {
        if(path==null||!enabled(c))return;
        try{
            JSONObject data=session(c,path);if(!data.has("started"))return;
            data.put("last_event",event);data.put("updated",System.currentTimeMillis());
            if(event.startsWith("phase:"))data.put("phase",event.substring(6));
            if(event.equals("first_frame"))data.put("first_frame",true);
            if(event.equals("user_exit"))data.put("user_exit",true);
            if(event.equals("fallback_requested"))data.put("fallback_requested",true);
            saveSession(c,path,data);
        }catch(Exception ignored){}
    }
    private static String describe(Context c,JSONObject data) {
        if(!data.has("started"))return "Sin registro de arranque. Activa la medición y vuelve a abrir el juego.";
        String result="Inicio: "+new java.util.Date(data.optLong("started"))
            +"\nMotor usado: "+data.optString("engine")+" · motivo: "+data.optString("decision")
            +"\nPantalla efectiva: "+data.optString("screen")
            +"\nÚltima fase: "+data.optString("phase")+" · primer fotograma: "+(data.optBoolean("first_frame")?"sí":"no registrado")
            +"\nÚltimo evento: "+data.optString("last_event","before_sdl")
            +"\nSalida solicitada desde el menú: "+(data.optBoolean("user_exit")?"sí":"no registrada");
        if(Build.VERSION.SDK_INT>=30){
            try{
                ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
                if(am!=null)for(ApplicationExitInfo exit:am.getHistoricalProcessExitReasons(c.getPackageName(),data.optInt("pid"),8)) {
                    if(!((c.getPackageName()+":game").equals(exit.getProcessName()))||exit.getTimestamp()<data.optLong("started"))continue;
                    result+="\nSalida registrada por Android: motivo "+exit.getReason()+" · estado "+exit.getStatus()
                        +" · "+new java.util.Date(exit.getTimestamp());break;
                }
            }catch(Exception ignored){}
        }
        return result+"\nLa ausencia de traza o de evento de salida no confirma una causa de cierre.";
    }
    public static String sessionSummary(Context c,String path){return describe(c,session(c,path));}

    public static void load(Context c,String path,long ms){if(path!=null&&enabled(c))write(file(c,path,"load"),Long.toString(ms));}
    public static void error(Context c,String path,String error){
        if(path==null||error==null||!enabled(c))return;
        String redacted=error.replaceAll("https?://\\S+","[enlace omitido]")
            .replaceAll("(?i)(api[_ -]?key|password|token|secret)\\s*[:=]\\s*\\S+","$1=[omitido]");
        write(file(c,path,"error"),redacted.substring(0,Math.min(redacted.length(),6000)));
    }
    public static String summary(Context c,String path){
        String title=Games.readTitle(new File(path)),load=read(file(c,path,"load")),fps=read(file(c,path,"fps")),error=read(file(c,path,"error"));
        String performance="sin medir";String[] values=fps.split("\t",-1);
        if(values.length>=2)performance=values[0]+" FPS · intervalo máximo "+values[1]+" ms (última muestra de 1 s)";
        Runtime rt=Runtime.getRuntime();
        return "Essentia "+Cloud.essentiaVersion(c)+"\nJuego: "+title+"\nAndroid: "+Build.VERSION.RELEASE
            +"\nCarga hasta el primer fotograma: "+(load.isEmpty()?"sin medir":load+" ms")
            +"\nFPS del motor: "+performance
            +"\nMemoria Java usada en este proceso: "+(rt.totalMemory()-rt.freeMemory())/1048576+" MB"
            +"\nMotor aprendido: "+(EssentiaSettings.learnedClassic(c,path)?"clásico":"automático/moderno")
            +"\nEsta memoria no incluye toda la memoria nativa del motor. No se puede deducir una mejora porcentual sin comparar mediciones."
            +"\n"+sessionSummary(c,path)
            +(error.isEmpty()?"":"\nÚltimo error registrado:\n"+error)
            +(read(file(c,path,"previous_session")).isEmpty()?"":"\nSesión anterior:\n"+read(file(c,path,"previous_session")))+"\n";
    }
    public static void show(Activity a,String path){
        String report=summary(a,path);
        new AlertDialog.Builder(a).setTitle("Diagnóstico local")
            .setMessage(report+"\nUsa el mismo juego, mapa, motor y velocidad 1× durante al menos un minuto. Los cambios de medición se aplican al volver a abrir el juego. No se activan envíos automáticos.")
            .setNegativeButton("Cerrar",null).setNeutralButton(enabled(a)?"Desactivar medición":"Activar medición",(d,w)-> {
                boolean on=!enabled(a);a.getSharedPreferences("diagnostics",0).edit().putBoolean("enabled",on).commit();
                if(!on)for(String kind:new String[]{"session","previous_session","load","fps","error"})file(a,path,kind).delete();
            })
            .setPositiveButton("Compartir informe",(d,w)->a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Compartir diagnóstico"))).show();
    }
}
