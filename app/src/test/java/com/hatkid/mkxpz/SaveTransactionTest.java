package com.hatkid.mkxpz;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class SaveTransactionTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void restoreKeepsPreviousSave()throws Exception{
        File game=temp.newFolder("game"),backups=temp.newFolder("backups");
        Files.write(new File(game,"Game.rxdata").toPath(),new byte[]{1,2});
        assertEquals(1,SaveTransaction.restore(game,backups,Collections.singletonMap("Game.rxdata",new byte[]{3,4})));
        assertArrayEquals(new byte[]{3,4},Files.readAllBytes(new File(game,"Game.rxdata").toPath()));
        File[] journals=backups.listFiles();assertEquals(1,journals.length);
        assertArrayEquals(new byte[]{1,2},Files.readAllBytes(new File(journals[0],"Game.rxdata").toPath()));
    }
    @Test public void validatesWholeBackupBeforeWriting()throws Exception{
        File game=temp.newFolder("game"),backups=temp.newFolder("backups");
        File save=new File(game,"Game.rxdata");Files.write(save.toPath(),new byte[]{1});
        Map<String,byte[]> payload=new LinkedHashMap<>();payload.put("Game.rxdata",new byte[]{2});payload.put("../outside.rxdata",new byte[]{9});
        try{SaveTransaction.restore(game,backups,payload);fail();}catch(IOException expected){}
        assertArrayEquals(new byte[]{1},Files.readAllBytes(save.toPath()));assertEquals(0,backups.list().length);
    }
    @Test public void rejectsCaseCollisions()throws Exception{
        File game=temp.newFolder("game");Map<String,byte[]> payload=new LinkedHashMap<>();
        payload.put("Game.rxdata",new byte[0]);payload.put("game.rxdata",new byte[0]);
        try{SaveTransaction.restore(game,temp.newFolder("backups"),payload);fail();}catch(IOException expected){}
        assertEquals(0,game.list().length);
    }
    @Test public void oversizedReadIsBounded()throws Exception{
        try{SaveTransaction.read(new ByteArrayInputStream(new byte[33]),32);fail();}catch(IOException expected){}
    }
    @Test public void rejectsSiblingSymlink()throws Exception{
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        File dir=temp.newFolder("saves"),real=new File(dir,"Real.rxdata");Files.write(real.toPath(),new byte[]{1});
        Files.createSymbolicLink(new File(dir,"Game.rxdata").toPath(),real.toPath());
        try{SaveTransaction.child(dir,"Game.rxdata");fail();}catch(IOException expected){}
        assertArrayEquals(new byte[]{1},Files.readAllBytes(real.toPath()));
    }
}
