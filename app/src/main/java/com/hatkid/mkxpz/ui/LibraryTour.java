package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.hatkid.mkxpz.*;

/** Guided tour of real controls. Opening it never marks it as completed. */
public final class LibraryTour {
    private static final String KEY="experience_tour_seen";
    private static final int REVISION=1;
    private static final java.util.WeakHashMap<Activity,Dialog> ACTIVE=new java.util.WeakHashMap<>();
    private static final String[][] STEPS={
        {"tour-brand","Tu aventura empieza aquí","Este frasco es Essentia. Biblioteca, Descubrir y Ajustes organizan tus juegos y herramientas."},
        {"tour-add","Trae tus juegos","Añadir abre una carpeta de juego. Si tienes un ZIP, RAR o 7z, la instalación está en Descubrir. Conserva siempre los archivos del creador."},
        {"tour-search","Encuentra lo que quieres jugar","Busca por nombre. Los botones de vista y orden cambian cómo recorres tu biblioteca, con mando o con los dedos."},
        {"tour-game","Todo al lado de tu juego","Jugar abre la aventura seleccionada. Opciones reúne pantalla, controles, partidas y herramientas. Si aún no añadiste juegos, aquí aparecerá el primero."},
        {"tour-nav-1","Descubre nuevas aventuras","Explora las fichas, consulta su procedencia y visita a sus creadores. Las actualizaciones de juegos también están aquí."},
        {"tour-nav-2","Hazlo tuyo","Cambia el tema, los controles y las opciones de pantalla. Durante el juego, el acompañante reúne notas y accesos compatibles, en teléfono o Thor. Puedes repetir este tour desde Biblioteca o Ajustes → Información."}
    };
    private LibraryTour(){}
    public static boolean needed(Context c){return c.getSharedPreferences(EssentiaSettings.PREFS,0).getInt(KEY,0)<REVISION;}
    public static void open(Activity a){
        if(a instanceof LibraryActivity){show(a);return;}
        a.startActivity(new Intent(a,LibraryActivity.class).putExtra("show_experience_tour",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    public static Dialog show(Activity a){
        if(a.isFinishing()||a.isDestroyed())return null;
        Dialog existing=ACTIVE.get(a);if(existing!=null&&existing.isShowing())return existing;
        Dialog d=new Dialog(a,android.R.style.Theme_Material_NoActionBar);ACTIVE.put(a,d);
        FrameLayout stage=new FrameLayout(a);
        Spotlight spotlight=new Spotlight(a,a.findViewById(android.R.id.content));stage.addView(spotlight,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);
        int pad=Ui.dp(a,18);card.setPadding(pad,pad,pad,pad);card.setBackground(Ui.rounded(a,Ui.SURFACE,24));card.setElevation(Ui.dp(a,12));
        LinearLayout heading=new LinearLayout(a);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(Ui.brand(a,46),new LinearLayout.LayoutParams(Ui.dp(a,46),Ui.dp(a,46)));
        LinearLayout identity=new LinearLayout(a);identity.setOrientation(LinearLayout.VERTICAL);identity.setPadding(Ui.dp(a,12),0,0,0);
        identity.addView(Ui.text(a,"CONOCE ESSENTIA",11,Ui.ACCENT_LIGHT,true));TextView count=Ui.text(a,"",14,Ui.TEXT_DIM,false);identity.addView(count);heading.addView(identity);card.addView(heading);
        LinearLayout markers=new LinearLayout(a);markers.setPadding(0,Ui.dp(a,14),0,Ui.dp(a,12));card.addView(markers);
        for(int i=0;i<STEPS.length;i++){View marker=new View(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(a,4),1);lp.rightMargin=Ui.dp(a,4);markers.addView(marker,lp);}
        TextView title=Ui.text(a,"",24,Ui.TEXT,true);card.addView(title);
        ScrollView scroll=new ScrollView(a);TextView text=Ui.text(a,"",15,Ui.TEXT_DIM,false);text.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,12));scroll.addView(text);
        LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1);card.addView(scroll,bodyLp);
        LinearLayout buttons=new LinearLayout(a);Button previous=Ui.button(a,"Atrás",false);Button next=Ui.button(a,"Siguiente",true);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(a,48),1);bp.rightMargin=Ui.dp(a,8);buttons.addView(previous,bp);buttons.addView(next,new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));card.addView(buttons);
        LinearLayout links=new LinearLayout(a);Button skip=Ui.button(a,"Omitir",false);Button guide=Ui.button(a,"Guía completa",false,R.drawable.ic_e_help);
        links.addView(skip,new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));links.addView(guide,new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));
        LinearLayout.LayoutParams linksLp=new LinearLayout.LayoutParams(-1,-2);linksLp.topMargin=Ui.dp(a,6);card.addView(links,linksLp);
        FrameLayout.LayoutParams cardLp=new FrameLayout.LayoutParams(-1,-2);stage.addView(card,cardLp);
        final int[] page={0};
        Runnable place=()->{
            View target=a.findViewById(android.R.id.content).findViewWithTag(STEPS[page[0]][0]);
            if(target==null||!target.isShown())target=a.findViewById(android.R.id.content).findViewWithTag("tour-add");
            spotlight.target=target;spotlight.invalidate();
            int w=stage.getWidth(),h=stage.getHeight();if(w<=0||h<=0)return;
            int[] xy=new int[2];if(target!=null)target.getLocationOnScreen(xy);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)card.getLayoutParams();
            boolean landscape=w>h*1.3f;lp.width=landscape?Math.min(Ui.dp(a,410),w/2):Math.min(Ui.dp(a,500),w-Ui.dp(a,24));
            lp.height=Math.min(Ui.dp(a,420),h-Ui.dp(a,32));
            lp.gravity=landscape?Gravity.END|Gravity.CENTER_VERTICAL:Gravity.CENTER_HORIZONTAL|(xy[1]>h/2?Gravity.TOP:Gravity.BOTTOM);
            lp.setMargins(Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16));card.setLayoutParams(lp);
        };
        Runnable render=()->{
            title.setText(STEPS[page[0]][1]);text.setText(STEPS[page[0]][2]);scroll.scrollTo(0,0);
            count.setText("Paso "+(page[0]+1)+" de "+STEPS.length);previous.setEnabled(page[0]>0);previous.setAlpha(page[0]>0?1:.4f);
            next.setText(page[0]==STEPS.length-1?"Terminar":"Siguiente");
            for(int i=0;i<markers.getChildCount();i++)markers.getChildAt(i).setBackground(Ui.rounded(a,i<=page[0]?Ui.ACCENT:Ui.SURFACE_HI,3));
            place.run();title.announceForAccessibility(STEPS[page[0]][1]);
        };
        Runnable finish=()->{a.getSharedPreferences(EssentiaSettings.PREFS,0).edit().putInt(KEY,REVISION).apply();d.dismiss();};
        previous.setOnClickListener(v->{if(page[0]>0){page[0]--;render.run();}});
        next.setOnClickListener(v->{if(page[0]==STEPS.length-1)finish.run();else{page[0]++;render.run();}});
        skip.setOnClickListener(v->finish.run());
        guide.setOnClickListener(v->{d.dismiss();a.startActivity(new Intent(a,GuideActivity.class));});
        d.setOnKeyListener((dialog,key,e)->{
            boolean handled=key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_B||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
            if(!handled)return false;if(e.getAction()!=KeyEvent.ACTION_UP)return true;
            if(key==KeyEvent.KEYCODE_BUTTON_B)finish.run();else if(key==KeyEvent.KEYCODE_DPAD_LEFT)previous.performClick();else next.performClick();return true;
        });
        d.setOnDismissListener(dialog->ACTIVE.remove(a));d.setCanceledOnTouchOutside(false);d.setOnCancelListener(dialog->finish.run());
        d.setContentView(stage);d.show();Window window=d.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-1);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)place.run();});
        render.run();stage.post(place);next.requestFocus();return d;
    }
    private static final class Spotlight extends View{
        private final View origin;View target;final Paint paint=new Paint(3);
        Spotlight(Context c,View origin){super(c);this.origin=origin;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            paint.setColor(0xDE100A27);Path mask=new Path();mask.setFillType(Path.FillType.EVEN_ODD);mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);
            if(target!=null&&target.isShown()){
                int[] p=new int[2],own=new int[2];target.getLocationOnScreen(p);getLocationOnScreen(own);float pad=Ui.dp(getContext(),4);
                RectF rect=new RectF(p[0]-own[0]-pad,p[1]-own[1]-pad,p[0]-own[0]+target.getWidth()+pad,p[1]-own[1]+target.getHeight()+pad);
                mask.addRoundRect(rect,Ui.dp(getContext(),18),Ui.dp(getContext(),18),Path.Direction.CW);canvas.drawPath(mask,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setColor(0xFF8AE8D0);canvas.drawRoundRect(rect,Ui.dp(getContext(),18),Ui.dp(getContext(),18),paint);paint.setStyle(Paint.Style.FILL);
            }else canvas.drawPath(mask,paint);
        }
    }
}
