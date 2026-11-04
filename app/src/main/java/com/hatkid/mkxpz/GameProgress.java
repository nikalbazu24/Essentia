package com.hatkid.mkxpz;
import android.content.Context;
import java.io.*;

/** Last live snapshot, never a parser for untrusted Ruby save files. */
public final class GameProgress {
    private GameProgress(){}
    public static File file(Context c,String path){
        try{
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(path.getBytes("UTF-8"));
            StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            File dir=new File(c.getFilesDir(),"progress");dir.mkdirs();return new File(dir,name+".tsv");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String summary(Context c,String path){
        try{
            File f=file(c,path);if(!f.isFile())return "Los logros aparecerán después de abrir una partida compatible.";
            String[] p;
            try (FileInputStream in = new FileInputStream(f)) {
                p = new String(SaveTransaction.read(in,16384),"UTF-8").split("\t",-1);
            }
            if(p.length<4)return "Progreso no disponible";
            return "Última lectura del juego\n"+p[0]+"\nMedallas: "+value(p[1])+" · Capturados: "+value(p[2])+" · Vistos: "+value(p[3])
                +"\nSolo se muestran datos que el juego permite leer; no se inventan logros ni se altera la partida.";
        }catch(Exception e){return "No se pudo leer el último progreso";}
    }
    private static String value(String s){return s.isEmpty()?"—":s;}
}
