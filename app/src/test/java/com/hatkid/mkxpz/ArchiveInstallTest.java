package com.hatkid.mkxpz;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.sevenz.*;
import static org.junit.Assert.*;

public class ArchiveInstallTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final GameUpdater.Progress progress=new GameUpdater.Progress(){public void update(int s,float f,String d){}public boolean cancelled(){return false;}};
    private File zip(String... names)throws Exception{
        File f=temp.newFile("download-"+UUID.randomUUID()+".zip");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(f))){for(String name:names){out.putNextEntry(new ZipEntry(name));out.write(name.getBytes("UTF-8"));out.closeEntry();}}return f;
    }
    @Test public void installationPreservesSavesAndCase()throws Exception{
        File game=temp.newFolder("game");File graphics=new File(game,"Graphics");assertTrue(graphics.mkdir());
        Files.write(new File(game,"Game.rxdata").toPath(),new byte[]{7});
        ArchiveInstall.install(zip("Pokemon/Game.ini","Pokemon/graphics/Test.txt","Pokemon/Game.rxdata"),game,Collections.emptyList(),progress);
        assertTrue(new File(graphics,"Test.txt").isFile());assertFalse(new File(game,"graphics").exists());
        assertArrayEquals(new byte[]{7},Files.readAllBytes(new File(game,"Game.rxdata").toPath()));
    }
    @Test public void traversalDoesNotWriteAnything()throws Exception{
        File game=temp.newFolder("game");
        try{ArchiveInstall.install(zip("Game.ini","../escape.txt"),game,Collections.emptyList(),progress);fail();}catch(IOException expected){}
        assertEquals(0,game.list().length);assertFalse(new File(temp.getRoot(),"escape.txt").exists());
    }
    @Test public void cancellationLeavesOldGameUntouched()throws Exception{
        File game=temp.newFolder("game");Files.write(new File(game,"Game.ini").toPath(),new byte[]{5});
        GameUpdater.Progress cancel=new GameUpdater.Progress(){public void update(int s,float f,String d){}public boolean cancelled(){return true;}};
        try{ArchiveInstall.install(zip("Game.ini","Data/file.txt"),game,Collections.emptyList(),cancel);fail();}catch(IOException expected){}
        assertArrayEquals(new byte[]{5},Files.readAllBytes(new File(game,"Game.ini").toPath()));assertFalse(new File(game,"Data").exists());
    }
    @Test public void multipleGamesAreRejected()throws Exception{
        try{GameArchives.gameRoot(zip("A/Game.ini","B/Game.ini"));fail();}catch(IOException expected){}
    }
    @Test public void caseDuplicateIsRejected()throws Exception{
        try{GameArchives.gameRoot(zip("Game.ini","game.ini"));fail();}catch(IOException expected){}
    }
    @Test public void unsafeArchivePathsRejected()throws Exception{
        for(String name:new String[]{"/Game.ini","C:/Game.ini","../Game.ini","a/../../Game.ini","a/./Game.ini","a\\..\\Game.ini"}){
            try{GameArchives.path(name);fail(name);}catch(IOException expected){}
        }
    }
    @Test public void interruptedCommitRestoresOldAndRemovesNew()throws Exception{
        File game=temp.newFolder("game"),work=temp.newFolder(".essentia-stage-test");
        Files.write(new File(game,"Game.ini").toPath(),new byte[]{9});Files.write(new File(game,"New.txt").toPath(),new byte[]{9});
        Files.write(new File(work,"old-0").toPath(),new byte[]{1});
        Properties journal=new Properties();journal.setProperty("game",game.getCanonicalPath());journal.setProperty("count","2");
        journal.setProperty("path.0","Game.ini");journal.setProperty("old.0","true");journal.setProperty("path.1","New.txt");journal.setProperty("old.1","false");
        try(OutputStream out=new FileOutputStream(new File(work,"journal"))){journal.store(out,"");}
        Properties marker=new Properties();marker.setProperty("work",work.getAbsolutePath());
        try(OutputStream out=new FileOutputStream(new File(game,".essentia-recovery"))){marker.store(out,"");}
        ArchiveInstall.recover(game);
        assertArrayEquals(new byte[]{1},Files.readAllBytes(new File(game,"Game.ini").toPath()));assertFalse(new File(game,"New.txt").exists());assertFalse(work.exists());
    }
    @Test public void sevenZipConvertsAndInstalls()throws Exception{
        File archive=temp.newFile("game.7z"),text=temp.newFile("Game.ini");Files.write(text.toPath(),new byte[]{1,2,3});
        try(SevenZOutputFile out=new SevenZOutputFile(archive)){
            SevenZArchiveEntry entry=out.createArchiveEntry(text,"Pokemon/Game.ini");out.putArchiveEntry(entry);out.write(new byte[]{1,2,3});out.closeArchiveEntry();
        }
        assertEquals("Pokemon/",GameArchives.gameRoot(archive));
        File work=temp.newFolder("convert"),game=temp.newFolder("game");
        ArchiveInstall.install(GameArchives.zip(archive,work,progress),game,Collections.emptyList(),progress);
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(game,"Game.ini").toPath()));
    }
}
