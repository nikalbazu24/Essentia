package com.hatkid.mkxpz;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.sevenz.*;

/** RAR/7z readers never create links or extract into a game directory. */
public final class GameArchives {
    private static final int MAX_FILES=100000;
    private static final long MAX_BYTES=16L*1024*1024*1024;
    private GameArchives(){}
    public static boolean supported(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(zip|rar|7z)$");}
    public static String path(String name)throws IOException{
        if(name==null)throw new IOException("Archivo sin nombre");
        String p=name.replace('\\','/');
        if(p.startsWith("/")||p.contains(":")||p.indexOf('\0')>=0)throw new IOException("Ruta no válida en la descarga");
        for(String part:p.split("/",-1))if(part.equals("..")||part.equals("."))throw new IOException("Ruta no válida en la descarga");
        return p;
    }
    private static int format(File f)throws IOException{
        byte[] magic=new byte[8];int n;
        try(InputStream in=new FileInputStream(f)){n=in.read(magic);}
        if(n>=4&&magic[0]=='P'&&magic[1]=='K')return 0;
        if(n>=7&&magic[0]=='R'&&magic[1]=='a'&&magic[2]=='r'&&magic[3]=='!')return 1;
        if(n>=6&&magic[0]==0x37&&magic[1]==0x7a&&magic[2]==(byte)0xbc&&magic[3]==(byte)0xaf)return 2;
        throw new IOException("La descarga no es un ZIP, RAR o 7z válido");
    }
    interface EntrySink{void entry(String name,long size,InputStream in)throws Exception;}
    private static void entries(File file,boolean contents,EntrySink sink)throws Exception{
        int kind=format(file),count=0;
        if(kind==0){
            try(ZipFile zip=openZip(file)){
                Enumeration<? extends ZipEntry> all=zip.entries();
                while(all.hasMoreElements()){
                    ZipEntry e=all.nextElement();if(++count>MAX_FILES)throw new IOException("Demasiados archivos");
                    String name=path(e.getName());if(e.isDirectory())continue;
                    try(InputStream in=contents?zip.getInputStream(e):null){sink.entry(name,e.getSize(),in);}
                }
            }
        }else if(kind==1){
            try(NativeArchive rar=new NativeArchive(file)){
                String value;
                while((value=rar.next())!=null){
                    if(++count>MAX_FILES)throw new IOException("Demasiados archivos");
                    String name=path(value);
                    sink.entry(name,rar.size(),contents?rar.stream():null);
                }
            }
        }else{
            try(SevenZFile seven=SevenZFile.builder().setFile(file).setMaxMemoryLimitKiB(65536).get()){
                if(!contents){
                    for(SevenZArchiveEntry e:seven.getEntries()){
                        if(++count>MAX_FILES)throw new IOException("Demasiados archivos");
                        String name=path(e.getName());if(!e.isDirectory())sink.entry(name,e.getSize(),null);
                    }
                }else{
                    SevenZArchiveEntry e;
                    while((e=seven.getNextEntry())!=null){
                        if(++count>MAX_FILES)throw new IOException("Demasiados archivos");
                        String name=path(e.getName());if(e.isDirectory())continue;
                        if(e.isAntiItem())throw new IOException("El 7z contiene una operación no admitida");
                        InputStream in=new InputStream(){
                            public int read()throws IOException{return seven.read();}
                            public int read(byte[] b,int o,int n)throws IOException{return seven.read(b,o,n);}
                        };
                        sink.entry(name,e.getSize(),in);
                    }
                }
            }
        }
    }
    public static ZipFile openZip(File f)throws IOException{
        try{
            ZipFile z=new ZipFile(f);
            try{Enumeration<? extends ZipEntry> e=z.entries();while(e.hasMoreElements())e.nextElement();return z;}
            catch(IllegalArgumentException ex){z.close();}
        }catch(ZipException ignored){}
        return new ZipFile(f,java.nio.charset.Charset.forName("CP437"));
    }
    public static String gameRoot(File f)throws Exception{
        Set<String> roots=new LinkedHashSet<>(),names=new HashSet<>();
        entries(f,false,(name,size,in)->{
            if(!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Nombres duplicados en la descarga");
            String base=name.substring(name.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
            if(base.matches("game\\.(ini|exe|rgssad|rgss2a|rgss3a)")||base.equals("mkxp.json")){
                if(!name.toLowerCase(Locale.ROOT).contains("poke_updater/"))roots.add(name.substring(0,name.length()-base.length()));
            }
        });
        if(roots.isEmpty())throw new IOException("No se encontró un juego RPG Maker");
        if(roots.size()!=1)throw new IOException("La descarga contiene varios juegos. Extrae y selecciona uno.");
        return roots.iterator().next();
    }
    /** Conversion uses level 0 to avoid recompressing gigabytes on the handheld. */
    public static File zip(File source,File work,GameUpdater.Progress progress)throws Exception{
        if(format(source)==0)return source;
        File dest=new File(work,"expanded.zip");
        try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(dest)))){
            out.setLevel(0);Set<String> seen=new HashSet<>();long[] total={0};
            entries(source,true,(name,size,in)->{
                if(!seen.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Nombres duplicados");
                if(size<0||size>MAX_BYTES)throw new IOException("Tamaño no permitido");
                out.putNextEntry(new ZipEntry(name));
                byte[] buffer=new byte[65536];int n;long written=0;
                while((n=in.read(buffer))!=-1){
                    if(progress.cancelled())throw new IOException("Instalación cancelada");
                    total[0]+=n;written+=n;
                    if(total[0]>MAX_BYTES||written>size)throw new IOException("La descarga supera el tamaño permitido");
                    if(dest.getUsableSpace()<32L*1024*1024)throw new IOException("No queda espacio para preparar la descarga");
                    out.write(buffer,0,n);
                }
                if(written!=size)throw new IOException("Archivo incompleto: "+name);
                out.closeEntry();progress.update(1,-1,"Preparando "+name);
            });
        }catch(LinkageError e){throw new IOException("RAR y 7z necesitan Android 8 o posterior. Extrae la descarga con un gestor de archivos.",e);}
        return dest;
    }
}
