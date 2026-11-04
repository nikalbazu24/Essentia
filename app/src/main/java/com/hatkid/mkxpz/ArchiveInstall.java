package com.hatkid.mkxpz;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Staged overlay with CRC checks and a recoverable, on-disk rollback journal. */
public final class ArchiveInstall {
    private static final String MARKER=".essentia-recovery";
    private ArchiveInstall(){}
    private static File resolve(File root,String relative,boolean create)throws IOException{
        String clean=GameArchives.path(relative);
        File dir=root.getCanonicalFile();
        String prefix=dir.getPath()+File.separator;
        String[] parts=clean.split("/",-1);
        for(int i=0;i<parts.length;i++){
            String part=parts[i];if(part.isEmpty())throw new IOException("Ruta vacía en la descarga");
            String[] names=dir.list();
            if(names!=null)for(String n:names)if(n.equalsIgnoreCase(part)){part=n;break;}
            File next=SaveTransaction.child(dir,part);
            if(!next.getCanonicalPath().startsWith(prefix))throw new IOException("La descarga sale de su carpeta");
            if(i<parts.length-1){
                if(next.exists()&&!next.isDirectory())throw new IOException("Una carpeta coincide con un archivo");
                if(create&&!next.isDirectory()&&!next.mkdir())throw new IOException("No se puede crear "+part);
            }
            dir=next;
        }
        return dir;
    }
    private static void writeProperties(Properties p,File f)throws IOException{
        try(FileOutputStream out=new FileOutputStream(f)){p.store(out,"Essentia update recovery");out.getFD().sync();}
    }
    public static synchronized void recover(File game)throws IOException{
        File marker=new File(game,MARKER);
        if(!marker.exists())return;
        Properties location=new Properties();try(InputStream in=new FileInputStream(marker)){location.load(in);}
        File work=new File(location.getProperty("work","")).getAbsoluteFile();
        if(!work.getName().startsWith(".essentia-stage-")||!work.getCanonicalFile().equals(work)
            ||!work.getParentFile().equals(game.getCanonicalFile().getParentFile()))throw new IOException("La copia de recuperación no es válida");
        Properties journal=new Properties();try(InputStream in=new FileInputStream(new File(work,"journal"))){journal.load(in);}
        if(!game.getCanonicalPath().equals(journal.getProperty("game")))throw new IOException("El respaldo pertenece a otro juego");
        int count;
        try{count=Integer.parseInt(journal.getProperty("count"));}catch(Exception e){throw new IOException("Respaldo incompleto",e);}
        if(count<0||count>100000)throw new IOException("Respaldo incompleto");
        for(int i=count-1;i>=0;i--){
            String rel=journal.getProperty("path."+i);File dest=resolve(game,rel,true);
            if(Boolean.parseBoolean(journal.getProperty("old."+i))){
                File old=new File(work,"old-"+i);
                if(!old.isFile())throw new IOException("Falta un archivo de recuperación");
                File temp=new File(dest.getParentFile(),".essentia-restore-"+UUID.randomUUID());
                try{SaveTransaction.copy(old,temp);if(!temp.renameTo(dest))throw new IOException("No se pudo recuperar "+rel);}
                finally{temp.delete();}
            }else if(dest.exists()&&!dest.delete())throw new IOException("No se pudo revertir "+rel);
        }
        if(!marker.delete())throw new IOException("No se pudo cerrar la recuperación");
        cleanup(work);
    }
    public static synchronized void install(File zip,File game,List<String> ignored,GameUpdater.Progress progress)throws Exception{
        recover(game);
        String root=GameArchives.gameRoot(zip);
        File work=new File(game.getCanonicalFile().getParentFile(),".essentia-stage-"+UUID.randomUUID());
        if(!work.mkdir())throw new IOException("No se puede preparar la instalación");
        boolean journalOpen=false;
        try(ZipFile z=GameArchives.openZip(zip)){
            List<ZipEntry> entries=new ArrayList<>();List<File> targets=new ArrayList<>();Set<String> names=new HashSet<>();
            long bytes=0;
            Enumeration<? extends ZipEntry> all=z.entries();
            while(all.hasMoreElements()){
                ZipEntry e=all.nextElement();String name=GameArchives.path(e.getName());
                if(e.isDirectory()||!name.startsWith(root))continue;
                String rel=name.substring(root.length());if(rel.isEmpty())continue;
                String first=rel.contains("/")?rel.substring(0,rel.indexOf('/')).toLowerCase(Locale.ROOT):"";
                if(ignored.contains(first)||first.contains("poke_updater"))continue;
                if(!rel.contains("/")&&Saves.looksLikeSave(rel))continue;
                if(rel.startsWith(".essentia-"))throw new IOException("Nombre reservado en la descarga");
                File target=resolve(game,rel,false);
                if(!names.add(target.getPath().toLowerCase(Locale.ROOT)))throw new IOException("Archivos repetidos");
                if(e.getSize()<0||e.getSize()>16L*1024*1024*1024)throw new IOException("Tamaño no permitido");
                bytes+=e.getSize()+(target.exists()?target.length():0);
                if(bytes>32L*1024*1024*1024)throw new IOException("La descarga supera el límite de instalación");
                entries.add(e);targets.add(target);
            }
            if(game.getUsableSpace()<bytes+32L*1024*1024)throw new GameUpdater.NoSpace(bytes+32L*1024*1024);
            byte[] buffer=new byte[65536];Properties journal=new Properties();journal.setProperty("game",game.getCanonicalPath());
            journal.setProperty("count",String.valueOf(entries.size()));
            for(int i=0;i<entries.size();i++){
                if(progress.cancelled())throw new IOException("Instalación cancelada");
                ZipEntry e=entries.get(i);File dest=targets.get(i);CRC32 crc=new CRC32();long size=0;
                try(InputStream in=z.getInputStream(e);FileOutputStream out=new FileOutputStream(new File(work,"new-"+i))){
                    int n;while((n=in.read(buffer))!=-1){
                        if(progress.cancelled())throw new IOException("Instalación cancelada");
                        size+=n;if(size>e.getSize())throw new IOException("Archivo demasiado grande");
                        out.write(buffer,0,n);crc.update(buffer,0,n);
                    }out.getFD().sync();
                }
                if(size!=e.getSize()||(e.getCrc()!=-1&&e.getCrc()!=crc.getValue()))throw new IOException("Descarga dañada: "+e.getName());
                if(dest.exists()){
                    if(!dest.isFile())throw new IOException("El destino no es un archivo");
                    SaveTransaction.copy(dest,new File(work,"old-"+i));
                }
                String rel=dest.getAbsolutePath().substring(game.getCanonicalPath().length()+1).replace(File.separatorChar,'/');
                journal.setProperty("path."+i,rel);journal.setProperty("old."+i,String.valueOf(dest.exists()));
                progress.update(1,(float)(i+1)/Math.max(1,entries.size()),"Preparando "+(i+1)+" / "+entries.size());
            }
            writeProperties(journal,new File(work,"journal"));
            Properties location=new Properties();location.setProperty("work",work.getAbsolutePath());
            writeProperties(location,new File(game,MARKER));journalOpen=true;
            for(int i=0;i<entries.size();i++){
                if(progress.cancelled())throw new IOException("Instalación cancelada");
                File target=resolve(game,journal.getProperty("path."+i),true);
                if(!new File(work,"new-"+i).renameTo(target))throw new IOException("No se pudo instalar "+target.getName());
            }
            if(!new File(game,MARKER).delete())throw new IOException("No se pudo finalizar la instalación");
            journalOpen=false;progress.update(1,1,"Instalación completa");
        }catch(Exception failure){
            if(journalOpen){
                try{recover(game);journalOpen=false;}catch(IOException rollback){failure.addSuppressed(rollback);}
            }
            throw failure;
        }finally{if(!journalOpen)cleanup(work);}
    }
    private static void cleanup(File f){
        // Only this class's newly created, flat journal directories reach here.
        File[] files=f.listFiles();if(files!=null)for(File child:files)if(child.isFile())child.delete();f.delete();
    }
}
