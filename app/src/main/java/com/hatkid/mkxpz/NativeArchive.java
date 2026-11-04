package com.hatkid.mkxpz;

import java.io.*;

/** Libarchive RAR4/RAR5 reader; the native code never writes files. */
final class NativeArchive implements Closeable {
    static {System.loadLibrary("essentia-archive");}
    private long handle;
    NativeArchive(File file)throws IOException{handle=open(file.getAbsolutePath().getBytes("UTF-8"));}
    String next()throws IOException{byte[] bytes=next(handle);return bytes==null?null:new String(bytes,"UTF-8");}
    long size(){return size(handle);}
    InputStream stream(){return new InputStream(){
        public int read()throws IOException{byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}
        public int read(byte[] b,int o,int n)throws IOException{return NativeArchive.read(handle,b,o,Math.min(n,65536));}
    };}
    @Override public void close(){if(handle!=0){close(handle);handle=0;}}
    private static native long open(byte[] file)throws IOException;
    private static native byte[] next(long handle)throws IOException;
    private static native long size(long handle);
    private static native int read(long handle,byte[] bytes,int offset,int length)throws IOException;
    private static native void close(long handle);
}
