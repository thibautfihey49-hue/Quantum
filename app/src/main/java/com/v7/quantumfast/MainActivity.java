
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
            mainH.postDelayed(()->{ try{ preloadMaxSafe(); }catch(Exception e){} },800);
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
    void showMenu(){
        try{
            new AlertDialog.Builder(this).setTitle("Menu").setItems(new String[]{"Couleur","Fond","Icon packs gratuits","Polices","Effacer fond"}, (d,w)->{
                try{
                    if(w==1) pickWallpaper();
                    else if(w==2) showIconPack();
                    else if(w==4){ if(wallpaperView!=null) wallpaperView.setImageDrawable(null); }
                    else Toast.makeText(this,"Bientot",0).show();
                }catch(Exception e){}
            }).show();
        }catch(Exception e){}
    }
    void showIconPack(){
        try{
            PackageManager pm=getPackageManager(); List<ResolveInfo> packs=new ArrayList<>();
            try{ packs.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"),0)); }catch(Exception e){}
            try{ packs.addAll(pm.queryIntentActivities(new Intent("com.novalauncher.THEME"),0)); }catch(Exception e){}
            HashSet<String> seen=new HashSet<>(); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
            for(ResolveInfo ri:packs){ String pkg=ri.activityInfo.packageName; if(!seen.add(pkg)) continue; TextView tv=new TextView(this); tv.setText(ri.loadLabel(pm).toString()+" ("+pkg+")"); tv.setPadding(20,20,20,20); tv.setTextColor(Color.WHITE); tv.setOnClickListener(v-> Toast.makeText(this,"Pack: "+pkg,0).show()); list.addView(tv); }
            if(packs.isEmpty()){ TextView tv=new TextView(this); tv.setText("Aucun pack - installe Delta, Arcticons gratuit"); tv.setPadding(20,20,20,20); tv.setTextColor(Color.WHITE); list.addView(tv); }
            ScrollView sv=new ScrollView(this); sv.addView(list);
            new AlertDialog.Builder(this).setTitle("Icon packs").setView(sv).show();
        }catch(Exception e){}
    }
    void pickWallpaper(){ try{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); startActivityForResult(i,1001); }catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ super.onActivityResult(rc,res,data); try{ if(rc==1001 && res==RESULT_OK && data!=null){ Uri uri=data.getData(); if(wallpaperView!=null) wallpaperView.setImageURI(uri); getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); prefs.edit().putString("wall_uri",uri.toString()).apply(); } }catch(Exception e){} }
    void checkDefault(){ try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); i.addCategory(Intent.CATEGORY_DEFAULT); ResolveInfo ri=getPackageManager().resolveActivity(i,0); if(ri!=null && !ri.activityInfo.packageName.equals(getPackageName())){ new AlertDialog.Builder(this).setTitle("Launcher par defaut").setMessage("Definir Quantum?").setPositiveButton("Oui", (d,w)->{ try{ startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }catch(Exception e){} }).setNegativeButton("Non",null).show(); } }catch(Exception e){} }
    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=suggList.get(pos); h.lb.setText(ri.loadLabel(getPackageManager()).toString()); h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); h.itemView.setOnClickListener(v-> launchInstant(ri.activityInfo.packageName)); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; H(View v){super(v); ic=v.findViewById(R.id.icon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ String pkg=favPkgs.get(pos); h.ic.setImageDrawable(getPackageManager().getApplicationInfo(pkg,0).loadIcon(getPackageManager())); h.itemView.setOnClickListener(v-> launchInstant(pkg)); h.itemView.setOnLongClickListener(v->{ favPkgs.remove(pos); saveFavs(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return favPkgs.size(); } }
}

