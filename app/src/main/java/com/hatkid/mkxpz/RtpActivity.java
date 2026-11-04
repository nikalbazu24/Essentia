package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.Ui;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Installs an authorized resource ZIP, never a Windows installer or bundled RTP. */
public class RtpActivity extends ControllerActivity {
    private String engine="XP";
    private boolean working;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)engine=state.getString("engine","XP");
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);int p=Ui.dp(this,20);list.setPadding(p,p,p,p);list.setBackgroundColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.addView(list);setContentView(scroll);
        Button back=Ui.button(this,"‹ Volver",false);back.setOnClickListener(v->finish());list.addView(back);
        list.addView(Ui.text(this,"Recursos RPG Maker · RTP",25,Ui.TEXT,true));
        list.addView(Ui.text(this,"Algunos juegos necesitan gráficos y audio RTP además de su descarga. Usa recursos que tengas permiso para utilizar. El instalador EXE oficial necesita Windows; en Android puedes importar un ZIP de los recursos ya extraídos (Graphics y Audio).",16,Ui.TEXT_DIM,false));
        list.addView(Ui.text(this,"1 · Elige la versión de RPG Maker",20,Ui.ACCENT_LIGHT,true));
        RadioGroup group=new RadioGroup(this);group.setBackground(Ui.rounded(this,Ui.SURFACE,16));group.setPadding(p,Ui.dp(this,8),p,Ui.dp(this,8));
        for(String name:new String[]{"XP","VX","VXAce"}){
            RadioButton b=new RadioButton(this);b.setText(name);b.setTextColor(Ui.TEXT);b.setChecked(engine.equals(name));b.setOnClickListener(v->engine=name);group.addView(b);
        }list.addView(group);
        list.addView(Ui.text(this,"2 · Importa tu paquete autorizado",20,Ui.ACCENT_LIGHT,true));
        Button install=Ui.button(this,"Importar recursos desde ZIP",false);list.addView(install);
        install.setOnClickListener(v->{
            if(working)return;
            if(!Games.hasAllFilesAccess()){message("Autoriza el acceso a archivos desde la biblioteca");return;}
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),71);
        });
        Button official=Ui.button(this,"Página oficial y condiciones del RTP",false);list.addView(official);
        official.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.rpgmakerweb.com/run-time-package"))));
        list.addView(Ui.text(this,"Recursos en este dispositivo",20,Ui.ACCENT_LIGHT,true));
        for(String name:new String[]{"XP","VX","VXAce"}){
            File f=new File(EssentiaSettings.rtpRoot(),name);
            list.addView(Ui.text(this,name+": "+(new File(f,"Graphics").isDirectory()?"recursos instalados":"sin instalar"),16,Ui.TEXT,false));
        }
    }
    @Override protected void onSaveInstanceState(Bundle s){s.putString("engine",engine);super.onSaveInstanceState(s);}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=71||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String chosen=engine;
        new AlertDialog.Builder(this).setTitle("Instalar RTP "+chosen)
            .setMessage("Se revisará el ZIP antes de reemplazar los recursos. Cierra los juegos y espera a que terminen las instalaciones.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->install(uri,chosen)).show();
    }
    private void install(Uri uri,String chosen){
        if(working)return;working=true;
        Toast.makeText(this,"Preparando recursos…",Toast.LENGTH_LONG).show();
        new Thread(()->{
            File zip=new File(getCacheDir(),"rtp-"+UUID.randomUUID()+".zip"),stage=null;
            String result;
            try{
                synchronized(UpdateService.class){
                    if(GameRemoval.busy()||UpdateService.hasWork()||Games.isGameRunning(this))throw new IOException("Cierra los juegos antes de instalar recursos");
                    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(zip)){
                        if(in==null)throw new IOException("No se pudo abrir el archivo");
                        byte[] b=new byte[65536];int n;long total=0;
                        while((n=in.read(b))!=-1){total+=n;if(total>4L*1024*1024*1024)throw new IOException("El ZIP supera 4 GB");out.write(b,0,n);}
                    }
                    File root=EssentiaSettings.rtpRoot();if(!root.isDirectory()&&!root.mkdirs())throw new IOException("No se puede crear la carpeta RTP");
                    stage=new File(root,".import-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException("No se puede preparar RTP");
                    try(ZipFile z=GameArchives.openZip(zip)){
                        Set<String> prefixes=new HashSet<>();List<ZipEntry> all=new ArrayList<>();Enumeration<? extends ZipEntry> entries=z.entries();
                        while(entries.hasMoreElements()){
                            ZipEntry e=entries.nextElement();String name=GameArchives.path(e.getName());if(all.size()>50000)throw new IOException("Demasiados archivos");
                            all.add(e);String lower=name.toLowerCase(Locale.ROOT);int pos=lower.indexOf("graphics/");if(pos>=0&&(pos==0||lower.charAt(pos-1)=='/'))prefixes.add(name.substring(0,pos));
                        }
                        if(prefixes.size()!=1)throw new IOException("El ZIP debe contener un único paquete con Graphics y Audio ya extraídos");
                        String prefix=prefixes.iterator().next();Set<String> names=new HashSet<>();long total=0;
                        for(ZipEntry e:all){
                            String name=GameArchives.path(e.getName());if(e.isDirectory()||!name.startsWith(prefix))continue;
                            String rel=name.substring(prefix.length());String folder=rel.contains("/")?rel.substring(0,rel.indexOf('/')).toLowerCase(Locale.ROOT):"";
                            if(!folder.equals("graphics")&&!folder.equals("audio")&&!folder.equals("fonts"))continue;
                            if(!names.add(rel.toLowerCase(Locale.ROOT)))throw new IOException("Recursos duplicados");
                            File dest=stage;String[] parts=rel.split("/");
                            for(int i=0;i<parts.length;i++){dest=SaveTransaction.child(dest,i==0?folder.substring(0,1).toUpperCase(Locale.ROOT)+folder.substring(1):parts[i]);if(i<parts.length-1&&!dest.isDirectory()&&!dest.mkdir())throw new IOException("No se pudo crear una carpeta");}
                            CRC32 crc=new CRC32();long size=0;
                            try(InputStream in=z.getInputStream(e);FileOutputStream out=new FileOutputStream(dest)){
                                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){size+=n;total+=n;if(total>4L*1024*1024*1024||size>e.getSize())throw new IOException("El paquete supera el límite");out.write(b,0,n);crc.update(b,0,n);}out.getFD().sync();
                            }
                            if(size!=e.getSize()||(e.getCrc()!=-1&&e.getCrc()!=crc.getValue()))throw new IOException("El ZIP está dañado");
                        }
                    }
                    if(!new File(stage,"Graphics").isDirectory()||!new File(stage,"Audio").isDirectory())throw new IOException("Faltan Graphics o Audio");
                    File target=new File(root,chosen),old=new File(root,".anterior-"+chosen+"-"+UUID.randomUUID());
                    boolean had=target.exists();if(had&&!target.renameTo(old))throw new IOException("No se pudo conservar el RTP anterior");
                    if(!stage.renameTo(target)){
                        if(had&&!old.renameTo(target))throw new IOException("La copia anterior quedó en "+old.getName());
                        throw new IOException("No se pudo instalar el RTP");
                    }
                    result="RTP "+chosen+" instalado. Ya puedes volver al juego.";
                }
            }catch(Exception ex){result="No se pudo importar: "+ex.getMessage();}
            finally{zip.delete();if(stage!=null)erase(stage);}
            String message=result;runOnUiThread(()->{working=false;if(!isFinishing()&&!isDestroyed())message(message);});
        },"EssentiaRTP").start();
    }
    private static void erase(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)erase(c);f.delete();}
    private void message(String text){new AlertDialog.Builder(this).setMessage(text).setPositiveButton("Aceptar",null).show();}
}
