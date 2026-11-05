package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.hatkid.mkxpz.*;

/** Guided tour of real controls. Opening it never marks it as completed. */
public final class LibraryTour {
    private static final String KEY="experience_tour_seen";
    private static final int REVISION=1;
    private static final java.util.WeakHashMap<Activity,Dialog> ACTIVE=new java.util.WeakHashMap<>();
    private static final String[] TARGETS = {"tour-brand", "tour-add", "tour-search", "tour-game", "tour-nav-1", "tour-nav-2"};
    private static final int[] TITLES = {R.string.tour_title_0, R.string.tour_title_1, R.string.tour_title_2, R.string.tour_title_3, R.string.tour_title_4, R.string.tour_title_5};
    private static final int[] BODIES = {R.string.tour_body_0, R.string.tour_body_1, R.string.tour_body_2, R.string.tour_body_3, R.string.tour_body_4, R.string.tour_body_5};
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
        Dialog d=new Dialog(a,R.style.EssentiaTourOverlay);ACTIVE.put(a,d);
        FrameLayout stage=new FrameLayout(a);
        Spotlight spotlight=new Spotlight(a);spotlight.setTag("tour-spotlight");stage.addView(spotlight,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout card=new LinearLayout(a);card.setTag("tour-card");card.setOrientation(LinearLayout.VERTICAL);
        int pad=Ui.dp(a,18);card.setPadding(pad,pad,pad,pad);card.setBackground(Ui.rounded(a,Ui.SURFACE,24));card.setElevation(Ui.dp(a,12));
        LinearLayout heading=new LinearLayout(a);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(Ui.brand(a,46),new LinearLayout.LayoutParams(Ui.dp(a,46),Ui.dp(a,46)));
        LinearLayout identity=new LinearLayout(a);identity.setOrientation(LinearLayout.VERTICAL);identity.setPadding(Ui.dp(a,12),0,0,0);
        identity.addView(Ui.text(a,a.getString(R.string.tour_heading),11,Ui.ACCENT_LIGHT,true));TextView count=Ui.text(a,"",14,Ui.TEXT_DIM,false);identity.addView(count);heading.addView(identity);card.addView(heading);
        LinearLayout markers=new LinearLayout(a);markers.setPadding(0,Ui.dp(a,14),0,Ui.dp(a,12));card.addView(markers);
        for(int i=0;i<TARGETS.length;i++){View marker=new View(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(a,4),1);lp.rightMargin=Ui.dp(a,4);markers.addView(marker,lp);}
        TextView title=Ui.text(a,"",24,Ui.TEXT,true);card.addView(title);
        ScrollView scroll=new ScrollView(a);TextView text=Ui.text(a,"",15,Ui.TEXT_DIM,false);text.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,12));scroll.addView(text);
        LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1);card.addView(scroll,bodyLp);
        LinearLayout buttons=new LinearLayout(a);Button previous=Ui.button(a,a.getString(R.string.tour_back),false);Button next=Ui.button(a,a.getString(R.string.tour_next),true);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(a,48),1);bp.rightMargin=Ui.dp(a,8);buttons.addView(previous,bp);buttons.addView(next,new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));card.addView(buttons);
        LinearLayout links=new LinearLayout(a);Button skip=Ui.button(a,a.getString(R.string.tour_skip),false);Button guide=Ui.button(a,a.getString(R.string.tour_guide),false,R.drawable.ic_e_help);
        links.addView(skip,new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));links.addView(guide,new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));
        LinearLayout.LayoutParams linksLp=new LinearLayout.LayoutParams(-1,-2);linksLp.topMargin=Ui.dp(a,6);card.addView(links,linksLp);
        android.util.DisplayMetrics metrics=a.getResources().getDisplayMetrics();
        FrameLayout.LayoutParams cardLp=new FrameLayout.LayoutParams(Math.min(Ui.dp(a,500),metrics.widthPixels-Ui.dp(a,24)),Math.min(Ui.dp(a,420),metrics.heightPixels-Ui.dp(a,32)),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        cardLp.setMargins(Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16));stage.addView(card,cardLp);
        final int[] page={0};
        Runnable place=()->{
            View target=a.findViewById(android.R.id.content).findViewWithTag(TARGETS[page[0]]);
            if(target==null||!target.isShown())target=a.findViewById(android.R.id.content).findViewWithTag("tour-add");
            spotlight.target=target;spotlight.invalidate();
            int w=stage.getWidth(),h=stage.getHeight();if(w<=0||h<=0)return;
            int[] xy=new int[2],origin=new int[2];stage.getLocationOnScreen(origin);if(target!=null)target.getLocationOnScreen(xy);
            int targetX=xy[0]-origin[0]+(target==null?0:target.getWidth()/2);
            int targetY=xy[1]-origin[1]+(target==null?0:target.getHeight()/2);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)card.getLayoutParams();
            boolean landscape=w>h*1.3f;lp.width=landscape?Math.min(Ui.dp(a,410),w/2):Math.min(Ui.dp(a,500),w-Ui.dp(a,24));
            lp.height=Math.min(Ui.dp(a,420),h-Ui.dp(a,32));
            lp.gravity=landscape?(targetX>w/2?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL
                :Gravity.CENTER_HORIZONTAL|(targetY>h/2?Gravity.TOP:Gravity.BOTTOM);
            lp.setMargins(Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16));card.setLayoutParams(lp);
        };
        Runnable render=()->{
            title.setText(a.getString(TITLES[page[0]]));text.setText(a.getString(BODIES[page[0]]));scroll.scrollTo(0,0);
            count.setText(a.getString(R.string.tour_step, page[0]+1, TARGETS.length));previous.setEnabled(page[0]>0);previous.setAlpha(page[0]>0?1:.4f);
            next.setText(page[0]==TARGETS.length-1?a.getString(R.string.tour_finish):a.getString(R.string.tour_next));
            for(int i=0;i<markers.getChildCount();i++)markers.getChildAt(i).setBackground(Ui.rounded(a,i<=page[0]?Ui.ACCENT:Ui.SURFACE_HI,3));
            place.run();title.announceForAccessibility(a.getString(TITLES[page[0]]));
        };
        Runnable finish=()->{a.getSharedPreferences(EssentiaSettings.PREFS,0).edit().putInt(KEY,REVISION).apply();d.dismiss();};
        previous.setOnClickListener(v->{if(page[0]>0){page[0]--;render.run();}});
        next.setOnClickListener(v->{if(page[0]==TARGETS.length-1)finish.run();else{page[0]++;render.run();}});
        skip.setOnClickListener(v->finish.run());
        guide.setOnClickListener(v->{d.dismiss();a.startActivity(new Intent(a,GuideActivity.class));});
        d.setOnKeyListener((dialog,key,e)->{
            boolean handled=key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_B||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
            if(!handled)return false;if(e.getAction()!=KeyEvent.ACTION_UP)return true;
            if(key==KeyEvent.KEYCODE_BUTTON_B)finish.run();
            else if(key==KeyEvent.KEYCODE_DPAD_LEFT)previous.performClick();
            else if(key==KeyEvent.KEYCODE_BUTTON_A){View focused=d.getCurrentFocus();if(focused!=null&&focused.isEnabled()&&focused.isClickable())focused.performClick();else next.performClick();}
            else next.performClick();return true;
        });
        d.setOnDismissListener(dialog->{if(ACTIVE.get(a)==d)ACTIVE.remove(a);});d.setCanceledOnTouchOutside(false);d.setOnCancelListener(dialog->finish.run());
        d.setContentView(stage);Window window=d.getWindow();
        // The first frame must allow composition with the library's existing surface.
        window.setBackgroundDrawableResource(android.R.color.transparent);window.setFormat(PixelFormat.TRANSLUCENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0f);window.setLayout(-1,-1);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        d.show();window.setLayout(-1,-1);
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)place.run();});
        render.run();stage.post(place);next.requestFocus();return d;
    }
    private static final class Spotlight extends View{
        View target;final Paint paint=new Paint(3);
        Spotlight(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            paint.setColor(0x70100A27);Path mask=new Path();mask.setFillType(Path.FillType.EVEN_ODD);mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);
            if(target!=null&&target.isShown()){
                int[] p=new int[2],own=new int[2];target.getLocationOnScreen(p);getLocationOnScreen(own);float pad=Ui.dp(getContext(),4);
                RectF rect=new RectF(p[0]-own[0]-pad,p[1]-own[1]-pad,p[0]-own[0]+target.getWidth()+pad,p[1]-own[1]+target.getHeight()+pad);
                mask.addRoundRect(rect,Ui.dp(getContext(),18),Ui.dp(getContext(),18),Path.Direction.CW);canvas.drawPath(mask,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setColor(0xFF8AE8D0);canvas.drawRoundRect(rect,Ui.dp(getContext(),18),Ui.dp(getContext(),18),paint);paint.setStyle(Paint.Style.FILL);
            }else canvas.drawPath(mask,paint);
        }
    }
}
