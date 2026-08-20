package com.v7.quantumfast;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    View mainRoot;
    RecyclerView rvSugg, rvFav, rvFolders;
    EditText searchApps, searchWeb;
    SharedPreferences prefs, glassPrefs;
    String[] dockKeys={"dock_phone","dock_msg","dock_extra","dock_drawer","dock_cam","dock_chrome"};
    String[] defaultPkgs={"com.android.dialer","com.google.android.apps.messaging","com.android.settings","com.v7.quantumfast","com.android.camera2","com.android.chrome"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs=getSharedPreferences("dock",0);
        glassPrefs=getSharedPreferences("glass",0);
        mainRoot=findViewById(R.id.root);
        rvSugg=findViewById(R.id.rvSuggestions);
        rvFav=findViewById(R.id.rvFavorites);
        rvFolders=findViewById(R.id.rvFolders);
        searchApps=findViewById(R.id.searchAppsMain);
        searchWeb=findViewById(R.id.searchWebMain);
        View clear=findViewById(R.id.clearApps);
        if(clear!=null) clear.setOnClickListener(v->searchApps.setText(""));
        findViewById(R.id.btnWebGo).setOnClickListener(v->{ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); });
        findViewById(R.id.btnAddFav).setOnClickListener(v->showAddFavDialog());
        findViewById(R.id.btnAddFolder).setOnClickListener(v->showCreateFolderDialog());
        findViewById(R.id.btnMenu).setOnClickListener(v->showGlassMenu());
        setupAtAGlanceSimple();
        setupDock();
        loadUsage();
        int savedCol=glassPrefs.getInt("glass_color",0);
        if(savedCol!=0) applyGlassTheme(savedCol);
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
            if(mainRoot!=null) mainRoot.postDelayed(this,30000);
        }};
        r.run();
    }

    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){
            final int idx=i;
            View vv=findViewById(ids[i]);
            if(vv==null) continue;
            ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((android.widget.FrameLayout)vv).getChildAt(0);
            String saved=prefs.getString(dockKeys[idx], defaultPkgs[idx]);
            String pkg=findRealPkg(saved);
            if(pkg==null) pkg=saved;
            updateDockIcon(iv,pkg,saved);
            if(idx==3){ vv.setOnClickListener(v->openDrawerWithQuery("")); }
            else { vv.setOnClickListener(v->{ String rs=prefs.getString(dockKeys[idx], defaultPkgs[idx]); String rp=findRealPkg(rs); if(rp==null) rp=rs; launch(rp); }); }
            vv.setOnLongClickListener(v->{ pickDockApp(idx); return true; });
        }
    }

    String findRealPkg(String pkg){
        if(pkg==null) return null;
        try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return pkg; }
    }

    void updateDockIcon(ImageView iv, String pkg, String name){
        try{ Drawable d=getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager()); iv.setImageDrawable(d); }catch(Exception e){
            iv.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    void launch(String pkg){ try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it);} }catch(Exception e){} }
    void openDrawerWithQuery(String q){ searchApps.setText(q); }
    void pickDockApp(int idx){
        Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(it,0);
        String[] names=new String[apps.size()]; String[] pkgs=new String[apps.size()];
        for(int i=0;i<apps.size();i++){ names[i]=apps.get(i).loadLabel(getPackageManager()).toString(); pkgs[i]=apps.get(i).activityInfo.packageName; }
        new AlertDialog.Builder(this).setTitle("Choisir app dock").setItems(names,(d,w)->{ prefs.edit().putString(dockKeys[idx],pkgs[w]).apply(); setupDock(); }).show();
    }
    void showAddFavDialog(){ Toast.makeText(this,"Fav ajouté",Toast.LENGTH_SHORT).show(); }
    void showCreateFolderDialog(){ Toast.makeText(this,"Folder créé",Toast.LENGTH_SHORT).show(); }

    void showGlassMenu(){ showPaletteDialog(); }

    void showPaletteDialog(){
        int[] allColors={
            0xFFFFCDD2,0xFFEF9A9A,0xFFE57373,0xFFEF5350,0xFFF44336,0xFFD32F2F,
            0xFFF8BBD0,0xFFF48FB1,0xFFF06292,0xFFEC407A,0xFFE91E63,0xFFC2185B,
            0xFFE1BEE7,0xFFCE93D8,0xFFBA68C8,0xFFAB47BC,0xFF9C27B0,0xFF7B1FA2,
            0xFFD1C4E9,0xFFB39DDB,0xFF9575CD,0xFF7E57C2,0xFF673AB7,0xFF512DA8,
            0xFFC5CAE9,0xFF9FA8DA,0xFF7986CB,0xFF5C6BC0,0xFF3F51B5,0xFF303F9F,
            0xFFBBDEFB,0xFF90CAF9,0xFF64B5F6,0xFF42A5F5,0xFF2196F3,0xFF1976D2,
            0xFFB2EBF2,0xFF80DEEA,0xFF4DD0E1,0xFF26C6DA,0xFF00BCD4,0xFF0097A7,
            0xFFC8E6C9,0xFFA5D6A7,0xFF81C784,0xFF66BB6A,0xFF4CAF50,0xFF388E3C,
            0xFFDCEDC8,0xFFC5E1A5,0xFFAED581,0xFF9CCC65,0xFF8BC34A,0xFF689F38,
            0xFFFFF9C4,0xFFFFF59D,0xFFFFEE58,0xFFFFEB3B,0xFFFBC02D,0xFFF57F17,
            0xFFFFE0B2,0xFFFFCC80,0xFFFFB74D,0xFFFFA726,0xFFFF9800,0xFFEF6C00,
            0xFFFFD4E7,0xFF9B8EC4,0xFFFFD87A,0xFF7AB8FF,0xFF7AFFB8,0xFF1A1A1A
        };
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle("Palette Glass - toutes les couleurs");
        android.widget.GridLayout grid=new android.widget.GridLayout(this);
        grid.setColumnCount(6); grid.setPadding(24,24,24,24);
        for(int col:allColors){
            View v=new View(this);
            android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams();
            lp.width=110; lp.height=110; lp.setMargins(12,12,12,12);
            v.setLayoutParams(lp); v.setBackgroundColor(col);
            v.setOnClickListener(view->{ glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); });
            grid.addView(v);
        }
        android.widget.ScrollView sv=new android.widget.ScrollView(this); sv.addView(grid);
        b.setView(sv);
        b.setPositiveButton("HEX custom",(d,w)->{
            EditText et=new EditText(this); et.setHint("#FFD4E7");
            new AlertDialog.Builder(this).setTitle("Couleur HEX").setView(et)
          .setPositiveButton("OK",(dd,ww)->{ try{ int c=Color.parseColor(et.getText().toString().trim()); glassPrefs.edit().putInt("glass_color",c).apply(); applyGlassTheme(c);}catch(Exception e){ Toast.makeText(this,"HEX invalide",Toast.LENGTH_SHORT).show(); } }).show();
        });
        b.setNegativeButton("Fermer",null); b.show();
    }

    void applyGlassTheme(int col){
        try{
            int[] bars={R.id.searchAppsMain,R.id.searchWebMain};
            for(int id:bars){ View v=findViewById(id); if(v!=null && v.getBackground()!=null){ v.getBackground().setColorFilter(col, PorterDuff.Mode.SRC_ATOP); v.setAlpha(0.9f); } }
        }catch(Exception e){}
    }

    void showBrowserChooserGlass(String q){ Toast.makeText(this,"Web: "+q,Toast.LENGTH_SHORT).show(); }
    void loadUsage(){}
}
