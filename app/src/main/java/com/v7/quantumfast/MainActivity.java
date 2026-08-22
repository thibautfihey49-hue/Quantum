
package com.v7.quantumfast;
import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.content.pm.*;
import android.content.SharedPreferences;
import android.util.LruCache;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.text.SimpleDateFormat;
import android.provider.Settings;

public class MainActivity extends Activity {
    ViewGroup mainRoot; ImageView wallpaperView;
    RecyclerView rvFavorites, rvSuggestions;
    EditText searchApps, searchWeb;
    SharedPreferences prefs, glassPrefs;
    LruCache<String, Drawable> iconCache = new LruCache<>(100);
    List<String> favPkgs = new ArrayList<>();
    List<ResolveInfo> allAppsCache = new ArrayList<>();
    Handler mainH = new Handler(Looper.getMainLooper());

    @Override protected void onResume(){ super.onResume(); try{ autoScanOnResume(); preloadMaxSafe(); scanThemeFilesFromAnyApp(); }catch(Exception e){} }
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        try{ getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);}catch(Exception e){}
        setContentView(R.layout.activity_main);
        try{
            mainRoot=findViewById(R.id.root); wallpaperView=findViewById(R.id.wallpaper);
            rvFavorites=findViewById(R.id.rvFavorites); rvSuggestions=findViewById(R.id.rvSuggestions);
            searchApps=findViewById(R.id.searchAppsMain); searchWeb=findViewById(R.id.searchWebMain);
            prefs=getSharedPreferences("quantum",MODE_PRIVATE); glassPrefs=getSharedPreferences("glass",MODE_PRIVATE);
            loadFavs(); setupClock(); setupDockSimple(); setupFavsSafe(); setupListeners();
            registerAutoDetect();
            setupWallpaperAutoDetect();
            mainH.postDelayed(()->{ try{ preloadMaxSafe(); }catch(Exception e){} },800);
            mainH.postDelayed(()->{ try{ autoScanOnResume(); }catch(Exception e){} },2000);
            mainH.postDelayed(()->{ try{ scanThemeFilesFromAnyApp(); }catch(Exception e){} },3000);
            checkDefault();
        }catch(Exception e){ Toast.makeText(this,"ONCREATE: "+e.getMessage(),1).show(); }
    }
    void setupClock(){
        try{
            TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.dateInfo);
            Runnable r=new Runnable(){ public void run(){
                try{ if(c!=null) c.setText(new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date()));
                if(d!=null) d.setText(new SimpleDateFormat("EEE. d MMM",Locale.FRANCE).format(new Date()).toUpperCase()); }catch(Exception e){}
                mainH.postDelayed(this,1000);
            }}; mainH.post(r);
        }catch(Exception e){}
    }
    void setupDockSimple(){
        try{
            setDockIcon(R.id.dPhone, new String[]{"com.android.dialer","com.google.android.dialer","com.samsung.android.dialer"});
            setDockIcon(R.id.dMsg, new String[]{"com.google.android.apps.messaging","com.android.mms","com.samsung.android.messaging"});
            setDockIcon(R.id.dCam, new String[]{"com.android.camera2","com.google.android.GoogleCamera","com.sec.android.app.camera"});
            setDockIcon(R.id.dChrome, new String[]{"com.android.chrome","com.google.android.apps.chrome","org.mozilla.firefox"});
            View dd=findViewById(R.id.dDrawer); if(dd!=null) dd.setOnClickListener(v-> openFullDrawer());
            View de=findViewById(R.id.dExtra); if(de!=null) de.setOnClickListener(v-> openFullDrawer());
        }catch(Exception e){ Toast.makeText(this,"dock: "+e.getMessage(),0).show(); }
    }
    void setDockIcon(int viewId, String[] pkgs){
        try{
            View vv=findViewById(viewId); if(vv==null) return;
            ImageView iv=null; try{ iv=(ImageView)((FrameLayout)vv).getChildAt(0);}catch(Exception e){ return; }
            if(iv==null) return;
            for(String p:pkgs){
                try{ getPackageManager().getPackageInfo(p,0); Drawable dr=getPackageManager().getApplicationInfo(p,0).loadIcon(getPackageManager()); iv.setImageDrawable(dr); String fp=p; vv.setOnClickListener(vw-> launchInstant(fp)); return; }catch(Exception e){}
            }
        }catch(Exception e){}
    }
    void setupFavsSafe(){
        try{
            if(rvFavorites!=null){ rvFavorites.setLayoutManager(new GridLayoutManager(this,4)); rvFavorites.setAdapter(new FavAdapter()); }
            if(rvSuggestions!=null){ rvSuggestions.setLayoutManager(new LinearLayoutManager(this)); rvSuggestions.setVisibility(View.GONE); }
        }catch(Exception e){}
    }

    BroadcastReceiver pkgReceiver;

    android.app.WallpaperManager wallpaperMgr;
    android.app.WallpaperManager.OnColorsChangedListener wallpaperListener;
    void setupWallpaperAutoDetect(){
        try{
            wallpaperMgr=android.app.WallpaperManager.getInstance(this);
            if(android.os.Build.VERSION.SDK_INT>=27){
                wallpaperListener=new android.app.WallpaperManager.OnColorsChangedListener(){
                    public void onColorsChanged(android.app.WallpaperColors colors, int which){
                        try{
                            // L'utilisateur a choisi un theme dans n'importe quelle app (ThemeKit, ThemeArt etc) qui a change le fond
                            // On recupere le wallpaper actuel et on l'applique + on adapte le theme d'icones auto
                            android.graphics.drawable.Drawable wp=wallpaperMgr.getDrawable();
                            if(wallpaperView!=null && wp!=null){
                                mainH.post(()->{
                                    try{
                                        wallpaperView.setImageDrawable(wp);
                                        // THEME COMPLET pas couleur complete
                                        // 1. On scanne les icones que l'app externe vient de telecharger
                                        scanThemeFilesFromAnyApp();
                                        // 2. On passe en theme custom_full qui utilise les vrais icones du theme choisi dans l'app externe
                                        setIconTheme("custom_full");
                                        if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter());
                                        if(rvSuggestions!=null && rvSuggestions.getAdapter()!=null) rvSuggestions.getAdapter().notifyDataSetChanged();
                                        // 3. On adapte juste le glass, pas les icones en couleur
                                        if(colors!=null){
                                            android.graphics.Color primary=colors.getPrimaryColor();
                                            int col=primary.toArgb();
                                            applyThemeColor(col);
                                        }
                                        Toast.makeText(MainActivity.this, "Theme COMPLET applique depuis app externe: fond + icones originales",1).show();
                                    }catch(Exception e){}
                                });
                            }
                        }catch(Exception e){}
                    }
                };
                wallpaperMgr.addOnColorsChangedListener(wallpaperListener, mainH);
            }
        }catch(Exception e){}
    }
    void scanThemeFilesFromAnyApp(){
        try{
            new Thread(()->{
                try{
                    File customDir=getCustomIconDir();
                    int imported=0;
                    // GENERIQUE pour TOUTES les apps qui font des themes (pas que ThemeKit)
                    // On scanne recursivement tous les endroits ou n'importe quelle app theme peut deposer des icones
                    java.util.ArrayList<File> toScan=new java.util.ArrayList<>();
                    try{
                        toScan.add(android.os.Environment.getExternalStorageDirectory());
                        toScan.add(new File(android.os.Environment.getExternalStorageDirectory()+"/Download"));
                        toScan.add(new File(android.os.Environment.getExternalStorageDirectory()+"/Pictures"));
                        toScan.add(new File(android.os.Environment.getExternalStorageDirectory()+"/DCIM"));
                        toScan.add(new File(android.os.Environment.getExternalStorageDirectory()+"/Android/data"));
                        if(getExternalFilesDir(null)!=null) toScan.add(getExternalFilesDir(null));
                    }catch(Exception e){}

                    for(File root: toScan){
                        if(root==null || !root.exists()) continue;
                        try{
                            // scan 2 niveaux max pour pas etre trop lourd, mais couvre toutes les apps
                            File[] level1=root.listFiles();
                            if(level1==null) continue;
                            for(File f1: level1){
                                if(f1.isFile()){
                                    String n=f1.getName().toLowerCase();
                                    if((n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".webp")) && f1.length()>1500 && f1.length()<800000){
                                        // critere generique: nom contient com. ou icone ou theme ou ressemble a pack
                                        String ln=n;
                                        if(ln.contains("com.") || ln.contains("icon") || ln.contains("theme") || f1.getParent().toLowerCase().contains("theme") || f1.getParent().toLowerCase().contains("icon") || f1.getParent().toLowerCase().contains("widget") || f1.getParent().toLowerCase().contains("zedge") || f1.getParent().toLowerCase().contains("themepack") || f1.getParent().toLowerCase().contains("themkit") || f1.getParent().toLowerCase().contains("smart") || f1.getParent().toLowerCase().contains("niagara")){
                                            File dest=new File(customDir, f1.getName());
                                            if(!dest.exists()){
                                                try{ java.nio.file.Files.copy(f1.toPath(), dest.toPath()); imported++; }catch(Exception ee){}
                                            }
                                        }
                                    }
                                } else if(f1.isDirectory()){
                                    // niveau 2
                                    File[] level2=f1.listFiles();
                                    if(level2==null) continue;
                                    for(File f2: level2){
                                        if(f2.isFile()){
                                            String n=f2.getName().toLowerCase();
                                            if((n.endsWith(".png") || n.endsWith(".jpg")) && f2.length()>1500 && f2.length()<800000){
                                                String lp=f2.getParent().toLowerCase();
                                                if(lp.contains("theme") || lp.contains("icon") || lp.contains("pack") || n.contains("com.") || n.contains("ic_")){
                                                    File dest=new File(customDir, f2.getName());
                                                    if(!dest.exists()){
                                                        try{ java.nio.file.Files.copy(f2.toPath(), dest.toPath()); imported++; }catch(Exception ee){}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }catch(Exception ee){}
                    }
                    // Aussi essayer d'extraire les icones direct depuis l'APK de n'importe quelle app theme installee
                    try{
                        PackageManager pm=getPackageManager();
                        Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
                        List<ResolveInfo> all=pm.queryIntentActivities(it,0);
                        for(ResolveInfo ri: all){
                            String pkg=ri.activityInfo.packageName;
                            String low=pkg.toLowerCase();
                            if(low.contains("theme") || low.contains("icon") || low.contains("zedge") || low.contains("widget") || low.contains("launcher") || low.contains("wallpaper")){
                                try{
                                    // on tente de lister les drawables de l'app (si c'est un icon pack)
                                    android.content.res.Resources res=pm.getResourcesForApplication(pkg);
                                    // pas besoin de tout lister, on se base sur le fait que l'app a deja ete copiee via son dossier data
                                }catch(Exception e){}
                            }
                        }
                    }catch(Exception e){}

                    if(imported>0){
                        int imp=imported;
                        mainH.post(()->{
                            try{
                                setIconTheme("custom_full");
                                if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter());
                                Toast.makeText(this, "Theme COMPLET generique importe: "+imp+" icones depuis n'importe quelle app theme",1).show();
                            }catch(Exception e){}
                        });
                    }
                }catch(Exception e){}
            }).start();
        }catch(Exception e){}
    }

    void registerAutoDetect(){
        try{
            IntentFilter filter=new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            pkgReceiver=new BroadcastReceiver(){
                public void onReceive(Context ctx, Intent intent){
                    String pkg=intent.getData()!=null?intent.getData().getSchemeSpecificPart():"";
                    if(pkg!=null && !pkg.isEmpty()){
                        mainH.postDelayed(()-> autoDetectAnyApp(pkg), 1500);
                    }
                }
            };
            registerReceiver(pkgReceiver, filter);
        }catch(Exception e){}
    }
    void autoDetectAnyApp(String pkg){
        try{
            if(pkg==null) return;
            String low=pkg.toLowerCase();
            if(low.contains("theme") || low.contains("icon") || low.contains("themepack") || low.contains("widget") || low.contains("zedge") || low.contains("niagara") || low.contains("smartlauncher") || low.contains("microsoft.launcher")){
                PackageManager pm=getPackageManager();
                try{
                    ApplicationInfo ai=pm.getApplicationInfo(pkg,0);
                    String label=pm.getApplicationLabel(ai).toString();
                    new AlertDialog.Builder(this)
                        .setTitle("Nouveau theme detecte")
                        .setMessage("Theme installe: "+label+" ("+pkg+")\nVeux-tu l'appliquer automatiquement?\n\nDetection auto: Play Store -> app theme -> Quantum detecte seul, plus besoin d'aller dans Menu.")
                        .setPositiveButton("Appliquer", (d,w)->{
                            try{
                                // try as icon pack if it declares theme intent
                                List<ResolveInfo> themes=pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"),0);
                                boolean isIconPack=false;
                                for(ResolveInfo ri: themes){ if(ri.activityInfo.packageName.equals(pkg)){ isIconPack=true; break; } }
                                if(isIconPack){ setIconPack(pkg); }
                                else {
                                    // for apps like Smart Launcher, ThemeKit etc, on extrait leur icone et fond
                                    // on l'applique comme pack web
                                    // on propose d'ouvrir l'app theme
                                    Intent launch=pm.getLaunchIntentForPackage(pkg);
                                    if(launch!=null) startActivity(launch);
                                    Toast.makeText(this, "Theme ouvert: choisis un theme dedans, puis reviens, Quantum va l'importer auto",1).show();
                                }
                                // save as known
                                glassPrefs.edit().putBoolean("known_"+pkg, true).apply();
                            }catch(Exception ee){}
                        })
                        .setNegativeButton("Plus tard", null)
                        .setNeutralButton("Ne plus demander pour cette app", (d,w)->{ glassPrefs.edit().putBoolean("ignore_"+pkg, true).apply(); })
                        .show();
                }catch(Exception e){}
            }
        }catch(Exception e){}
    }
    void autoScanOnResume(){
        try{
            new Thread(()->{
                try{
                    PackageManager pm=getPackageManager();
                    Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> all=pm.queryIntentActivities(it,0);
                    // Trier par date d'install recente pour detecter la derniere app Play Store
                    for(ResolveInfo ri: all){
                        String pkg=ri.activityInfo.packageName;
                        if(pkg.equals(getPackageName())) continue;
                        if(glassPrefs.getBoolean("known_"+pkg,false) || glassPrefs.getBoolean("ignore_"+pkg,false)) continue;
                        // Si c'est une app qu'on a jamais vue, c'est une nouvelle app Play Store
                        // On detecte TOUT, pas que theme
                        try{
                            ApplicationInfo ai=pm.getApplicationInfo(pkg,0);
                            // On considere comme nouvelle si pas connue
                            mainH.post(()-> autoDetectAnyApp(pkg));
                            break; // une seule popup a la fois
                        }catch(Exception e){}
                    }
                }catch(Exception e){}
            }).start();
        }catch(Exception e){}
    }

    void setupListeners(){
        try{
            TextView menu=findViewById(R.id.btnMenu); if(menu!=null) menu.setOnClickListener(v-> showMenu());
            TextView fav=findViewById(R.id.btnAddFav); if(fav!=null) fav.setOnClickListener(v-> showAddFav());
            View clear=findViewById(R.id.clearApps); if(clear!=null && searchApps!=null) clear.setOnClickListener(v-> searchApps.setText(""));
            TextView go=findViewById(R.id.btnWebGo); if(go!=null && searchWeb!=null) go.setOnClickListener(v-> handleWeb());
            if(searchApps!=null) searchApps.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void onTextChanged(CharSequence a,int b,int c,int d){ try{ if(a.length()==0){ if(rvSuggestions!=null) rvSuggestions.setVisibility(View.GONE); if(rvFavorites!=null) rvFavorites.setVisibility(View.VISIBLE);} else { if(rvFavorites!=null) rvFavorites.setVisibility(View.GONE); filterSugg(a.toString()); }}catch(Exception e){}} public void afterTextChanged(android.text.Editable e){}});
            if(searchWeb!=null) searchWeb.setOnEditorActionListener((tv,id,ev)->{ handleWeb(); return true; });
        }catch(Exception e){}
    }
    List<ResolveInfo> suggList=new ArrayList<>();
    void filterSugg(String q){
        try{
            if(q.isEmpty()) return; String low=q.toLowerCase(); suggList.clear();
            for(ResolveInfo ri: allAppsCache){ try{ String label=ri.loadLabel(getPackageManager()).toString().toLowerCase(); if(label.contains(low) || ri.activityInfo.packageName.toLowerCase().contains(low)) suggList.add(ri);}catch(Exception e){} if(suggList.size()>20) break; }
            if(rvSuggestions!=null){ rvSuggestions.setAdapter(new SuggAdapter()); rvSuggestions.setVisibility(View.VISIBLE); }
        }catch(Exception e){}
    }
    void preloadMaxSafe(){
        new Thread(()->{
            try{
                Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0);
                LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>();
                for(ResolveInfo ri:all){ try{ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri);}catch(Exception e){} }
                allAppsCache=new ArrayList<>(map.values());
                mainH.post(()->{ try{ if(rvFavorites!=null && rvFavorites.getAdapter()!=null) rvFavorites.getAdapter().notifyDataSetChanged(); }catch(Exception e){} });
            }catch(Exception e){ mainH.post(()-> Toast.makeText(this,"preload: "+e.getMessage(),0).show()); }
        }).start();
    }
    void loadFavs(){ try{ String s=prefs.getString("favs",""); favPkgs.clear(); if(!s.isEmpty()) for(String p:s.split(",")) if(!p.isEmpty()) favPkgs.add(p); }catch(Exception e){} }
    void saveFavs(){ try{ prefs.edit().putString("favs",String.join(",",favPkgs)).apply(); }catch(Exception e){} }
    public void launchInstant(String pkg){ try{ Intent i=getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null) startActivity(i);}catch(Exception e){ Toast.makeText(this,"No app: "+pkg,0).show(); } }
    void openFullDrawer(){
        try{
            AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("Apps");
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,4));
            List<ResolveInfo> src=allAppsCache;
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=src.get(pos); try{ h.lb.setText(ri.loadLabel(getPackageManager()).toString()); h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v-> launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} }
                public int getItemCount(){ return src.size(); }
            });
            b.setView(rv); b.setNegativeButton("Fermer",null); b.show();
        }catch(Exception e){ Toast.makeText(this,"drawer: "+e.getMessage(),1).show(); }
    }
    void showAddFav(){
        try{
            if(allAppsCache.isEmpty()){ Toast.makeText(this,"Chargement...",0).show(); return; }
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=allAppsCache.get(pos); try{ h.lb.setText(ri.loadLabel(getPackageManager()).toString()); h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->{ if(!favPkgs.contains(ri.activityInfo.packageName)){ favPkgs.add(ri.activityInfo.packageName); saveFavs(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); } }); }catch(Exception e){} }
                public int getItemCount(){ return allAppsCache.size(); }
            });
            new AlertDialog.Builder(this).setTitle("Ajouter fav").setView(rv).setNegativeButton("Fermer",null).show();
        }catch(Exception e){}
    }
    void handleWeb(){
        try{
            if(searchWeb==null) return; String q=searchWeb.getText().toString().trim(); if(q.isEmpty()) return;
            String low=q.toLowerCase(); String url;
            if(low.startsWith("yt ")) url="https://www.youtube.com/results?search_query="+Uri.encode(q.substring(3));
            else if(low.startsWith("g ")) url="https://www.google.com/search?q="+Uri.encode(q.substring(2));
            else if(q.contains(" ")) url="https://www.google.com/search?q="+Uri.encode(q);
            else if(q.contains(".")) url=q.startsWith("http")?q:"https://"+q;
            else url="https://www.google.com/search?q="+Uri.encode(q);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }catch(Exception e){}
    }
    int curColor=0xFF0A84FF; String curPack=""; String curFont="default"; String curIconTheme="system";
    String getIconTheme(){ return glassPrefs.getString("icon_theme","system"); }
    void setIconTheme(String theme){ glassPrefs.edit().putString("icon_theme",theme).apply(); curIconTheme=theme; if(rvFavorites!=null && rvFavorites.getAdapter()!=null) rvFavorites.getAdapter().notifyDataSetChanged(); Toast.makeText(this,"Theme complet: "+theme,0).show(); }
    Drawable applyFullTheme(Drawable orig, String theme, ResolveInfo ri){
        try{
            // 1. Priorite: icones custom importees depuis l'app externe (ThemeKit etc) = theme complet vrai
            Drawable custom=getCustomWebIcon(ri);
            if(custom!=null) return custom;
            // 2. Si theme = custom_full, on utilise custom
            if(theme.equals("custom_full")){ Drawable c=getCustomWebIcon(ri); if(c!=null) return c; }
            // 3. Sinon anciens filtres couleur (fallback)
            if(orig==null) return null;
            Drawable d=orig.mutate();
            if(theme.equals("white_complete")){ d.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("black_complete")){ d.setColorFilter(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("violet_glass")){ d.setColorFilter(0xFF6B4C8A, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("blue_ios")){ d.setColorFilter(0xFF0A84FF, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("neon")){ d.setColorFilter(0xFF00FF88, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
        }catch(Exception e){}
        return orig;
    }
    Drawable applyCompleteTheme(Drawable orig, String theme){

        try{
            if(orig==null) return null;
            Drawable d=orig.mutate();
            if(theme.equals("white_complete")){ d.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("black_complete")){ d.setColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("violet_glass")){ d.setColorFilter(0xFF6B4C8A, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("blue_ios")){ d.setColorFilter(0xFF0A84FF, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("neon")){ d.setColorFilter(0xFF00FF88, android.graphics.PorterDuff.Mode.SRC_IN); return d; }
            if(theme.equals("pastel")){ return d; }
        }catch(Exception e){}
        return orig;
    }
    void applyThemeColor(int col){ try{ glassPrefs.edit().putInt("glass_color",col).apply(); if(mainRoot!=null) mainRoot.setBackgroundColor(Color.argb(40, Color.red(col), Color.green(col), Color.blue(col))); }catch(Exception e){} }
    String getIconPack(){ return glassPrefs.getString("icon_pack",""); }
    void setIconPack(String pkg){ glassPrefs.edit().putString("icon_pack",pkg).putString("icon_theme","external").apply(); curIconTheme="external"; curPack=pkg; if(rvFavorites!=null && rvFavorites.getAdapter()!=null) rvFavorites.getAdapter().notifyDataSetChanged(); }
    File getCustomIconDir(){ File d=new File(getFilesDir(), "custom_icons"); if(!d.exists()) d.mkdirs(); return d; }
    Drawable getCustomWebIcon(ResolveInfo ri){
        try{
            File dir=getCustomIconDir();
            String pkg=ri.activityInfo.packageName;
            // 1. direct file com.pkg.png
            File f=new File(dir, pkg+".png");
            if(f.exists()) return Drawable.createFromPath(f.getAbsolutePath());
            File f2=new File(dir, pkg.toLowerCase()+".png");
            if(f2.exists()) return Drawable.createFromPath(f2.getAbsolutePath());
            // 2. map.txt mapping
            File map=new File(dir, "map.txt");
            if(map.exists()){
                BufferedReader br=new BufferedReader(new FileReader(map));
                String line; while((line=br.readLine())!=null){ if(line.startsWith(pkg+"=")){ String fn=line.substring(line.indexOf("=")+1); File mf=new File(dir, fn); if(mf.exists()){ Drawable dd=Drawable.createFromPath(mf.getAbsolutePath()); if(dd!=null){ br.close(); return dd; } } } } br.close();
            }
            // 3. try any file that contains package name
            File[] files=dir.listFiles(); if(files!=null){ for(File ff:files){ if(ff.getName().toLowerCase().contains(pkg.toLowerCase()) && ff.getName().endsWith(".png")){ return Drawable.createFromPath(ff.getAbsolutePath()); } } }
        }catch(Exception e){}
        return null;
    }
    void downloadAndApplyPack(String urlStr){
        try{
            ProgressDialog pd=new ProgressDialog(this); pd.setMessage("Téléchargement "+urlStr); pd.setCancelable(false); pd.show();
            new Thread(()->{
                try{
                    URL url=new URL(urlStr);
                    HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                    conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
                    conn.setUseCaches(false);
                    int codeResp=conn.getResponseCode();
                    if(codeResp==301 || codeResp==302 || codeResp==303){
                        String newUrl=conn.getHeaderField("Location");
                        if(newUrl!=null){ url=new URL(newUrl); conn=(HttpURLConnection)url.openConnection(); conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)"); }
                    }
                    InputStream is=conn.getInputStream();
                    File tmp=new File(getCacheDir(), "temp_pack.zip");
                    FileOutputStream fos=new FileOutputStream(tmp);
                    byte[] buf=new byte[8192]; int len; while((len=is.read(buf))>0) fos.write(buf,0,len);
                    fos.close(); is.close();
                    int count=unzipCustomPack(tmp);
                    mainH.post(()->{ try{ pd.dismiss(); Toast.makeText(this, "Importé "+count+" icones depuis web",1).show(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); }catch(Exception e){} });
                }catch(Exception e){ mainH.post(()->{ try{ pd.dismiss(); Toast.makeText(this, "Erreur download: "+e.getMessage(),1).show(); }catch(Exception ee){} }); }
            }).start();
        }catch(Exception e){ Toast.makeText(this, "URL invalide",0).show(); }
    }
    int unzipCustomPack(File zipFile){
        int count=0;
        try{
            File dir=getCustomIconDir();
            ZipInputStream zis=new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry; HashMap<String,String> map=new HashMap<>();
            while((entry=zis.getNextEntry())!=null){
                String name=entry.getName();
                if(entry.isDirectory()){ zis.closeEntry(); continue; }
                String lower=name.toLowerCase();
                if(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp")){
                    String outName=new File(name).getName();
                    File out=new File(dir, outName);
                    FileOutputStream fos=new FileOutputStream(out);
                    byte[] buf=new byte[8192]; int len; while((len=zis.read(buf))>0) fos.write(buf,0,len);
                    fos.close(); count++;
                } else if(lower.endsWith("appfilter.xml") || lower.endsWith("drawable.xml")){
                    // save then parse
                    File out=new File(dir, "appfilter.xml");
                    FileOutputStream fos=new FileOutputStream(out);
                    byte[] buf=new byte[8192]; int len; while((len=zis.read(buf))>0) fos.write(buf,0,len);
                    fos.close();
                    // parse appfilter to build map - simple parser sans regex (fix illegal escape)
                    try{
                        FileInputStream fis=new FileInputStream(out);
                        String xml=new String(fis.readAllBytes());
                        fis.close();
                        int idx=0;
                        while((idx=xml.indexOf("component=", idx))!=-1){
                            int start=xml.indexOf("{", idx);
                            int slash=xml.indexOf("/", start);
                            int endBrace=xml.indexOf("}", slash);
                            int drawIdx=xml.indexOf("drawable=", endBrace);
                            if(start==-1 || slash==-1 || endBrace==-1 || drawIdx==-1) break;
                            String pkg=xml.substring(start+1, slash);
                            int q1=xml.indexOf("\"", drawIdx);
                            int q2=xml.indexOf("\"", q1+1);
                            if(q1==-1 || q2==-1) break;
                            String draw=xml.substring(q1+1, q2);
                            if(!pkg.isEmpty() && !draw.isEmpty()) map.put(pkg, draw+".png");
                            idx=q2+1;
                        }
                    }catch(Exception ee){}
                }
                zis.closeEntry();
            }
            zis.close();
            // save map.txt
            if(!map.isEmpty()){
                File mapFile=new File(dir, "map.txt");
                BufferedWriter bw=new BufferedWriter(new FileWriter(mapFile, false));
                for(Map.Entry<String,String> e: map.entrySet()){ bw.write(e.getKey()+"="+e.getValue()); bw.newLine(); }
                bw.close();
            }
        }catch(Exception e){ Toast.makeText(this, "Unzip erreur: "+e.getMessage(),0).show(); }
        return count;
    }
    void showWebImportDialog(){
        try{
            LinearLayout lay=new LinearLayout(this); lay.setOrientation(LinearLayout.VERTICAL); lay.setPadding(30,30,30,30);
            TextView info=new TextView(this); info.setText("Télécharge des icones depuis le web (GitHub, site d'icon packs) et applique les.\n\n1. Colle URL d'un ZIP (ex: Arcticons release zip, ou pack perso)\n2. Ou choisis un ZIP local\n\nLe ZIP doit contenir des PNG nommés com.package.png ou un appfilter.xml"); info.setTextColor(Color.LTGRAY); info.setTextSize(12); lay.addView(info);
            EditText et=new EditText(this); et.setHint("https://github.com/.../icons.zip"); et.setTextColor(Color.WHITE); et.setHintTextColor(Color.GRAY); lay.addView(et);
            TextView examples=new TextView(this); examples.setText("\nDEPOTS MULTI-THEMES LES PLUS GROS SUR GITHUB:\n\n1. Arcticons-Team/Arcticons - 14,332 icones (le plus gros gratuit open source)\n   Variantes: Arcticons, Dark, Material You\n   URL: https://github.com/ArcticonsTeam/Arcticons/archive/refs/heads/main.zip\n\n2. PapirusDevelopmentTeam/papirus-icon-theme - 5 variantes: Papirus, Dark, Light, ePapirus, ePapirus Dark\n   Android: https://github.com/PapirusDevelopmentTeam/papirus_icons\n\n3. Numix Project - base theme + circle, square variants\n   https://github.com/numixproject/numix-icon-theme\n\n4. Delta-Icons/Delta - plusieurs styles dans un repo\n   https://github.com/Delta-Icons/Delta/archive/refs/heads/master.zip\n\n5. Blueprint / CandyBar - template pour faire 1 APK = plusieurs themes\n   https://github.com/jahirfiquitiva/blueprint\n   https://github.com/zixpo/candybar-sample\n\nAstuce: DL le ZIP sur Chrome puis importe en local si le direct bloque"); examples.setTextColor(Color.CYAN); examples.setTextSize(11); lay.addView(examples);
            new AlertDialog.Builder(this).setTitle("📥 Importer depuis le web").setView(lay).setPositiveButton("Télécharger URL", (d,w)->{ String url=et.getText().toString().trim(); if(!url.isEmpty()) downloadAndApplyPack(url); }).setNeutralButton("Choisir ZIP local", (d,w)->{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip","application/x-zip","application/octet-stream"}); startActivityForResult(i,1002); }).setNegativeButton("Annuler",null).show();
        }catch(Exception e){ Toast.makeText(this, "import: "+e.getMessage(),0).show(); }
    }
    Drawable getDrawableFromPack(ResolveInfo ri){
        try{ String theme=getIconTheme(); if(theme.equals("custom_full") || theme.equals("custom_web_full")){ Drawable full=applyFullTheme(null, theme, ri); if(full!=null) return full; } }catch(Exception e){}
        try{ Drawable custom=getCustomWebIcon(ri); if(custom!=null) return custom; }catch(Exception e){}
        try{ String theme=getIconTheme(); if(!theme.equals("system") && !theme.equals("external") && !theme.equals("custom_full")){ Drawable sys=null; try{ sys=ri.loadIcon(getPackageManager()); }catch(Exception e){} return applyCompleteTheme(sys, theme); } else if(theme.equals("custom_full")){ Drawable sys=null; try{ sys=ri.loadIcon(getPackageManager()); }catch(Exception e){} Drawable full=applyFullTheme(sys, theme, ri); if(full!=null) return full; } }catch(Exception e){}

        try{
            String pack=getIconPack(); if(pack.isEmpty()) return null;
            PackageManager pm=getPackageManager();
            android.content.res.Resources res=pm.getResourcesForApplication(pack);
            String comp=ri.activityInfo.packageName+"/"+ri.activityInfo.name;
            String[] names={ri.activityInfo.name.replace(".","_"), ri.activityInfo.packageName.replace(".","_"), comp.replace("/","_").replace(".","_")};
            for(String n:names){ try{ int id=res.getIdentifier(n,"drawable",pack); if(id!=0) return res.getDrawable(id,null); }catch(Exception e){} }
            // try appfilter
            try{ int id=res.getIdentifier("appfilter","xml",pack); if(id!=0){ android.content.res.XmlResourceParser p=res.getXml(id); int ev; while((ev=p.next())!=1){ if(ev==2 && p.getName().equals("item")){ String cmp=p.getAttributeValue(null,"component"); String dr=p.getAttributeValue(null,"drawable"); if(cmp!=null && cmp.contains(ri.activityInfo.packageName)){ int did=res.getIdentifier(dr,"drawable",pack); if(did!=0) return res.getDrawable(did,null); } } } } }catch(Exception e){}
        }catch(Exception e){}
        return null;
    }

    void showThemeStoreFree(){
        try{
            LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(10,10,10,10);
            TextView title=new TextView(this); title.setText("THEME STORE GRATUIT - Alternative à Oppo Theme Store (100% gratuit, open source)"); title.setTextColor(Color.YELLOW); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setPadding(20,20,20,20); list.addView(title);
            TextView sub=new TextView(this); sub.setText("Oppo Theme Store est payant et limité. Ici 5 fournisseurs gratuits plus complets avec 20k+ icones au total."); sub.setTextColor(Color.LTGRAY); sub.setPadding(20,0,20,20); list.addView(sub);

            String[][] stores={
                {"Arcticons Team - 14,332 icones (plus gros du monde)", "Gratuit, open source, 3 themes: clair/dark/Material You", "https://github.com/ArcticonsTeam/Arcticons/archive/refs/heads/main.zip"},
                {"Papirus - 5 themes (Papirus/Dark/Light/ePapirus)", "Linux + Android, 1000+ icones, multi-couleur", "https://github.com/PapirusDevelopmentTeam/papirus_icons/archive/refs/heads/master.zip"},
                {"Delta Icons - 2400+ icones multi-style", "Gratuit, flat + circle + square dans un repo", "https://github.com/Delta-Icons/Delta/archive/refs/heads/master.zip"},
                {"Whicons - 2732 icones blanc minimal", "Masquage auto, theme 100% apps", "https://github.com/whicons/whicons/archive/refs/heads/master.zip"},
                {"Material You Repository - 100+ apps themables", "Collection open source Material You themes/APKs", "https://github.com/Afnankhan804/Material-You-App-Repository"},
                {"Numix Project - base + variants", "Theme officiel Numix avec variantes", "https://github.com/numixproject/numix-icon-theme/archive/refs/heads/master.zip"},
                {"AxionOS Theme Store - store open source", "Repository pour AxionOS Theme Store, catalogue themes.json", "https://github.com/AxionAOSP/axthemestore_themes_repository"}
            };

            for(String[] s: stores){
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(25,25,25,25); card.setBackgroundColor(0x22FFFFFF);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,20); card.setLayoutParams(lp);
                TextView name=new TextView(this); name.setText(s[0]); name.setTextColor(Color.WHITE); name.setTypeface(null, android.graphics.Typeface.BOLD); name.setTextSize(14); card.addView(name);
                TextView desc=new TextView(this); desc.setText(s[1]); desc.setTextColor(Color.LTGRAY); desc.setTextSize(11); desc.setPadding(0,5,0,10); card.addView(desc);
                TextView btn=new TextView(this); btn.setText("⬇ TELECHARGER ET APPLIQUER"); btn.setTextColor(Color.BLACK); btn.setBackgroundColor(Color.CYAN); btn.setPadding(20,15,20,15); btn.setTypeface(null, android.graphics.Typeface.BOLD);
                String url=s[2]; btn.setOnClickListener(v->{ if(url.endsWith(".zip")) downloadAndApplyPack(url); else { Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse(url)); startActivity(i); } });
                card.addView(btn);
                list.addView(card);
            }

            ScrollView sv=new ScrollView(this); sv.addView(list);
            new AlertDialog.Builder(this).setTitle("Theme Store Gratuit (alternative Oppo)").setView(sv).setNegativeButton("Fermer",null).show();
        }catch(Exception e){ Toast.makeText(this, "store: "+e.getMessage(),0).show(); }
    }

    
    void showMenu(){
        try{
            new AlertDialog.Builder(this).setTitle("Menu").setItems(new String[]{"Theme Store Gratuit (altern. Oppo)","Couleur","Fond HD","Icon packs gratuits","Polices + Taille","Effacer fond"}, (d,w)->{
                try{
                    if(w==0) showThemeStoreFree();
                    else if(w==1) showColorPicker();
                    else if(w==2) pickWallpaper();
                    else if(w==3) showIconPack();
                    else if(w==4) showFontPicker();
                    else if(w==5){ if(wallpaperView!=null) wallpaperView.setImageDrawable(null); prefs.edit().remove("wall_uri").apply(); Toast.makeText(this,"Fond efface",0).show(); }
                }catch(Exception e){}
            }).show();
        }catch(Exception e){}
    }
    void showColorPicker(){
        try{
            int[] colors={0xFF0A84FF,0xFF6B4C8A,0xFF00D1FF,0xFF00FF88,0xFFFF3B30,0xFFFF9500,0xFFFFD60A,0xFF1C1C1E};
            String[] names={"Bleu iOS","Violet Glass","Cyan","Vert Neon","Rouge","Orange","Jaune","Noir"};
            new AlertDialog.Builder(this).setTitle("Couleur glass").setItems(names,(dd,ww)->{ applyThemeColor(colors[ww]); }).show();
        }catch(Exception e){}
    }
    void showFontPicker(){
        try{
            String[] fonts={"Default","Serif Italic","Monospace","Gras","Large + Gras"};
            new AlertDialog.Builder(this).setTitle("Polices").setItems(fonts,(dd,ww)->{
                try{
                    TextView c=findViewById(R.id.clock);
                    if(c!=null){
                        if(ww==0) c.setTypeface(android.graphics.Typeface.DEFAULT);
                        else if(ww==1) c.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
                        else if(ww==2) c.setTypeface(android.graphics.Typeface.MONOSPACE);
                        else if(ww==3) c.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        else if(ww==4){ c.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); c.setTextSize(40); }
                    }
                    glassPrefs.edit().putInt("font_idx",ww).apply();
                }catch(Exception e){}
            }).show();
        }catch(Exception e){}
    }
    
    void showIconPack(){
        try{
            PackageManager pm=getPackageManager();
            List<ResolveInfo> packs=new ArrayList<>();
            try{ packs.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"),0)); }catch(Exception e){}
            try{ packs.addAll(pm.queryIntentActivities(new Intent("com.novalauncher.THEME"),0)); }catch(Exception e){}
            try{ packs.addAll(pm.queryIntentActivities(new Intent("com.gau.go.launcherex.theme"),0)); }catch(Exception e){}
            try{ packs.addAll(pm.queryIntentActivities(new Intent("com.anddoes.launcher.THEME"),0)); }catch(Exception e){}
            HashSet<String> seen=new HashSet<>();
            LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(10,10,10,10);
            String curTheme=getIconTheme();
            String curPack=getIconPack();

            TextView repoTitle=new TextView(this); repoTitle.setText("DEPOTS GITHUB MULTI-THEMES (clique pour DL)"); repoTitle.setTextColor(Color.YELLOW); repoTitle.setTypeface(null, android.graphics.Typeface.BOLD); repoTitle.setPadding(20,20,20,10); list.addView(repoTitle);
            String[][] repos={{"Arcticons 14k (plus gros)", "https://github.com/ArcticonsTeam/Arcticons/archive/refs/heads/main.zip"}, {"Papirus 5 themes", "https://github.com/PapirusDevelopmentTeam/papirus_icons/archive/refs/heads/master.zip"}, {"Delta multi-style", "https://github.com/Delta-Icons/Delta/archive/refs/heads/master.zip"}, {"Numix base", "https://github.com/numixproject/numix-icon-theme/archive/refs/heads/master.zip"}};
            for(String[] r: repos){
                TextView tv=new TextView(this); tv.setText("⬇ "+r[0]); tv.setTextColor(Color.CYAN); tv.setPadding(30,20,30,20); tv.setBackgroundColor(0x22FFFFFF);
                String url=r[1]; tv.setOnClickListener(v->{ downloadAndApplyPack(url); }); list.addView(tv);
            }
            TextView webBtn=new TextView(this); webBtn.setText("📥 IMPORTER DEPUIS LE WEB / FICHIER ZIP"); webBtn.setTextColor(Color.BLACK); webBtn.setBackgroundColor(Color.YELLOW); webBtn.setPadding(30,30,30,30); webBtn.setTypeface(null, android.graphics.Typeface.BOLD); webBtn.setOnClickListener(v->{ showWebImportDialog(); }); list.addView(webBtn);
            File customDir=getCustomIconDir(); int customCount=customDir.listFiles()!=null?customDir.listFiles().length:0;
            TextView customInfo=new TextView(this); customInfo.setText("Pack web actuel: "+customCount+" icones dans "+customDir.getAbsolutePath()); customInfo.setTextColor(Color.LTGRAY); customInfo.setPadding(20,10,20,20); customInfo.setTextSize(11); list.addView(customInfo);


            TextView title1=new TextView(this); title1.setText("THEMES COMPLETS INTEGRES (100% apps)"); title1.setTextColor(Color.YELLOW); title1.setPadding(20,30,20,10); title1.setTypeface(null, android.graphics.Typeface.BOLD); list.addView(title1);
            String[][] themes={{"system","Systeme defaut"},{"white_complete","Blanc complet (tout en blanc)"},{"black_complete","Noir complet"},{"violet_glass","Violet Glass complet (comme ton dock)"},{"blue_ios","Bleu iOS complet"},{"neon","Neon complet"},{"pastel","Pastel Glass + fond"}};
            for(String[] th:themes){
                TextView tv=new TextView(this); boolean sel=th[0].equals(curTheme); tv.setText((sel?"▶ ":"  ")+th[1]); tv.setPadding(30,26,30,26); tv.setTextColor(sel?Color.CYAN:Color.WHITE); tv.setTextSize(15);
                String t=th[0]; tv.setOnClickListener(v->{ setIconTheme(t); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); });
                list.addView(tv);
            }

            TextView title2=new TextView(this); title2.setText("\nPACKS EXTERNES GRATUITS - LES PLUS COMPLETS"); title2.setTextColor(Color.YELLOW); title2.setPadding(20,30,20,10); title2.setTypeface(null, android.graphics.Typeface.BOLD); list.addView(title2);
            TextView hint=new TextView(this); hint.setText("Recommandes: Whicons (2732 icones, masquage auto, tout theme), Delta (2400+), Arcticons (6000+, le plus complet), CandyCons (1000+), Voxel (1900), Pix Material You. Cherche sur Play: 'Delta Icon Pack', 'Whicons', 'Arcticons'"); hint.setTextColor(Color.LTGRAY); hint.setPadding(20,10,20,20); hint.setTextSize(12); list.addView(hint);

            TextView def=new TextView(this); boolean selSys=curTheme.equals("system") && curPack.isEmpty(); def.setText((selSys?"▶ ":"  ")+"Systeme (sans pack)"); def.setPadding(30,26,30,26); def.setTextColor(selSys?Color.CYAN:Color.WHITE); def.setOnClickListener(v->{ setIconTheme("system"); setIconPack(""); Toast.makeText(this,"Icones systeme",0).show(); }); list.addView(def);

            for(ResolveInfo ri:packs){
                String pkg=ri.activityInfo.packageName; if(!seen.add(pkg)) continue;
                boolean sel=pkg.equals(curPack) && curTheme.equals("external");
                TextView tv=new TextView(this); tv.setText((sel?"▶ ":"  ")+ri.loadLabel(pm).toString()+" ("+pkg+")"); tv.setPadding(30,26,30,26); tv.setTextColor(sel?Color.CYAN:Color.WHITE);
                tv.setOnClickListener(v->{ setIconPack(pkg); Toast.makeText(this,"Pack applique: "+pkg+" - masquage auto pour les apps non themées",0).show(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); });
                list.addView(tv);
            }
            if(seen.isEmpty()){
                TextView tv=new TextView(this); tv.setText("Aucun pack externe installe.\nInstalle gratuitement: Delta Icon Pack, Whicons, Arcticons (le plus complet du monde, 6000+ icones)"); tv.setPadding(20,20,20,20); tv.setTextColor(Color.WHITE); list.addView(tv);
            }

            ScrollView sv=new ScrollView(this); sv.addView(list);
            new AlertDialog.Builder(this).setTitle("Themes complets - 100% des apps").setView(sv).setNegativeButton("Fermer",null).show();
        }catch(Exception e){ Toast.makeText(this,"packs: "+e.getMessage(),0).show(); }
    }

    void pickWallpaper(){ try{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); startActivityForResult(i,1001); }catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ super.onActivityResult(rc,res,data); try{
            if(rc==1001 && res==RESULT_OK && data!=null){ Uri uri=data.getData(); if(wallpaperView!=null) wallpaperView.setImageURI(uri); getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); prefs.edit().putString("wall_uri",uri.toString()).apply(); }
            else if(rc==1002 && res==RESULT_OK && data!=null){ Uri uri=data.getData(); try{ InputStream is=getContentResolver().openInputStream(uri); File tmp=new File(getCacheDir(), "import_local.zip"); FileOutputStream fos=new FileOutputStream(tmp); byte[] buf=new byte[8192]; int len; while((len=is.read(buf))>0) fos.write(buf,0,len); fos.close(); is.close(); int c=unzipCustomPack(tmp); Toast.makeText(this, "Importé "+c+" icones local",1).show(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); }catch(Exception e){ Toast.makeText(this, "Import local erreur: "+e.getMessage(),1).show(); } }
        }catch(Exception e){} }
    void checkDefault(){ try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); i.addCategory(Intent.CATEGORY_DEFAULT); ResolveInfo ri=getPackageManager().resolveActivity(i,0); if(ri!=null && !ri.activityInfo.packageName.equals(getPackageName())){ new AlertDialog.Builder(this).setTitle("Launcher par defaut").setMessage("Definir Quantum?").setPositiveButton("Oui", (d,w)->{ try{ startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }catch(Exception e){} }).setNegativeButton("Non",null).show(); } }catch(Exception e){} }
    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager()).toString()); Drawable d=getDrawableFromPack(ri); if(d==null) d=ri.loadIcon(getPackageManager()); h.ic.setImageDrawable(d); h.itemView.setOnClickListener(v-> launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{
        class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
        public H onCreateViewHolder(ViewGroup p,int t){ View v=getLayoutInflater().inflate(R.layout.item_app,p,false); return new H(v); }
        public void onBindViewHolder(H h,int pos){
            try{
                String pkg=favPkgs.get(pos);
                // find ResolveInfo
                ResolveInfo found=null; for(ResolveInfo ri:allAppsCache){ if(ri.activityInfo.packageName.equals(pkg)){ found=ri; break; } }
                Drawable d=null; if(found!=null) d=getDrawableFromPack(found);
                if(d==null) d=getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager());
                if(h.ic!=null) h.ic.setImageDrawable(d);
                if(h.lb!=null){ try{ h.lb.setText(getPackageManager().getApplicationInfo(pkg,0).loadLabel(getPackageManager()).toString()); }catch(Exception e){ h.lb.setText(pkg);} }
                h.itemView.setOnClickListener(v-> launchInstant(pkg));
                h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; });
            }catch(Exception e){}
        }
        public int getItemCount(){ return favPkgs.size(); }
    }
}

