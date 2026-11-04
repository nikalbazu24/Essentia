package com.hatkid.mkxpz;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.net.*;
import java.io.*;

/** Android schedules this best-effort daily job; no permanent worker or game launch. */
public class DailyUpdates extends JobService {
    private static final int JOB=2030;
    private volatile boolean stopped;
    public static void configure(Activity a) {
        boolean enabled=a.getSharedPreferences("daily_updates",MODE_PRIVATE).getBoolean("enabled",false);
        new AlertDialog.Builder(a).setTitle("Avisos de actualizaciones")
            .setMessage("Android puede revisar aproximadamente una vez al día si hay una nueva versión de Essentia. Necesita conexión; el ahorro de batería puede retrasar el aviso. No descarga ni instala nada por sí solo.")
            .setNegativeButton("Cerrar",null)
            .setNeutralButton(enabled?"Desactivar":"Activar",(d,w)->{
                boolean next=!enabled;
                a.getSharedPreferences("daily_updates",MODE_PRIVATE).edit().putBoolean("enabled",next).apply();
                JobScheduler scheduler=(JobScheduler)a.getSystemService(JOB_SCHEDULER_SERVICE);
                if(scheduler==null)return;
                if(!next){scheduler.cancel(JOB);return;}
                if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2030);
                int result=scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(a,DailyUpdates.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24L*60*60*1000).setPersisted(true).build());
                Toast.makeText(a,result==JobScheduler.RESULT_SUCCESS?"Revisión diaria activada":"Android no pudo programar la revisión",Toast.LENGTH_LONG).show();
            }).show();
    }
    @Override public boolean onStartJob(JobParameters params) {
        stopped=false;
        new Thread(()->{
            try {
                if(!getSharedPreferences("daily_updates",MODE_PRIVATE).getBoolean("enabled",false))return;
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
                HttpURLConnection c=(HttpURLConnection)new URL("https://github.com/nikalbazu24/Essentia/releases/download/dev/version.json").openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(10000);
                JSONObject manifest;
                try(InputStream in=c.getInputStream()) {manifest=new JSONObject(new String(SaveTransaction.read(in,16384),"UTF-8"));}
                finally{c.disconnect();}
                int code=manifest.getInt("versionCode");
                if(code<=getPackageManager().getPackageInfo(getPackageName(),0).versionCode||stopped)return;
                if(code==getSharedPreferences("daily_updates",MODE_PRIVATE).getInt("notified",0))return;
                NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm==null)return;
                String channel="essentia_daily";
                if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(channel,"Nuevas versiones",NotificationManager.IMPORTANCE_DEFAULT));
                PendingIntent open=PendingIntent.getActivity(this,JOB,new Intent(this,LibraryActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
                nm.notify(JOB,b.setSmallIcon(R.drawable.ic_e_store).setContentTitle("Hay una nueva versión de Essentia")
                    .setContentText(manifest.optString("versionName","")+" · Toca para revisar").setContentIntent(open).setAutoCancel(true).build());
                getSharedPreferences("daily_updates",MODE_PRIVATE).edit().putInt("notified",code).apply();
            }catch(Exception ignored){}
            finally{if(!stopped)jobFinished(params,false);}
        },"EssentiaDaily").start();return true;
    }
    @Override public boolean onStopJob(JobParameters params){stopped=true;return true;}
}
