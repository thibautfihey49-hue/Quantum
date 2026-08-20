package com.v7.quantumfast;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends android.app.Activity {
    View mainRoot;
    EditText searchApps, searchWeb;
    RecyclerView rvSugg, rvFav, rvFolders;
    SharedPreferences prefs, glassPrefs;
    List<ResolveInfo> suggList=new ArrayList<>();
    List<String> favPkgs=new ArrayList<>();
    List<Folder> folders=new ArrayList<>();
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
        rvSugg=(RecyclerView)findViewGlass("rvSuggestions","rvSugg","suggestions");
        rvFav=(RecyclerView)findViewGlass("rvFavorites","rvFav","favs");
        rvFolders=(RecyclerView)findViewGlass("rvFolders","folders");
        if(rvSugg!=null){ rvSugg.setLayoutManager(new LinearLayoutManager(this)); rvSugg.setAdapter(new SuggAdapter()); }
        if(rvFav!=null){ rvFav.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,false)); }
        if(rvFolders!=null){ rvFolders.setLayoutManager(new GridLayoutManager(this,5)); }

        // cadre moderne : marges + radius
        fixModernFrame();

        View clear=findViewGlass("clearApps","btnClear","clear"); if(clear!=null) clear.setOnClickListener(v->{ if(searchApps!=null) searchApps.setText(""); });
        View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setOnClickListener(v->handleWeb());
        if(searchWeb!=null) searchWeb.setOnEditorActionListener((v,id,ev)->{ if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){ handleWeb(); return true;} return false; });
        View fav=findViewGlass("btnAddFav","Fav","fav"); if(fav!=null) fav.setOnClickListener(v->showAddFavDialog());
        View fol=findViewGlass("btnAddFolder","Folder","folder"); if(fol!=null) fol.setOnClickListener(v->showCreateFolderDialog());
        View men=findViewGlass("btnMenu","Menu","menu"); if(men!=null) men.setOnClickListener(v->showPaletteDialog());

        loadFavs(); loadFolders();
        if(rvFav!=null) rvFav.setAdapter(new FavAdapter());
        if(rvFolders!=null) rvFolders.setAdapter(new FolderAdapter());

        setupAtAGlanceSimple(); setupDock(); preloadFast();
        int saved=glassPrefs.getInt("glass_color",0xFF6B4C8A);
        applyGlassTheme(saved);

        if(searchApps!=null) searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ filterApps(s.toString()); }
            public void afterTextChanged(android.text.Editable s){}
        });

        // --- PERMISSIONS + DEFAULT LAUNCHER AU DEMARRAGE ---
        checkAndAskPermissions();
        mainRoot.postDelayed(this::checkDefaultLauncher, 800);
    }

    // MODERNE BIEN CADRE
    void fixModernFrame(){
        try{
            float d=getResources().getDisplayMetrics().density;
            if(mainRoot!=null){ mainRoot.setPadding((int)(12*d),(int)(28*d),(int)(12*d),(int)(12*d)); }
            for(int id:new int[]{R.id.searchAppsMain,R.id.searchWebMain}){
                View v=findViewById(id); if(v!=null){ ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)v.getLayoutParams(); lp.topMargin=(int)(8*d); lp.bottomMargin=(int)(8*d); v.setLayoutParams(lp); }
            }
        }catch(Exception e){}
    }

    boolean isMyLauncherDefault(){
        try{
            Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r=getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r!=null && r.activityInfo.packageName.equals(getPackageName());
        }catch(Exception e){ return false; }
    }
    void checkDefaultLauncher(){
        if(isMyLauncherDefault()) return;
        if(prefs.getBoolean("asked_default",false)) return;
        new AlertDialog.Builder(this).setTitle("Launcher par défaut")
           .setMessage("Mettre Quantum en launcher par défaut au démarrage?")
           .setPositiveButton("Oui",(di,w)->{
                prefs.edit().putBoolean("asked_default",true).apply();
                try{ startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                catch(Exception e){ Intent c=new Intent(Intent.ACTION_MAIN); c.addCategory(Intent.CATEGORY_HOME); c.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(Intent.createChooser(c,"Choisir launcher")); }
            }).setNegativeButton("Plus tard",null).show();
    }
    void checkAndAskPermissions(){
        try{
            if(Build.VERSION.SDK_INT>=33){
                if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED)
                    ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, 101);
            }else{
                if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
                    ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
            }
        }catch(Exception e){}
    }

    View findViewGlass(String...names){ for(String n:names){ int id=getResources().getIdentifier(n,"id",getPackageName()); if(id!=0){ View v=findViewById(id); if(v!=null) return v; } } return null; }
    void preloadFast(){ try{ loadWallpaperFast(); }catch(Exception e){} }
    void loadWallpaperFast(){
        try{ String s=prefs.getString("custom_wallpaper_uri",""); if(s.isEmpty()) return; Uri uri=Uri.parse(s); View bg=findViewGlass("wallpaper","bg","background","wall"); if(bg instanceof ImageView) ((ImageView)bg).setImageURI(uri); }catch(Exception e){}
    }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri u=data.getData(); try{ getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri",u.toString()).apply(); loadWallpaperFast(); } }

    void setupAtAGlanceSimple(){
        clock();
        try{ BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context c, Intent i){ int lvl=i.getIntExtra("level",-1); TextView tv=(TextView)findViewGlass("batteryInfo","battery","bat"); if(tv!=null&&lvl!=-1) tv.setText("🔋 "+lvl+"%"); } }; registerReceiver(br,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); }catch(Exception e){}
    }
    void clock(){
        TextView c=(TextView)findViewGlass("clock","time"); TextView d=(TextView)findViewGlass("date","dateInfo");
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE);
        Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} if(mainRoot!=null) mainRoot.postDelayed(this,30000); }}; r.run();
    }
    void filterApps(String q){
        try{ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> all=getPackageManager().queryIntentActivities(it,0); suggList.clear(); if(q==null||q.trim().isEmpty()){ if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); return;} String lq=q.toLowerCase(); for(ResolveInfo ri:all){ String lb=ri.loadLabel(getPackageManager()).toString().toLowerCase(); if(lb.contains(lq)) suggList.add(ri);} if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); }catch(Exception e){}
    }
    String resolveIntentPkg(Intent intent){ try{ List<ResolveInfo> r=getPackageManager().queryIntentActivities(intent,0); if(r!=null&&!r.isEmpty()) return r.get(0).activityInfo.packageName; }catch(Exception e){} return null; }
    String getSmartDefault(int idx){ try{ if(idx==0){ String p=resolveIntentPkg(new Intent(Intent.ACTION_DIAL)); if(p!=null) return p;} if(idx==1){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))); if(p!=null) return p;} if(idx==2) return "com.android.settings"; if(idx==4){ String p=resolveIntentPkg(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); if(p!=null) return p;} if(idx==5){ String p=resolveIntentPkg(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com"))); if(p!=null) return p;} }catch(Exception e){} return defaultPkgs[idx]; }
    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv; if(vv instanceof ImageView) iv=(ImageView)vv; else{ try{ iv=(ImageView)((FrameLayout)vv).getChildAt(0);}catch(Exception e){ continue; } } if(idx==3){ try{ iv.setImageResource(android.R.drawable.ic_menu_sort_by_size); iv.setBackgroundColor(Color.TRANSPARENT);}catch(Exception e){} vv.setOnClickListener(v->openDrawerWithQuery("")); continue; } String saved=prefs.getString(dockKeys[idx], null); if(saved==null) saved=getSmartDefault(idx); String pkg=findRealPkg(saved); if(pkg==null) pkg=getSmartDefault(idx); updateDockIcon(iv,pkg); vv.setOnClickListener(v->{ String rs=prefs.getString(dockKeys[idx], getSmartDefault(idx)); String rp=findRealPkg(rs); if(rp!=null) launch(rp); }); vv.setOnLongClickListener(v->{ pickDockApp(idx); return true; }); }
    }
    String findRealPkg(String pkg){ if(pkg==null) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){ return null; } }
    void updateDockIcon(ImageView iv, String pkg){ try{ if(pkg==null) throw new Exception(); iv.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); iv.setBackgroundColor(Color.TRANSPARENT);}catch(Exception e){ iv.setImageResource(android.R.drawable.sym_def_app_icon); } }
    void launch(String pkg){ try{ Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); clearSearchNow(); startActivity(ii); return; } }catch(Exception e){} try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); clearSearchNow(); startActivity(it);} }catch(Exception e){} }
    void openDrawerWithQuery(String q){ if(searchApps!=null) searchApps.setText(q); }
    void clearSearchNow(){ if(searchApps!=null) searchApps.setText(""); suggList.clear(); if(rvSugg!=null) rvSugg.setAdapter(new SuggAdapter()); }
    void pickDockApp(int idx){
        Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(it,0);
        LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>();
        for(ResolveInfo ri:apps){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName, ri); }
        List<ResolveInfo> uniq=new ArrayList<>(map.values());
        Collections.sort(uniq,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        String[] names=new String[uniq.size()]; String[] pkgs=new String[uniq.size()];
        for(int i=0;i<uniq.size();i++){ names[i]=uniq.get(i).loadLabel(getPackageManager()).toString(); pkgs[i]=uniq.get(i).activityInfo.packageName; }
        new AlertDialog.Builder(this).setTitle("Choisir app dock").setItems(names,(d,w)->{ prefs.edit().putString(dockKeys[idx],pkgs[w]).apply(); setupDock(); }).show();
    }
    void loadFavs(){ favPkgs.clear(); String s=prefs.getString("favs",""); if(!s.isEmpty()) favPkgs.addAll(Arrays.asList(s.split(","))); }
    void saveFavs(){ prefs.edit().putString("favs", String.join(",", favPkgs)).apply(); }
    void loadFolders(){ folders.clear(); String s=prefs.getString("folders",""); if(!s.isEmpty()){ for(String f:s.split("\\|")){ String[] p=f.split(":"); if(p.length>=1){ Folder fo=new Folder(p[0]); if(p.length>1&&!p[1].isEmpty()) fo.pkgs.addAll(Arrays.asList(p[1].split(","))); folders.add(fo);} } } }
    void saveFolders(){ StringBuilder sb=new StringBuilder(); for(Folder fo:folders){ if(sb.length()>0) sb.append("|"); sb.append(fo.name).append(":").append(String.join(",", fo.pkgs)); } prefs.edit().putString("folders", sb.toString()).apply(); }

    void showAddFavDialog(){
        Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> apps=getPackageManager().queryIntentActivities(it,0);
        LinkedHashMap<String, ResolveInfo> map=new LinkedHashMap<>(); for(ResolveInfo ri:apps){ if(!map.containsKey(ri.activityInfo.packageName)) map.put(ri.activityInfo.packageName, ri); }
        List<ResolveInfo> uniq=new ArrayList<>(map.values()); Collections.sort(uniq,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        String[] names=new String[uniq.size()]; String[] pkgs=new String[uniq.size()]; for(int i=0;i<uniq.size();i++){ names[i]=uniq.get(i).loadLabel(getPackageManager()).toString(); pkgs[i]=uniq.get(i).activityInfo.packageName; }
        new AlertDialog.Builder(this).setTitle("Ajouter fav").setItems(names,(d,w)->{ if(!favPkgs.contains(pkgs[w])){ favPkgs.add(pkgs[w]); saveFavs(); if(rvFav!=null) rvFav.setAdapter(new FavAdapter()); }}).show();
    }
    void showCreateFolderDialog(){
        EditText et=new EditText(this); et.setHint("Nom dossier");
        new AlertDialog.Builder(this).setTitle("Nouveau dossier").setView(et).setPositiveButton("Créer",(d,w)->{ String n=et.getText().toString().trim(); if(n.isEmpty()) n="Dossier"; Folder fo=new Folder(n); folders.add(fo); saveFolders(); if(rvFolders!=null) rvFolders.setAdapter(new FolderAdapter()); }).setNegativeButton("Annuler",null).show();
    }
    void handleWeb(){ if(searchWeb==null) return; String q=searchWeb.getText().toString().trim(); if(q.isEmpty()) return; showBrowserChooserGlass(q); }
    void showBrowserChooserGlass(String q){
        String low=q.toLowerCase(); String url;
        if(low.startsWith("yt ")) url="https://www.youtube.com/results?search_query="+Uri.encode(q.substring(3));
        else if(low.startsWith("g ")) url="https://www.google.com/search?q="+Uri.encode(q.substring(2));
        else if(low.startsWith("d ")) url="https://drive.google.com/drive/search?q="+Uri.encode(q.substring(2));
        else if(low.startsWith("w ")) url="https://fr.wikipedia.org/wiki/"+Uri.encode(q.substring(2));
        else if(q.contains(" ")) url="https://www.google.com/search?q="+Uri.encode(q);
        else if(q.contains(".")) url=q.startsWith("http")?q:"https://"+q;
        else url="https://www.google.com/search?q="+Uri.encode(q);
        try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }catch(Exception e){ Toast.makeText(this,q,0).show(); }
    }
    android.graphics.drawable.Drawable createGlassDrawable(int col, float rad, int alpha){
        int fill=Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col));
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); d.setCornerRadius(rad); d.setColor(fill);
        d.setStroke((int)(1.2f*getResources().getDisplayMetrics().density), Color.argb(110,255,255,255));
        return d;
    }
    void showPaletteDialog(){
        float dens=getResources().getDisplayMetrics().density;
        int[] pastels={0xFFFFD4E7,0xFFD4E7FF,0xFFC7B6FF,0xFFB6FFE8,0xFFFFF3B6,0xFFFFE0B2};
        int[] vifs={0xFF2196F3,0xFF00BCD4,0xFF4CAF50,0xFFFFC107,0xFFFF5722,0xFFE91E63,0xFF9C27B0,0xFF3F51B5};
        int[] all={0xFFFFCDD2,0xFFF44336,0xFFF8BBD0,0xFFE91E63,0xFFCE93D8,0xFF9C27B0,0xFFB39DDB,0xFF673AB7,0xFF7986CB,0xFF3F51B5,0xFF64B5F6,0xFF2196F3,0xFF4DD0E1,0xFF00BCD4,0xFF81C784,0xFF4CAF50,0xFFFFEB3B,0xFFFF9800,0xFF795548,0xFF212121,0xFFFFFFFF};
        AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("Palette Glass Moderne");
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(16*dens),(int)(16*dens),(int)(16*dens),(int)(16*dens));
        TextView preview=new TextView(this); preview.setText(" Aperçu verre moderne "); preview.setTextSize(18); preview.setBackground(createGlassDrawable(0xFF6B4C8A, 24*dens, 75)); root.addView(preview);
        GridLayout gp=new GridLayout(this); gp.setColumnCount(6); for(int col:pastels){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(52*dens); lp.height=(int)(52*dens); lp.setMargins((int)(6*dens),(int)(6*dens),(int)(6*dens),(int)(6*dens)); v.setLayoutParams(lp); v.setBackground(createGlassDrawable(col,18*dens,95)); v.setOnClickListener(vw->{ preview.setBackground(createGlassDrawable(col,24*dens,75)); glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); gp.addView(v); } root.addView(gp);
        GridLayout gv=new GridLayout(this); gv.setColumnCount(6); for(int col:vifs){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(52*dens); lp.height=(int)(52*dens); lp.setMargins((int)(6*dens),(int)(6*dens),(int)(6*dens),(int)(6*dens)); v.setLayoutParams(lp); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(18*dens); bg.setColor(col); v.setBackground(bg); v.setOnClickListener(vw->{ preview.setBackground(createGlassDrawable(col,24*dens,75)); glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); gv.addView(v); } root.addView(gv);
        GridLayout grid=new GridLayout(this); grid.setColumnCount(6); for(int col:all){ View v=new View(this); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(42*dens); lp.height=(int)(42*dens); lp.setMargins((int)(5*dens),(int)(5*dens),(int)(5*dens),(int)(5*dens)); v.setLayoutParams(lp); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(12*dens); bg.setColor(col); if(col==0xFFFFFFFF) bg.setStroke((int)dens,0xFFCCCCCC); v.setBackground(bg); v.setOnClickListener(vw->{ preview.setBackground(createGlassDrawable(col,24*dens,75)); glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); } ScrollView sv=new ScrollView(this); sv.addView(grid); root.addView(sv);
        b.setView(root); b.setPositiveButton("HEX",(d,w)->{ EditText et=new EditText(this); et.setHint("#6B4C8A"); new AlertDialog.Builder(this).setTitle("HEX").setView(et).setPositiveButton("OK",(dd,ww)->{ try{ int c=Color.parseColor(et.getText().toString().trim()); glassPrefs.edit().putInt("glass_color",c).apply(); applyGlassTheme(c);}catch(Exception e){} }).show(); }); b.setNegativeButton("Fermer",null); b.show();
    }
    void applyGlassTheme(int col){
        try{
            float dens=getResources().getDisplayMetrics().density;
            double lum=(0.299*Color.red(col)+0.587*Color.green(col)+0.114*Color.blue(col))/255;
            int textCol= lum>0.6? 0xFF1A1A1A : 0xFFFFFFFF; int hintCol= lum>0.6? 0x661A1A1A : 0x99FFFFFF;
            for(int id:new int[]{R.id.searchAppsMain,R.id.searchWebMain}){ View v=findViewById(id); if(v!=null){ v.setBackground(createGlassDrawable(col, 32*dens, 72)); if(v instanceof EditText){ ((EditText)v).setTextColor(textCol); ((EditText)v).setHintTextColor(hintCol);} } }
            View go=findViewGlass("btnWebGo","go","web_go","btnGo"); if(go!=null) go.setBackground(createGlassDrawable(col, 32*dens, 78));
            View dock=findViewGlass("dock","dockBar","dock_container","dockContainer","bottomDock"); if(dock!=null) dock.setBackground(createGlassDrawable(col, 28*dens, 55));
        }catch(Exception e){}
    }
    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launch(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; H(View v){super(v); ic=v.findViewById(R.id.icon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ String pkg=favPkgs.get(pos); h.ic.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); h.itemView.setOnClickListener(v->launch(pkg)); h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return favPkgs.size(); } }
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView lb; H(View v){super(v); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ Folder fo=folders.get(pos); h.lb.setText(fo.name); h.itemView.setOnClickListener(v->{ AlertDialog.Builder b=new AlertDialog.Builder(MainActivity.this); b.setTitle(fo.name); String[] pkgs=fo.pkgs.toArray(new String[0]); String[] names=new String[pkgs.length]; for(int i=0;i<pkgs.length;i++){ try{ names[i]=getPackageManager().getApplicationInfo(pkgs[i],0).loadLabel(getPackageManager()).toString(); }catch(Exception e){ names[i]=pkgs[i]; } } b.setItems(names,(d,w)->launch(pkgs[w])); b.setPositiveButton("Ajouter app",(d,w)->{ Intent it=new Intent(Intent.ACTION_MAIN); it.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> apps=getPackageManager().queryIntentActivities(it,0); String[] an=new String[apps.size()]; String[] ap=new String[apps.size()]; for(int i=0;i<apps.size();i++){ an[i]=apps.get(i).loadLabel(getPackageManager()).toString(); ap[i]=apps.get(i).activityInfo.packageName; } new AlertDialog.Builder(MainActivity.this).setTitle("Ajouter").setItems(an,(dd,ww)->{ fo.pkgs.add(ap[ww]); saveFolders(); notifyDataSetChanged(); }).show(); }); b.setNegativeButton("Supprimer dossier",(d,w)->{ folders.remove(pos); saveFolders(); notifyDataSetChanged(); }); b.show(); }); } public int getItemCount(){ return folders.size(); } }
}
