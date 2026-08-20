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
    RecyclerView rvSugg, rvFav;
    SharedPreferences prefs, glassPrefs;
    List<ResolveInfo> suggList=new ArrayList<>();
    List<ResolveInfo> allAppsCache=new ArrayList<>();
    LruCache<String, Drawable> iconCache=new LruCache<>(200);
    List<String> favPkgs=new ArrayList<>();
    ExecutorService pool=Executors.newFixedThreadPool(2);
    Handler mainH=new Handler(Looper.getMainLooper());
    String[] dockKeys={"dock_phone","dock_msg","dock_extra","dock_drawer","dock_cam","dock_chrome"};
    String[] defaultPkgs={"com.android.dialer","com.google.android.apps.messaging","com.android.settings","com.v7.quantumfast","com.android.camera2","com.android.chrome"};

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
        rvSugg=findViewById(R.id.rvSuggestions);
        rvFav=findViewById(R.id.rvFavorites);
        if(rvFav==null) rvFav=(RecyclerView)findViewById(R.id.rvFav);

        if(rvSugg!=null){ rvSugg.setLayoutManager(new LinearLayoutManager(this)); rvSugg.setVisibility(View.GONE); rvSugg.setAdapter(new SuggAdapter()); }
        if(rvFav!=null){ rvFav.setLayoutManager(new GridLayoutManager(this,5)); rvFav.setVisibility(View.VISIBLE); }

        // cache SEULEMENT les vues parasites, pas de récursif
        try{
            for(String idName:new String[]{"btnBoost","Google","YouTube","gCard","yCard","folderZone","rvFolders","folders","btnAddFolder"}){
                int id=getResources().getIdentifier(idName,"id",getPackageName());
                if(id!=0){ View v=findViewById(id); if(v!=null) v.setVisibility(View.GONE); }
            }
        }catch(Exception e){}

        View cl=findViewById(R.id.clearApps); if(cl!=null) cl.setOnClickListener(v->{ if(searchApps!=null) searchApps.setText(""); });
        View go=findViewById(R.id.btnWebGo); if(go==null) go=findViewById(R.id.go); if(go!=null) go.setOnClickListener(v->handleWeb());
        View favBtn=findViewById(R.id.btnAddFav); if(favBtn==null) favBtn=findViewById(R.id.Fav); if(favBtn!=null) favBtn.setOnClickListener(v->showAddFavDialog());
        View men=findViewById(R.id.btnMenu); if(men==null) men=findViewById(R.id.Menu); if(men!=null) men.setOnClickListener(v->showMenuModern());

        loadFavs();
        if(rvFav!=null) rvFav.setAdapter(new FavAdapter());

        setupAtAGlance(); preloadMax(); setupDock();
        applyGlassTheme(glassPrefs.getInt("glass_color",0xFF6B4C8A));

        if(searchApps!=null) searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                String q=s.toString(); boolean typing=!q.trim().isEmpty();
                if(rvFav!=null) rvFav.setVisibility(typing?View.GONE:View.VISIBLE);
                if(rvSugg!=null) rvSugg.setVisibility(typing?View.VISIBLE:View.GONE);
                filterAppsInstant(q);
            }
            public void afterTextChanged(android.text.Editable s){}
        });

        checkAndAskPermissions();
    }

    Drawable glassBg(int col,float rad,int alpha){ int fill=Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)); android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setShape(0); d.setCornerRadius(rad); d.setColor(fill); d.setStroke((int)(1.2f*getResources().getDisplayMetrics().density), Color.argb(110,255,255,255)); return d; }
    AlertDialog createModernDialog(String title, View content){
        float dens=getResources().getDisplayMetrics().density;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(20*dens),(int)(20*dens),(int)(20*dens),(int)(16*dens));
        root.setBackground(glassBg(glassPrefs.getInt("glass_color",0xFF6B4C8A), 24*dens, 96));
        TextView tv=new TextView(this); tv.setText(title); tv.setTextSize(18); tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD); tv.setPadding(0,0,0,(int)(12*dens)); root.addView(tv);
        if(content!=null) root.addView(content);
        AlertDialog dlg=new AlertDialog.Builder(this).setView(root).create();
        if(dlg.getWindow()!=null) dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dlg;
    }
    void showMenuModern(){
        float dens=getResources().getDisplayMetrics().density;
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        String[] opts={"🎨 Couleur","🖼️ Fond d'écran","🧹 Effacer fond"};
        for(int i=0;i<opts.length;i++){ final int idx=i; TextView row=new TextView(this); row.setText(opts[i]); row.setTextSize(16); row.setTextColor(Color.WHITE); row.setPadding((int)(14*dens),(int)(16*dens),(int)(14*dens),(int)(16*dens)); row.setBackground(glassBg(Color.BLACK, 14*dens, 70)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,(int)(10*dens)); row.setLayoutParams(lp); row.setOnClickListener(v->{ if(idx==0) showPaletteModern(); else if(idx==1) pickWallpaper(); else { prefs.edit().remove("custom_wallpaper_uri").apply(); View bg=findViewById(R.id.wallpaper); if(bg instanceof ImageView) ((ImageView)bg).setImageDrawable(null); } }); list.addView(row); }
        AlertDialog dlg=createModernDialog("Menu", list); dlg.show();
    }
    void showPaletteModern(){
        float dens=getResources().getDisplayMetrics().density;
        GridLayout grid=new GridLayout(this); grid.setColumnCount(5);
        int[] cols={0xFF6B4C8A,0xFF2196F3,0xFF00BCD4,0xFF4CAF50,0xFFE91E63,0xFF9C27B0,0xFF3F51B5,0xFFFF5722,0xFF212121,0xFFFFFFFF};
        for(int col:cols){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(56*dens); lp.height=(int)(56*dens); lp.setMargins((int)(8*dens),(int)(8*dens),(int)(8*dens),(int)(8*dens)); v.setLayoutParams(lp); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(16*dens); bg.setColor(col); if(col==0xFFFFFFFF) bg.setStroke((int)dens,0xFFCCCCCC); v.setBackground(bg); v.setOnClickListener(vw->{ glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); }
        AlertDialog dlg=createModernDialog("Thème", grid); dlg.show();
    }
    void pickWallpaper(){ try{ Intent it=new Intent(Intent.ACTION_OPEN_DOCUMENT); it.addCategory(Intent.CATEGORY_OPENABLE); it.setType("image/*"); startActivityForResult(it, 201); }catch(Exception e){ try{ Intent it2=new Intent(Intent.ACTION_PICK); it2.setType("image/*"); startActivityForResult(it2,201); }catch(Exception ee){} } }

    void preloadMax(){
        pool.execute(()->{
            try{
                Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all=getPackageManager().queryIntentActivities(it, 0);
                LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>();
                for(ResolveInfo ri:all){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri); }
                List<ResolveInfo> dedup=new ArrayList<>(map.values());
                Collections.sort(dedup,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
                allAppsCache=dedup;
                for(ResolveInfo ri:dedup){ try{ if(iconCache.get(ri.activityInfo.packageName)==null) iconCache.put(ri.activityInfo.packageName, ri.loadIcon(getPackageManager())); }catch(Exception e){} }
                mainH.post(()->{ setupDock(); if(rvFav!=null) rvFav.getAdapter().notifyDataSetChanged(); loadWallpaperFast(); });
            }catch(Exception e){}
        });
    }

    void filterAppsInstant(String q){
        try{
            suggList.clear();
            if(q==null||q.trim().isEmpty()){ if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); return;}
            String lq=q.toLowerCase().trim();
            List<ResolveInfo> src=allAppsCache.isEmpty()? getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0) : allAppsCache;
            for(ResolveInfo ri:src){
                String label=ri.loadLabel(getPackageManager()).toString().toLowerCase();
                if(label.contains(lq) || ri.activityInfo.packageName.toLowerCase().contains(lq)){ suggList.add(ri); if(suggList.size()>=80) break; }
            }
            if(rvSugg!=null){ rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.VISIBLE); }
        }catch(Exception e){}
    }

    void openFullDrawer(){
        try{
            List<ResolveInfo> tmp=allAppsCache;
            if(tmp.isEmpty()){ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0); LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:all){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri);} tmp=new ArrayList<>(map.values()); }
            final List<ResolveInfo> src=tmp;
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setPadding(12,12,12,12);
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ View vv=getLayoutInflater().inflate(R.layout.item_app,p,false); TextView lb=vv.findViewById(R.id.label); if(lb!=null){ lb.setMaxLines(1); lb.setEllipsize(android.text.TextUtils.TruncateAt.END); lb.setTextSize(10); lb.setTextColor(Color.WHITE); } return new H(vv); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=src.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->{ launchInstant(ri.activityInfo.packageName); }); }
                public int getItemCount(){ return src.size(); }
            });
            AlertDialog dlg=createModernDialog("Tiroir - "+src.size()+" apps", rv); dlg.show();
        }catch(Exception e){}
    }

    View findViewGlass(String...names){ for(String n:names){ int id=getResources().getIdentifier(n,"id",getPackageName()); if(id!=0){ View v=findViewById(id); if(v!=null) return v; } } return null; }
    void loadWallpaperFast(){ try{ String s=prefs.getString("custom_wallpaper_uri",""); if(s.isEmpty()) return; Uri uri=Uri.parse(s); View bg=findViewById(R.id.wallpaper); if(bg==null) bg=findViewById(R.id.bg); if(bg instanceof ImageView){ ((ImageView)bg).setScaleType(ImageView.ScaleType.CENTER_CROP); ((ImageView)bg).setImageURI(uri); } }catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri u=data.getData(); try{ getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri",u.toString()).apply(); loadWallpaperFast(); } }

    void setupAtAGlance(){ TextView c=(TextView)findViewById(R.id.clock); if(c==null) c=(TextView)findViewById(R.id.time); TextView d=(TextView)findViewById(R.id.dateInfo); if(d==null) d=(TextView)findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} if(mainRoot!=null) mainRoot.postDelayed(this,30000); }}; r.run(); }

    String resolveIntentPkg(Intent intent){ try{ List<ResolveInfo> r=getPackageManager().queryIntentActivities(intent,0); if(r!=null&&!r.isEmpty()) return r.get(0).activityInfo.packageName; }catch(Exception e){} return null; }
    String getSmartDefault(int idx){ try{ if(idx==0){ String p=resolveIntentPkg(new Intent(Intent.ACTION_DIAL)); if(p!=null) return p;} if(idx==1){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))); if(p!=null) return p;} if(idx==2) return "com.android.settings"; if(idx==4){ String p=resolveIntentPkg(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); if(p!=null) return p;} if(idx==5){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com"))); if(p!=null) return p;} }catch(Exception e){} return defaultPkgs[idx]; }
    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv; if(vv instanceof ImageView) iv=(ImageView)vv; else{ try{ iv=(ImageView)((FrameLayout)vv).getChildAt(0);}catch(Exception e){ continue; } }
            if(idx==3){ iv.setImageResource(android.R.drawable.ic_menu_sort_by_size); iv.setBackgroundResource(android.R.color.transparent); vv.setOnClickListener(v->openFullDrawer()); continue; }
            String saved=prefs.getString(dockKeys[idx], null); if(saved==null) saved=getSmartDefault(idx); String pkg=findRealPkg(saved); if(pkg==null) pkg=getSmartDefault(idx); updateDockIcon(iv,pkg);
            vv.setOnClickListener(v->{ String rs=prefs.getString(dockKeys[idx], getSmartDefault(idx)); String rp=findRealPkg(rs); if(rp!=null) launchInstant(rp); });
            vv.setOnLongClickListener(v->{ pickDockApp(idx); return true; });
        }
    }
    String findRealPkg(String pkg){ if(pkg==null) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return null; } }
    void updateDockIcon(ImageView iv, String pkg){ try{ Drawable c=iconCache.get(pkg); if(c!=null) iv.setImageDrawable(c); else iv.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); iv.setBackgroundColor(Color.TRANSPARENT);}catch(Exception e){ iv.setImageResource(android.R.drawable.sym_def_app_icon); } }
    void launchInstant(String pkg){ try{ Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION); clearSearchNow(); startActivity(ii); overridePendingTransition(0,0); } }catch(Exception e){} }
    void clearSearchNow(){ if(searchApps!=null) searchApps.setText(""); suggList.clear(); if(rvSugg!=null){ rvSugg.setAdapter(new SuggAdapter()); rvSugg.setVisibility(View.GONE); } if(rvFav!=null) rvFav.setVisibility(View.VISIBLE); }

    void pickDockApp(int idx){
        List<ResolveInfo> apps=allAppsCache.isEmpty()? getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0) : allAppsCache;
        LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:apps){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName, ri); }
        List<ResolveInfo> uniq=new ArrayList<>(map.values());
        RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=uniq.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[idx],ri.activityInfo.packageName).apply(); setupDock(); }); }
            public int getItemCount(){ return uniq.size(); }
        });
        AlertDialog dlg=createModernDialog("Choisir app dock", rv); dlg.show();
    }

    void loadFavs(){ favPkgs.clear(); String s=prefs.getString("favs",""); if(!s.isEmpty()) favPkgs.addAll(Arrays.asList(s.split(","))); }
    void saveFavs(){ prefs.edit().putString("favs", String.join(",", favPkgs)).apply(); }

    void showAddFavDialog(){
        try{
            List<ResolveInfo> tmp=allAppsCache;
            if(tmp.isEmpty()){ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0); LinkedHashMap<String,ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:all){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName,ri);} tmp=new ArrayList<>(map.values()); }
            final List<ResolveInfo> src=tmp;
            RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setPadding(12,12,12,12);
            AlertDialog dlg=createModernDialog("Grille 5x5 - "+favPkgs.size()+"/25", rv);
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ View vv=getLayoutInflater().inflate(R.layout.item_app,p,false); TextView lb=vv.findViewById(R.id.label); if(lb!=null){ lb.setMaxLines(1); lb.setEllipsize(android.text.TextUtils.TruncateAt.END); lb.setTextSize(9); lb.setTextColor(Color.WHITE); } return new H(vv); }
                public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=src.get(pos); String pkg=ri.activityInfo.packageName; h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(pkg); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); boolean added=favPkgs.contains(pkg); h.itemView.setAlpha(added?0.35f:1f);
                    h.itemView.setOnClickListener(v->{ if(favPkgs.contains(pkg)) favPkgs.remove(pkg); else { if(favPkgs.size()>=25){ Toast.makeText(MainActivity.this,"Max 25",0).show(); return; } favPkgs.add(pkg); } saveFavs(); if(rvFav!=null) rvFav.setAdapter(new FavAdapter()); notifyDataSetChanged(); }); }
                public int getItemCount(){ return src.size(); }
            });
            dlg.show();
        }catch(Exception e){}
    }

    void handleWeb(){ if(searchWeb==null) return; String q=searchWeb.getText().toString().trim(); if(q.isEmpty()) return; String url=q.contains(" ")?"https://www.google.com/search?q="+Uri.encode(q):q.startsWith("http")?q:"https://"+q; try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }catch(Exception e){} }
    void applyGlassTheme(int col){ try{ float dens=getResources().getDisplayMetrics().density; for(int id:new int[]{R.id.searchAppsMain,R.id.searchWebMain}){ View v=findViewById(id); if(v!=null) v.setBackground(glassBg(col, 28*dens, 92)); } View dock=findViewById(R.id.dock); if(dock==null) dock=findViewById(R.id.dockBar); if(dock!=null) dock.setBackground(glassBg(col, 26*dens, 68)); }catch(Exception e){} }
    void checkAndAskPermissions(){ try{ if(Build.VERSION.SDK_INT>=33){ if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},101); } }catch(Exception e){} }
    boolean isMyLauncherDefault(){ try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); ResolveInfo r=getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY); return r!=null && r.activityInfo.packageName.equals(getPackageName()); }catch(Exception e){ return false; } }
    void checkDefaultLauncher(){}

    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(ri.activityInfo.packageName); h.ic.setImageDrawable(cd!=null?cd:ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ View vv=getLayoutInflater().inflate(R.layout.item_app,p,false); TextView lb=vv.findViewById(R.id.label); if(lb!=null){ lb.setMaxLines(1); lb.setEllipsize(android.text.TextUtils.TruncateAt.END); lb.setTextSize(10); lb.setTextColor(Color.WHITE); } return new H(vv); } public void onBindViewHolder(H h,int pos){ try{ String pkg=favPkgs.get(pos); ResolveInfo ri=null; for(ResolveInfo r:allAppsCache){ if(r.activityInfo.packageName.equals(pkg)){ ri=r; break; } } if(ri!=null) h.lb.setText(ri.loadLabel(getPackageManager())); else h.lb.setText(pkg); Drawable cd=iconCache.get(pkg); if(cd!=null) h.ic.setImageDrawable(cd); else h.ic.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launchInstant(pkg)); h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return favPkgs.size(); } }
}
