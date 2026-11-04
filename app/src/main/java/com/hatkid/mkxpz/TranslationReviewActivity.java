package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.Ui;
import java.io.*;
import java.util.*;

/** Edits the app's TSV rather than game scripts. Latest correction wins. */
public class TranslationReviewActivity extends ControllerActivity {
    private LinearLayout list;private File file;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        String path=getIntent().getStringExtra(MainActivity.EXTRA_GAME_PATH);
        Library.Entry e=path==null?null:Library.find(this,path);
        if(e==null){finish();return;}
        file=new File(Translator.folder(e.displayTitle()),"es.tsv");
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);
        int p=Ui.dp(this,18);list.setPadding(p,p,p,p);list.setBackgroundColor(Ui.BG);scroll.addView(list);setContentView(scroll);
        Button back=Ui.button(this,"‹ Volver",false);back.setOnClickListener(v->finish());list.addView(back);
        list.addView(Ui.text(this,"Revisar traducciones · "+e.displayTitle(),22,Ui.TEXT,true));
        list.addView(Ui.text(this,"Cierra el juego antes de corregir. Las correcciones son locales y conservan los códigos de formato.",15,Ui.TEXT_DIM,false));
        EditText search=new EditText(this);search.setHint("Buscar texto original o traducción");search.setTextColor(Ui.TEXT);list.addView(search);
        Button find=Ui.button(this,"Buscar",false);list.addView(find);
        LinearLayout results=new LinearLayout(this);results.setOrientation(1);list.addView(results);
        find.setOnClickListener(v->{
            if(Games.isGameRunning(this)){new AlertDialog.Builder(this).setMessage("Cierra el juego antes de revisar traducciones").setPositiveButton("Aceptar",null).show();return;}
            results.removeAllViews();
            String query=search.getText().toString().toLowerCase(Locale.ROOT);
            new Thread(()->{
                Map<String,String> rows=new LinkedHashMap<>();String error=null;
                try{
                    if(file.exists())for(String line:new String(SaveTransaction.read(file),"UTF-8").split("\n")){
                        String[] pair=line.split("\t",2);if(pair.length==2)rows.put(Translator.unesc(pair[0]),Translator.unesc(pair[1]));
                    }
                }catch(Exception ex){error="No se pudo abrir el archivo de traducciones";}
                String problem=error;
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
                    if(problem!=null){results.addView(Ui.text(this,problem,16,Ui.TEXT,false));return;}
                    int count=0;
                    for(Map.Entry<String,String> row:rows.entrySet())if((row.getKey()+" "+row.getValue()).toLowerCase(Locale.ROOT).contains(query)){
                        Button b=Ui.button(this,row.getKey()+"\n→ "+row.getValue(),false);b.setOnClickListener(x->edit(row.getKey(),row.getValue()));results.addView(b);if(++count==100)break;
                    }
                    results.addView(Ui.text(this,count==0?"No hay traducciones coincidentes":"Mostrando hasta 100 coincidencias",14,Ui.TEXT_DIM,false));
                });
            },"EssentiaTranslations").start();
        });
        find.performClick();
    }
    private void edit(String original,String value){
        EditText input=new EditText(this);input.setText(value);input.setMinLines(3);
        new AlertDialog.Builder(this).setTitle(original).setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar corrección",(d,w)->{
                if(Games.isGameRunning(this))return;
                try{
                    List<String> codes=new ArrayList<>();Translator.protect(original,codes);
                    String corrected=input.getText().toString();
                    for(String code:codes)if(!corrected.contains(code))throw new IOException("Conserva el código "+code);
                    try(Writer out=new OutputStreamWriter(new FileOutputStream(file,true),"UTF-8")){
                        out.write(Translator.esc(original)+"\t"+Translator.esc(corrected)+"\n");
                    }
                    Toast.makeText(this,"Corrección guardada",Toast.LENGTH_SHORT).show();
                }catch(Exception ex){new AlertDialog.Builder(this).setMessage(ex.getMessage()).setPositiveButton("Aceptar",null).show();}
            }).show();
    }
}
