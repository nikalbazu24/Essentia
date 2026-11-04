package com.hatkid.mkxpz;
import android.app.*;
import android.widget.*;
import java.io.*;
import com.hatkid.mkxpz.ui.Ui;

public final class Licenses {
    private Licenses(){}
    public static void show(Activity a){
        StringBuilder text=new StringBuilder("Essentia by Díptico · GPL-2.0-or-later\nCódigo y licencia: github.com/nikalbazu24/Essentia\n\n");
        for(String name:new String[]{"libarchive","commons-compress","xz"}){
            text.append(name).append("\n");
            try(InputStream in=a.getAssets().open("licenses/"+name+".txt")){
                text.append(new String(SaveTransaction.read(in,256*1024),"UTF-8")).append("\n\n");
            }catch(Exception e){text.append("Consulta los avisos de esta dependencia en el repositorio.\n\n");}
        }
        TextView contents=Ui.text(a,text.toString(),13,Ui.TEXT,false);contents.setTextIsSelectable(true);
        int p=Ui.dp(a,16);contents.setPadding(p,p,p,p);ScrollView scroll=new ScrollView(a);scroll.addView(contents);
        new AlertDialog.Builder(a).setTitle("Créditos y licencias").setView(scroll).setPositiveButton("Cerrar",null).show();
    }
}
