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
    @Test @Config(qualifiers="land-w720dp-h480dp") public void guideAlsoStartsInLandscape(){
        Activity guide=Robolectric.buildActivity(GuideActivity.class).setup().get();assertTrue(text(guide.getWindow().getDecorView()).contains("Abrir tienda"));
    }
    @Test public void optionalAiStartsDisabledWithoutCreatingAKey(){
        RuntimeEnvironment.getApplication().getSharedPreferences("optional_ai",0).edit().clear().commit();
        Activity ai=Robolectric.buildActivity(AiTranslationActivity.class).setup().get();
        assertFalse(AiTranslation.enabled(ai));assertFalse(AiTranslation.prefs(ai).contains("key"));
        assertTrue(text(ai.getWindow().getDecorView()).contains("desactivada por defecto"));
    }
    private String text(View view){
        StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText()).append('\n');
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(text(((ViewGroup)view).getChildAt(i)));return out.toString();
    }
}
