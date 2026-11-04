package com.hatkid.mkxpz;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.hatkid.mkxpz.ui.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Real Android view renders, original fixtures only; never launches native game code. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, shadows={ExperienceTest.OfflineUpdater.class, ExperienceTest.OfflineCatalog.class, ExperienceTest.OfflineCommunity.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExperienceTest {
    @Implements(Updater.class) public static class OfflineUpdater {
        @Implementation public static void check(Activity a,Updater.Done done,boolean manual){done.run();}
    }
    @Implements(Catalog.class) public static class OfflineCatalog {
        @Implementation public static List<Catalog.Game> refresh(Context c,boolean force){return Catalog.cached(c);}
    }
    @Implements(Community.class) public static class OfflineCommunity {
        @Implementation public static void refresh(Context c){}
    }
    private Context app;
    @Before public void setup()throws Exception {
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences(EssentiaSettings.PREFS,0).edit().clear().putBoolean(EssentiaSettings.MUSIC,false)
            .putBoolean(EssentiaSettings.RESCAN,false).putBoolean(EssentiaSettings.AUTO_UPDATE,false).commit();
        app.getSharedPreferences("diagnostics",0).edit().clear().commit();
        java.lang.reflect.Field intro=LibraryActivity.class.getDeclaredField("sIntroShown");intro.setAccessible(true);intro.set(null,true);
    }
    private static String text(View v){
        StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText()).append('\n');
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append(text(((ViewGroup)v).getChildAt(i)));return s.toString();
    }
    private static View label(View v,String label){
        if(v instanceof TextView&&label.equals(((TextView)v).getText().toString()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=label(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private void shot(String name,View v,int w,int h)throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Ui.BG);v.draw(canvas);
        File folder=new File("build/reports/ui");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    @Test public void phoneNavigationAndLibrary()throws Exception {
        RuntimeEnvironment.setQualifiers("es-rES-w390dp-h844dp-port-mdpi");
        org.json.JSONArray entries=new org.json.JSONArray();
        for(int i=0;i<6;i++)entries.put(new org.json.JSONObject().put("path","/fixture/adventure-"+i).put("title",i==0?"Una aventura con un nombre muy largo":"Aventura "+(i+1)));
        app.getSharedPreferences(EssentiaSettings.PREFS,0).edit().putString("library_v1",entries.toString()).commit();
        Activity a=Robolectric.buildActivity(LibraryActivity.class).setup().get();View root=a.findViewById(android.R.id.content);
        assertTrue(text(root).contains("Biblioteca"));assertTrue(text(root).contains("Descubrir"));assertTrue(text(root).contains("Ajustes"));
        shot("phone-library",root,390,844);a.finish();
        RuntimeEnvironment.setQualifiers("es-rES-w960dp-h540dp-land-mdpi");
        a=Robolectric.buildActivity(LibraryActivity.class).setup().get();shot("handheld-library",a.findViewById(android.R.id.content),960,540);a.finish();
    }
    @Test public void settingsPhoneAndHandheld()throws Exception {
        RuntimeEnvironment.setQualifiers("es-rES-w360dp-h800dp-port-mdpi");
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity a=controller.get();shot("phone-settings",a.findViewById(android.R.id.content),360,800);controller.pause().stop().destroy();
        RuntimeEnvironment.setQualifiers("es-rES-w960dp-h540dp-land-mdpi");
        controller=Robolectric.buildActivity(SettingsActivity.class).setup();a=controller.get();
        shot("handheld-settings",a.findViewById(android.R.id.content),960,540);controller.pause().stop().destroy();
    }
    @Test public void discoverAndTools()throws Exception {
        RuntimeEnvironment.setQualifiers("es-rES-w390dp-h844dp-port-mdpi");
        org.robolectric.android.controller.ActivityController<StoreActivity> store=Robolectric.buildActivity(StoreActivity.class).setup();
        shot("phone-discover",store.get().findViewById(android.R.id.content),390,844);store.pause().stop().destroy();
        Activity guide=Robolectric.buildActivity(GuideActivity.class).setup().get();shot("phone-guide",guide.findViewById(android.R.id.content),390,844);guide.finish();
        Activity controls=Robolectric.buildActivity(ControlsActivity.class).setup().get();shot("phone-controls",controls.findViewById(android.R.id.content),390,844);controls.finish();
        Activity ai=Robolectric.buildActivity(AiTranslationActivity.class).setup().get();shot("phone-optional-api",ai.findViewById(android.R.id.content),390,844);ai.finish();
        Activity rtp=Robolectric.buildActivity(RtpActivity.class).setup().get();shot("phone-rtp",rtp.findViewById(android.R.id.content),390,844);rtp.finish();
    }
    @Test public void remainingToolScreensAndSettingsCategoriesRender()throws Exception {
        RuntimeEnvironment.setQualifiers("es-rES-w390dp-h844dp-port-mdpi");
        String path="/fixture/review";
        app.getSharedPreferences(EssentiaSettings.PREFS,0).edit().putString("library_v1",new org.json.JSONArray().put(new org.json.JSONObject().put("path",path).put("title","Aventura original de prueba")).toString()).commit();
        Intent scoped=new Intent(app,GuideActivity.class).putExtra(MainActivity.EXTRA_GAME_PATH,path);
        Activity hub=Robolectric.buildActivity(GuideActivity.class,scoped).setup().get();View root=hub.findViewById(android.R.id.content);
        for(String tab:new String[]{"Partida","Notas","Respaldo","Herramientas"}){label(root,tab).performClick();shot("tools-"+tab,root,390,844);}hub.finish();
        Activity review=Robolectric.buildActivity(TranslationReviewActivity.class,new Intent(app,TranslationReviewActivity.class).putExtra(MainActivity.EXTRA_GAME_PATH,path)).setup().get();
        shot("phone-translation-review",review.findViewById(android.R.id.content),390,844);review.finish();
        Activity report=Robolectric.buildActivity(ReportActivity.class,new Intent(app,ReportActivity.class).putExtra(ReportActivity.EXTRA_PATH,path)).setup().get();
        shot("phone-report",report.findViewById(android.R.id.content),390,844);report.finish();
        Activity settings=Robolectric.buildActivity(SettingsActivity.class).setup().get();root=settings.findViewById(android.R.id.content);
        int[] categories={R.string.cat_screen,R.string.cat_controls,R.string.cat_games,R.string.cat_cloud,R.string.cat_advanced,R.string.cat_about};
        for(int category:categories){View categoryText=label(root,settings.getString(category));View tab=(View)categoryText.getParent();tab.performClick();assertTrue(tab.isSelected());shot("settings-"+category,root,390,844);}settings.finish();
    }
    private static final GamePanel.Keys KEYS=new GamePanel.Keys(){public void down(int k){}public void up(int k){}};
    private static final GamePanel.Actions ACTIONS=new GamePanel.Actions(){
        public void controls(){}public void notes(){}public void progress(){}public void menu(){}public void game(String c){}
        public void turbo(){}public void autoFire(){}public void keyboard(){}
    };
    @Test public void companionHasSameToolsAtBothSizesAndNotesStayPerGame()throws Exception {
        app.getSharedPreferences("essentia_notes",0).edit().putString("/fixture/one","Buscar la siguiente ruta").putString("/fixture/two","Otro objetivo").commit();
        for(int[] size:new int[][]{{390,730},{640,480},{800,300}}){
            RuntimeEnvironment.setQualifiers("es-rES-w"+size[0]+"dp-h"+size[1]+"dp-mdpi");
            Activity a=Robolectric.buildActivity(GuideActivity.class).setup().get();
            CompanionView view=new CompanionView(a,false,"Aventura de prueba","/fixture/one",new File(a.getCacheDir(),"no-live"),KEYS,ACTIONS);a.setContentView(view);
            shot("companion-"+size[0]+"-summary",view,size[0],size[1]);
            for(String tab:new String[]{"Notas","Mapa","Acciones","Teclas","Ayudas"}){
                View button=label(view,tab);assertNotNull(button);button.performClick();shot("companion-"+size[0]+"-"+tab,view,size[0],size[1]);
            }
            assertTrue(text(view).contains("Equipo y progreso"));
            view.dispose();a.finish();
        }
        assertEquals("Otro objetivo",app.getSharedPreferences("essentia_notes",0).getString("/fixture/two",""));
    }
    @Test public void phoneCompanionDoesNotStealSdlFocusAndNotesSavePerGame()throws Exception {
        Activity a=Robolectric.buildActivity(GuideActivity.class).setup().get();
        Dialog dialog=GamePanel.phone(a,"Prueba","/fixture/notes",new File(a.getCacheDir(),"none"),KEYS,ACTIONS,false,3,false);
        assertTrue((dialog.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
        dialog.dismiss();
        GamePanel.editNotes(a,"/fixture/notes");
        AlertDialog editor=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        EditText input=findEditor(editor.findViewById(android.R.id.content));assertNotNull(input);input.setText("Visitar el siguiente pueblo");
        editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("Visitar el siguiente pueblo",a.getSharedPreferences("essentia_notes",0).getString("/fixture/notes",""));
        assertFalse(a.getSharedPreferences("essentia_notes",0).contains("/fixture/other"));a.finish();
    }
    private EditText findEditor(View v){
        if(v instanceof EditText)return (EditText)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText found=findEditor(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;
    }
    @Test public void referenceThemeAndDarkOptionKeepReadableActions()throws Exception {
        RuntimeEnvironment.setQualifiers("es-rES-w390dp-h844dp-port-mdpi");
        for(String theme:new String[]{"esencia","noche"}) {
            Ui.setTheme(app,theme);
            Button primary=Ui.button(app,"Jugar",true);assertEquals(Ui.contrast(Ui.ACCENT),primary.getCurrentTextColor());
            org.robolectric.android.controller.ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup();
            shot("theme-"+theme,controller.get().findViewById(android.R.id.content),390,844);controller.pause().stop().destroy();
        }
        Ui.setTheme(app,"esencia");
    }
    @Test public void automaticRetryKeepsPreviousErrorWithoutAttributingItToClassic(){
        String path="/fixture/fallback-evidence";
        app.getSharedPreferences("diagnostics",0).edit().putBoolean("enabled",true).commit();
        Diagnostics.start(app,path,"modern","automatic_modern","wide_safe");
        Diagnostics.error(app,path,"Solicitud de motor clásico: fallback-fixture token=secret-fixture");
        Diagnostics.event(app,path,"fallback_requested");
        Diagnostics.start(app,path,"classic","learned_fallback","wide_safe");
        String report=Diagnostics.summary(app,path);
        assertTrue(report.contains("Error del intento anterior"));assertTrue(report.contains("fallback-fixture"));
        assertFalse(report.contains("secret-fixture"));assertTrue(report.contains("learned_fallback"));
        assertFalse(Diagnostics.sessionSummary(app,path).contains("fallback-fixture"));
        assertFalse(Diagnostics.file(app,path,"error").exists());
    }
    @Test public void diagnosticsAreOptInAndPreserveRealEngineDecision()throws Exception {
        String path="/fixture/diagnostics";Diagnostics.file(app,path,"session").delete();
        Diagnostics.start(app,path,"classic","learned_fallback","wide_safe");assertFalse(Diagnostics.file(app,path,"session").exists());
        app.getSharedPreferences("diagnostics",0).edit().putBoolean("enabled",true).commit();
        Diagnostics.start(app,path,"classic","learned_fallback","wide_safe");Diagnostics.event(app,path,"phase:scripts");
        String summary=Diagnostics.summary(app,path);assertTrue(summary.contains("learned_fallback"));assertTrue(summary.contains("scripts"));assertTrue(summary.contains("wide_safe"));
        Diagnostics.error(app,path,"https://example.org/private token=private-value");assertFalse(Diagnostics.summary(app,path).contains("private-value"));
        Diagnostics.event(app,path,"first_frame");Diagnostics.event(app,path,"user_exit");
        Diagnostics.start(app,path,"modern","user_choice","original");
        assertTrue(Diagnostics.summary(app,path).contains("Sesión anterior"));assertTrue(Diagnostics.sessionSummary(app,path).contains("user_choice"));
        assertFalse(Diagnostics.file(app,path,"error").exists());
    }
}
