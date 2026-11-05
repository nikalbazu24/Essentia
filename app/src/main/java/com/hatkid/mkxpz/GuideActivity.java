package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.Ui;
import java.io.*;
import java.util.*;

/** Offline help and recovery tools, usable with touch or controller. */
public class GuideActivity extends ControllerActivity {
    private LinearLayout list;
    private String path, title;
    private EditText notes;
    private boolean working;
    private int toolTab;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        path = getIntent().getStringExtra(MainActivity.EXTRA_GAME_PATH);
        Library.Entry game = path == null ? null : Library.find(this, path);
        title = game == null ? "" : game.displayTitle();
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20); list.setPadding(pad,pad,pad,pad); list.setBackgroundColor(Ui.BG);
        scroll.addView(list); setContentView(scroll);
        button("‹ Volver", this::finish);
        list.addView(Ui.brand(this,64),new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64)));
        text("Essentia by Díptico",27);
        if(path!=null&&game!=null) {
            text(title,23);
            text("Tu espacio de juego · partidas, notas y herramientas",16);
            LinearLayout tabs=new LinearLayout(this);
            android.widget.HorizontalScrollView tabScroll=new android.widget.HorizontalScrollView(this);tabScroll.setHorizontalScrollBarEnabled(false);tabScroll.addView(tabs);list.addView(tabScroll);
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);list.addView(content);
            String[] labels={"Partida","Notas","Respaldo","Herramientas"};
            int selected=state==null?0:state.getInt("tools_tab",0);
            for(int n=0;n<labels.length;n++){
                final int tab=n;Button button=Ui.button(this,labels[n],false);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=Ui.dp(this,8);tabs.addView(button,lp);
                button.setOnClickListener(v->{
                    saveNotes();toolTab=tab;notes=null;
                    for(int i=0;i<tabs.getChildCount();i++)tabs.getChildAt(i).setSelected(i==tab);
                    content.removeAllViews();LinearLayout outer=list;list=content;
                    if(tab==0){
                        text("Última lectura del juego",21);text(GameProgress.summary(this,path),16);
                        button("Controles de este juego",R.drawable.ic_e_gamepad,()->startActivity(scoped(ControlsActivity.class)));
                        button("Buscar actualizaciones del juego",R.drawable.ic_e_cloud,()->startActivity(new Intent(this,StoreActivity.class).putExtra(StoreActivity.EXTRA_TAB,1).putExtra(StoreActivity.EXTRA_GAME,path)));
                        Catalog.Game catalog=Catalog.match(Catalog.cached(this),title,new File(path));
                        if(catalog!=null&&!Catalog.webUrl(catalog.page).isEmpty())button("Visitar al creador",R.drawable.ic_e_compass,()->open(catalog.page));
                    }else if(tab==1){
                        text("Mis notas",21);text("Se guardan en este dispositivo para este juego.",16);
                        notes=com.hatkid.mkxpz.ui.Ui.input(this);notes.setTextColor(Ui.TEXT);notes.setHintTextColor(Ui.TEXT_DIM);notes.setMinLines(6);
                        notes.setGravity(android.view.Gravity.TOP);notes.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        notes.setHint("Objetivos, lugares y recordatorios…");notes.setText(getSharedPreferences("essentia_notes",MODE_PRIVATE).getString(path,""));list.addView(notes);
                        button("Guardar notas",R.drawable.ic_e_note,()->{saveNotes();android.widget.Toast.makeText(this,"Notas guardadas",0).show();});
                    }else if(tab==2){
                        text("Cuida tus partidas",21);text("Cierra el juego antes de restaurar. Se conservará una copia de la partida actual.",16);
                        button("Restaurar respaldo local",R.drawable.ic_p_save,this::chooseBackup);
                        text("Las copias en la nube son opcionales. Se configuran desde Ajustes → Conexiones.",16);
                    }else{
                        text("Herramientas de este juego",21);
                        button("Diagnóstico local e informe",R.drawable.ic_e_spark,()->Diagnostics.show(this,path));
                        button("Revisar traducciones",R.drawable.ic_e_note,()->startActivity(scoped(TranslationReviewActivity.class)));
                        button("Revisar selección automática del motor",R.drawable.ic_e_settings,()->new AlertDialog.Builder(this).setTitle("Motor automático de este juego")
                            .setMessage("Motor clásico aprendido: "+(EssentiaSettings.learnedClassic(this,path)?"sí":"no")+". Puedes olvidar solo este aprendizaje; no cambia tu elección manual ni las recomendaciones del perfil, y no toca partidas.")
                            .setNegativeButton("Cerrar",null).setPositiveButton("Olvidar aprendizaje",(d,w)->EssentiaSettings.forgetLearnedClassic(this,path)).show());
                        button("Reportar un problema",R.drawable.ic_e_help,()->ReportActivity.open(this,path,null));
                        button("Ver la guía de Essentia",()->startActivity(new Intent(this,GuideActivity.class)));
                    }
                    list=outer;
                });
            }
            tabs.getChildAt(Math.max(0,Math.min(3,selected))).performClick();return;
        }
        button("Repetir tour visual",R.drawable.ic_e_compass,()->com.hatkid.mkxpz.ui.LibraryTour.open(this));
        text("Tu biblioteca de aventuras en Android",17);
        text("1 · Prepara el juego", 21);
        text("Descarga la versión completa del creador. Essentia ejecuta juegos RPG Maker XP, VX y VX Ace; no ejecuta ROMs de GBA/NDS ni juegos Unity o EXE de Windows. En la tienda puedes visitar las páginas y ver la compatibilidad conocida.",16);
        text("2 · Instala y abre",21);
        text("Usa Tienda → Instalar una descarga, o Añadir carpeta si el juego ya está extraído. Ejemplo: Descargas/Pokémon Añil/Game.ini. Conserva Graphics, Audio y Data. Deja espacio libre para la instalación y sus respaldos.",16);
        text("3 · Teléfono o Thor",21);
        text("En el teléfono usa los controles táctiles. En la Thor, Select + Start abre el menú; puedes cambiar cada botón y probar el mando en Controles. El panel inferior muestra el mapa regional cuando el juego lo permite. Empieza con pantalla segura y motor Automático; cada juego puede guardar sus propios ajustes.",16);
        text("4 · Cuida tus partidas",21);
        text("Guarda desde el juego antes de cerrarlo. Copias en la nube es opcional: usa la misma clave de respaldo en tus dos dispositivos Android. Restaurar requiere tu confirmación y conserva la partida anterior. Las actualizaciones de juegos guardan copias en Essentia/Respaldos.",16);
        text("5 · Si algo falla",21);
        text("Prueba el motor clásico para juegos antiguos, vuelve a pantalla segura y desactiva ayudas o traducción. Si faltan recursos RTP, abre Recursos RPG Maker. Las funciones que necesitan DLL de Windows pueden no funcionar. Reporta el punto exacto del fallo y la versión del juego.",16);
        button("Abrir tienda y compatibilidad", () -> startActivity(new Intent(this, StoreActivity.class)));
        button("Controles y prueba del mando",R.drawable.ic_e_gamepad, () -> startActivity(scoped(ControlsActivity.class)));
        button("Recursos RPG Maker (RTP)",R.drawable.ic_e_grid, () -> startActivity(new Intent(this, RtpActivity.class)));
        button(AiTranslation.enabled(this)?"Traducción IA opcional (activada)":"Traducción IA opcional (desactivada)", () -> startActivity(new Intent(this, AiTranslationActivity.class)));
        button("Avisos diarios de actualizaciones",R.drawable.ic_e_cloud, () -> DailyUpdates.configure(this));
        button("Comunidad beta y sugerencias", () -> open("https://github.com/nikalbazu24/Essentia/issues"));
    }

    private Intent scoped(Class<?> target) {
        Intent i = new Intent(this,target);
        if (path != null) i.putExtra(MainActivity.EXTRA_GAME_PATH,path);
        return i;
    }
    private void text(String value,int size) {
        TextView t = Ui.text(this,value,size,size>=21?Ui.ACCENT_LIGHT:Ui.TEXT,size>=21);
        int p=Ui.dp(this,9);t.setPadding(0,p,0,p);list.addView(t);
        if(size<21){t.setBackground(Ui.rounded(this,Ui.SURFACE,14));t.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));}
    }
    private void button(String label,Runnable action) {button(label,R.drawable.ic_e_help,action);}
    private void button(String label,int icon,Runnable action) {
        Button b=Ui.button(this,label,false,icon);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,8);list.addView(b,lp);
    }
    private void open(String url) {
        try { if (!Catalog.webUrl(url).isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(Exception e){message("No hay un navegador disponible");}
    }
    private void chooseBackup() {
        if (working) return;
        if (!Games.hasAllFilesAccess()) {message("Autoriza el acceso a archivos desde la biblioteca");return;}
        working=true;
        new Thread(()->{
            List<File> folders=new ArrayList<>();
            File root=new File(Environment.getExternalStorageDirectory(),"Essentia/Respaldos");
            scan(root,root,folders,0);
            Collections.sort(folders,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            runOnUiThread(()->{
                working=false;if(isFinishing()||isDestroyed())return;
                if(folders.isEmpty()){message("No hay respaldos locales para este juego");return;}
                String[] labels=new String[folders.size()];
                for(int i=0;i<labels.length;i++)labels[i]=folders.get(i).getAbsolutePath().substring(root.getAbsolutePath().length()+1);
                new AlertDialog.Builder(this).setTitle("Respaldos de "+title).setItems(labels,(d,n)->confirmRestore(folders.get(n)))
                    .setNegativeButton("Cancelar",null).show();
            });
        },"EssentiaBackupList").start();
    }
    private void scan(File root,File f,List<File> out,int depth) {
        if(depth>3||out.size()>=250||f.getName().endsWith(".preparando"))return;
        try {
            if(!f.getCanonicalFile().equals(f.getAbsoluteFile()))return;
            String relative=f.getAbsolutePath().substring(root.getAbsolutePath().length());
            boolean matches=relative.startsWith("/"+Saves.safeName(title)+"/")
                ||relative.startsWith("/"+Saves.safeName(new File(path).getName())+"_")
                ||relative.startsWith("/"+Saves.safeName(new File(path).getName())+"/");
            if(matches&&!f.getName().equals("nuevos")&&!Saves.findIn(f).isEmpty())out.add(f);
            File[] children=f.listFiles();if(children!=null)for(File child:children)if(child.isDirectory())scan(root,child,out,depth+1);
        }catch(Exception ignored){}
    }
    private void confirmRestore(File source) {
        new AlertDialog.Builder(this).setTitle("¿Restaurar estas partidas?")
            .setMessage(title+"\n"+source.getName()+"\n\nSe conservará una copia de las partidas actuales. Cierra el juego antes de continuar.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->new AlertDialog.Builder(this)
                .setTitle("Confirmar restauración").setMessage("La partida de este respaldo reemplazará la actual de "+title+".")
                .setNegativeButton("Cancelar",null).setPositiveButton("Restaurar",(dd,ww)->restore(source)).show()).show();
    }
    private void restore(File source) {
        if(working)return;working=true;
        new Thread(()->{
            String result;
            try {
                synchronized(UpdateService.class){
                    if(UpdateService.hasWork()||GameRemoval.busy()||Games.isGameRunning(this))throw new IOException("Cierra el juego y espera a que terminen las instalaciones");
                    Map<String,byte[]> files=new LinkedHashMap<>();long size=0;
                    for(File f:Saves.findIn(source)) {
                        SaveTransaction.child(source,f.getName());byte[] data=SaveTransaction.read(f);size+=data.length;
                        if(size>SaveTransaction.LIMIT)throw new IOException("El respaldo supera 20 MB");files.put(f.getName(),data);
                    }
                    int n=SaveTransaction.restore(Saves.dirFor(this,new File(path),title),
                        new File(Environment.getExternalStorageDirectory(),"Essentia/Respaldos/"+Saves.safeName(title)),files);
                    result="Restauradas "+n+" partidas. Tu partida anterior se conservó en Respaldos.";
                }
            }catch(Exception e){result="No se pudo restaurar: "+e.getMessage();}
            String message=result;runOnUiThread(()->{working=false;if(!isFinishing()&&!isDestroyed())message(message);});
        },"EssentiaRestore").start();
    }
    private void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Aceptar",null).show();}
    private void saveNotes(){if(notes!=null&&path!=null)getSharedPreferences("essentia_notes",MODE_PRIVATE).edit().putString(path,notes.getText().toString()).commit();}
    @Override protected void onPause(){saveNotes();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){saveNotes();out.putInt("tools_tab",toolTab);super.onSaveInstanceState(out);}
}
