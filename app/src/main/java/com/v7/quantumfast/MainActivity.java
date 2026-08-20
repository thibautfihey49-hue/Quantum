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
import androidx.recyclerview.widget.*;
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
        rvSugg=(RecyclerView)findViewGlass("rvSuggestions","rvSugg","suggestions");
        if(rvSugg!=null){ rvSugg.setLayoutManager(new LinearLayoutManager(this)); rvSugg.setAdapter(new SuggAdapter()); }

        try{ View cl=findViewGlass("clearApps","btnClear","clear","clearAppsMain"); if(cl!=null) cl.setOnClickListener(v->{ if(searchApps!=null) searchApps.setText(""); }); }catch(Exception e){}
        try{ View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setOnClickListener(v->{ if(searchWeb!=null){ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); }}); }catch(Exception e){}
        try{ View fav=findViewGlass("btnAddFav","Fav","fav"); if(fav!=null) fav.setOnClickListener(v->showAddFavDialog()); }catch(Exception e){}
        try{ View fol=findViewGlass("btnAddFolder","Folder","folder"); if(fol!=null) fol.setOnClickListener(v->showCreateFolderDialog()); }catch(Exception e){}
        try{ View men=findViewGlass("btnMenu","Menu","menu"); if(men!=null) men.setOnClickListener(v->showGlassMenu()); }catch(Exception e){}

        setupAtAGlanceSimple();
        setupDock();
        preloadFast();

        int saved=getSharedPreferences("glass",0).getInt("glass_color",0);
        if(saved!=0) applyGlassTheme(saved);
        else applyGlassTheme(0xFF8A8A7A); // neutre si rien

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

    // ===== wallpaper =====
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

    // ===== at a glance =====
    void setupAtAGlanceSimple(){
        clock();
        try{
            IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context c, Intent i){
                int lvl=i.getIntExtra("level",-1);
                TextView tv=(TextView)findViewGlass("batteryInfo","battery","bat");
                if(tv!=null && lvl!=-1) tv.setText("🔋 "+lvl+"%");
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
                if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris");
            }catch(Exception e){}
            if(mainRoot!=null) mainRoot.postDelayed(this,30000);
        }};
        r.run();
    }

    // ===== search =====
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
                String pkg=ri.activityInfo.packageName.toLowerCase();
                if(lb.contains(lq) || pkg.contains(lq)) suggList.add(ri);
            }
            if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter());
        }catch(Exception e){}
    }

    // ===== DOCK FIX - plus d'icone android marron =====
    String resolveIntentPkg(Intent intent){
        try{
            List<ResolveInfo> r=getPackageManager().queryIntentActivities(intent,0);
            if(r!=null &&!r.isEmpty()) return r.get(0).activityInfo.packageName;
        }catch(Exception e){}
        return null;
    }
    String getSmartDefault(int idx){
        try{
            if(idx==0){ String p=resolveIntentPkg(new Intent(Intent.ACTION_DIAL)); if(p!=null) return p; }
            if(idx==1){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))); if(p!=null) return p; }
            if(idx==2) return "com.android.settings";
            if(idx==4){ String p=resolveIntentPkg(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); if(p!=null) return p; }
            if(idx==5){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com"))); if(p!=null) return p; }
        }catch(Exception e){}
        return defaultPkgs[idx];
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
            String saved=prefs.getString(dockKeys[idx], null);
            if(saved==null) saved=getSmartDefault(idx);
            String pkg=findRealPkg(saved);
            if(pkg==null) pkg=getSmartDefault(idx);
            updateDockIcon(iv,pkg);
            vv.setOnClickListener(v->{
                String rs=prefs.getString(dockKeys[idx], getSmartDefault(idx));
                String rp=findRealPkg(rs);
                if(rp==null) rp=resolveIntentPkg(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));
                if(rp!=null) launch(rp);
            });
            vv.setOnLongClickListener(v->{ pickDockApp(idx); return true; });
        }
        View drawer=findViewById(R.id.dDrawer); if(drawer!=null) drawer.setOnClickListener(v->openDrawerWithQuery(""));
    }
    String findRealPkg(String pkg){
        if(pkg==null) return null;
        try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return null; }
    }
    void updateDockIcon(ImageView iv, String pkg){
        try{
            if(pkg==null) throw new Exception();
            iv.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager()));
            iv.setBackgroundColor(Color.TRANSPARENT);
        }catch(Exception e){
            iv.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }
    void launch(String pkg){
        try{ Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); clearSearchNow(); startActivity(ii); return; } }catch(Exception e){}
        try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); clearSearchNow(); startActivity(it);} }catch(Exception e){}
    }
    void openDrawerWithQuery(String q){ if(searchApps!=null) searchApps.setText(q); }
    void clearSearchNow(){ if(searchApps!=null) searchApps.setText(""); suggList.clear(); if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); }
    void pickDockApp(int idx){
        Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(it,0);
        Collections.sort(apps, (a,b)-> a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        String[] names=new String[apps.size()]; String[] pkgs=new String[apps.size()];
        for(int i=0;i<apps.size();i++){ names[i]=apps.get(i).loadLabel(getPackageManager()).toString(); pkgs[i]=apps.get(i).activityInfo.packageName; }
        new AlertDialog.Builder(this).setTitle("Choisir app dock").setItems(names,(d,w)->{ prefs.edit().putString(dockKeys[idx],pkgs[w]).apply(); setupDock(); }).show();
    }
    void showAddFavDialog(){ Toast.makeText(this,"Fav",0).show(); }
    void showCreateFolderDialog(){ Toast.makeText(this,"Folder",0).show(); }
    void showBrowserChooserGlass(String q){
        Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse(q.startsWith("http")?q:"https://www.google.com/search?q="+Uri.encode(q)));
        try{ startActivity(i); }catch(Exception e){ Toast.makeText(this,q,0).show(); }
    }

    // ===== GLASS PREMIUM - palette retravaillée =====
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
        int[] pastels={0xFFFFD4E7,0xFFD4E7FF,0xFFC7B6FF,0xFFB6FFE8,0xFFFFF3B6,0xFFFFE0B2,0xFFB2DFDB,0xFFFFCCBC};
        int[] vifs={0xFF2196F3,0xFF00BCD4,0xFF4CAF50,0xFFFFC107,0xFFFF5722,0xFFE91E63,0xFF9C27B0,0xFF3F51B5};
        int[] all={0xFFFFCDD2,0xFFEF9A9A,0xFFE57373,0xFFF44336,0xFFD32F2F,0xFFF8BBD0,0xFFF48FB1,0xFFEC407A,0xFFE91E63,0xFFE1BEE7,0xFFCE93D8,0xFFAB47BC,0xFF9C27B0,0xFFD1C4E9,0xFFB39DDB,0xFF7E57C2,0xFF673AB7,0xFFC5CAE9,0xFF7986CB,0xFF3F51B5,0xFFBBDEFB,0xFF64B5F6,0xFF2196F3,0xFF1976D2,0xFFB2EBF2,0xFF4DD0E1,0xFF00BCD4,0xFFC8E6C9,0xFF81C784,0xFF4CAF50,0xFFDCEDC8,0xFFAED581,0xFF8BC34A,0xFFFFF9C4,0xFFFFEB3B,0xFFFBC02D,0xFFFFE0B2,0xFFFFB74D,0xFFFF9800,0xFF795548,0xFF9E9E9E,0xFF212121,0xFFFFFFFF,0xFF000000};

        AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("Palette Glass • premium");
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(16*dens),(int)(16*dens),(int)(16*dens),(int)(16*dens));

        TextView preview=new TextView(this); preview.setText(" Aperçu verre "); preview.setTextSize(18); preview.setTextColor(Color.WHITE);
        preview.setPadding((int)(16*dens),(int)(14*dens),(int)(16*dens),(int)(14*dens));
        preview.setBackground(createGlassDrawable(0xFF8A8A7A, 24*dens, 75));
        root.addView(preview);

        TextView t1=new TextView(this); t1.setText("Pastels"); t1.setPadding(0,(int)(12*dens),0,(int)(6*dens)); root.addView(t1);
        GridLayout gp=new GridLayout(this); gp.setColumnCount(6);
        for(int col:pastels){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(52*dens); lp.height=(int)(52*dens); lp.setMargins((int)(6*dens),(int)(6*dens),(int)(6*dens),(int)(6*dens)); v.setLayoutParams(lp); v.setBackground(createGlassDrawable(col, 18*dens, 95)); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 24*dens, 75)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); gp.addView(v); }
        root.addView(gp);

        TextView t2=new TextView(this); t2.setText("Vifs"); t2.setPadding(0,(int)(10*dens),0,(int)(6*dens)); root.addView(t2);
        GridLayout gv=new GridLayout(this); gv.setColumnCount(6);
        for(int col:vifs){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(52*dens); lp.height=(int)(52*dens); lp.setMargins((int)(6*dens),(int)(6*dens),(int)(6*dens),(int)(6*dens)); v.setLayoutParams(lp); GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(18*dens); bg.setColor(col); v.setBackground(bg); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 24*dens, 75)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); gv.addView(v); }
        root.addView(gv);

        TextView t3=new TextView(this); t3.setText("Toutes"); t3.setPadding(0,(int)(10*dens),0,(int)(6*dens)); root.addView(t3);
        GridLayout grid=new GridLayout(this); grid.setColumnCount(6);
        for(int col:all){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(42*dens); lp.height=(int)(42*dens); lp.setMargins((int)(5*dens),(int)(5*dens),(int)(5*dens),(int)(5*dens)); v.setLayoutParams(lp); GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(12*dens); bg.setColor(col); if(col==0xFFFFFFFF) bg.setStroke((int)dens, 0xFFCCCCCC); v.setBackground(bg); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 24*dens, 75)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); }
        ScrollView sv=new ScrollView(this); sv.addView(grid); root.addView(sv);

        b.setView(root);
        b.setPositiveButton("HEX custom",(d,w)->{ EditText et=new EditText(this); et.setHint("#8A8A7A"); new AlertDialog.Builder(this).setTitle("HEX").setView(et).setPositiveButton("OK",(dd,ww)->{ try{ int c=Color.parseColor(et.getText().toString().trim()); getSharedPreferences("glass",0).edit().putInt("glass_color",c).apply(); applyGlassTheme(c);}catch(Exception e){} }).show(); });
        b.setNegativeButton("Fermer",null); b.show();
    }
    void showGlassMenu(){ showPaletteDialog(); }
    void applyGlassTheme(int col){
        try{
            float dens=getResources().getDisplayMetrics().density;
            double lum=(0.299*Color.red(col)+0.587*Color.green(col)+0.114*Color.blue(col))/255;
            int textCol= lum>0.6? 0xFF1A1A1A : 0xFFFFFFFF;
            int hintCol= lum>0.6? 0x661A1A1A : 0x99FFFFFF;
            int[] bars={R.id.searchAppsMain,R.id.searchWebMain};
            for(int id:bars){ View v=findViewById(id); if(v!=null){ v.setBackground(createGlassDrawable(col, 32*dens, 72)); if(v instanceof EditText){ ((EditText)v).setTextColor(textCol); ((EditText)v).setHintTextColor(hintCol);} } }
            View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setBackground(createGlassDrawable(col, 32*dens, 78));
            View dock=findViewGlass("dock","dockBar","dock_container","dockContainer","bottomDock"); if(dock!=null) dock.setBackground(createGlassDrawable(col, 28*dens, 55));
            View fav=findViewGlass("btnAddFav","Fav"); if(fav!=null) fav.setBackground(createGlassDrawable(col, 20*dens, 50));
            View fol=findViewGlass("btnAddFolder","Folder"); if(fol!=null) fol.setBackground(createGlassDrawable(col, 20*dens, 50));
            View men=findViewGlass("btnMenu","Menu"); if(men!=null) men.setBackground(createGlassDrawable(col, 20*dens, 50));
        }catch(Exception e){}
    }

    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{
        class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
        public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
        public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launch(ri.activityInfo.packageName)); }catch(Exception e){} }
        public int getItemCount(){ return suggList.size(); }
    }
}
