package com.hatkid.mkxpz;

import android.app.*;
import android.content.*;
import android.os.*;
import com.hatkid.mkxpz.ui.Ui;
import java.io.File;

/** Local, opt-in measurements; exported only after the user's review. */
public final class Diagnostics {
    private Diagnostics(){}
    public static boolean enabled(Context c){return c.getSharedPreferences("diagnostics",0).getBoolean("enabled",false);}
    public static void load(Context c,String path,long ms){
        if(path!=null&&enabled(c))c.getSharedPreferences("diagnostics",0).edit().putLong(path+":load",ms).apply();
    }
    public static String summary(Context c,String path){
        String title=Games.readTitle(new File(path));
        long ms=c.getSharedPreferences("diagnostics",0).getLong(path+":load",-1);
        Runtime rt=Runtime.getRuntime();
        return "Essentia "+Cloud.essentiaVersion(c)+"\nJuego: "+title+"\nAndroid: "+Build.VERSION.RELEASE
            +"\nCarga hasta el primer fotograma: "+(ms<0?"sin medir":ms+" ms")
            +"\nMemoria Java usada en este proceso: "+(rt.totalMemory()-rt.freeMemory())/1048576+" MB"
            +"\nMotor aprendido: "+(EssentiaSettings.learnedClassic(c,path)?"clásico":"automático/moderno")
            +"\nLas mediciones de FPS aparecen en el juego con Mostrar FPS. Esta memoria no incluye toda la memoria del motor.\n";
    }
    public static void show(Activity a,String path){
        String report=summary(a,path);
        new AlertDialog.Builder(a).setTitle("Diagnóstico local")
            .setMessage(report+"\nPara comparar rendimiento usa el mismo juego, mapa, motor y velocidad 1× durante al menos un minuto. El diagnóstico no activa envíos automáticos.")
            .setNegativeButton("Cerrar",null).setNeutralButton(enabled(a)?"Desactivar medición":"Activar medición",(d,w)->
                a.getSharedPreferences("diagnostics",0).edit().putBoolean("enabled",!enabled(a)).commit())
            .setPositiveButton("Compartir informe",(d,w)->a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Compartir diagnóstico"))).show();
    }
}
