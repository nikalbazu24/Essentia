package com.hatkid.mkxpz;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;

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
            +(error.isEmpty()?"":"\nÚltimo error registrado:\n"+error)+"\n";
    }
    public static void show(Activity a,String path){
        String report=summary(a,path);
        new AlertDialog.Builder(a).setTitle("Diagnóstico local")
            .setMessage(report+"\nUsa el mismo juego, mapa, motor y velocidad 1× durante al menos un minuto. Los cambios de medición se aplican al volver a abrir el juego. No se activan envíos automáticos.")
            .setNegativeButton("Cerrar",null).setNeutralButton(enabled(a)?"Desactivar medición":"Activar medición",(d,w)->
                a.getSharedPreferences("diagnostics",0).edit().putBoolean("enabled",!enabled(a)).commit())
            .setPositiveButton("Compartir informe",(d,w)->a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Compartir diagnóstico"))).show();
    }
}
