package com.hatkid.mkxpz;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AndroidToolsTest {
    @Before public void clean(){RuntimeEnvironment.getApplication().getSharedPreferences(EssentiaSettings.PREFS,0).edit().clear().commit();}
    private Activity controls(String path){
        Intent i=new Intent(RuntimeEnvironment.getApplication(),ControlsActivity.class);
        if(path!=null)i.putExtra(MainActivity.EXTRA_GAME_PATH,path);
        return Robolectric.buildActivity(ControlsActivity.class,i).setup().get();
    }
    @Test public void gameOverridesDoNotChangeGeneralOrOtherGames(){
        Activity general=controls(null);ButtonMap.load(general);ButtonMap.set(general,KeyEvent.KEYCODE_BUTTON_A,"esc");
        Activity one=controls("/games/one");ButtonMap.load(one);assertEquals("esc",ButtonMap.forButton(KeyEvent.KEYCODE_BUTTON_A).id);
        ButtonMap.set(one,KeyEvent.KEYCODE_BUTTON_A,"shift");
        Activity two=controls("/games/two");ButtonMap.load(two);assertEquals("esc",ButtonMap.forButton(KeyEvent.KEYCODE_BUTTON_A).id);
        ButtonMap.load(general);assertEquals("esc",ButtonMap.forButton(KeyEvent.KEYCODE_BUTTON_A).id);
        ButtonMap.load(one);assertEquals("shift",ButtonMap.forButton(KeyEvent.KEYCODE_BUTTON_A).id);
        ButtonMap.reset(one);assertEquals("esc",ButtonMap.forButton(KeyEvent.KEYCODE_BUTTON_A).id);
    }
    @Test public void offlineGuideStartsOnPhoneAndHasRecoveryHelp(){
        Activity guide=Robolectric.buildActivity(GuideActivity.class).setup().get();
        String text=text(guide.getWindow().getDecorView());
        assertTrue(text.contains("Essentia by Díptico"));assertTrue(text.contains("Cuida tus partidas"));assertTrue(text.contains("Thor"));
    }
    @Test @Config(qualifiers="w720dp-h480dp-land") public void guideAlsoStartsInLandscape(){
        Activity guide=Robolectric.buildActivity(GuideActivity.class).setup().get();assertTrue(text(guide.getWindow().getDecorView()).contains("Abrir tienda"));
    }
    @Test public void optionalAiStartsDisabledWithoutCreatingAKey(){
        RuntimeEnvironment.getApplication().getSharedPreferences("optional_ai",0).edit().clear().commit();
        Activity ai=Robolectric.buildActivity(AiTranslationActivity.class).setup().get();
        assertFalse(AiTranslation.enabled(ai));assertFalse(AiTranslation.prefs(ai).contains("key"));
        assertTrue(text(ai.getWindow().getDecorView()).contains("desactivada por defecto"));
    }
    @Test public void writableProbeDoesNotDeleteAnExistingUserFile()throws Exception{
        Activity a=controls(null);
        java.io.File folder=new java.io.File(a.getCacheDir(),"probe-"+java.util.UUID.randomUUID());assertTrue(folder.mkdir());
        java.io.File existing=new java.io.File(folder,".essentia_write_test");
        java.nio.file.Files.write(existing.toPath(),new byte[]{1,2,3});
        Saves.dirFor(a,folder,"Demo");
        assertArrayEquals(new byte[]{1,2,3},java.nio.file.Files.readAllBytes(existing.toPath()));
    }
    @Test public void importingSaveKeepsPreviousBytes()throws Exception{
        Activity a=controls(null);java.io.File base=new java.io.File(a.getCacheDir(),"import-"+java.util.UUID.randomUUID());assertTrue(base.mkdir());
        java.io.File source=new java.io.File(base,"source"),target=new java.io.File(base,"target");assertTrue(source.mkdir());assertTrue(target.mkdir());
        java.io.File incoming=new java.io.File(source,"Game.rxdata"),old=new java.io.File(target,"Game.rxdata");
        java.nio.file.Files.write(incoming.toPath(),new byte[]{4,5});java.nio.file.Files.write(old.toPath(),new byte[]{1,2});
        assertEquals(1,Saves.copyFiles(java.util.Collections.singletonList(incoming),target));
        assertArrayEquals(new byte[]{4,5},java.nio.file.Files.readAllBytes(old.toPath()));
        java.io.File root=new java.io.File(android.os.Environment.getExternalStorageDirectory(),"Essentia/Respaldos/target");
        boolean found=false;for(java.io.File journal:root.listFiles()){
            java.io.File previous=new java.io.File(journal,"Game.rxdata");
            if(previous.isFile()&&java.util.Arrays.equals(new byte[]{1,2},java.nio.file.Files.readAllBytes(previous.toPath())))found=true;
        }assertTrue(found);
    }
    private String text(View view){
        StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText()).append('\n');
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(text(((ViewGroup)view).getChildAt(i)));return out.toString();
    }
}
