package com.hatkid.mkxpz;

import android.app.*;
import android.os.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offers a newer cloud snapshot without silently overwriting a local game. */
public final class CloudResume {
    private CloudResume(){}
    private static final java.util.concurrent.atomic.AtomicInteger GENERATION=new java.util.concurrent.atomic.AtomicInteger();
    public static void before(Activity a,File game,Runnable play){
        final int generation=GENERATION.incrementAndGet();
        Runnable guardedPlay=()->{if(generation==GENERATION.get()&&!a.isFinishing()&&!a.isDestroyed())play.run();};
        if(!EssentiaSettings.flag(a,EssentiaSettings.CLOUD_BACKUP,false)||!Cloud.backupsReady(a)||Games.isGameRunning(a)){
            guardedPlay.run();return;
        }
        AtomicBoolean completed=new AtomicBoolean();Handler ui=new Handler(Looper.getMainLooper());
        Runnable fallback=()->{if(completed.compareAndSet(false,true)&&!a.isFinishing()&&!a.isDestroyed())guardedPlay.run();};
        ui.postDelayed(fallback,8500);
        new Thread(()->{
            try{
                String path=game.getAbsolutePath();Library.Entry e=Library.find(a,path);
                String title=e==null?Games.readTitle(game):e.displayTitle();
                long latest=CloudBackup.lastBackup(a,path);
                for(File f:Saves.findIn(Saves.dirFor(a,game,title)))latest=Math.max(latest,f.lastModified());
                List<String[]> backups=CloudBackup.list(a,path,8000);
                if(backups.isEmpty()){ui.post(fallback);return;}
                String id=backups.get(0)[0];SimpleDateFormat format=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));format.setLenient(false);
                long remote=format.parse(id).getTime();
                if(remote<=latest+2000){ui.post(fallback);return;}
                ui.post(()->{
                    if(!completed.compareAndSet(false,true)||generation!=GENERATION.get()||a.isFinishing()||a.isDestroyed())return;
                    ui.removeCallbacks(fallback);
                    new AlertDialog.Builder(a).setTitle("Hay una copia más reciente en la nube")
                        .setMessage(title+"\n"+CloudBackup.readable(id)+"\n\nPuede venir de tu otro dispositivo Android. La fecha corresponde a la subida; revisa cuál partida quieres usar. No se reemplazará nada sin tu confirmación.")
                        .setNegativeButton("Usar partida local",(d,w)->guardedPlay.run())
                        .setOnCancelListener(d->guardedPlay.run())
                        .setPositiveButton("Revisar restauración",(d,w)->new AlertDialog.Builder(a)
                            .setTitle("¿Restaurar antes de jugar?").setMessage("Se conservará una copia de tu partida local antes de traer la de la nube.")
                            .setNegativeButton("Usar local",(dd,ww)->guardedPlay.run()).setOnCancelListener(dd->guardedPlay.run())
                            .setPositiveButton("Restaurar y jugar",(dd,ww)->restore(a,path,id,guardedPlay)).show()).show();
                });
            }catch(Exception ignored){ui.post(fallback);}
        },"EssentiaCloudCheck").start();
    }
    private static void restore(Activity a,String path,String id,Runnable play){
        ProgressDialog dialog=ProgressDialog.show(a,"Partidas","Restaurando respaldo…",true,false);
        new Thread(()->{
            String failure=null;
            try{CloudBackup.restore(a,path,id);}
            catch(Exception e){failure="No se pudo restaurar: "+e.getMessage();}
            String error=failure;
            a.runOnUiThread(()->{
                if(a.isFinishing()||a.isDestroyed())return;dialog.dismiss();
                if(error==null)play.run();
                else new AlertDialog.Builder(a).setMessage(error).setNegativeButton("Volver",null)
                    .setPositiveButton("Usar partida local",(d,w)->play.run()).show();
            });
        },"EssentiaCloudRestore").start();
    }
}
