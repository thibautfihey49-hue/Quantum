package com.v7.quantumfast;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    View mainRoot;
    EditText searchApps, searchWeb;
    RecyclerView rvSugg, rvFav, rvFolders;
    View googleZone, youtubeZone, folderZone;
    SharedPreferences prefs, glassPrefs;
    List<ResolveInfo> suggList=new ArrayList<>();
    List<ResolveInfo> allAppsCache=new ArrayList<>();
    LruCache<String, Drawable> iconCache=new LruCache<>(150);
    List<String> favPkgs=new ArrayList<>();
    List<Folder> folders=new ArrayList<>();
    ExecutorService pool=Executors.newFixedThreadPool(2);
    Handler mainH=new Handler(Looper.getMainLooper());
    String[] dockKeys={"dock_phone","dock_msg","dock_extra","dock_drawer","dock_cam","dock_chrome"};
    String[] defaultPkgs={"com.android.dialer","com.google.android.apps.messaging","com.android.settings","com.v7.quantumfast","com.android.camera2","com.android.chrome"};
    static class Folder{ String name; List<String> pkgs=new ArrayList<>(); Folder(String n){name=n;} }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        prefs=getSharedPreferences("dock",0);
        glassPrefs=getSharedPreferences("glass",0);
        mainRoot=findViewById(R.id.root);
        searchApps=findViewById(R.id.searchAppsMain);
        searchWeb=findViewById(R.id.searchWebMain);
        rvSugg=findViewById(R.id.rvSuggestions); if(rvSugg==null) rvSugg=(RecyclerView)findViewGlass("rvSugg","suggestions");
        rvFav=(RecyclerView)findViewGlass("rvFavorites","rvFav","favs");
        rvFolders=(RecyclerView)findViewGlass("rvFolders","folders");
        googleZone=findViewGlass("Google","btnGoogle","google");
        youtubeZone=findViewGlass("YouTube","youtube");
        folderZone=findViewGlass("folderZone","containerFolders");

        if(rvSugg!=null){ rvSugg.setLayoutManager(new LinearLayoutManager(this)); rvSugg.setHasFixedSize(true); rvSugg.setItemViewCacheSize(40); rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.GONE); }
        if(rvFav!=null){ rvFav.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,false)); }
        if(rvFolders!=null){ rvFolders.setLayoutManager(new GridLayoutManager(this,5)); }

        try{ View bv=findViewGlass("btnBoost","Boost","boost"); if(bv!=null) bv.setVisibility(View.GONE); if(googleZone!=null) googleZone.setVisibility(View.GONE); if(youtubeZone!=null) youtubeZone.setVisibility(View.GONE);}catch(Exception e){}

        fixModernFrame();

        View cl=findViewGlass("clearApps","btnClear","clear"); if(cl!=null) cl.setOnClickListener(v->{ if(searchApps!=null) searchApps.setText(""); });
        View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setOnClickListener(v->handleWeb());
        if(searchWeb!=null) searchWeb.setOnEditorActionListener((v,id,ev)->{ if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){ handleWeb(); return true;} return false; });
        View fav=findViewGlass("btnAddFav","Fav","fav"); if(fav!=null) fav.setOnClickListener(v->showAddFavDialog());
        View fol=findViewGlass("btnAddFolder","Folder","folder"); if(fol!=null) fol.setOnClickListener(v->showCreateFolderDialog());
        View men=findViewGlass("btnMenu","Menu","menu"); if(men!=null) men.setOnClickListener(v->showMenuModern());

        loadFavs(); loadFolders();
        if(rvFav!=null) rvFav.setAdapter(new FavAdapter());
        if(rvFolders!=null) rvFolders.setAdapter(new FolderAdapter());

        setupAtAGlance(); setupDock(); preloadMax();
        applyGlassTheme(glassPrefs.getInt("glass_color",0xFF6B4C8A));

        if(searchApps!=null) searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                String q=s.toString(); boolean typing=!q.trim().isEmpty();
                if(rvFolders!=null) rvFolders.setVisibility(typing?View.GONE:View.VISIBLE);
                if(folderZone!=null) folderZone.setVisibility(typing?View.GONE:View.VISIBLE);
                if(rvFav!=null) rvFav.setVisibility(typing?View.GONE:View.VISIBLE);
                if(rvSugg!=null) rvSugg.setVisibility(typing?View.VISIBLE:View.GONE);
                filterAppsInstant(q);
            }
            public void afterTextChanged(android.text.Editable s){}
        });

        checkAndAskPermissions();
        mainRoot.postDelayed(this::checkDefaultLauncher, 700);
    }

    // ====== FENETRES MODERNES ======
    Drawable glassBg(int col,float rad,int alpha){ int fill=Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)); android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setShape(0); d.setCornerRadius(rad); d.setColor(fill); d.setStroke((int)(1.2f*getResources().getDisplayMetrics().density), Color.argb(110,255,255,255)); return d; }
    AlertDialog createModernDialog(String title, View content){
        float dens=getResources().getDisplayMetrics().density;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(20*dens),(int)(20*dens),(int)(20*dens),(int)(16*dens));
        root.setBackground(glassBg(glassPrefs.getInt("glass_color",0xFF6B4C8A), 24*dens, 92));
        TextView tv=new TextView(this); tv.setText(title); tv.setTextSize(18); tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD); tv.setPadding(0,0,0,(int)(12*dens)); root.addView(tv);
        if(content!=null) root.addView(content);
        AlertDialog dlg=new AlertDialog.Builder(this).setView(root).create();
        if(dlg.getWindow()!=null){ dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); }
        return dlg;
    }

    void showMenuModern(){
        float dens=getResources().getDisplayMetrics().density;
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        String[] opts={"🎨 Choisir couleur thème","🖼️ Choisir fond d'écran","🧹 Effacer fond d'écran","ℹ️ À propos"};
        for(int i=0;i<opts.length;i++){ final int idx=i; TextView row=new TextView(this); row.setText(opts[i]); row.setTextSize(16); row.setTextColor(Color.WHITE); row.setPadding((int)(14*dens),(int)(14*dens),(int)(14*dens),(int)(14*dens)); row.setBackground(glassBg(Color.BLACK, 14*dens, 55)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,(int)(10*dens)); row.setLayoutParams(lp); row.setOnClickListener(v->{ if(idx==0) showPaletteModern(); else if(idx==1) pickWallpaper(); else if(idx==2){ prefs.edit().remove("custom_wallpaper_uri").apply(); View bg=findViewGlass("wallpaper","bg","background","wall"); if(bg instanceof ImageView) ((ImageView)bg).setImageDrawable(null); Toast.makeText(this,"Fond effacé",0).show(); } else { AlertDialog d=createModernDialog("Quantum",null); d.show(); } }); list.addView(row); }
        AlertDialog dlg=createModernDialog("Menu", list); dlg.show();
    }

    void showPaletteModern(){
        float dens=getResources().getDisplayMetrics().density;
        GridLayout grid=new GridLayout(this); grid.setColumnCount(5);
        int[] cols={0xFF6B4C8A,0xFF2196F3,0xFF00BCD4,0xFF4CAF50,0xFFE91E63,0xFF9C27B0,0xFF3F51B5,0xFFFF5722,0xFF212121,0xFFFFFFFF,0xFF121212,0xFF607D8B};
        for(int col:cols){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(56*dens); lp.height=(int)(56*dens); lp.setMargins((int)(8*dens),(int)(8*dens),(int)(8*dens),(int)(8*dens)); v.setLayoutParams(lp); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(16*dens); bg.setColor(col); if(col==0xFFFFFFFF) bg.setStroke((int)dens,0xFFCCCCCC); v.setBackground(bg); v.setOnClickListener(vw->{ glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); }
        AlertDialog dlg=createModernDialog("Thème moderne", grid); dlg.show();
    }

    void pickWallpaper(){
        try{
            Intent it=new Intent(Intent.ACTION_OPEN_DOCUMENT); it.addCategory(Intent.CATEGORY_OPENABLE); it.setType("image/*");
            startActivityForResult(it, 201);
        }catch(Exception e){
            try{ Intent it2=new Intent(Intent.ACTION_PICK); it2.setType("image/*"); startActivityForResult(it2,201); }catch(Exception ee){ Toast.makeText(this,"Galerie indisponible",0).show(); }
        }
    }

    void fixModernFrame(){ try{ float d=getResources().getDisplayMetrics().density; if(mainRoot!=null) mainRoot.setPadding((int)(14*d),(int)(30*d),(int)(14*d),(int)(14*d)); }catch(Exception e){} }

    void preloadMax(){
        pool.execute(()->{
            try{
                Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all=getPackageManager().queryIntentActivities(it, PackageManager.MATCH_DEFAULT_ONLY);
                Collections.sort(all,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
                allAppsCache=all;
                for(ResolveInfo ri:all){ try{ String pkg=ri.activityInfo.packageName; if(iconCache.get(pkg)==null) iconCache.put(pkg, ri.loadIcon(getPackageManager())); }catch(Exception e){} }
                mainH.post(()->{ setupDock(); if(rvFav!=null) rvFav.getAdapter().notifyDataSetChanged(); loadWallpaperFast(); });
            }catch(Exception e){}
        });
    }

    void filterAppsInstant(String q){
        try{
            suggList.clear();
            if(q==null||q.trim().isEmpty()){ if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); return;}
            String lq=q.toLowerCase().trim();
            List<ResolveInfo> src=allAppsCache;
            if(src.isEmpty()){ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); src=getPackageManager().queryIntentActivities(it,0); allAppsCache=src; }
            for(ResolveInfo ri:src){ String label=ri.loadLabel(getPackageManager()).toString(); if(label.toLowerCase().contains(lq) || ri.activityInfo.packageName.toLowerCase().contains(lq)){ suggList.add(ri); } if(suggList.size()>=80) break; }
            if(rvSugg!=null){ rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.VISIBLE); }
        }catch(Exception e){}
    }

    void openFullDrawer(){
        try{
            List<ResolveInfo> src=allAppsCache.isEmpty()? getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0) : allAppsCache;
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setPadding(20,20,20,20);
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=src.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->{ launchInstant(ri.activityInfo.packageName); }); }
                public int getItemCount(){ return src.size(); }
            });
            AlertDialog dlg=createModernDialog("Tiroir - "+src.size()+" apps", rv); dlg.show();
        }catch(Exception e){}
    }

    boolean isMyLauncherDefault(){ try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); ResolveInfo r=getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY); return r!=null && r.activityInfo.packageName.equals(getPackageName()); }catch(Exception e){ return false; } }
    void checkDefaultLauncher(){ if(isMyLauncherDefault()) return; if(prefs.getBoolean("asked_default",false)) return; AlertDialog dlg=createModernDialog("Launcher par défaut", null); dlg.setButton(AlertDialog.BUTTON_POSITIVE,"Oui",(DialogInterface d,int w)->{ prefs.edit().putBoolean("asked_default",true).apply(); try{ startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }catch(Exception e){ Intent c=new Intent(Intent.ACTION_MAIN); c.addCategory(Intent.CATEGORY_HOME); c.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(Intent.createChooser(c,"Choisir launcher")); } }); dlg.setButton(AlertDialog.BUTTON_NEGATIVE,"Plus tard",(DialogInterface d,int w)->{}); dlg.show(); }
    void checkAndAskPermissions(){ try{ if(Build.VERSION.SDK_INT>=33){ if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},101); }else{ if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},101); } }catch(Exception e){} }

    View findViewGlass(String...names){ for(String n:names){ int id=getResources().getIdentifier(n,"id",getPackageName()); if(id!=0){ View v=findViewById(id); if(v!=null) return v; } } return null; }
    void loadWallpaperFast(){ try{ String s=prefs.getString("custom_wallpaper_uri",""); if(s.isEmpty()) return; Uri uri=Uri.parse(s); View bg=findViewGlass("wallpaper","bg","background","wall","wallpaperView"); if(bg instanceof ImageView){ ((ImageView)bg).setScaleType(ImageView.ScaleType.CENTER_CROP); ((ImageView)bg).setImageURI(uri); } }catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri u=data.getData(); try{ getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri",u.toString()).apply(); loadWallpaperFast(); Toast.makeText(this,"Fond appliqué",0).show(); } }

    void setupAtAGlance(){ TextView c=(TextView)findViewGlass("clock","time"); TextView d=(TextView)findViewGlass("date","dateInfo"); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} if(mainRoot!=null) mainRoot.postDelayed(this,30000); }}; r.run(); try{ BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context cc, Intent ii){ int lvl=ii.getIntExtra("level",-1); TextView tv=(TextView)findViewGlass("batteryInfo","battery","bat"); if(tv!=null&&lvl!=-1) tv.setText("🔋 "+lvl+"%"); } }; registerReceiver(br,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); }catch(Exception e){} }

    String resolveIntentPkg(Intent intent){ try{ List<ResolveInfo> r=getPackageManager().queryIntentActivities(intent,0); if(r!=null&&!r.isEmpty()) return r.get(0).activityInfo.packageName; }catch(Exception e){} return null; }
    String getSmartDefault(int idx){ try{ if(idx==0){ String p=resolveIntentPkg(new Intent(Intent.ACTION_DIAL)); if(p!=null) return p;} if(idx==1){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))); if(p!=null) return p;} if(idx==2) return "com.android.settings"; if(idx==4){ String p=resolveIntentPkg(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); if(p!=null) return p;} if(idx==5){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com"))); if(p!=null) return p;} }catch(Exception e){} return defaultPkgs[idx]; }
    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv; if(vv instanceof ImageView) iv=(ImageView)vv; else{ try{ iv=(ImageView)((FrameLayout)vv).getChildAt(0);}catch(Exception e){ continue; } } if(idx==3){ iv.setImageResource(android.R.drawable.ic_menu_sort_by_size); iv.setBackgroundColor(Color.TRANSPARENT); vv.setOnClickListener(v->openFullDrawer()); continue; } String saved=prefs.getString(dockKeys[idx], null); if(saved==null) saved=getSmartDefault(idx); String pkg=findRealPkg(saved); if(pkg==null) pkg=getSmartDefault(idx); updateDockIcon(iv,pkg); vv.setOnClickListener(v->{ String rs=prefs.getString(dockKeys[idx], getSmartDefault(idx)); String rp=findRealPkg(rs); if(rp!=null) launchInstant(rp); }); vv.setOnLongClickListener(v->{ pickDockApp(idx); return true; }); }
    }
    String findRealPkg(String pkg){ if(pkg==null) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return null; } }
    void updateDockIcon(ImageView iv, String pkg){ try{ Drawable c=iconCache.get(pkg); if(c!=null) iv.setImageDrawable(c); else iv.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); iv.setBackgroundColor(Color.TRANSPARENT);}catch(Exception e){ iv.setImageResource(android.R.drawable.sym_def_app_icon); } }
    void launchInstant(String pkg){ try{ Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION); clearSearchNow(); startActivity(ii); overridePendingTransition(0,0); return; } }catch(Exception e){} }
    void clearSearchNow(){ if(searchApps!=null) searchApps.setText(""); suggList.clear(); if(rvSugg!=null){ rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.GONE); } if(folderZone!=null) folderZone.setVisibility(View.VISIBLE); if(rvFolders!=null) rvFolders.setVisibility(View.VISIBLE); if(rvFav!=null) rvFav.setVisibility(View.VISIBLE); }
    void pickDockApp(int idx){
        List<ResolveInfo> apps=allAppsCache.isEmpty()? getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0) : allAppsCache;
        LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:apps){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName, ri); }
        List<ResolveInfo> uniq=new ArrayList<>(map.values());
        String[] names=new String[uniq.size()]; String[] pkgs=new String[uniq.size()]; for(int i=0;i<uniq.size();i++){ names[i]=uniq.get(i).loadLabel(getPackageManager()).toString(); pkgs[i]=uniq.get(i).activityInfo.packageName; }
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); ScrollView sv=new ScrollView(this); sv.addView(list);
        AlertDialog dlg=createModernDialog("Choisir app dock", sv);
        for(int i=0;i<names.length;i++){ final int wi=i; TextView row=new TextView(this); row.setText(names[i]); row.setTextColor(Color.WHITE); row.setPadding(30,30,30,30); row.setOnClickListener(v->{ prefs.edit().putString(dockKeys[idx],pkgs[wi]).apply(); setupDock(); dlg.dismiss(); }); list.addView(row); }
        dlg.show();
    }
    void loadFavs(){ favPkgs.clear(); String s=prefs.getString("favs",""); if(!s.isEmpty()) favPkgs.addAll(Arrays.asList(s.split(","))); }
    void saveFavs(){ prefs.edit().putString("favs", String.join(",", favPkgs)).apply(); }
    void loadFolders(){ folders.clear(); String s=prefs.getString("folders",""); if(!s.isEmpty()){ for(String f:s.split("\\|")){ String[] p=f.split(":"); if(p.length>=1){ Folder fo=new Folder(p[0]); if(p.length>1&&!p[1].isEmpty()) fo.pkgs.addAll(Arrays.asList(p[1].split(","))); folders.add(fo);} } } }
    void saveFolders(){ StringBuilder sb=new StringBuilder(); for(Folder fo:folders){ if(sb.length()>0) sb.append("|"); sb.append(fo.name).append(":").append(String.join(",", fo.pkgs)); } prefs.edit().putString("folders", sb.toString()).apply(); }
    void showAddFavDialog(){ List<ResolveInfo> apps=allAppsCache; LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); ScrollView sv=new ScrollView(this); sv.addView(list); AlertDialog dlg=createModernDialog("Ajouter fav", sv); for(ResolveInfo ri:apps){ TextView row=new TextView(this); row.setText(ri.loadLabel(getPackageManager()).toString()); row.setTextColor(Color.WHITE); row.setPadding(30,28,30,28); row.setOnClickListener(v->{ if(!favPkgs.contains(ri.activityInfo.packageName)){ favPkgs.add(ri.activityInfo.packageName); saveFavs(); if(rvFav!=null) rvFav.setAdapter(new FavAdapter()); } dlg.dismiss(); }); list.addView(row); } dlg.show(); }
    void showCreateFolderDialog(){ EditText et=new EditText(this); et.setHint("Nom dossier"); et.setTextColor(Color.WHITE); et.setHintTextColor(0x88FFFFFF); AlertDialog dlg=createModernDialog("Nouveau dossier", et); dlg.setButton(AlertDialog.BUTTON_POSITIVE,"Créer",(d,w)->{ String n=et.getText().toString().trim(); if(n.isEmpty()) n="Dossier"; Folder fo=new Folder(n); folders.add(fo); saveFolders(); if(rvFolders!=null) rvFolders.setAdapter(new FolderAdapter()); }); dlg.setButton(AlertDialog.BUTTON_NEGATIVE,"Annuler",(d,w)->{}); dlg.show(); }
    void handleWeb(){ if(searchWeb==null) return; String q=searchWeb.getText().toString().trim(); if(q.isEmpty()) return; String low=q.toLowerCase(); String url; if(low.startsWith("yt ")) url="https://www.youtube.com/results?search_query="+Uri.encode(q.substring(3)); else if(low.startsWith("g ")) url="https://www.google.com/search?q="+Uri.encode(q.substring(2)); else if(low.startsWith("d ")) url="https://drive.google.com/drive/search?q="+Uri.encode(q.substring(2)); else if(low.startsWith("w ")) url="https://fr.wikipedia.org/wiki/"+Uri.encode(q.substring(2)); else if(q.contains(" ")) url="https://www.google.com/search?q="+Uri.encode(q); else if(q.contains(".")) url=q.startsWith("http")?q:"https://"+q; else url="https://www.google.com/search?q="+Uri.encode(q); try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }catch(Exception e){ Toast.makeText(this,q,0).show(); } }

    void applyGlassTheme(int col){ try{ float dens=getResources().getDisplayMetrics().density; double lum=(0.299*Color.red(col)+0.587*Color.green(col)+0.114*Color.blue(col))/255; int textCol= lum>0.6? 0xFF1A1A1A : 0xFFFFFFFF; int hintCol= lum>0.6? 0x661A1A1A : 0x99FFFFFF; for(int id:new int[]{R.id.searchAppsMain,R.id.searchWebMain}){ View v=findViewById(id); if(v!=null){ v.setBackground(glassBg(col, 28*dens, 85)); if(v instanceof EditText){ ((EditText)v).setTextColor(textCol); ((EditText)v).setHintTextColor(hintCol);} } } View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setBackground(glassBg(col, 28*dens, 90)); View dock=findViewGlass("dock","dockBar","dock_container","dockContainer","bottomDock"); if(dock!=null) dock.setBackground(glassBg(col, 26*dens, 60)); }catch(Exception e){} }

    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; H(View v){super(v); ic=v.findViewById(R.id.icon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ String pkg=favPkgs.get(pos); Drawable cd=iconCache.get(pkg); if(cd!=null) h.ic.setImageDrawable(cd); else h.ic.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launchInstant(pkg)); h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return favPkgs.size(); } }
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView lb; ImageView ic; H(View v){super(v); lb=v.findViewById(R.id.label); ic=v.findViewById(R.id.icon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ Folder fo=folders.get(pos); h.lb.setText(fo.name); if(h.ic!=null) h.ic.setImageResource(android.R.drawable.ic_menu_sort_by_size); h.itemView.setOnClickListener(v->{ AlertDialog.Builder b=new AlertDialog.Builder(MainActivity.this); b.setTitle(fo.name); String[] pkgs=fo.pkgs.toArray(new String[0]); String[] names=new String[pkgs.length]; for(int i=0;i<pkgs.length;i++){ try{ names[i]=getPackageManager().getApplicationInfo(pkgs[i],0).loadLabel(getPackageManager()).toString(); }catch(Exception e){ names[i]=pkgs[i]; } } b.setItems(names,(d,w)->launchInstant(pkgs[w])); b.show(); }); } public int getItemCount(){ return folders.size(); } }
}
