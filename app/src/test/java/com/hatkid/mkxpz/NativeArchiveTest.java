package com.hatkid.mkxpz;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import static org.junit.Assert.*;

/** Real upstream RAR5 samples, read through the same JNI bridge shipped to Android. */
public class NativeArchiveTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private File fixture(String name)throws Exception{
        File f=temp.newFile(name);
        try(InputStream in=getClass().getResourceAsStream("/archives/"+name);OutputStream out=new FileOutputStream(f)){
            assertNotNull("Run tools/prepare_archive_fixtures.py before JVM tests",in);
            out.write(SaveTransaction.read(in,1024*1024));
        }return f;
    }
    @Test public void storedRar5ContentIsExact()throws Exception{
        try(NativeArchive rar=new NativeArchive(fixture("test_read_format_rar5_stored.rar"))){
            assertEquals("helloworld.txt",rar.next());
            assertEquals("hello libarchive test suite!\n",new String(SaveTransaction.read(rar.stream(),1024),"UTF-8"));
            assertNull(rar.next());
        }
    }
    @Test public void compressedRar5IsDecoded()throws Exception{
        try(NativeArchive rar=new NativeArchive(fixture("test_read_format_rar5_compressed.rar"))){
            assertEquals("test.bin",rar.next());assertEquals(1200,rar.size());
            assertEquals(1200,SaveTransaction.read(rar.stream(),2048).length);assertNull(rar.next());
        }
    }
    @Test public void corruptRarDoesNotAppearAsEmptySuccess()throws Exception{
        File f=temp.newFile("invalid.rar");try(OutputStream out=new FileOutputStream(f)){out.write(new byte[]{'R','a','r','!',0x1a,0x07,0x01,0,1,2,3});}
        try(NativeArchive rar=new NativeArchive(f)){rar.next();fail("Corrupt archive accepted");}catch(IOException expected){}
    }
}
