
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
    BroadcastReceiver pkgReceiver;
    android.app.WallpaperManager wallpaperMgr;
    android.app.WallpaperManager.OnColorsChangedListener wallpaperListener;
    android.appwidget.AppWidgetManager widgetManager;
    android.appwidget.AppWidgetHost widgetHost;
    LinearLayout widgetContainer;
    static final int WIDGET_HOST_ID=9001;
    static final int REQ_PICK_WIDGET=1101;
    static final int REQ_CREATE_WIDGET=1102;

    @Override protected void onResume(){ super.onResume(); try{ autoScanOnResume(); preloadMaxSafe(); scanThemeFilesFromAnyApp(); loadWidgets(); }catch(Exception e){} }
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        try{ getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);}catch(Exception e){}
        setContentView(R.layout.activity_main);
        try{
            mainRoot=findViewById(R.id.root); wallpaperView=findViewById(R.id.wallpaper);
            rvFavorites=findViewById(R.id.rvFavorites); rvSuggestions=findViewById(R.id.rvSuggestions);
            searchApps=findViewById(R.id.searchAppsMain); searchWeb=findViewById(R.id.searchWebMain);
            widgetContainer=findViewById(R.id.widgetContainer);
            prefs=getSharedPreferences("quantum",MODE_PRIVATE); glassPrefs=getSharedPreferences("glass",MODE_PRIVATE);
            loadFavs(); setupClock(); setupDockSimple(); setupFavsSafe(); setupListeners();
            setupWidgetHost(); registerAutoDetect(); setupWallpaperAutoDetect();
            mainH.postDelayed(()->{ try{ preloadMaxSafe(); }catch(Exception e){} },800);
            mainH.postDelayed(()->{ try{ autoScanOnResume(); scanThemeFilesFromAnyApp(); }catch(Exception e){} },2000);
            checkDefault();
        }catch(Exception e){ Toast.makeText(this,"ONCREATE: "+e.getMessage(),1).show(); }
    }

    void setupClock(){
        TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.dateInfo);
        Runnable r=new Runnable(){ public void run(){
            try{ if(c!=null) c.setText(new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date()));
            if(d!=null) d.setText(new SimpleDateFormat("EEE. d MMM",Locale.FRANCE).format(new Date()).toUpperCase()); }catch(Exception e){}
            mainH.postDelayed(this,1000);
        }}; mainH.post(r);
    }
    void setupDockSimple(){
        setDockIcon(R.id.dPhone, new String[]{"com.android.dialer","com.google.android.dialer"});
        setDockIcon(R.id.dMsg, new String[]{"com.google.android.apps.messaging","com.android.mms"});
        setDockIcon(R.id.dCam, new String[]{"com.android.camera2","com.google.android.GoogleCamera"});
        setDockIcon(R.id.dChrome, new String[]{"com.android.chrome"});
        View dd=findViewById(R.id.dDrawer); if(dd!=null) dd.setOnClickListener(v-> openFullDrawer());
        View de=findViewById(R.id.dExtra); if(de!=null) de.setOnClickListener(v-> openFullDrawer());
    }
    void setDockIcon(int viewId, String[] pkgs){
        View vv=findViewById(viewId); if(vv==null) return;
        ImageView iv=null; try{ iv=(ImageView)((FrameLayout)vv).getChildAt(0);}catch(Exception e){ return; }
        if(iv==null) return;
        for(String p:pkgs){ try{ getPackageManager().getPackageInfo(p,0); Drawable dr=getPackageManager().getApplicationInfo(p,0).loadIcon(getPackageManager()); iv.setImageDrawable(dr); String fp=p; vv.setOnClickListener(vw-> launchInstant(fp)); return; }catch(Exception e){} }
    }
    void setupFavsSafe(){
        if(rvFavorites!=null){ rvFavorites.setLayoutManager(new GridLayoutManager(this,4)); rvFavorites.setAdapter(new FavAdapter()); }
        if(rvSuggestions!=null){ rvSuggestions.setLayoutManager(new LinearLayoutManager(this)); rvSuggestions.setVisibility(View.GONE); }
    }

    // ===== AUTO-DETECT POUR TOUTES LES APPS =====
    void registerAutoDetect(){
        try{
            IntentFilter filter=new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            pkgReceiver=new BroadcastReceiver(){
                public void onReceive(Context ctx, Intent intent){
                    String pkg=intent.getData()!=null?intent.getData().getSchemeSpecificPart():"";
                    if(pkg!=null && !pkg.isEmpty()) mainH.postDelayed(()-> autoDetectAnyApp(pkg), 1500);
                }
            };
            registerReceiver(pkgReceiver, filter);
        }catch(Exception e){}
    }
    void autoDetectAnyApp(String pkg){
        if(pkg==null || pkg.isEmpty() || pkg.equals(getPackageName())) return;
        if(glassPrefs.getBoolean("known_"+pkg,false) || glassPrefs.getBoolean("ignore_"+pkg,false)) return;
        try{
            PackageManager pm=getPackageManager();
            Intent launch=pm.getLaunchIntentForPackage(pkg);
            if(launch==null) return;
            ApplicationInfo ai=pm.getApplicationInfo(pkg,0);
            String label=pm.getApplicationLabel(ai).toString();
            boolean isTheme=pkg.toLowerCase().contains("theme")||pkg.toLowerCase().contains("icon")||pkg.toLowerCase().contains("widget")||pkg.toLowerCase().contains("zedge")||pkg.toLowerCase().contains("niagara")||pkg.toLowerCase().contains("wallpaper");
            mainH.post(()->{
                if(isTheme){
                    new AlertDialog.Builder(this).setTitle("Nouveau theme detecte").setMessage("Theme installe: "+label+"\nAppliquer le theme COMPLET?").setPositiveButton("Appliquer", (d,w)->{
                        List<ResolveInfo> themes=pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"),0);
                        for(ResolveInfo ri: themes){ if(ri.activityInfo.packageName.equals(pkg)){ setIconPack(pkg); glassPrefs.edit().putBoolean("known_"+pkg,true).apply(); return; } }
                        // sinon c'est une app type ThemeKit -> scan generique
                        scanThemeFilesFromAnyApp();
                        setIconTheme("custom_full");
                        glassPrefs.edit().putBoolean("known_"+pkg,true).apply();
                        Toast.makeText(this,"Theme complet importe depuis "+label,1).show();
                    }).setNegativeButton("Ignorer", (d,w)-> glassPrefs.edit().putBoolean("ignore_"+pkg,true).apply()).show();
                }else{
                    new AlertDialog.Builder(this).setTitle("Nouvelle appli").setMessage(label+" installee, ajouter en fav?").setPositiveButton("Oui", (d,w)->{ if(!favPkgs.contains(pkg)){ favPkgs.add(pkg); saveFavs(); rvFavorites.setAdapter(new FavAdapter()); } glassPrefs.edit().putBoolean("known_"+pkg,true).apply(); }).setNegativeButton("Non",null).show();
                }
            });
        }catch(Exception e){}
    }
    void autoScanOnResume(){
        new Thread(()->{
            try{
                PackageManager pm=getPackageManager();
                Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all=pm.queryIntentActivities(it,0);
                for(ResolveInfo ri: all){
                    String pkg=ri.activityInfo.packageName;
                    if(pkg.equals(getPackageName())) continue;
                    if(glassPrefs.getBoolean("known_"+pkg,false)||glassPrefs.getBoolean("ignore_"+pkg,false)) continue;
                    mainH.post(()-> autoDetectAnyApp(pkg));
                    break;
                }
            }catch(Exception e){}
        }).start();
    }

    // ===== WALLPAPER AUTO = THEME COMPLET =====
    void setupWallpaperAutoDetect(){
        try{
            wallpaperMgr=android.app.WallpaperManager.getInstance(this);
            if(android.os.Build.VERSION.SDK_INT>=27){
                wallpaperListener=(colors, which)->{
                    try{
                        Drawable wp=wallpaperMgr.getDrawable();
                        if(wallpaperView!=null && wp!=null){
                            mainH.post(()->{
                                wallpaperView.setImageDrawable(wp);
                                scanThemeFilesFromAnyApp();
                                setIconTheme("custom_full");
                                if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter());
                                Toast.makeText(this,"Theme COMPLET applique",1).show();
                            });
                        }
                    }catch(Exception e){}
                };
                wallpaperMgr.addOnColorsChangedListener(wallpaperListener, mainH);
            }
        }catch(Exception e){}
    }

    // ===== SCAN GENERIQUE TOUTES APPS =====
    void scanThemeFilesFromAnyApp(){
        new Thread(()->{
            try{
                File customDir=getCustomIconDir();
                int imported=0;
                List<File> roots=new ArrayList<>();
                roots.add(new File(android.os.Environment.getExternalStorageDirectory()+"/Download"));
                roots.add(new File(android.os.Environment.getExternalStorageDirectory()+"/Pictures"));
                roots.add(new File(android.os.Environment.getExternalStorageDirectory()+"/DCIM"));
                for(File root: roots){
                    if(!root.exists()) continue;
                    File[] l1=root.listFiles(); if(l1==null) continue;
                    for(File f1: l1){
                        if(f1.isFile() && f1.getName().toLowerCase().endsWith(".png") && f1.length()>1500){
                            File dest=new File(customDir, f1.getName());
                            if(!dest.exists() && f1.getName().contains("com.")){ try{ java.nio.file.Files.copy(f1.toPath(), dest.toPath()); imported++; }catch(Exception e){} }
                        } else if(f1.isDirectory()){
                            File[] l2=f1.listFiles(); if(l2==null) continue;
                            for(File f2: l2){
                                if(f2.isFile() && f2.getName().toLowerCase().endsWith(".png") && f2.length()>1500){
                                    File dest=new File(customDir, f2.getName());
                                    if(!dest.exists()){ try{ java.nio.file.Files.copy(f2.toPath(), dest.toPath()); imported++; }catch(Exception e){} }
                                }
                            }
                        }
                    }
                }
                if(imported>0) mainH.post(()->{ setIconTheme("custom_full"); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); });
            }catch(Exception e){}
        }).start();
    }

    // ===== WIDGETS AVEC SUPPRESSION =====
    void setupWidgetHost(){
        try{
            widgetManager=android.appwidget.AppWidgetManager.getInstance(this);
            widgetHost=new android.appwidget.AppWidgetHost(this, WIDGET_HOST_ID);
            widgetHost.startListening();
            if(widgetContainer==null) widgetContainer=findViewById(R.id.widgetContainer);
            loadWidgets();
        }catch(Exception e){}
    }
    void loadWidgets(){
        if(widgetManager==null || widgetHost==null || widgetContainer==null) return;
        widgetContainer.removeAllViews();
        String saved=glassPrefs.getString("widget_ids","");
        if(saved.isEmpty()) return;
        for(String s: saved.split(",")){
            if(s.isEmpty()) continue;
            try{
                int id=Integer.parseInt(s);
                android.appwidget.AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);
                if(info!=null){
                    android.appwidget.AppWidgetHostView hv=widgetHost.createView(this, id, info);
                    hv.setAppWidget(id, info);
                    hv.setOnLongClickListener(v->{
                        new AlertDialog.Builder(this).setTitle("Supprimer widget?").setMessage(info.loadLabel(getPackageManager())).setPositiveButton("Supprimer", (d,w)-> removeWidget(id)).setNegativeButton("Annuler",null).show();
                        return true;
                    });
                    widgetContainer.addView(hv);
                }
            }catch(Exception e){}
        }
    }
    void removeWidget(int id){
        try{
            widgetHost.deleteAppWidgetId(id);
            String cur=glassPrefs.getString("widget_ids","");
            List<String> list=new ArrayList<>(Arrays.asList(cur.split(",")));
            list.remove(String.valueOf(id)); list.remove("");
            glassPrefs.edit().putString("widget_ids", String.join(",", list)).apply();
            loadWidgets();
            Toast.makeText(this,"Widget supprime",0).show();
        }catch(Exception e){}
    }
    void saveWidgetId(int id){
        String cur=glassPrefs.getString("widget_ids","");
        Set<String> set=new HashSet<>(Arrays.asList(cur.split(",")));
        set.add(String.valueOf(id)); set.remove("");
        glassPrefs.edit().putString("widget_ids", String.join(",", set)).apply();
    }
    void pickWidgetFromAnyApp(){
        try{
            int id=widgetHost.allocateAppWidgetId();
            Intent pick=new Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK);
            pick.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            startActivityForResult(pick, REQ_PICK_WIDGET);
        }catch(Exception e){}
    }
    void scanWidgetsFromAnyThemeApp(){
        new Thread(()->{
            try{
                List<android.appwidget.AppWidgetProviderInfo> all=widgetManager.getInstalledProviders();
                List<android.appwidget.AppWidgetProviderInfo> themeWidgets=new ArrayList<>();
                for(android.appwidget.AppWidgetProviderInfo info: all){
                    String pkg=info.provider.getPackageName().toLowerCase();
                    if(pkg.contains("theme")||pkg.contains("widget")||pkg.contains("clock")||pkg.contains("weather")||pkg.contains("zedge")) themeWidgets.add(info);
                }
                if(themeWidgets.isEmpty()) return;
                mainH.post(()->{
                    LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
                    for(android.appwidget.AppWidgetProviderInfo info: themeWidgets){
                        TextView tv=new TextView(this); tv.setText("📱 "+info.loadLabel(getPackageManager())+" ("+info.provider.getPackageName()+")"); tv.setTextColor(Color.CYAN); tv.setPadding(30,20,30,20);
                        tv.setOnClickListener(v->{
                            try{
                                int id=widgetHost.allocateAppWidgetId();
                                boolean bound=widgetManager.bindAppWidgetIdIfAllowed(id, info.provider);
                                if(bound){
                                    android.appwidget.AppWidgetHostView hv=widgetHost.createView(this, id, info); hv.setAppWidget(id, info);
                                    hv.setOnLongClickListener(v2->{ new AlertDialog.Builder(this).setTitle("Supprimer?").setPositiveButton("Supprimer",(d,w)->removeWidget(id)).show(); return true; });
                                    widgetContainer.addView(hv); saveWidgetId(id);
                                }else pickWidgetFromAnyApp();
                            }catch(Exception e){ pickWidgetFromAnyApp(); }
                        });
                        list.addView(tv);
                    }
                    ScrollView sv=new ScrollView(this); sv.addView(list);
                    new AlertDialog.Builder(this).setTitle("Widgets - appui long pour supprimer").setView(sv).setPositiveButton("Ajouter", (d,w)->pickWidgetFromAnyApp()).setNegativeButton("Fermer",null).show();
                });
            }catch(Exception e){}
        }).start();
    }

    // ===== ICON PACKS / THEMES =====
    File getCustomIconDir(){ File d=new File(getFilesDir(),"custom_icons"); if(!d.exists()) d.mkdirs(); return d; }
    Drawable getCustomWebIcon(ResolveInfo ri){
        try{
            String pkg=ri.activityInfo.packageName;
            File dir=getCustomIconDir();
            for(String ext: new String[]{".png",".jpg"}){
                File f=new File(dir, pkg+ext); if(f.exists()) return Drawable.createFromPath(f.getAbsolutePath());
            }
        }catch(Exception e){}
        return null;
    }
    String getIconTheme(){ return prefs.getString("icon_theme","system"); }
    void setIconTheme(String t){ prefs.edit().putString("icon_theme",t).apply(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); }
    Drawable applyCompleteTheme(Drawable orig, String theme){
        try{
            if(orig==null) return null;
            Drawable d=orig.mutate();
            if(theme.equals("white_complete")) d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            else if(theme.equals("black_complete")) d.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
            else if(theme.equals("violet_glass")) d.setColorFilter(0xFF6B4C8A, PorterDuff.Mode.SRC_IN);
            else if(theme.equals("blue_ios")) d.setColorFilter(0xFF0A84FF, PorterDuff.Mode.SRC_IN);
            return d;
        }catch(Exception e){ return orig; }
    }
    Drawable applyFullTheme(Drawable orig, String theme, ResolveInfo ri){
        Drawable custom=getCustomWebIcon(ri);
        if(custom!=null) return custom;
        if(theme.equals("custom_full")){ Drawable c=getCustomWebIcon(ri); if(c!=null) return c; }
        return applyCompleteTheme(orig, theme);
    }
    Drawable getDrawableFromPack(ResolveInfo ri){
        try{
            String theme=getIconTheme();
            if(theme.equals("custom_full")){ Drawable full=applyFullTheme(null, theme, ri); if(full!=null) return full; }
            Drawable custom=getCustomWebIcon(ri); if(custom!=null) return custom;
            if(!theme.equals("system") && !theme.equals("external")){ Drawable sys=ri.loadIcon(getPackageManager()); return applyCompleteTheme(sys, theme); }
        }catch(Exception e){}
        try{ return ri.loadIcon(getPackageManager()); }catch(Exception e){ return null; }
    }
    void setIconPack(String pkg){ prefs.edit().putString("icon_pack",pkg).apply(); setIconTheme("external"); }
    void downloadAndApplyPack(String url){
        Toast.makeText(this,"DL "+url,0).show();
        new Thread(()->{
            try{
                URL u=new URL(url); HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setRequestProperty("User-Agent","Mozilla/5.0");
                InputStream in=c.getInputStream(); File out=new File(getCacheDir(),"pack.zip");
                FileOutputStream fos=new FileOutputStream(out); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) fos.write(buf,0,n); fos.close(); in.close();
                unzipCustomPack(out);
                mainH.post(()->{ setIconTheme("custom_full"); Toast.makeText(this,"Theme complet applique",1).show(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); });
            }catch(Exception e){ mainH.post(()-> Toast.makeText(this,"Erreur DL: "+e.getMessage(),1).show()); }
        }).start();
    }
    void unzipCustomPack(File zip){
        try{
            ZipInputStream zis=new ZipInputStream(new FileInputStream(zip));
            ZipEntry e; File dir=getCustomIconDir(); int cnt=0;
            while((e=zis.getNextEntry())!=null){
                String name=e.getName(); if(name.endsWith("/")) continue;
                if(name.toLowerCase().endsWith(".png")){ String outName=new File(name).getName(); File out=new File(dir,outName); FileOutputStream fos=new FileOutputStream(out); byte[] b=new byte[8192]; int n; while((n=zis.read(b))!=-1) fos.write(b,0,n); fos.close(); cnt++; }
                zis.closeEntry();
            }
            zis.close();
        }catch(Exception e){}
    }

    // ===== MENU CLEAN =====
    void showThemeStoreFree(){
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this); title.setText("THEME STORE GRATUIT"); title.setTextColor(Color.YELLOW); title.setTypeface(null, Typeface.BOLD); title.setPadding(20,20,20,20); list.addView(title);
        String[][] stores={{"Arcticons 14k","https://github.com/ArcticonsTeam/Arcticons/archive/refs/heads/main.zip"},{"Papirus 5 themes","https://github.com/PapirusDevelopmentTeam/papirus_icons/archive/refs/heads/master.zip"},{"Delta","https://github.com/Delta-Icons/Delta/archive/refs/heads/master.zip"}};
        for(String[] s: stores){
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(25,25,25,25); card.setBackgroundColor(0x22FFFFFF);
            TextView name=new TextView(this); name.setText(s[0]); name.setTextColor(Color.WHITE); name.setTypeface(null, Typeface.BOLD); card.addView(name);
            TextView btn=new TextView(this); btn.setText("⬇ TELECHARGER THEME COMPLET"); btn.setTextColor(Color.BLACK); btn.setBackgroundColor(Color.CYAN); btn.setPadding(20,15,20,15);
            String url=s[1]; btn.setOnClickListener(v-> downloadAndApplyPack(url)); card.addView(btn); list.addView(card);
        }
        ScrollView sv=new ScrollView(this); sv.addView(list);
        new AlertDialog.Builder(this).setTitle("Theme Store").setView(sv).setNegativeButton("Fermer",null).show();
    }
    void showColorPicker(){ String[] cols={"Systeme","Blanc complet","Noir complet","Violet Glass","Bleu iOS","Custom complet"}; String[] vals={"system","white_complete","black_complete","violet_glass","blue_ios","custom_full"}; new AlertDialog.Builder(this).setTitle("Theme icones").setItems(cols,(d,w)-> setIconTheme(vals[w])).show(); }
    void showIconPack(){
        PackageManager pm=getPackageManager(); List<ResolveInfo> packs=new ArrayList<>();
        try{ packs.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"),0)); }catch(Exception e){}
        try{ packs.addAll(pm.queryIntentActivities(new Intent("com.novalauncher.THEME"),0)); }catch(Exception e){}
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        TextView repoTitle=new TextView(this); repoTitle.setText("DEPOTS MULTI-THEMES"); repoTitle.setTextColor(Color.YELLOW); repoTitle.setTypeface(null, Typeface.BOLD); repoTitle.setPadding(20,20,20,10); list.addView(repoTitle);
        String[][] repos={{"Arcticons 14k","https://github.com/ArcticonsTeam/Arcticons/archive/refs/heads/main.zip"},{"Papirus","https://github.com/PapirusDevelopmentTeam/papirus_icons/archive/refs/heads/master.zip"}};
        for(String[] r: repos){ TextView tv=new TextView(this); tv.setText("⬇ "+r[0]); tv.setTextColor(Color.CYAN); tv.setPadding(30,20,30,20); String url=r[1]; tv.setOnClickListener(v-> downloadAndApplyPack(url)); list.addView(tv); }
        TextView webBtn=new TextView(this); webBtn.setText("📥 IMPORTER ZIP LOCAL"); webBtn.setTextColor(Color.WHITE); webBtn.setPadding(20,20,20,20); webBtn.setOnClickListener(v->{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, 2001); }); list.addView(webBtn);
        HashSet<String> seen=new HashSet<>();
        for(ResolveInfo ri: packs){ String pkg=ri.activityInfo.packageName; if(!seen.add(pkg)) continue; TextView tv=new TextView(this); tv.setText(ri.loadLabel(pm)+" ("+pkg+")"); tv.setPadding(20,20,20,20); tv.setTextColor(Color.WHITE); tv.setOnClickListener(v->{ setIconPack(pkg); Toast.makeText(this,"Pack: "+pkg,0).show(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); }); list.addView(tv); }
        ScrollView sv=new ScrollView(this); sv.addView(list);
        new AlertDialog.Builder(this).setTitle("Icon packs").setView(sv).show();
    }
    void showFontPicker(){ Toast.makeText(this,"Polices - bientot",0).show(); }
    void showMenu(){
        new AlertDialog.Builder(this).setTitle("Menu").setItems(new String[]{"Theme Store Gratuit","Widgets (appui long suppr)","Couleur icones","Fond HD","Icon packs","Supprimer tous widgets","Effacer fond"}, (d,w)->{
            if(w==0) showThemeStoreFree();
            else if(w==1) scanWidgetsFromAnyThemeApp();
            else if(w==2) showColorPicker();
            else if(w==3) pickWallpaper();
            else if(w==4) showIconPack();
            else if(w==5){ new AlertDialog.Builder(this).setTitle("Supprimer tous widgets?").setPositiveButton("Oui",(dd,ww)->{ glassPrefs.edit().remove("widget_ids").apply(); if(widgetContainer!=null) widgetContainer.removeAllViews(); Toast.makeText(this,"Widgets supprimes",0).show(); }).show(); }
            else if(w==6){ if(wallpaperView!=null) wallpaperView.setImageDrawable(null); prefs.edit().remove("wall_uri").apply(); }
        }).show();
    }
    void pickWallpaper(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); startActivityForResult(i,1001); }
    @Override protected void onActivityResult(int rc,int res,Intent data){ super.onActivityResult(rc,res,data); try{
        if(rc==1001 && res==RESULT_OK && data!=null){ Uri uri=data.getData(); if(wallpaperView!=null) wallpaperView.setImageURI(uri); getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); prefs.edit().putString("wall_uri",uri.toString()).apply(); }
        else if(rc==2001 && res==RESULT_OK && data!=null){ Uri uri=data.getData(); File out=new File(getCacheDir(),"import.zip"); InputStream in=getContentResolver().openInputStream(uri); FileOutputStream fos=new FileOutputStream(out); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) fos.write(b,0,n); fos.close(); in.close(); unzipCustomPack(out); setIconTheme("custom_full"); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); }
        else if(rc==REQ_PICK_WIDGET && res==RESULT_OK && data!=null){ int id=data.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,-1); if(id!=-1){ Intent cfg=data.getParcelableExtra(Intent.EXTRA_INTENT); if(cfg!=null) startActivityForResult(cfg, REQ_CREATE_WIDGET); else { android.appwidget.AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id); android.appwidget.AppWidgetHostView hv=widgetHost.createView(this,id,info); hv.setAppWidget(id,info); hv.setOnLongClickListener(v->{ new AlertDialog.Builder(this).setTitle("Supprimer?").setPositiveButton("Supprimer",(dd,ww)->removeWidget(id)).show(); return true; }); widgetContainer.addView(hv); saveWidgetId(id); } } }
        else if(rc==REQ_CREATE_WIDGET && res==RESULT_OK && data!=null){ int id=data.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,-1); if(id!=-1){ android.appwidget.AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id); android.appwidget.AppWidgetHostView hv=widgetHost.createView(this,id,info); hv.setAppWidget(id,info); hv.setOnLongClickListener(v->{ new AlertDialog.Builder(this).setTitle("Supprimer?").setPositiveButton("Supprimer",(dd,ww)->removeWidget(id)).show(); return true; }); widgetContainer.addView(hv); saveWidgetId(id); } }
    }catch(Exception e){} }
    void checkDefault(){ try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); i.addCategory(Intent.CATEGORY_DEFAULT); ResolveInfo ri=getPackageManager().resolveActivity(i,0); if(ri!=null && !ri.activityInfo.packageName.equals(getPackageName())){ new AlertDialog.Builder(this).setTitle("Launcher par defaut").setMessage("Definir Quantum comme defaut?").setPositiveButton("Oui",(d,w)-> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS))).setNegativeButton("Non",null).show(); } }catch(Exception e){} }
    void loadFavs(){ try{ String s=prefs.getString("favs",""); favPkgs.clear(); if(!s.isEmpty()) favPkgs.addAll(Arrays.asList(s.split(","))); }catch(Exception e){} }
    void saveFavs(){ try{ prefs.edit().putString("favs", String.join(",", favPkgs)).apply(); }catch(Exception e){} }
    void preloadMaxSafe(){ new Thread(()->{ try{ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0); LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri: all) map.put(ri.activityInfo.packageName, ri); allAppsCache=new ArrayList<>(map.values()); }catch(Exception e){} }).start(); }
    List<ResolveInfo> suggList=new ArrayList<>();
    void filterSugg(String q){ String low=q.toLowerCase(); suggList.clear(); for(ResolveInfo ri: allAppsCache){ try{ String label=ri.loadLabel(getPackageManager()).toString().toLowerCase(); if(label.contains(low)||ri.activityInfo.packageName.toLowerCase().contains(low)) suggList.add(ri);}catch(Exception e){} if(suggList.size()>20) break; } if(rvSuggestions!=null){ rvSuggestions.setAdapter(new SuggAdapter()); rvSuggestions.setVisibility(View.VISIBLE); } }
    void openFullDrawer(){
        RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,4));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=allAppsCache.get(pos); try{ h.lb.setText(ri.loadLabel(getPackageManager())); h.ic.setImageDrawable(getDrawableFromPack(ri)); h.itemView.setOnClickListener(v-> launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} }
            public int getItemCount(){ return allAppsCache.size(); }
        });
        new AlertDialog.Builder(this).setTitle("Toutes apps").setView(rv).setNegativeButton("Fermer",null).show();
    }
    void setupListeners(){
        TextView menu=findViewById(R.id.btnMenu); if(menu!=null) menu.setOnClickListener(v-> showMenu());
        TextView fav=findViewById(R.id.btnAddFav); if(fav!=null) fav.setOnClickListener(v-> showAddFav());
        View clear=findViewById(R.id.clearApps); if(clear!=null && searchApps!=null) clear.setOnClickListener(v-> searchApps.setText(""));
        TextView go=findViewById(R.id.btnWebGo); if(go!=null && searchWeb!=null) go.setOnClickListener(v-> handleWeb());
        if(searchApps!=null) searchApps.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void onTextChanged(CharSequence a,int b,int c,int d){ if(a.length()==0){ if(rvSuggestions!=null) rvSuggestions.setVisibility(View.GONE); if(rvFavorites!=null) rvFavorites.setVisibility(View.VISIBLE);} else { if(rvFavorites!=null) rvFavorites.setVisibility(View.GONE); filterSugg(a.toString()); }} public void afterTextChanged(android.text.Editable e){}});
        if(searchWeb!=null) searchWeb.setOnEditorActionListener((tv,id,ev)->{ handleWeb(); return true; });
    }
    void showAddFav(){
        if(allAppsCache.isEmpty()){ Toast.makeText(this,"Chargement...",0).show(); return; }
        RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=allAppsCache.get(pos); try{ h.lb.setText(ri.loadLabel(getPackageManager()).toString()); h.ic.setImageDrawable(getDrawableFromPack(ri)); h.itemView.setOnClickListener(v->{ if(!favPkgs.contains(ri.activityInfo.packageName)){ favPkgs.add(ri.activityInfo.packageName); saveFavs(); if(rvFavorites!=null) rvFavorites.setAdapter(new FavAdapter()); } }); }catch(Exception e){} }
            public int getItemCount(){ return allAppsCache.size(); }
        });
        new AlertDialog.Builder(this).setTitle("Ajouter fav").setView(rv).setNegativeButton("Fermer",null).show();
    }
    void handleWeb(){
        if(searchWeb==null) return; String q=searchWeb.getText().toString().trim(); if(q.isEmpty()) return;
        String low=q.toLowerCase(); String url;
        if(low.startsWith("yt ")) url="https://www.youtube.com/results?search_query="+Uri.encode(q.substring(3));
        else if(low.startsWith("g ")) url="https://www.google.com/search?q="+Uri.encode(q.substring(2));
        else if(q.contains(" ")) url="https://www.google.com/search?q="+Uri.encode(q);
        else if(q.contains(".")) url=q.startsWith("http")?q:"https://"+q;
        else url="https://www.google.com/search?q="+Uri.encode(q);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
    void launchInstant(String pkg){ try{ Intent i=getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null) startActivity(i); }catch(Exception e){} }
    void applyThemeColor(int col){ try{ if(mainRoot!=null) mainRoot.setBackgroundColor(col & 0x22FFFFFF); }catch(Exception e){} }

    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager()).toString()); h.ic.setImageDrawable(getDrawableFromPack(ri)); h.itemView.setOnClickListener(v-> launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; H(View v){super(v); ic=v.findViewById(R.id.icon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ String pkg=favPkgs.get(pos); ResolveInfo ri=null; for(ResolveInfo r: allAppsCache) if(r.activityInfo.packageName.equals(pkg)){ ri=r; break; } if(ri!=null) h.ic.setImageDrawable(getDrawableFromPack(ri)); else h.ic.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); h.itemView.setOnClickListener(v-> launchInstant(pkg)); h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return favPkgs.size(); } }
}

