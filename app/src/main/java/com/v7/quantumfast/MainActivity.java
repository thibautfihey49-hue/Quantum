package com.v7.quantumfast;
import android.app.AppOpsManager; import android.app.Activity; import android.app.ActivityManager; import android.app.admin.DevicePolicyManager; import android.app.usage.StorageStats; import android.app.usage.StorageStatsManager; import android.content.*; import android.content.pm.*; import android.graphics.*; import android.graphics.drawable.Drawable; import android.net.Uri; import android.os.*; import android.os.storage.StorageManager; import android.provider.Settings; import android.view.*; import android.view.inputmethod.EditorInfo; import android.widget.*; import androidx.core.app.ActivityCompat; import androidx.core.content.ContextCompat; import androidx.recyclerview.widget.*; import java.io.*; import java.util.*; import java.util.concurrent.*; import java.text.SimpleDateFormat;
public class MainActivity extends Activity {
    android.content.BroadcastReceiver timeReceiver;
    android.content.BroadcastReceiver pkgReceiver;
    android.content.BroadcastReceiver batteryReceiver;
    boolean cacheLoaded=false;

 EditText searchApps; EditText searchWeb;
    ImageView wallpaperView; ExecutorService exec = Executors.newSingleThreadExecutor(); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); Map<String,String> labelCache = new ConcurrentHashMap<>(); Map<String,Long> usageMap = new HashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"}; String[] defaultPkgs={"com.android.dialer","com.google.android.gm","com.google.android.apps.messaging","com.google.android.calendar","com.android.camera2","com.android.chrome"};
    RecyclerView rvSugg, rvFav, rvFolders; List<ResolveInfo> suggList = new ArrayList<>(); SuggAdapter suggAd;
    static class Fav{ String name; String url; Fav(String n,String u){name=n;url=u;} } List<Fav> favs = new ArrayList<>(); FavAdapter favAd;
    static class Folder{ String name; List<String> pkgs; Folder(String n,List<String> p){name=n;pkgs=p;} } List<Folder> folders = new ArrayList<>(); FolderAdapter folderAd;
    List<String> bigCachePkgs=new ArrayList<>();
    @Override protected void onResume(){ super.onResume(); clearSearchNow(); }
protected void onCreate(Bundle b){
        super.onCreate(b);
 try{ getWindow().setBackgroundDrawable(null); }catch(Exception e){}
 registerTimeTick();
 registerPkgReceiver();
 registerBatteryReceiver();
        getWindow().setStatusBarColor(0); getWindow().setNavigationBarColor(0);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        setContentView(R.layout.activity_main);
        requestAllPerms();
        wallpaperView=findViewById(R.id.wallpaperView); prefs=getSharedPreferences("dock",0); loadUsage();
        rvSugg=findViewById(R.id.rvSuggestions); rvFav=findViewById(R.id.rvFavorites); rvFolders=findViewById(R.id.rvFolders);
        rvSugg.setHasFixedSize(true); rvSugg.setItemAnimator(null); rvFav.setHasFixedSize(true); rvFav.setItemAnimator(null); rvFolders.setHasFixedSize(true); rvFolders.setItemAnimator(null);
        rvSugg.setLayoutManager(new LinearLayoutManager(this)); suggAd=new SuggAdapter(); rvSugg.setAdapter(suggAd);
        rvFav.setLayoutManager(new GridLayoutManager(this,4)); loadFavs(); favAd=new FavAdapter(); rvFav.setAdapter(favAd);
        rvFolders.setLayoutManager(new GridLayoutManager(this,2)); loadFolders(); ensureLowRamDefaults(); folderAd=new FolderAdapter(); rvFolders.setAdapter(folderAd);
        if(!cacheLoaded) preloadFast(); setupAtAGlanceSimple(); setupDock(); loadWallpaperFast(); setupGestures();
        searchApps=findViewById(R.id.searchAppsMain); searchWeb=findViewById(R.id.searchWebMain); TextView clear=findViewById(R.id.clearApps);
        final Runnable[] debounce=new Runnable[1];
        searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void afterTextChanged(android.text.Editable s){}
            public void onTextChanged(CharSequence q,int bb,int cc,int dd){
                if(debounce[0]!=null) main.removeCallbacks(debounce[0]);
                debounce[0]=()->{
                    String qq=q.toString().trim(); if(qq.isEmpty()){ main.post(()->{ rvSugg.setVisibility(View.GONE); clear.setVisibility(View.GONE); suggList.clear(); if(!rvSugg.isComputingLayout()) suggAd.notifyDataSetChanged(); }); return; }
                    List<ResolveInfo> snap; synchronized(cache){ snap=new ArrayList<>(cache); }
                    List<ResolveInfo> r=new ArrayList<>(); String low=qq.toLowerCase();
                    for(ResolveInfo ri:snap){ String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null && lbl.toLowerCase().contains(low)){ r.add(ri); if(r.size()>=12) break; } }
                    r.sort((x,y)-> Long.compare(usageMap.getOrDefault(y.activityInfo.packageName,0L), usageMap.getOrDefault(x.activityInfo.packageName,0L)));
                    main.post(()->{ clear.setVisibility(View.VISIBLE); suggList.clear(); suggList.addAll(r); rvSugg.setVisibility(r.isEmpty()?View.GONE:View.VISIBLE); if(!rvSugg.isComputingLayout()) suggAd.notifyDataSetChanged(); });
                }; main.postDelayed(debounce[0], 50);
            }
        });
        clear.setOnClickListener(v->searchApps.setText(""));
        searchWeb.setOnEditorActionListener((v,actionId,event)->{ if(actionId==EditorInfo.IME_ACTION_SEARCH){ String q=v.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); return true;} return false; });
        findViewById(R.id.btnWebGo).setOnClickListener(v->{ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); });
        findViewById(R.id.btnAddFav).setOnClickListener(v->showAddFavDialog());
        findViewById(R.id.btnAddFolder).setOnClickListener(v->showCreateFolderDialog());
        main.postDelayed(()->{ if(!isDefaultLauncher()) showGlassDialog(); }, 800);
        findViewById(R.id.btnMenu).setOnClickListener(v->showGlassMenu());
    }
    void setupGestures(){ GestureDetector gd=new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy){ float dy=e2.getY()-e1.getY(); if(Math.abs(dy)>200){ if(dy<0){ openDrawerWithQuery(""); return true; } } return false; }
        @Override public boolean onDoubleTap(MotionEvent e){ try{ DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE); if(dpm.isAdminActive(new ComponentName(MainActivity.this, AdminReceiver.class))) dpm.lockNow(); }catch(Exception ex){} return true; }
    }); View root=findViewById(R.id.root); root.setOnTouchListener((v,ev)->{ gd.onTouchEvent(ev); return false; }); }
    
    .drawable.Drawable getCachedIcon(String pkg, android.content.pm.PackageManager pm){
 try{
  android.graphics.drawable.Drawable d = iconCache.get(pkg);
  if(d!=null) return d;
  d = pm.getApplicationIcon(pkg);
  if(d!=null) iconCache.put(pkg,d);
  return d;
 }catch(Exception e){ return null; }
}
void saveFolders(){ StringBuilder sb=new StringBuilder(); for(int i=0;i<folders.size();i++){ if(i>0) sb.append(";;"); sb.append(folders.get(i).name).append("||").append(String.join(",",folders.get(i).pkgs)); } prefs.edit().putString("folders",sb.toString()).apply(); }
    boolean isDefaultLauncher(){ Intent home = new Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY); return ri!=null && ri.activityInfo!=null && getPackageName().equals(ri.activityInfo.packageName); }
    public void onTrimMemory(int level){ super.onTrimMemory(level); if(iconCache!=null) iconCache.clear(); if(level>=40) DiskIconCache.clear(this); }
    void pickWallpaperInternal(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i,201); }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri uri=data.getData(); try{ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply(); loadWallpaperFast(); } }
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<250) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} main.postDelayed(this,30000);} }; r.run(); }
    void setupDock(){ int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome}; for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((FrameLayout)vv).getChildAt(0); if(idx==3) continue; String pkg=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(pkg!=null) updateDockIcon(iv, pkg); vv.setOnClickListener(view->{ if(idx==3) openDrawerWithQuery(""); else { String rp=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(rp!=null) launch(rp); }}); vv.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; }); } findViewById(R.id.dDrawer).setOnClickListener(v->openDrawerWithQuery("")); }

 void pickDockApp(int dockIdx){
        dlg.setContentView(R.layout.picker_dock);
        androidx.recyclerview.widget.RecyclerView rv=dlg.findViewById(R.id.recyclerDock);
        rv.setHasFixedSize(true);
        rv.setItemAnimator(null);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this,5));
        java.util.List<android.content.pm.ResolveInfo> list;
        synchronized(cache){ list=new java.util.ArrayList<>(cache); }
        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter(){
            class H extends androidx.recyclerview.widget.RecyclerView.ViewHolder{ android.widget.ImageView ic; android.widget.TextView lb; H(android.view.View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int viewType){ return new H(getLayoutInflater().inflate(R.layout.item_app,parent,false)); }
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder hh,int pos){
                try{
                    H h=(H)hh; if(pos>=list.size()) return;
                    android.content.pm.ResolveInfo ri=list.get(pos);
                    String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl);
                    android.graphics.drawable.Drawable d=iconCache.get(ri.activityInfo.packageName);
                    if(d!=null) h.ic.setImageDrawable(d);
                    else {
                        h.ic.setImageResource(android.R.drawable.sym_def_app_icon);
 void updateDockIcon(ImageView iv, String pkg){
        try{
            android.graphics.drawable.Drawable cd=iconCache.get(pkg);
            if(cd!=null){ iv.setImageDrawable(cd); return; }
            android.graphics.Bitmap disk=DiskIconCache.get(this,pkg);
            if(disk!=null){ iv.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(disk,96,96,true)); return; }
            android.graphics.drawable.Drawable d=getPackageManager().getApplicationIcon(pkg);
            android.graphics.Bitmap bmp;
            if(d instanceof android.graphics.drawable.BitmapDrawable){ bmp=((android.graphics.drawable.BitmapDrawable)d).getBitmap(); }
            else { bmp=android.graphics.Bitmap.createBitmap(96,96, android.graphics.Bitmap.Config.RGB_565); android.graphics.Canvas c=new android.graphics.Canvas(bmp); d.setBounds(0,0,96,96); d.draw(c); }
            DiskIconCache.put(this,pkg,bmp);
            iv.setImageDrawable(d);
            if(iconCache.size()<5) iconCache.put(pkg,d);
        }catch(Exception e){ try{ iv.setImageResource(android.R.drawable.sym_def_app_icon); }catch(Exception ee){} }
    }
    void launch(String pkg){ try{ android.content.Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); clearSearchNow(); startActivity(ii); return; } }catch(Exception e){}  try{ trackUsage(pkg); Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); clearSearchNow(); startActivity(it);} }catch(Exception e){} }
    void loadFavs(){ favs.clear(); String saved=prefs.getString("favs",""); if(saved.isEmpty()){ favs.add(new Fav("Google","https://google.com")); favs.add(new Fav("YouTube","https://youtube.com")); return; } try{ for(String p:saved.split(";;")){ String[] sp=p.split("\\|\\|"); if(sp.length==2) favs.add(new Fav(sp[0],sp[1])); } }catch(Exception e){} }
    void saveFavs(){ StringBuilder sb=new StringBuilder(); for(int i=0;i<favs.size();i++){ if(i>0) sb.append(";;"); sb.append(favs.get(i).name).append("||").append(favs.get(i).url); } prefs.edit().putString("favs", sb.toString()).apply(); }
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView name; TextView icon; H(View v){super(v); name=v.findViewById(R.id.favName); icon=v.findViewById(R.id.favIcon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_fav,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=folders.size()) return; Folder f=folders.get(pos); h.name.setText("📁 "+f.name+" ("+f.pkgs.size()+")"); h.icon.setText("📁"); h.itemView.setOnClickListener(v->showFolderContent(f)); h.itemView.setOnLongClickListener(v->{ folders.remove(pos); saveFolders(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return folders.size(); } }
    void boostAllApps(){
        try{
            getWindow().setBackgroundDrawable(null);
            if(wallpaperView!=null && QuantumApp.wallpaperCache!=null) wallpaperView.setImageDrawable(QuantumApp.wallpaperCache);
            try{
                rvFolders.setRecycledViewPool(sharedPool);
                rvFolders.setItemViewCacheSize(1000);
                rvFolders.setHasFixedSize(true);
                rvFolders.setItemAnimator(null);
                rvFolders.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);
                rvFolders.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
                ((androidx.recyclerview.widget.LinearLayoutManager)rvFolders.getLayoutManager()).setInitialPrefetchItemCount(1000);
            }catch(Exception e){}
            try{
                rvSuggestions.setRecycledViewPool(sharedPool);
                rvSuggestions.setItemViewCacheSize(1000);
                rvSuggestions.setHasFixedSize(true);
                rvSuggestions.setItemAnimator(null);
                rvSuggestions.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);
                rvSuggestions.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
                ((androidx.recyclerview.widget.LinearLayoutManager)rvSuggestions.getLayoutManager()).setInitialPrefetchItemCount(1000);
            }catch(Exception e){}
            android.os.Process.setThreadPriority(-20);
        }catch(Exception e){}
        QuantumApp.boot();
    }


    void showPaletteDialog(){
        float dens=getResources().getDisplayMetrics().density;
        int[] pastels={0xFFFFD4E7,0xFFD4E7FF,0xFFC7B6FF,0xFFB6FFE8,0xFFFFF3B6,0xFFFFE0B2};
        int[] vifs={0xFFF44336,0xFFE91E63,0xFF9C27B0,0xFF3F51B5,0xFF2196F3,0xFF00BCD4,0xFF4CAF50,0xFFFF9800};
        int[] all={0xFFFFCDD2,0xFFEF9A9A,0xFFE57373,0xFFEF5350,0xFFF44336,0xFFD32F2F,0xFFF8BBD0,0xFFF48FB1,0xFFF06292,0xFFEC407A,0xFFE91E63,0xFFC2185B,0xFFE1BEE7,0xFFCE93D8,0xFFBA68C8,0xFFAB47BC,0xFF9C27B0,0xFF7B1FA2,0xFFD1C4E9,0xFFB39DDB,0xFF9575CD,0xFF7E57C2,0xFF673AB7,0xFF512DA8,0xFFC5CAE9,0xFF9FA8DA,0xFF7986CB,0xFF5C6BC0,0xFF3F51B5,0xFF303F9F,0xFFBBDEFB,0xFF90CAF9,0xFF64B5F6,0xFF42A5F5,0xFF2196F3,0xFF1976D2,0xFFB2EBF2,0xFF80DEEA,0xFF4DD0E1,0xFF26C6DA,0xFF00BCD4,0xFF0097A7,0xFFC8E6C9,0xFFA5D6A7,0xFF81C784,0xFF66BB6A,0xFF4CAF50,0xFF388E3C,0xFFDCEDC8,0xFFC5E1A5,0xFFAED581,0xFF9CCC65,0xFF8BC34A,0xFF689F38,0xFFFFF9C4,0xFFFFF59D,0xFFFFEE58,0xFFFFEB3B,0xFFFBC02D,0xFFF57F17,0xFFFFE0B2,0xFFFFCC80,0xFFFFB74D,0xFFFFA726,0xFFFF9800,0xFFEF6C00,0xFF1A1A1A};
        android.app.AlertDialog.Builder b=new android.app.AlertDialog.Builder(this);
        b.setTitle("Palette Glass");
        android.widget.LinearLayout root=new android.widget.LinearLayout(this); root.setOrientation(android.widget.LinearLayout.VERTICAL); root.setPadding((int)(16*dens),(int)(16*dens),(int)(16*dens),(int)(16*dens));
        TextView preview=new TextView(this); preview.setText("Aperçu verre"); preview.setTextSize(16); preview.setPadding((int)(16*dens),(int)(12*dens),(int)(16*dens),(int)(12*dens)); root.addView(preview);
        android.widget.GridLayout gridPastel=new android.widget.GridLayout(this); gridPastel.setColumnCount(6);
        for(int col:pastels){ View v=new View(this); android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(); lp.width=(int)(48*dens); lp.height=(int)(48*dens); lp.setMargins((int)(8*dens),(int)(8*dens),(int)(8*dens),(int)(8*dens)); v.setLayoutParams(lp); v.setBackground(createGlassDrawable(col, 18*dens, 120)); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 18*dens, 85)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); gridPastel.addView(v); }
        root.addView(gridPastel);
        android.widget.GridLayout grid=new android.widget.GridLayout(this); grid.setColumnCount(6);
        for(int col:all){ View v=new View(this); android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(); lp.width=(int)(40*dens); lp.height=(int)(40*dens); lp.setMargins((int)(6*dens),(int)(6*dens),(int)(6*dens),(int)(6*dens)); v.setLayoutParams(lp); v.setBackgroundColor(col); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(10*dens); bg.setColor(col); v.setBackground(bg); v.setOnClickListener(view->{ preview.setBackground(createGlassDrawable(col, 18*dens, 85)); getSharedPreferences("glass",0).edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); }
        android.widget.ScrollView sv=new android.widget.ScrollView(this); sv.addView(grid); root.addView(sv);
        b.setView(root);
        b.setPositiveButton("HEX custom",(d,w)->{ EditText et=new EditText(this); et.setHint("#FFD4E7"); new android.app.AlertDialog.Builder(this).setTitle("HEX").setView(et).setPositiveButton("OK",(dd,ww)->{ try{ int c=android.graphics.Color.parseColor(et.getText().toString().trim()); getSharedPreferences("glass",0).edit().putInt("glass_color",c).apply(); applyGlassTheme(c);}catch(Exception e){} }).show(); });
        b.setNegativeButton("Fermer",null); b.show();
    }
    void showGlassMenu(){ showPaletteDialog(); }
    void applyGlassTheme(int col){
        try{
            float dens=getResources().getDisplayMetrics().density;
            double lum=(0.299*android.graphics.Color.red(col)+0.587*android.graphics.Color.green(col)+0.114*android.graphics.Color.blue(col))/255;
            int textCol= lum>0.6? 0xFF1A1A1A : 0xFFFFFFFF;
            int hintCol= lum>0.6? 0x661A1A1A : 0x66FFFFFF;
            int[] bars={R.id.searchAppsMain,R.id.searchWebMain};
            for(int id:bars){ View v=findViewById(id); if(v!=null){ v.setBackground(createGlassDrawable(col, 32*dens, 85)); if(v instanceof EditText){ ((EditText)v).setTextColor(textCol); ((EditText)v).setHintTextColor(hintCol);} } }
            View go=findView(R.id.class,"btnWebGo","go","web_go","btnGo"); if(go!=null) go.setBackground(createGlassDrawable(col, 32*dens, 85));
            View dock=findView(R.id.class,"dock","dockBar","dock_container","dockContainer","bottomDock"); if(dock!=null) dock.setBackground(createGlassDrawable(col, 28*dens, 70));
        }catch(Exception e){}
    }

}