package com.v7.quantumfast;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends android.app.Activity {
    View mainRoot;
    EditText searchApps, searchWeb;
    RecyclerView rvSugg;
    SharedPreferences prefs;
    List<ResolveInfo> suggList=new ArrayList<>();
    String[] dockKeys={"dock_phone","dock_msg","dock_extra","dock_drawer","dock_cam","dock_chrome"};
    String[] defaultPkgs={"com.android.dialer","com.google.android.apps.messaging","com.android.settings","com.v7.quantumfast","com.android.camera2","com.android.chrome"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs=getSharedPreferences("dock",0);
        mainRoot=findViewById(R.id.root);
        searchApps=findViewById(R.id.searchAppsMain);
        searchWeb=findViewById(R.id.searchWebMain);
        rvSugg=findViewGlass("rvSuggestions","rvSugg","suggestions");
        try{ View cl=findViewGlass("clearApps","btnClear","clear"); if(cl!=null) cl.setOnClickListener(v->{ if(searchApps!=null) searchApps.setText(""); }); }catch(Exception e){}
        try{ View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setOnClickListener(v->{ if(searchWeb!=null){ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); }}); }catch(Exception e){}
        try{ View fav=findViewGlass("btnAddFav","Fav","fav","addFav"); if(fav!=null) fav.setOnClickListener(v->showAddFavDialog()); }catch(Exception e){}
        try{ View fol=findViewGlass("btnAddFolder","Folder","folder","addFolder"); if(fol!=null) fol.setOnClickListener(v->showCreateFolderDialog()); }catch(Exception e){}
        try{ View men=findViewGlass("btnMenu","Menu","menu"); if(men!=null) men.setOnClickListener(v->showGlassMenu()); }catch(Exception e){}
        setupAtAGlanceSimple();
        setupDock();
        preloadFast();
        int saved=getSharedPreferences("glass",0).getInt("glass_color",0);
        if(saved!=0) applyGlassTheme(saved);
        if(searchApps!=null){
            searchApps.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                public void onTextChanged(CharSequence s,int a,int b,int c){ filterApps(s.toString()); }
                public void afterTextChanged(android.text.Editable s){}
            });
        }
    }
    View findViewGlass(String...names){
        for(String n:names){
            int id=getResources().getIdentifier(n,"id",getPackageName());
            if(id!=0){ View v=findViewById(id); if(v!=null) return v; }
        }
        return null;
    }
    void preloadFast(){ try{ loadWallpaperFast(); }catch(Exception e){} }
    void loadWallpaperFast(){
        try{
            String uriStr=prefs.getString("custom_wallpaper_uri","");
            if(uriStr.isEmpty()) return;
            Uri uri=Uri.parse(uriStr);
            View bgv=findViewGlass("wallpaper","bg","background","wall");
            if(bgv instanceof ImageView) ((ImageView)bgv).setImageURI(uri);
        }catch(Exception e){}
    }
    @Override protected void onActivityResult(int rc,int res,Intent data){
        if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();
            try{ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){}
            prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply();
            loadWallpaperFast();
        }
    }
    void setupAtAGlanceSimple(){
        clock();
        try{
            IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context c, Intent i){
                int lvl=i.getIntExtra("level",-1);
                TextView tv=(TextView)findViewGlass("batteryInfo","battery","bat");
                if(tv!=null && lvl!=-1) tv.setText("\uD83D\uDD0B "+lvl+"%");
            }};
            registerReceiver(br,f);
        }catch(Exception e){}
    }
    void clock(){
        TextView c=(TextView)findViewGlass("clock","time");
        TextView d=(TextView)findViewGlass("date","dateInfo");
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE);
        SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE);
        Runnable r=new Runnable(){public void run(){
            try{
                if(c!=null) c.setText(tf.format(new Date()));
                if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" \u2022 Paris");
            }catch(Exception e){}
            if(mainRoot!=null) mainRoot.postDelayed(this,30000);
        }};
        r.run();
    }
    void filterApps(String q){
        try{
            Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0);
            suggList.clear();
            if(q==null || q.trim().isEmpty()){
                if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter());
                return;
            }
            String lq=q.toLowerCase();
            for(ResolveInfo ri:all){
                String lb=ri.loadLabel(getPackageManager()).toString().toLowerCase();
                if(lb.contains(lq)) suggList.add(ri);
            }
            if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter());
        }catch(Exception e){}
    }
    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){
            final int idx=i;
            View vv=findViewById(ids[i]); if(vv==null) continue;
            ImageView iv;
            if(vv instanceof ImageView) iv=(ImageView)vv;
            else { try{ iv=(ImageView)((android.widget.FrameLayout)vv).getChildAt(0); }catch(Exception e){ continue; } }
            if(idx==3){ vv.setOnClickListener(v->openDrawerWithQuery("")); continue; }
            String saved=prefs.getString(dockKeys[idx], defaultPkgs[idx]);
            String pkg=findRealPkg(saved); if(pkg==null) pkg=saved;
            updateDockIcon(iv,pkg);
            vv.setOnClickListener(v->{
                String rs=prefs.getString(dockKeys[idx], defaultPkgs[idx]);
                String rp=findRealPkg(rs); if(rp==null) rp=rs;
                launch(rp);
            });
            vv.setOnLongClickListener(v->{ pickDockApp(idx); return true; });
        }
        View drawer=findViewById(R.id.dDrawer); if(drawer!=null) drawer.setOnClickListener(v->openDrawerWithQuery(""));
    }
    String findRealPkg(String pkg){ try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return pkg; } }
    void updateDockIcon(ImageView iv, String pkg){ try{ iv.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); }catch(Exception e){ iv.setImageResource(android.R.drawable.sym_def_app_icon); } }
    void launch(String pkg){
        try{ Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); clearSearchNow(); startActivity(ii); return; } }catch(Exception e){}
        try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); clearSearchNow(); startActivity(it);} }catch(Exception e){}
    }
    void openDrawerWithQuery(String q){ if(searchApps!=null) searchApps.setText(q); }
    void clearSearchNow(){ if(searchApps!=null) searchApps.setText(""); suggList.clear(); if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); }
    void pickDockApp(int idx){
        Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(it,0);
        String[] names=new String[apps.size()]; String[] pkgs=new String[apps.size()];
        for(int i=0;i<apps.size();i++){ names[i]=apps.get(i).loadLabel(getPackageManager()).toString(); pkgs[i]=apps.get(i).activityInfo.packageName; }
        new AlertDialog.Builder(this).setTitle("Choisir app dock").setItems(names,(d,w)->{ prefs.edit().putString(dockKeys[idx],pkgs[w]).apply(); setupDock(); }).show();
    }
    void showAddFavDialog(){ Toast.makeText(this,"Fav",0).show(); }
    void showCreateFolderDialog(){ Toast.makeText(this,"Folder",0).show(); }
    void showBrowserChooserGlass(String q){ Toast.makeText(this,q,0).show(); }
    android.graphics.drawable.Drawable createGlassDrawable(int col, float rad, int alpha){
        int fill=Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col));
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setCornerRadius(rad);
        d.setColor(fill);
        d.setStroke((int)(1.2f*getResources().getDisplayMetrics().density), Color.argb(110,255,255,255));
        return d;
    }
    void showPaletteDialog(){
        float dens=getResources().getDisplayMetrics().density;
        int[] pastels={0xFFFFD4E7,0xFFD4E7FF,0xFFC7B6FF,0xFFB6FFE8,0xFFFFF3B6,0xFFFFE0B2};
        int[] all={0xFFFFCDD2,0xFFEF9A9A,0xFFE57373,0xFFEF5350,0xFFF44336,0xFFD32F2F,0xFFF8BBD0,0xFFF48FB1,0xFFF06292,0xFFEC407A,0xFFE91E63,0xFFC2185B,0xFFE1BEE7,0xFFCE93D8,0xFFBA68C8,0xFFAB47BC,0xFF9C27B0,0xFF7B1FA2,0xFFD1C4E9,0xFFB39DDB,0xFF9575CD,0xFF7E57C2,0xFF673AB7,0xFF512DA8,0xFFC5CAE9,0xFF9FA8DA,0xFF7986CB,0xFF5C6BC0,0xFF3F51B5,0xFF303F9F,0xFFBBDEFB,0xFF90CAF9,0xFF64B5F6,0xFF42A5F5,0xFF2196F3,0xFF1976D2,0xFFB2EBF2,0xFF80DEEA,0xFF4DD0E1,0xFF26C6DA,0xFF00BCD4,0xFF0097A7,0xFFC8E6C9,0xFFA5D6A7,0xFF81C784,0xFF66BB6A,0xFF4CAF50,0xFF388E3C,0xFFDCEDC8,0xFFC5E1A5,0xFFAED581,0xFF9CCC65,0xFF8BC34A,0xFF689F38,0xFFFFF9C4,0xFFFFF59D,0xFFFFEE58,0xFFFFEB3B,0xFFFBC02D,0xFFF57F17,0xFFFFE0B2,0xFFFFCC80,0xFFFFB74D,0xFFFFA726,0xFFFF9800,0xFFEF6C00,0xFF1A1A1A};
        AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("Palette Glass");
        android.widget.LinearLayout root=new android.widget.LinearLayout(this); root.setOrientation(android.widget.LinearLayout.VERTICAL); root.setPadding((int)(16*dens),(int)(16*dens),(int)(16*dens),(int)(16*dens));
        TextView preview=new TextView(this); preview.setText(" Apercu verre "); preview.setTextSize(16); preview.setPadding((int)(16*dens),(int)(12*dens),(int)(16*dens),(int)(12*dens)); preview.setBackground(createGlassDrawable(0xFFFFD4E7, 18*dens, 85)); root.addView(preview);
        android.widget.GridLayout gp=new android.widget.GridLayout(this); gp.setColumnCount(6);
        for(int col:pastels){ View v=new View(this); android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(); lp.width=(int)(48*dens); lp.height=(int)(48*dens); lp.setMargins((int)(8*dens),(int)(8*dens),(int)(8*dens),(int)(8*dens)); v.setLayoutParams(lp); v.setBackground(createGlassDrawable(col, 18*dens, 120)); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 18*dens, 85)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); gp.addView(v); }
        root.addView(gp);
        android.widget.GridLayout grid=new android.widget.GridLayout(this); grid.setColumnCount(6);
        for(int col:all){ View v=new View(this); android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(); lp.width=(int)(40*dens); lp.height=(int)(40*dens); lp.setMargins((int)(6*dens),(int)(6*dens),(int)(6*dens),(int)(6*dens)); v.setLayoutParams(lp); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(10*dens); bg.setColor(col); v.setBackground(bg); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 18*dens, 85)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); }
        android.widget.ScrollView sv=new android.widget.ScrollView(this); sv.addView(grid); root.addView(sv);
        b.setView(root);
        b.setPositiveButton("HEX custom",(d,w)->{ EditText et=new EditText(this); et.setHint("#FFD4E7"); new AlertDialog.Builder(this).setTitle("HEX").setView(et).setPositiveButton("OK",(dd,ww)->{ try{ int c=Color.parseColor(et.getText().toString().trim()); getSharedPreferences("glass",0).edit().putInt("glass_color",c).apply(); applyGlassTheme(c);}catch(Exception e){} }).show(); });
        b.setNegativeButton("Fermer",null); b.show();
    }
    void showGlassMenu(){ showPaletteDialog(); }
    void applyGlassTheme(int col){
        try{
            float dens=getResources().getDisplayMetrics().density;
            double lum=(0.299*Color.red(col)+0.587*Color.green(col)+0.114*Color.blue(col))/255;
            int textCol= lum>0.6? 0xFF1A1A1A : 0xFFFFFFFF;
            int hintCol= lum>0.6? 0x661A1A1A : 0x66FFFFFF;
            int[] bars={R.id.searchAppsMain,R.id.searchWebMain};
            for(int id:bars){ View v=findViewById(id); if(v!=null){ v.setBackground(createGlassDrawable(col, 32*dens, 85)); if(v instanceof EditText){ ((EditText)v).setTextColor(textCol); ((EditText)v).setHintTextColor(hintCol);} } }
            View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setBackground(createGlassDrawable(col, 32*dens, 85));
            View dock=findViewGlass("dock","dockBar","dock_container","dockContainer","bottomDock"); if(dock!=null) dock.setBackground(createGlassDrawable(col, 28*dens, 70));
        }catch(Exception e){}
    }
    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{
        class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
        public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
        public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launch(ri.activityInfo.packageName)); }catch(Exception e){} }
        public int getItemCount(){ return suggList.size(); }
    }
}
