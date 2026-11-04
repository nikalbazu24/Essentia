package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.hatkid.mkxpz.*;

/** One navigation model on phones and handhelds. Tools keep their own back stack. */
public final class AppShell {
    private AppShell() { }
    public static View wrap(Activity a, View content) {
        int selected = a instanceof LibraryActivity ? 0 : a instanceof StoreActivity ? 1 : a instanceof SettingsActivity ? 2 : -1;
        if (selected < 0) {
            FrameLayout tool=new FrameLayout(a);tool.setBackgroundColor(Ui.BG);
            int width=a.getResources().getConfiguration().screenWidthDp>=900?Ui.dp(a,860):ViewGroup.LayoutParams.MATCH_PARENT;
            tool.addView(content,new FrameLayout.LayoutParams(width,-1,Gravity.CENTER_HORIZONTAL));return tool;
        }
        boolean rail = a.getResources().getConfiguration().screenWidthDp >= 740;
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(rail ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackground(Ui.backdrop());
        // Respect cutouts even in immersive mode; navigation stays reachable.
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout()!=null) {
                android.view.DisplayCutout c=insets.getDisplayCutout();
                v.setPadding(c.getSafeInsetLeft(),c.getSafeInsetTop(),c.getSafeInsetRight(),c.getSafeInsetBottom());
            }
            return insets;
        });
        LinearLayout nav = new LinearLayout(a);
        nav.setOrientation(rail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        nav.setGravity(rail?Gravity.TOP|Gravity.CENTER_HORIZONTAL:Gravity.CENTER);
        int p=Ui.dp(a,8); nav.setPadding(p,p,p,p);
        nav.setBackgroundColor(Ui.SURFACE);
        if (rail) {
            TextView brand=Ui.text(a,"E",26,Ui.ACCENT_LIGHT,true);
            brand.setGravity(Gravity.CENTER); brand.setContentDescription("Essentia");
            nav.addView(brand,new LinearLayout.LayoutParams(-1,Ui.dp(a,64)));
        }
        String[] labels={"Biblioteca","Descubrir","Ajustes"};
        int[] icons={R.drawable.ic_e_grid,R.drawable.ic_e_store,R.drawable.ic_e_settings};
        Class<?>[] screens={LibraryActivity.class,StoreActivity.class,SettingsActivity.class};
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
            item.setFocusable(true);item.setClickable(true);item.setSelected(i==selected);
            item.setContentDescription(labels[i]);item.setBackground(Ui.focusable(a,Ui.SURFACE,14));
            ImageView icon=new ImageView(a);icon.setImageResource(icons[i]);icon.setImageTintList(Ui.tint(i==selected?Ui.ACCENT_LIGHT:Ui.TEXT_DIM));
            item.addView(icon,new LinearLayout.LayoutParams(Ui.dp(a,22),Ui.dp(a,22)));
            TextView label=Ui.text(a,labels[i],12,i==selected?Ui.TEXT:Ui.TEXT_DIM,i==selected);label.setGravity(Gravity.CENTER);
            item.addView(label);
            item.setOnClickListener(v->{
                if(index==selected)return;
                Intent intent=new Intent(a,screens[index]);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                a.startActivity(intent);
                if(!(a instanceof LibraryActivity))a.finish();
            });
            LinearLayout.LayoutParams lp=rail?new LinearLayout.LayoutParams(-1,Ui.dp(a,76)):new LinearLayout.LayoutParams(0,Ui.dp(a,56),1f);
            nav.addView(item,lp);
        }
        if(rail)root.addView(nav,new LinearLayout.LayoutParams(Ui.dp(a,100),-1));
        root.addView(content,rail?new LinearLayout.LayoutParams(0,-1,1f):new LinearLayout.LayoutParams(-1,0,1f));
        if(!rail)root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        return root;
    }
}
