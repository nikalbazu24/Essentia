package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import android.widget.EditText;
import java.io.File;

/** Hosts the same companion on the lower display and on single-screen phones. */
public final class GamePanel extends Presentation {
    public interface Keys {void down(int keycode);void up(int keycode);}
    public interface Actions {
        void controls();void notes();void progress();void menu();void game(String command);
        void turbo();void autoFire();void keyboard();
    }
    private final boolean black;private final String title,path;private final File live;
    private final Keys keys;private final Actions actions;private CompanionView content;
    private GamePanel(Context c,Display display,boolean black,String title,String path,File live,Keys keys,Actions actions) {
        super(c,display);this.black=black;this.title=title;this.path=path;this.live=live;this.keys=keys;this.actions=actions;
    }
    public static GamePanel show(Context c,Display current,boolean black,String title,String path,File live,Keys keys,Actions actions) {
        Display other=SecondScreen.findOtherDisplay(c,current);if(other==null)return null;
        try {GamePanel panel=new GamePanel(c,other,black,title,path,live,keys,actions);panel.show();return panel;}
        catch(Exception e){return null;}
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);SecondScreen.notFocusable(this);
        content=new CompanionView(getContext(),black,title,path,live,keys,withNotes(actions,()->editNotes(getContext(),path)));
        setContentView(content);
    }
    @Override protected void onStop(){if(content!=null)content.dispose();super.onStop();}
    public void setTurbo(boolean on,int speed){if(content!=null)content.setTurbo(on,speed);}
    public void setAutoFire(boolean on){if(content!=null)content.setAutoFire(on);}
    public void setToggle(String what,boolean on){if(content!=null)content.setToggle(what,on);}
    public void showMessage(String text){if(content!=null)content.showMessage(text);}
    public static Dialog phone(Activity a,String title,String path,File live,Keys keys,Actions actions,boolean turbo,int speed,boolean autoFire) {
        Dialog dialog=new Dialog(a,android.R.style.Theme_Material_NoActionBar);
        CompanionView view=new CompanionView(a,false,title,path,live,keys,withNotes(actions,()->editNotes(a,path)));
        view.setTag("phone-companion");
        android.widget.LinearLayout root=new android.widget.LinearLayout(a);root.setOrientation(1);root.setBackgroundColor(Ui.BG);
        android.widget.Button close=Ui.button(a,"Volver al juego",false);close.setOnClickListener(v->dialog.dismiss());root.addView(close);
        root.addView(view,new android.widget.LinearLayout.LayoutParams(-1,0,1f));dialog.setContentView(root);
        dialog.setOnDismissListener(d->view.dispose());
        // Touch panel must not pause SDL: actions are processed by the running game loop.
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);dialog.show();
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
        view.setTurbo(turbo,speed);view.setAutoFire(autoFire);return dialog;
    }
    private static Actions withNotes(Actions a,Runnable notes) {
        return new Actions(){
            public void controls(){a.controls();}public void notes(){notes.run();}public void progress(){a.progress();}
            public void menu(){a.menu();}public void game(String c){a.game(c);}public void turbo(){a.turbo();}
            public void autoFire(){a.autoFire();}public void keyboard(){a.keyboard();}
        };
    }
    public static void editNotes(Context c,String path) {
        EditText input=com.hatkid.mkxpz.ui.Ui.input(c);input.setTextColor(Ui.TEXT);input.setHintTextColor(Ui.TEXT_DIM);
        input.setMinLines(5);input.setGravity(android.view.Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(c.getSharedPreferences("essentia_notes",0).getString(path,""));
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle("Mis notas del juego").setView(input)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->c.getSharedPreferences("essentia_notes",0).edit().putString(path,input.getText().toString()).commit()).create();
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.show();
    }
}
