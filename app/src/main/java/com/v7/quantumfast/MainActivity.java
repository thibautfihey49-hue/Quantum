package com.v7.quantumfast;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.*;
import android.app.Activity;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    RecyclerView rvSugg, rvFav, rvFolders;
    EditText searchApps, searchWeb;
    SharedPreferences prefs;
    String[] dockKeys = {"dock_phone","dock_msg","dock_extra","dock_drawer","dock_cam","dock_chrome"};
    String[] defaultPkgs = {"com.android.dialer","com.google.android.apps.messaging","com.android.settings","com.v7.quantumfast","com.android.camera2","com.android.chrome"};
    LinearLayout main;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs=getSharedPreferences("dock",0);
        main=findViewById(R.id.root);
        rvSugg=findViewById(R.id.rvSuggestions);
        rvFav=findViewById(R.id.rvFavorites);
        rvFolders=findViewById(R.id.rvFolders);
        searchApps=findViewById(R.id.searchAppsMain);
        searchWeb=findViewById(R.id.searchWebMain);
        TextView clear=findViewById(R.id.clearApps);
        if(clear!=null) clear.setOnClickListener(v->searchApps.setText(""));
        findViewById(R.id.btnWebGo).setOnClickListener(v->{ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); });
        findViewById(R.id.btnAddFav).setOnClickListener(v->showAddFavDialog());
        findViewById(R.id.btnAddFolder).setOnClickListener(v->showCreateFolderDialog());
        findViewById(R.id.btnMenu).setOnClickListener(v->showGlassMenu());
        setupAtAGlanceSimple();
        setupDock();
        loadUsage();
    }

    void setupAtAGlanceSimple(){
        clock();
        try{
            IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context c, Intent i){ int lvl=i.getIntExtra("level",-1); TextView tv=findViewById(R.id.batteryInfo); if(tv!=null && lvl!=-1) tv.setText("🔋 "+lvl+"%"); } };
            registerReceiver(br,f);
        }catch(Exception e){}
    }

    void clock(){
        TextView c=findViewById(R.id.clock);
        TextView d=findViewById(R.id.date);
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE);
        SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE);
        Runnable r=new Runnable(){public void run(){
            try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){}
            if(main!=null) main.postDelayed(this,30000);
        }};
        r.run();
    }

    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){
            final int idx=i;
            android.view.View vv=findViewById(ids[i]);
            if(vv==null) continue;
            ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((android.widget.FrameLayout)vv).getChildAt(0);
            if(idx==3){ vv.setOnClickListener(v->openDrawerWithQuery("")); continue; }
            String pkg=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx]));
            if(pkg!=null) updateDockIcon(iv, pkg, pkg);
            vv.setOnClickListener(view->{ String rp=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(rp!=null) launch(rp); });
            vv.setOnLongClickListener(view->{ pickDockApp(idx); return true; });
        }
        android.view.View dd=findViewById(R.id.dDrawer);
        if(dd!=null) dd.setOnClickListener(v->openDrawerWithQuery(""));
    }

    String findRealPkg(String pkg){
        if(pkg==null) return null;
        try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return null; }
    }

    void updateDockIcon(ImageView iv, String pkg, String name){
        try{
            Drawable d = HuntrxIconHelper.getThemedIcon(this, pkg, name);
            iv.setImageDrawable(d);
        }catch(Exception e){}
    }

    void launch(String pkg){ try{ startActivity(getPackageManager().getLaunchIntentForPackage(pkg)); }catch(Exception e){} }
    void openDrawerWithQuery(String q){ Toast.makeText(this,"Drawer: "+q,Toast.LENGTH_SHORT).show(); }
    void pickDockApp(int idx){ Toast.makeText(this,"Long press dock "+idx,Toast.LENGTH_SHORT).show(); }
    void showAddFavDialog(){ Toast.makeText(this,"Add Fav HUNTR/X",Toast.LENGTH_SHORT).show(); }
    void showCreateFolderDialog(){ Toast.makeText(this,"Create Folder HUNTR/X",Toast.LENGTH_SHORT).show(); }
    void showGlassMenu(){ Toast.makeText(this,"Menu HUNTR/X",Toast.LENGTH_SHORT).show(); }
    void showBrowserChooserGlass(String q){ Toast.makeText(this,"Web: "+q,Toast.LENGTH_SHORT).show(); }
    void loadUsage(){}
}
