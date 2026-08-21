package com.v7.quantumfast;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 android.content.BroadcastReceiver mThemeReceiver=null;
 android.appwidget.AppWidgetManager awm;
 android.appwidget.AppWidgetHost awHost;
 static final int REQ_PICK_WIDGET=1950;
 static final int REQ_CREATE_WIDGET=1951;
 android.widget.FrameLayout widgetContainer;

    View mainRoot;
    EditText searchApps, searchWeb;
    RecyclerView rvSugg, rvFav;
    SharedPreferences prefs, glassPrefs, usagePrefs;
    List<ResolveInfo> suggList=new ArrayList<>();
    List<ResolveInfo> allAppsCache=new ArrayList<>();
    LruCache<String, Drawable> iconCache=new LruCache<>(200);
    List<String> favPkgs=new ArrayList<>();
    List<String> manualTopPkgs=new ArrayList<>();
    Map<String, Intent> launchIntentCache=new HashMap<>();
    Map<String, String> labelCache=new HashMap<>();
    Map<String, String> labelCacheLow=new HashMap<>();
 static android.graphics.drawable.Drawable cachedWallpaperDrawable=null;
 static boolean fullCacheReady=false;
 List<ResolveInfo> cachedUniqApps=new ArrayList<>();
 long lastCacheTime=0;
    ExecutorService pool=Executors.newFixedThreadPool(2);
    Handler mainH=new Handler(Looper.getMainLooper());
    String[] dockKeys={"dock_phone","dock_msg","dock_extra","dock_drawer","dock_cam","dock_chrome"};
    String[] defaultPkgs={"com.android.dialer","com.google.android.apps.messaging","com.android.settings","com.v7.quantumfast","com.android.camera2","com.android.chrome"};
    int getNavBarH(){ int id=getResources().getIdentifier("navigation_bar_height","dimen","android"); return id>0?getResources().getDimensionPixelSize(id):0; }
    View findV(String... names){ for(String s:names){ int id=getResources().getIdentifier(s,"id",getPackageName()); if(id!=0){ View v=findViewById(id); if(v!=null) return v; } } return null; }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
 if(mAppWidgetHost==null){ mAppWidgetHost = new android.appwidget.AppWidgetHost(this, 1); mAppWidgetHost.startListening(); awHost=mAppWidgetHost; }
        setContentView(R.layout.activity_main);
        try{ java.io.File wf=new java.io.File(getFilesDir(),"quantum_wall.jpg"); if(wf.exists()){ android.graphics.drawable.Drawable wd=android.graphics.drawable.Drawable.createFromPath(wf.getAbsolutePath()); if(wd!=null){ cachedWallpaperDrawable=wd; getWindow().setBackgroundDrawable(wd); if(mainRoot!=null) mainRoot.setBackground(wd); } } }catch(Exception e){}
        try{
            awm=android.appwidget.AppWidgetManager.getInstance(this);
            awHost=new android.appwidget.AppWidgetHost(this, 2025); awHost.startListening();
            widgetContainer=new android.widget.FrameLayout(this); widgetContainer.setId(R.id.widget_container);
            if(mainRoot!=null) ((android.view.ViewGroup)mainRoot).addView(widgetContainer, new android.widget.FrameLayout.LayoutParams(-1,-1));
            restoreWidgets();
            mThemeReceiver=new android.content.BroadcastReceiver(){ public void onReceive(android.content.Context c, Intent i){ refreshFromSystemTheme(); } };
            android.content.IntentFilter f=new android.content.IntentFilter();
            f.addAction(Intent.ACTION_WALLPAPER_CHANGED); f.addAction("com.oppo.themechooser.THEME_CHANGED"); f.addAction("com.heytap.themestore.THEME_CHANGED");
            registerReceiver(mThemeReceiver, f);
        }catch(Exception e){}

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        prefs=getSharedPreferences("dock",0);
        glassPrefs=getSharedPreferences("glass",0);
        usagePrefs=getSharedPreferences("usage",0);
        mainRoot=findViewById(R.id.root);
        searchApps=findViewById(R.id.searchAppsMain);
        searchWeb=findViewById(R.id.searchWebMain);
        rvSugg=findViewById(R.id.rvSuggestions);
        rvFav=findViewById(R.id.rvFavorites);

        // FIX DOCK ROGNE DEFINITIF
        View dock=findViewById(R.id.dock);
        if(dock!=null){
            dock.setVisibility(View.VISIBLE);
            dock.setPadding(0,0,0,getNavBarH()-40);
            try{ ((ViewGroup.MarginLayoutParams)dock.getLayoutParams()).bottomMargin=getNavBarH()-40; }catch(Exception e){}
        }

        if(rvSugg!=null){ rvSugg.setLayoutManager(new LinearLayoutManager(this)); rvSugg.setVisibility(View.GONE); rvSugg.setAdapter(new SuggAdapter()); }
        if(rvFav!=null){ rvFav.setLayoutManager(new GridLayoutManager(this,5)); rvFav.setVisibility(View.VISIBLE); }

        for(String n:new String[]{"btnBoost","gCard","yCard","folderZone","rvFolders","btnAddFolder"}){ View v=findV(n); if(v!=null) v.setVisibility(View.GONE); }

        View cl=findV("clearApps","btnClear"); if(cl!=null) cl.setOnClickListener(v->{ if(searchApps!=null) searchApps.setText(""); });
        View go=findV("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setOnClickListener(v->handleWeb());
        View favBtn=findV("btnAddFav","Fav","fav"); if(favBtn!=null) favBtn.setOnClickListener(v->showAddFavDialog());
        View men=findV("btnMenu","Menu","menu"); if(men!=null) men.setOnClickListener(v->showMenuModern());

        ensureFullCache(); loadFavs(); loadManualTop(); loadWallpaperPersist(); askDefaultLauncher(); if(rvFav!=null) rvFav.setAdapter(new FavAdapter());
        setupAtAGlance(); preloadMax(); setupDock();
        applyGlassTheme(glassPrefs.getInt("glass_color",0xFF7C4DFF));
        if(searchApps!=null) searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ boolean typing=!s.toString().trim().isEmpty(); if(rvFav!=null) rvFav.setVisibility(typing?View.GONE:View.VISIBLE); if(rvSugg!=null) rvSugg.setVisibility(typing?View.VISIBLE:View.GONE); filterAppsInstant(s.toString()); }
            public void afterTextChanged(android.text.Editable s){}
        });
        checkAndAskPermissions();
    }

    GradientDrawable glassBg(int col,float rad,int alpha){ int fill=Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)); GradientDrawable d=new GradientDrawable(); d.setShape(0); d.setCornerRadius(rad); d.setColor(fill); d.setStroke((int)(1.2f*getResources().getDisplayMetrics().density), Color.argb(90,255,255,255)); return d; }
    AlertDialog createModernDialog(String title, View content){ float dens=getResources().getDisplayMetrics().density; LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(20*dens),(int)(20*dens),(int)(20*dens),(int)(16*dens)); root.setBackground(glassBg(glassPrefs.getInt("glass_color",0xFF7C4DFF), 24*dens, 96)); TextView tv=new TextView(this); tv.setText(title); tv.setTextSize(18); tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD); tv.setPadding(0,0,0,(int)(12*dens)); root.addView(tv); if(content!=null) root.addView(content); AlertDialog dlg=new AlertDialog.Builder(getDialogContext()).setView(root).create(); if(dlg.getWindow()!=null) dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); return dlg; }
    
    void showAppDrawer(){
        try{
            List<ResolveInfo> base = allAppsCache.isEmpty()? getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0) : allAppsCache;
            LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:base) map.putIfAbsent(ri.activityInfo.packageName, ri);
            final List<ResolveInfo> uniq= cachedUniqApps.isEmpty()? new ArrayList<>(map.values()) : cachedUniqApps;
            final List<ResolveInfo> filtered=new ArrayList<>(uniq);
            LinearLayout container=new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
            EditText search=new EditText(this); search.setHint("Rechercher..."); search.setTextColor(0xFFFFFFFF); search.setHintTextColor(0xFFAAAAAA); search.setPadding(40,30,40,30); search.setBackgroundColor(0x22FFFFFF);
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this)); rv.setHasFixedSize(true);
            container.addView(search, new LinearLayout.LayoutParams(-1,-2));
            container.addView(rv, new LinearLayout.LayoutParams(-1,-1,1f));
            RecyclerView.Adapter ad=new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(1001); lb=v.findViewById(1002);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup pg,int vt){ LinearLayout row=new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(30,18,30,18); ImageView iv=new ImageView(MainActivity.this); iv.setId(1001); row.addView(iv,new LinearLayout.LayoutParams(88,88)); TextView tv=new TextView(MainActivity.this); tv.setId(1002); tv.setTextColor(Color.WHITE); tv.setPadding(24,0,0,0); row.addView(tv,new LinearLayout.LayoutParams(0,-2,1f)); return new H(row); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=filtered.get(pos); String pkg=ri.activityInfo.packageName; h.lb.setText(labelCache.containsKey(pkg)?labelCache.get(pkg):ri.loadLabel(getPackageManager()).toString()); Drawable cd=iconCache.get(pkg); if(cd!=null) h.ic.setImageDrawable(cd); else { try{ h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); }catch(Exception e){} } h.itemView.setOnClickListener(v->{ try{ launchInstant(pkg); ((AlertDialog)container.getTag()).dismiss(); }catch(Exception e){} }); }
                public int getItemCount(){ return filtered.size(); }
            };
            rv.setAdapter(ad);
            search.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){} public void afterTextChanged(android.text.Editable s){ String q=s.toString().toLowerCase().trim(); filtered.clear(); if(q.isEmpty()) filtered.addAll(uniq); else { for(ResolveInfo ri:uniq){ String pkg=ri.activityInfo.packageName; String low=labelCacheLow.containsKey(pkg)?labelCacheLow.get(pkg):""; if(low.isEmpty()) low=ri.loadLabel(getPackageManager()).toString().toLowerCase(); if(low.contains(q) || pkg.toLowerCase().contains(q)) filtered.add(ri); } } ad.notifyDataSetChanged(); } });
            AlertDialog dlg=createModernDialog("Tiroir", container); container.setTag(dlg); try{ dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)); }catch(Exception e){} dlg.show();
        }catch(Exception e){}
    }
    void showMenuModern(){
 float dens=getResources().getDisplayMetrics().density; LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); String[] opts={"🎨 Couleur thème","🖼️ Fond d'écran","🧹 Effacer fond","⭐ Mes apps fusée","🎨 Thèmes d'icônes gratuits","🔤 Polices + Fonds HD","🧩 Widget draggable"}; for(int i=0;i<opts.length;i++){ final int idx=i; TextView row=new TextView(this); row.setText(opts[i]); row.setTextSize(16); row.setTextColor(Color.WHITE); row.setPadding((int)(14*dens),(int)(16*dens),(int)(14*dens),(int)(16*dens)); row.setBackground(glassBg(Color.BLACK, 14*dens, 70)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,(int)(10*dens)); row.setLayoutParams(lp); row.setOnClickListener(v->{ if(idx==0) showPaletteModern(); else if(idx==1) pickWallpaper(); else if(idx==3) showManualTopPicker(); else if(idx==4) showIconPackPicker(); else if(idx==5) showFontsWallpapersPicker(); else if(idx==6) pickWidget(); else { prefs.edit().remove("custom_wallpaper_uri").apply(); android.view.View bg=findV("wallpaper","bg","background","wall"); if(bg instanceof android.widget.ImageView) ((android.widget.ImageView)bg).setImageDrawable(null); } } }); list.addView(row); } AlertDialog dlg=createModernDialog("Quantum Ultra", list); dlg.show(); }
    void showPaletteModern(){ float dens=getResources().getDisplayMetrics().density; GridLayout grid=new GridLayout(this); grid.setColumnCount(5); int[] cols={0xFF7C4DFF,0xFF00E5FF,0xFF00FF94,0xFFFF3D8B,0xFFFFAB00,0xFF6B4C8A,0xFF2196F3,0xFF212121,0xFFFFFFFF}; for(int col:cols){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(56*dens); lp.height=(int)(56*dens); lp.setMargins((int)(8*dens),(int)(8*dens),(int)(8*dens),(int)(8*dens)); v.setLayoutParams(lp); GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(16*dens); bg.setColor(col); if(col==0xFFFFFFFF) bg.setStroke((int)dens,0xFFCCCCCC); v.setBackground(bg); v.setOnClickListener(vw->{ glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); } AlertDialog dlg=createModernDialog("Thème Ultra", grid); dlg.show(); }
    void pickWallpaper(){ try{ Intent it=new Intent(Intent.ACTION_OPEN_DOCUMENT); it.addCategory(Intent.CATEGORY_OPENABLE); it.setType("image/*"); startActivityForResult(it, 201); }catch(Exception e){ try{ Intent it2=new Intent(Intent.ACTION_PICK); it2.setType("image/*"); startActivityForResult(it2,201); }catch(Exception ee){} } }
    
    android.content.Context getDialogContext(){ return new android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert); }
 
    void pickWidget(){
        try{
            android.appwidget.AppWidgetManager awm = android.appwidget.AppWidgetManager.getInstance(this);
            java.util.List<android.appwidget.AppWidgetProviderInfo> providers = awm.getInstalledProviders();
            if(providers.isEmpty()){ android.widget.Toast.makeText(this,"Aucun widget",0).show(); return; }
            android.content.Context ctx = getDialogContext();
            android.widget.LinearLayout list = new android.widget.LinearLayout(ctx);
            list.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = (int)(getResources().getDisplayMetrics().density*12);
            if(mAppWidgetHost==null){ mAppWidgetHost = new android.appwidget.AppWidgetHost(this, 1); mAppWidgetHost.startListening(); }
            awHost = mAppWidgetHost;
            for(android.appwidget.AppWidgetProviderInfo info: providers){
                android.widget.TextView row = new android.widget.TextView(ctx);
                try{ row.setText(info.loadLabel(getPackageManager())); }catch(Exception e){ row.setText(info.provider.getPackageName()); }
                row.setTextSize(15); row.setTextColor(0xFFFFFFFF);
                row.setPadding(pad,pad,pad,pad);
                row.setOnClickListener(vv->{
                    try{
                        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                        boolean bound = awm.bindAppWidgetIdIfAllowed(appWidgetId, info.provider);
                        if(!bound){
                            android.content.Intent bi = new android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND);
                            bi.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                            bi.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                            startActivityForResult(bi, 9002);
                        }else{ addWidgetView(appWidgetId, info); }
                    }catch(Exception e){ android.widget.Toast.makeText(this,"Widget: "+e.getMessage(),1).show(); }
                });
                list.addView(row);
            }
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx); sv.addView(list);
            createModernDialog("Choisir widget", sv).show();
        }catch(Exception e){ android.widget.Toast.makeText(this,"Widget err: "+e.getMessage(),1).show(); }
    }
    void addWidgetView(int appWidgetId, android.appwidget.AppWidgetProviderInfo info){
        try{
            android.appwidget.AppWidgetHostView hv = mAppWidgetHost.createView(this, appWidgetId, info);
            hv.setAppWidget(appWidgetId, info);
            android.view.ViewGroup vg = (android.view.ViewGroup) mainRoot;
            if(vg!=null){ vg.addView(hv); makeWidgetDraggable(hv); }
        }catch(Exception e){ android.widget.Toast.makeText(this,"Add: "+e.getMessage(),1).show(); }
    }
    void makeWidgetDraggable(android.view.View v){
        v.setOnTouchListener(new android.view.View.OnTouchListener(){
            float dx, dy;
            public boolean onTouch(android.view.View vv, android.view.MotionEvent ev){
                switch(ev.getAction()){
                    case android.view.MotionEvent.ACTION_DOWN: dx=vv.getX()-ev.getRawX(); dy=vv.getY()-ev.getRawY(); return true;
                    case android.view.MotionEvent.ACTION_MOVE: vv.setX(ev.getRawX()+dx); vv.setY(ev.getRawY()+dy); return true;
                }
                return false;
            }
        });
    }
    void makeDraggable(android.view.View v, int id){ makeWidgetDraggable(v); }
    void makeDraggable(android.view.View v){ makeWidgetDraggable(v); }

    void showIconPackPicker(){
        try{
            android.content.Context ctx = getDialogContext();
            android.widget.LinearLayout list = new android.widget.LinearLayout(ctx);
            list.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = (int)(getResources().getDisplayMetrics().density*12);
            android.content.pm.PackageManager pm = getPackageManager();
            java.util.List<android.content.pm.ResolveInfo> packs = new java.util.ArrayList<>();
            try{ packs.addAll(pm.queryIntentActivities(new android.content.Intent("org.adw.launcher.THEMES"), 0)); }catch(Exception e){}
            try{ packs.addAll(pm.queryIntentActivities(new android.content.Intent("com.novalauncher.THEME"), 0)); }catch(Exception e){}
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for(android.content.pm.ResolveInfo ri: packs){
                String pkg = ri.activityInfo.packageName;
                if(!seen.add(pkg)) continue;
                android.widget.TextView row = new android.widget.TextView(ctx);
                try{ row.setText(ri.loadLabel(pm)); }catch(Exception e){ row.setText(pkg); }
                row.setTextSize(15); row.setTextColor(0xFFFFFFFF);
                row.setPadding(pad,pad,pad,pad);
                row.setOnClickListener(vv->{ prefs.edit().putString("icon_pack", pkg).apply(); android.widget.Toast.makeText(this, "Pack: "+pkg, 0).show(); });
                list.addView(row);
            }
            android.widget.TextView more = new android.widget.TextView(ctx);
            more.setText("➕ Chercher packs gratuits Play Store");
            more.setTextColor(0xFF00E5FF); more.setPadding(pad,pad*2,pad,pad);
            more.setOnClickListener(vv->{ try{ startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=free icon pack"))); }catch(Exception e){ startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/search?q=free%20icon%20pack"))); } });
            list.addView(more);
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx); sv.addView(list);
            createModernDialog("Thèmes d'icônes gratuits", sv).show();
        }catch(Exception e){ android.widget.Toast.makeText(this, "Icon: "+e.getMessage(), 1).show(); }
    }
    void showFontsWallpapersPicker(){
        try{
            android.content.Context ctx = getDialogContext();
            android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = (int)(getResources().getDisplayMetrics().density*12);
            android.widget.TextView h1 = new android.widget.TextView(ctx); h1.setText("🔤 Polices Google"); h1.setTextColor(0xFFFFFFFF); h1.setTextSize(16); h1.setPadding(pad,pad,pad,pad/2); root.addView(h1);
            String[][] fonts = {{"Poppins","https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Regular.ttf"},{"Montserrat","https://github.com/google/fonts/raw/main/ofl/montserrat/Montserrat-Regular.ttf"},{"Nunito","https://github.com/google/fonts/raw/main/ofl/nunito/Nunito-Regular.ttf"}};
            for(String[] f: fonts){
                android.widget.TextView row = new android.widget.TextView(ctx); row.setText("• "+f[0]); row.setTextColor(0xFFFFFFFF); row.setPadding(pad*2,pad/2,pad,pad/2);
                row.setOnClickListener(vv->{ downloadFont(f[0], f[1]); }); root.addView(row);
            }
            android.widget.TextView h2 = new android.widget.TextView(ctx); h2.setText("\n🖼️ Fonds HD gratuits"); h2.setTextColor(0xFFFFFFFF); h2.setTextSize(16); h2.setPadding(pad,pad,pad,pad/2); root.addView(h2);
            String[][] walls = {{"Aléatoire HD","https://picsum.photos/1080/1920"}};
            for(String[] w: walls){
                android.widget.TextView row = new android.widget.TextView(ctx); row.setText("• "+w[0]); row.setTextColor(0xFF00E5FF); row.setPadding(pad*2,pad/2,pad,pad/2);
                row.setOnClickListener(vv->{ downloadWallpaper(w[1]); }); root.addView(row);
            }
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx); sv.addView(root);
            createModernDialog("Polices + Fonds", sv).show();
        }catch(Exception e){ android.widget.Toast.makeText(this, "Fonts: "+e.getMessage(), 1).show(); }
    }
    void downloadFont(String name, String url){
        new Thread(()->{
            try{
                java.io.File dir = new java.io.File(getFilesDir(), "fonts"); dir.mkdirs();
                java.io.File out = new java.io.File(dir, name+".ttf");
                java.io.InputStream in = new java.net.URL(url).openStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                byte[] buf = new byte[8192]; int r; while((r=in.read(buf))!=-1) fos.write(buf,0,r);
                fos.close(); in.close();
                runOnUiThread(()->{ prefs.edit().putString("custom_font_path", out.getAbsolutePath()).apply(); android.widget.Toast.makeText(this, "Police "+name+" installée", 0).show(); });
            }catch(Exception e){ runOnUiThread(()-> android.widget.Toast.makeText(this, "Font err: "+e.getMessage(), 1).show()); }
        }).start();
    }
    void downloadWallpaper(String url){
        new Thread(()->{
            try{
                java.io.InputStream in = new java.net.URL(url).openStream();
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in); in.close();
                runOnUiThread(()->{
                    try{ android.view.View bg = findV("wallpaper","bg","background","wall"); if(bg instanceof android.widget.ImageView) ((android.widget.ImageView)bg).setImageBitmap(bmp); android.widget.Toast.makeText(this, "Fond appliqué", 0).show(); }catch(Exception ee){}
                });
            }catch(Exception e){ runOnUiThread(()-> android.widget.Toast.makeText(this, "Wall err: "+e.getMessage(), 1).show()); }
        }).start();
    }

 void ensureFullCache(){
        try{
            if(fullCacheReady && System.currentTimeMillis()-lastCacheTime<60000){ loadWallpaperPersist(); return; }
            if(allAppsCache.isEmpty()){
                List<ResolveInfo> q=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
                allAppsCache=new ArrayList<>(q);
            }
            if(labelCache.isEmpty()){
                for(ResolveInfo ri: allAppsCache){ try{ String pkg=ri.activityInfo.packageName; String lab=ri.loadLabel(getPackageManager()).toString(); labelCache.put(pkg, lab); labelCacheLow.put(pkg, lab.toLowerCase()); }catch(Exception e){} }
            }
            LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:allAppsCache) map.putIfAbsent(ri.activityInfo.packageName, ri);
            cachedUniqApps=new ArrayList<>(map.values());
            try{ Collections.sort(cachedUniqApps,(a,b)-> labelCache.getOrDefault(a.activityInfo.packageName,"").compareToIgnoreCase(labelCache.getOrDefault(b.activityInfo.packageName,""))); }catch(Exception e){}
            new Thread(()->{ try{ for(ResolveInfo ri: allAppsCache){ String pkg=ri.activityInfo.packageName; if(iconCache.get(pkg)==null){ try{ Drawable d=ri.loadIcon(getPackageManager()); if(d!=null) iconCache.put(pkg,d); }catch(Exception e){} } } }catch(Exception e){} }).start();
            loadWallpaperPersist(); fullCacheReady=true; lastCacheTime=System.currentTimeMillis();
        }catch(Exception e){}
    }
    void preloadMax(){
 pool.execute(()->{ try{ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it, 0); LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:all){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri); } List<ResolveInfo> dedup=new ArrayList<>(map.values()); Collections.sort(dedup,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString())); allAppsCache=dedup; for(ResolveInfo ri:dedup){ try{ if(iconCache.get(ri.activityInfo.packageName)==null) iconCache.put(ri.activityInfo.packageName, ri.loadIcon(getPackageManager())); }catch(Exception e){} } mainH.post(()->{ setupDock(); if(rvFav!=null) rvFav.getAdapter().notifyDataSetChanged(); loadWallpaperFast(); }); }catch(Exception e){} }); }
    void filterAppsInstant(String q){ try{
            suggList.clear(); if(q==null||q.trim().isEmpty()){ if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); return; }
            String lq=q.toLowerCase().trim();
            for(String pkg: manualTopPkgs){ try{ String low=labelCacheLow.get(pkg); if(low==null) continue; if(low.contains(lq) || pkg.toLowerCase().contains(lq)){ for(ResolveInfo ri: allAppsCache){ if(ri.activityInfo.packageName.equals(pkg)){ suggList.add(ri); break; } } } }catch(Exception e){} }
            for(ResolveInfo ri: allAppsCache){ if(suggList.size()>=80) break; String pkg=ri.activityInfo.packageName; boolean already=false; for(ResolveInfo x:suggList) if(x.activityInfo.packageName.equals(ri.activityInfo.packageName)){already=true; break;} if(already) continue; String low=labelCacheLow.get(pkg); if(low==null){ low=ri.loadLabel(getPackageManager()).toString().toLowerCase(); labelCacheLow.put(pkg, low); } if(low.contains(lq) || pkg.toLowerCase().contains(lq)) suggList.add(ri); }
            try{ Collections.sort(suggList,(a,b)-> Integer.compare(getUsage(b.activityInfo.packageName), getUsage(a.activityInfo.packageName))); }catch(Exception e){}
            if(rvSugg!=null){ rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.VISIBLE); }
        }catch(Exception e){} }

    void openFullDrawer(){
        try{
            List<ResolveInfo> tmp=allAppsCache; if(tmp.isEmpty()){ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0); LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:all){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri);} tmp=new ArrayList<>(map.values()); }
            final List<ResolveInfo> src=tmp;
            Dialog dlg=new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            dlg.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
            TextView title=new TextView(this); title.setText("✦ QUANTUM • "+src.size()+" APPS"); title.setTextSize(20); title.setTextColor(Color.WHITE); title.setPadding(50,90,40,30); title.setTypeface(null, Typeface.BOLD); title.setLetterSpacing(0.06f); root.addView(title);
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setPadding(16,16,16, getNavBarH()+24); rv.setClipToPadding(false);
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ View vv=getLayoutInflater().inflate(R.layout.item_app,p,false); return new H(vv); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=src.get(pos); h.lb.setText(ri.loadLabel(getPackageManager()).toString()); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->{ launchInstant(ri.activityInfo.packageName); dlg.dismiss(); }); }
                public int getItemCount(){ return src.size(); }
            });
            root.addView(rv, new LinearLayout.LayoutParams(-1,-1)); dlg.setContentView(root); dlg.show();
        }catch(Exception e){}
    }

    void loadWallpaperFast(){ try{ String s=prefs.getString("custom_wallpaper_uri",""); if(s.isEmpty()) return; Uri uri=Uri.parse(s); View bg=findV("wallpaper","bg","background","wall"); if(bg instanceof ImageView){ ((ImageView)bg).setScaleType(ImageView.ScaleType.CENTER_CROP); ((ImageView)bg).setImageURI(uri); } }catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri u=data.getData(); try{ getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri",u.toString()).apply(); loadWallpaperFast(); } }
    
    void loadManualTop(){ try{ manualTopPkgs.clear(); String s=prefs.getString("manual_top",""); if(!s.isEmpty()) for(String pkg:s.split(",")) if(!pkg.trim().isEmpty()) manualTopPkgs.add(pkg.trim()); }catch(Exception e){} }
    void trackUsage(String pkg){ try{ int c=usagePrefs.getInt(pkg,0)+1; usagePrefs.edit().putInt(pkg,c).apply(); }catch(Exception e){} }
    int getUsage(String pkg){ if(manualTopPkgs.contains(pkg)) return 999999; return usagePrefs.getInt(pkg,0); }
    
    void askDefaultLauncher(){
        try{
            if(prefs.getBoolean("default_asked", false)) return;
            Intent home=new Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); home.addCategory(Intent.CATEGORY_DEFAULT);
            ResolveInfo def=getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if(def!=null && !def.activityInfo.packageName.equals(getPackageName())){
                LinearLayout lay=new LinearLayout(this); lay.setOrientation(LinearLayout.VERTICAL); lay.setPadding(60,40,60,20);
                TextView tv=new TextView(this); tv.setText("Définir Quantum comme launcher par défaut pour un lancement instantané ?"); tv.setTextColor(Color.WHITE); tv.setTextSize(15); tv.setPadding(0,0,0,30);
                lay.addView(tv);
                android.widget.Button b1=new android.widget.Button(this); b1.setText("DEFINIR MAINTENANT");
                android.widget.Button b2=new android.widget.Button(this); b2.setText("Plus tard");
                lay.addView(b1); lay.addView(b2);
                AlertDialog dlg=createModernDialog("Launcher par défaut ?", lay);
                b1.setOnClickListener(v->{ try{ prefs.edit().putBoolean("default_asked", true).apply(); startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS)); }catch(Exception e){ try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(Intent.createChooser(i,"Choisir launcher")); }catch(Exception ex){} } dlg.dismiss(); });
                b2.setOnClickListener(v->{ prefs.edit().putBoolean("default_asked", true).apply(); dlg.dismiss(); });
                dlg.show();
            }
        }catch(Exception e){}
    }
    void showManualTopPicker(){
        try{
            List<ResolveInfo> apps=allAppsCache.isEmpty()? getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0) : allAppsCache;
            LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:apps){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName, ri); }
            final List<ResolveInfo> uniq=new ArrayList<>(map.values());
            Collections.sort(uniq,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; CheckBox cb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label); cb=v.findViewById(R.id.check);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup pg,int vt){ LinearLayout row=new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(24,20,24,20); ImageView iv=new ImageView(MainActivity.this); iv.setId(R.id.icon); row.addView(iv,new LinearLayout.LayoutParams(96,96)); TextView tv=new TextView(MainActivity.this); tv.setId(R.id.label); tv.setTextColor(Color.WHITE); tv.setPadding(24,0,0,0); row.addView(tv,new LinearLayout.LayoutParams(0,-2,1f)); CheckBox c=new CheckBox(MainActivity.this); c.setId(R.id.check); row.addView(c); return new H(row); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=uniq.get(pos); String pkg=ri.activityInfo.packageName; h.lb.setText(ri.loadLabel(getPackageManager()).toString()); Drawable cd=iconCache.get(pkg); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.cb.setOnCheckedChangeListener(null); h.cb.setChecked(manualTopPkgs.contains(pkg)); h.cb.setOnCheckedChangeListener((b,is)->{ if(is){ if(!manualTopPkgs.contains(pkg)&&manualTopPkgs.size()<12) manualTopPkgs.add(pkg);} else manualTopPkgs.remove(pkg); prefs.edit().putString("manual_top", String.join(",", manualTopPkgs)).apply(); }); }
                public int getItemCount(){ return uniq.size(); }
            });
            AlertDialog dlg=createModernDialog("Mes apps fusee (12 max)", rv); dlg.show();
        }catch(Exception e){}
    }
    
    void pickDockApp(int idx){
        try{
            List<ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
            LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:apps){ map.putIfAbsent(ri.activityInfo.packageName, ri); }
            final List<ResolveInfo> uniq=new ArrayList<>(map.values());
            Collections.sort(uniq,(a,b)-> (labelCache.containsKey(a.activityInfo.packageName)?labelCache.get(a.activityInfo.packageName):a.loadLabel(getPackageManager()).toString()).compareToIgnoreCase(labelCache.containsKey(b.activityInfo.packageName)?labelCache.get(b.activityInfo.packageName):b.loadLabel(getPackageManager()).toString()));
            final List<ResolveInfo> filtered=new ArrayList<>(uniq);
            LinearLayout container=new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
            EditText search=new EditText(this); search.setHint("Rechercher..."); search.setTextColor(0xFFFFFFFF); search.setHintTextColor(0xFFAAAAAA); search.setPadding(40,30,40,30); search.setBackgroundColor(0x22FFFFFF);
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this));
            container.addView(search, new LinearLayout.LayoutParams(-1,-2));
                try{
                    android.widget.Button importThemeBtn = new android.widget.Button(this);
                    importThemeBtn.setText("🎨 Importer thème système Oppo");
                    importThemeBtn.setTextColor(0xFFFFFFFF);
                    importThemeBtn.setAllCaps(false);
                    android.widget.LinearLayout.LayoutParams lp1 = new android.widget.LinearLayout.LayoutParams(-1,-2); lp1.setMargins(0,20,0,0);
                    importThemeBtn.setLayoutParams(lp1);
                    importThemeBtn.setOnClickListener(vv->{ try{ refreshFromSystemTheme(); android.widget.Toast.makeText(this,"Thème système importé",0).show(); ((android.app.AlertDialog)container.getTag()).dismiss(); }catch(Exception e){} });
                    container.addView(importThemeBtn);

                    android.widget.Button addWBtn = new android.widget.Button(this);
                    addWBtn.setText("✨ Ajouter widget (déplaçable)");
                    addWBtn.setTextColor(0xFFFFFFFF);
                    addWBtn.setAllCaps(false);
                    android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(-1,-2); lp2.setMargins(0,12,0,0);
                    addWBtn.setLayoutParams(lp2);
                    addWBtn.setOnClickListener(vw->{ try{ ((android.app.AlertDialog)container.getTag()).dismiss(); pickWidget(); }catch(Exception e){} });
                    container.addView(addWBtn);
                }catch(Exception e){}

            container.addView(rv, new LinearLayout.LayoutParams(-1,-1,1f));
            RecyclerView.Adapter ad=new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(1001); lb=v.findViewById(1002);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup pg,int vt){ LinearLayout row=new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(30,20,30,20); ImageView iv=new ImageView(MainActivity.this); iv.setId(1001); row.addView(iv,new LinearLayout.LayoutParams(96,96)); TextView tv=new TextView(MainActivity.this); tv.setId(1002); tv.setTextColor(Color.WHITE); tv.setPadding(24,0,0,0); row.addView(tv,new LinearLayout.LayoutParams(0,-2,1f)); return new H(row); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=filtered.get(pos); String pkg=ri.activityInfo.packageName; h.lb.setText(labelCache.containsKey(pkg)?labelCache.get(pkg):ri.loadLabel(getPackageManager()).toString()); Drawable cd=iconCache.get(pkg); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->{ try{ String cur=prefs.getString("dock",""); List<String> dock=new ArrayList<>(); if(cur!=null && !cur.isEmpty()){ for(String s:cur.split(",")) dock.add(s.trim()); } while(dock.size()<=idx) dock.add(""); dock.set(idx, pkg); String joined=String.join(",", dock); prefs.edit().putString("dock", joined).commit(); try{ setupDock(); }catch(Exception e){ try{ recreate(); }catch(Exception ex){} } }catch(Exception e){} try{ ((AlertDialog)container.getTag()).dismiss(); }catch(Exception e){} }); }
                public int getItemCount(){ return filtered.size(); }
            };
            rv.setAdapter(ad);
            search.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){} public void afterTextChanged(android.text.Editable s){ String q=s.toString().toLowerCase().trim(); filtered.clear(); if(q.isEmpty()) filtered.addAll(uniq); else { for(ResolveInfo ri:uniq){ String pkg=ri.activityInfo.packageName; String low=labelCacheLow.containsKey(pkg)?labelCacheLow.get(pkg):ri.loadLabel(getPackageManager()).toString().toLowerCase(); if(low.contains(q) || pkg.toLowerCase().contains(q)) filtered.add(ri); } } ad.notifyDataSetChanged(); } });
            AlertDialog dlg=createModernDialog("Choisir app dock "+(idx+1), container); container.setTag(dlg); dlg.show();
        }catch(Exception e){ e.printStackTrace(); }
    }


    @Override protected void onResume(){ super.onResume(); if(cachedWallpaperDrawable!=null && mainRoot!=null) mainRoot.setBackground(cachedWallpaperDrawable); else loadWallpaperPersist(); }
    void refreshFromSystemTheme(){
        try{
            cachedWallpaperDrawable=null; try{ iconCache.evictAll(); }catch(Exception e){} fullCacheReady=false;
            java.io.File wf=new java.io.File(getFilesDir(),"quantum_wall.jpg"); if(wf.exists()) wf.delete();
            try{ android.app.WallpaperManager wm=android.app.WallpaperManager.getInstance(this); android.graphics.drawable.Drawable sys=wm.getDrawable(); if(sys!=null){ cachedWallpaperDrawable=sys; getWindow().setBackgroundDrawable(sys); if(mainRoot!=null) mainRoot.setBackground(sys); } }catch(Exception e){}
            loadWallpaperPersist(); ensureFullCache();
            runOnUiThread(()->{ try{ setupDock(); }catch(Exception e){} });
        }catch(Exception e){}
    }
    void restoreWidgets(){
        try{
            String ids=prefs.getString("widgets",""); if(ids.isEmpty()) return;
            for(String s:ids.split(",")){ try{ int id=Integer.parseInt(s.trim()); android.appwidget.AppWidgetProviderInfo info=awm.getAppWidgetInfo(id); if(info!=null){ android.appwidget.AppWidgetHostView hv=awHost.createView(this,id,info); hv.setAppWidget(id,info); makeDraggable(hv,id); hv.setX(prefs.getInt("wx_"+id,0)); hv.setY(prefs.getInt("wy_"+id,200)); widgetContainer.addView(hv); } }catch(Exception e){} }
        }catch(Exception e){}
    }
    void makeDraggable(android.view.View v, int appId){
        final int[] off=new int[2]; final boolean[] dragging={false};
        v.setOnLongClickListener(vv->{ dragging[0]=true; vv.bringToFront(); return true; });
        v.setOnTouchListener((view, ev)->{
            if(!dragging[0]) return false;
            switch(ev.getAction()){
                case android.view.MotionEvent.ACTION_DOWN: off[0]=(int)(ev.getRawX()-view.getX()); off[1]=(int)(ev.getRawY()-view.getY()); break;
                case android.view.MotionEvent.ACTION_MOVE: view.setX(ev.getRawX()-off[0]); view.setY(ev.getRawY()-off[1]); break;
                case android.view.MotionEvent.ACTION_UP: dragging[0]=false; prefs.edit().putInt("wx_"+appId,(int)view.getX()).putInt("wy_"+appId,(int)view.getY()).apply(); break;
            }
            return true;
        });
    }
    void pickWidget(){ try{ int id=awHost.allocateAppWidgetId(); Intent pick=new Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK); pick.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(pick, REQ_PICK_WIDGET); }catch(Exception e){} }
    void addWidget(int id){
        try{
            android.appwidget.AppWidgetProviderInfo info=awm.getAppWidgetInfo(id);
            android.appwidget.AppWidgetHostView hv=awHost.createView(this,id,info); hv.setAppWidget(id,info);
            makeDraggable(hv,id); hv.setX(0); hv.setY(200);
            widgetContainer.addView(hv);
            String cur=prefs.getString("widgets",""); prefs.edit().putString("widgets", cur.isEmpty()? String.valueOf(id): cur+","+id).apply();
        }catch(Exception e){}
    }
    protected void onNewIntent
(Intent intent){ super.onNewIntent(intent); clearSearchNow(); }
    
    void loadWallpaperPersist(){
        try{
            String saved=prefs.getString("wallpaper_file","");
            java.io.File f=null;
            if(!saved.isEmpty()) f=new java.io.File(saved);
            if(f==null ||!f.exists()){
                java.io.File internal=new java.io.File(getFilesDir(),"quantum_wall.jpg");
                if(internal.exists()) f=internal;
            }
            if(f!=null && f.exists()){
                android.graphics.drawable.Drawable d=android.graphics.drawable.Drawable.createFromPath(f.getAbsolutePath());
                if(d!=null){
                    if(mainRoot!=null) mainRoot.setBackground(d);
                    else getWindow().setBackgroundDrawable(d);
                }
            }
        }catch(Exception e){}
    }
    void saveWallpaperPersist(android.net.Uri uri){
        try{
            java.io.InputStream in=getContentResolver().openInputStream(uri);
            java.io.File out=new java.io.File(getFilesDir(),"quantum_wall.jpg");
            java.io.OutputStream os=new java.io.FileOutputStream(out);
            byte[] buf=new byte[8192]; int r; while((r=in.read(buf))!=-1) os.write(buf,0,r);
            in.close(); os.close();
            prefs.edit().putString("wallpaper_file", out.getAbsolutePath()).commit();
            loadWallpaperPersist();
        }catch(Exception e){}
    }
    void setupAtAGlance(){
 TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.dateInfo); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} if(mainRoot!=null) mainRoot.postDelayed(this,30000); }}; r.run(); }
    String resolveIntentPkg(Intent intent){ try{ List<ResolveInfo> r=getPackageManager().queryIntentActivities(intent,0); if(r!=null&&!r.isEmpty()) return r.get(0).activityInfo.packageName; }catch(Exception e){} return null; }
    String getSmartDefault(int idx){ try{ if(idx==0){ String p=resolveIntentPkg(new Intent(Intent.ACTION_DIAL)); if(p!=null) return p;} if(idx==1){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))); if(p!=null) return p;} if(idx==2) return "com.android.settings"; if(idx==4){ String p=resolveIntentPkg(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); if(p!=null) return p;} if(idx==5){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com"))); if(p!=null) return p;} }catch(Exception e){} return defaultPkgs[idx]; }
    
    
    
    void setupDock(){
        try{
            LinearLayout dock=findViewById(R.id.dock); if(dock==null) return;
            dock.removeAllViews(); dock.setVisibility(View.VISIBLE);
            dock.setOrientation(LinearLayout.HORIZONTAL); dock.setGravity(android.view.Gravity.CENTER);
            int pad=(int)(8*getResources().getDisplayMetrics().density);
            dock.setPadding(pad,pad,pad,pad);
            String cur=prefs.getString("dock",""); java.util.List<String> dockPkgs=new java.util.ArrayList<>();
            if(cur!=null &&!cur.isEmpty()){ for(String s:cur.split(",")) dockPkgs.add(s.trim()); }
            while(dockPkgs.size()<7) dockPkgs.add("");
            for(int ui=0; ui<8; ui++){
                ImageView iv=new ImageView(this);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0, (int)(64*getResources().getDisplayMetrics().density), 1f);
                lp.setMargins(pad/2,0,pad/2,0); iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                if(ui==3){
                    iv.setImageResource(android.R.drawable.ic_menu_sort_by_size);
                    iv.setOnClickListener(v->{ showAppDrawer(); });
                }else{
                    int dockIdx = ui<3? ui : ui-1;
                    String pkg = dockIdx < dockPkgs.size()? dockPkgs.get(dockIdx) : "";
                    if(pkg==null || pkg.isEmpty()) iv.setImageResource(android.R.drawable.ic_menu_add);
                    else{ try{ Drawable d=iconCache.get(pkg); if(d==null){ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ ResolveInfo ri=getPackageManager().resolveActivity(it,0); if(ri!=null) d=ri.loadIcon(getPackageManager()); } } if(d!=null) iv.setImageDrawable(d); else iv.setImageResource(android.R.drawable.sym_def_app_icon); }catch(Exception e){ iv.setImageResource(android.R.drawable.sym_def_app_icon); } }
                    final int idx=dockIdx;
                    iv.setOnClickListener(v->{ try{ String pp=dockPkgs.get(idx); if(pp!=null &&!pp.isEmpty()) launchInstant(pp); else pickDockApp(idx); }catch(Exception e){} });
                    iv.setOnLongClickListener(v->{ pickDockApp(idx); return true; });
                }
                dock.addView(iv);
            }
        }catch(Exception e){ e.printStackTrace(); }
    }
    String findRealPkg(String pkg){ if(pkg==null) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return null; } }
    void updateDockIcon(ImageView iv, String pkg){ try{ Drawable c=iconCache.get(pkg); if(c!=null) iv.setImageDrawable(c); else iv.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); }catch(Exception e){ iv.setImageResource(android.R.drawable.sym_def_app_icon); } }
    void launchInstant(String pkg){ try{ trackUsage(pkg); Intent cached=launchIntentCache.get(pkg); if(cached==null){ cached=getPackageManager().getLaunchIntentForPackage(pkg); if(cached!=null){ cached.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION); launchIntentCache.put(pkg, cached); } } if(cached!=null){ try{ startActivity(cached); overridePendingTransition(0,0); return; }catch(Exception e){} } Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION); clearSearchNow(); startActivity(ii); overridePendingTransition(0,0); } }catch(Exception e){} }
    void clearSearchNow(){
        try{
            if(searchApps!=null){ searchApps.setText(""); searchApps.clearFocus(); }
            if(searchWeb!=null){ searchWeb.setText(""); searchWeb.clearFocus(); }
            suggList.clear();
            if(rvSugg!=null){ rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.GONE); }
            if(rvFav!=null) rvFav.setVisibility(View.VISIBLE);
            if(mainRoot!=null) mainRoot.requestFocus();
            android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm!=null){
                if(getCurrentFocus()!=null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);
                if(searchApps!=null) imm.hideSoftInputFromWindow(searchApps.getWindowToken(),0);
            }
        }catch(Exception e){}
    }
    void loadFavs(){ favPkgs.clear(); String s=prefs.getString("favs",""); if(!s.isEmpty()) favPkgs.addAll(Arrays.asList(s.split(","))); }
    void saveFavs(){ prefs.edit().putString("favs", String.join(",", favPkgs)).apply(); }
    void showAddFavDialog(){
        try{
            List<ResolveInfo> tmp=allAppsCache; if(tmp.isEmpty()){ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0); LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:all){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri);} tmp=new ArrayList<>(map.values()); }
            final List<ResolveInfo> src=tmp;
            Dialog dlg=new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen); dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
            TextView title=new TextView(this); title.setText("QUANTUM 5x5 - "+favPkgs.size()+"/25"); title.setTextSize(18); title.setTextColor(Color.WHITE); title.setPadding(50,90,40,30); title.setTypeface(null, Typeface.BOLD); title.setLetterSpacing(0.08f); root.addView(title);
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setPadding(16,16,16,getNavBarH()+14); rv.setClipToPadding(false);
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ View vv=getLayoutInflater().inflate(R.layout.item_app,p,false); return new H(vv); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=src.get(pos); String pkg=ri.activityInfo.packageName; h.lb.setText(ri.loadLabel(getPackageManager()).toString()); Drawable cd=iconCache.get(pkg); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); boolean added=favPkgs.contains(pkg); h.itemView.setAlpha(added?0.35f:1f); h.itemView.setOnClickListener(v->{ if(favPkgs.contains(pkg)) favPkgs.remove(pkg); else { if(favPkgs.size()>=25){ Toast.makeText(MainActivity.this,"Max 25",0).show(); return; } favPkgs.add(pkg); } saveFavs(); if(rvFav!=null) rvFav.setAdapter(new FavAdapter()); title.setText("QUANTUM 5x5 - "+favPkgs.size()+"/25"); notifyDataSetChanged(); }); }
                public int getItemCount(){ return src.size(); }
            });
            root.addView(rv,new LinearLayout.LayoutParams(-1,-1)); dlg.setContentView(root); dlg.show();
        }catch(Exception e){}
    }
    void handleWeb(){ if(searchWeb==null) return; String q=searchWeb.getText().toString().trim(); if(q.isEmpty()) return; String url=q.contains(" ")?"https://www.google.com/search?q="+Uri.encode(q):q.startsWith("http")?q:"https://"+q; try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }catch(Exception e){} }
    void applyGlassTheme(int col){
        try{
            float dens=getResources().getDisplayMetrics().density;
            GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(32*dens); bg.setColor(Color.argb(85, Color.red(col), Color.green(col), Color.blue(col))); bg.setStroke((int)(1*dens), Color.argb(90,255,255,255));
            for(String n:new String[]{"searchAppsMain","searchWebMain"}){ View v=findV(n); if(v!=null){ v.setBackground(bg); if(v instanceof EditText){ ((EditText)v).setTextColor(Color.WHITE); ((EditText)v).setHintTextColor(0x88FFFFFF); } } }
            View go=findV("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setBackground(glassBg(col, 32*dens, 85));
            View dock=findV("dock","dockBar","dock_container","dockContainer","bottomDock");
            if(dock!=null){ GradientDrawable dBg=new GradientDrawable(); dBg.setCornerRadius(36*dens); dBg.setColor(Color.argb(110, 20,20,20)); dBg.setStroke((int)(1*dens), Color.argb(70,255,255,255)); dock.setBackground(dBg); dock.setElevation(20*dens); }
        }catch(Exception e){}
    }
    void checkAndAskPermissions(){ try{ if(Build.VERSION.SDK_INT>=33){ if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},101); } }catch(Exception e){} }

    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager()).toString()); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ View vv=getLayoutInflater().inflate(R.layout.item_app,p,false); return new H(vv); } public void onBindViewHolder(H h,int pos){ try{ String pkg=favPkgs.get(pos); ResolveInfo ri=null; for(ResolveInfo r:allAppsCache){ if(r.activityInfo.packageName.equals(pkg)){ ri=r; break; } } if(ri!=null) h.lb.setText(ri.loadLabel(getPackageManager()).toString()); else try{ h.lb.setText(getPackageManager().getApplicationInfo(pkg,0).loadLabel(getPackageManager()).toString()); }catch(Exception e){ h.lb.setText(pkg); } Drawable cd=iconCache.get(pkg); if(cd!=null) h.ic.setImageDrawable(cd); else try{ h.ic.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); }catch(Exception e){} h.itemView.setOnClickListener(v->launchInstant(pkg)); h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return favPkgs.size(); } }
}
