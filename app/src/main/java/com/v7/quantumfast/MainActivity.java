package com.v7.quantumfast;
import android.app.Activity; import android.content.*; import android.content.pm.*; import android.graphics.drawable.Drawable; import android.net.Uri; import android.os.*; import android.view.*; import android.view.inputmethod.EditorInfo; import android.widget.*; import androidx.recyclerview.widget.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.concurrent.*;
public class MainActivity extends Activity {
    ImageView wallpaperView; ExecutorService exec = Executors.newFixedThreadPool(3); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"}; String[] defaultPkgs={"com.android.dialer","com.google.android.gm","com.google.android.apps.messaging","com.google.android.calendar","com.android.camera2","com.android.chrome"};
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0); getWindow().setNavigationBarColor(0);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER); setContentView(R.layout.activity_main);
        wallpaperView=findViewById(R.id.wallpaperView); prefs=getSharedPreferences("dock",0); preload(); clock(); setupDock(); loadSavedWallpaper();
        EditText searchApps=findViewById(R.id.searchAppsMain); EditText searchWeb=findViewById(R.id.searchWebMain);
        searchApps.setOnFocusChangeListener((v,has)->{ if(has) openDrawerWithQuery(""); }); searchApps.setOnClickListener(v->openDrawerWithQuery(""));
        searchApps.setOnEditorActionListener((v,actionId,event)->{ if(actionId==EditorInfo.IME_ACTION_SEARCH){ openDrawerWithQuery(v.getText().toString()); return true;} return false; });
        View.OnClickListener goWeb= vv->{ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooser(q); };
        findViewById(R.id.btnWebGo).setOnClickListener(goWeb);
        searchWeb.setOnEditorActionListener((v,actionId,event)->{ if(actionId==EditorInfo.IME_ACTION_SEARCH){ String q=v.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooser(q); return true;} return false; });
        main.postDelayed(()->{ if(!isDefaultLauncher()) showGlassDialog(); }, 800);
        findViewById(R.id.btnMenu).setOnClickListener(v->showGlassMenu());
    }
    boolean isDefaultLauncher(){ Intent home = new Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY); return ri!=null && ri.activityInfo!=null && getPackageName().equals(ri.activityInfo.packageName); }
    void showGlassDialog(){
        if(prefs.getBoolean("asked_default", false)) return;
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar); dlg.setContentView(R.layout.dialog_default);
        dlg.findViewById(R.id.bOk).setOnClickListener(v->{ prefs.edit().putBoolean("asked_default", true).apply(); dlg.dismiss(); try{ Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS); startActivity(i);}catch(Exception e){} });
        dlg.findViewById(R.id.bCancel).setOnClickListener(v->{ prefs.edit().putBoolean("asked_default", true).apply(); dlg.dismiss(); }); dlg.show();
    }
    void showGlassMenu(){ android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar); dlg.setContentView(R.layout.dialog_default); ((TextView)dlg.findViewById(R.id.dTitle)).setText("Quantum"); ((TextView)dlg.findViewById(R.id.dMsg)).setText("Changer fond • Ouvrir tiroir"); ((TextView)dlg.findViewById(R.id.bOk)).setText("Fond"); ((TextView)dlg.findViewById(R.id.bCancel)).setText("Tiroir"); dlg.findViewById(R.id.bOk).setOnClickListener(v->{ dlg.dismiss(); pickWallpaperInternal(); }); dlg.findViewById(R.id.bCancel).setOnClickListener(v->{ dlg.dismiss(); openDrawerWithQuery(""); }); dlg.show(); }
    void loadSavedWallpaper(){ String uriStr=prefs.getString("custom_wallpaper_uri",null); if(uriStr!=null){ try{ Uri u=Uri.parse(uriStr); wallpaperView.setImageURI(u);}catch(Exception e){} } }
    void pickWallpaperInternal(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i,201); }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri uri=data.getData(); try{ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply(); wallpaperView.setImageURI(uri); Toast.makeText(this,"Fond ✓",Toast.LENGTH_SHORT).show(); } }
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<180) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); main.postDelayed(this,30000);} }; r.run(); }
    void preload(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent ii=new Intent(Intent.ACTION_MAIN,null); ii.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(ii,0); l.sort((a,bb)->a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString())); cache.clear(); cache.addAll(l); for(ResolveInfo ri:l){ try{ if(!iconCache.containsKey(ri.activityInfo.packageName)) iconCache.put(ri.activityInfo.packageName, ri.loadIcon(pm)); }catch(Exception e){} } main.post(()->setupDock()); }catch(Exception e){}}); }
    void setupDock(){ int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome}; for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((FrameLayout)vv).getChildAt(0); if(idx==3) continue; String pkg=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(pkg!=null) updateDockIcon(iv, pkg); vv.setOnClickListener(view->{ if(idx==3) openDrawerWithQuery(""); else { String rp=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(rp!=null) launch(rp); }}); vv.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; }); } findViewById(R.id.dDrawer).setOnClickListener(v->openDrawerWithQuery("")); }
    String findRealPkg(String pkg){ if(pkg==null||pkg.isEmpty()) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg;}catch(Exception e){} return null; }
    void updateDockIcon(ImageView iv, String pkg){ Drawable cd=iconCache.get(pkg); if(cd!=null){ iv.setImageDrawable(cd); return; } exec.execute(()->{ try{ Drawable d=getPackageManager().getApplicationIcon(pkg); iconCache.put(pkg,d); main.post(()->iv.setImageDrawable(d)); }catch(Exception e){}}); }
    void pickDockApp(int dockIdx){ android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.picker_dock); RecyclerView rv=dlg.findViewById(R.id.recyclerDock); rv.setHasFixedSize(true); rv.setLayoutManager(new GridLayoutManager(this,5)); List<ResolveInfo> list=new ArrayList<>(cache); rv.setAdapter(new RecyclerView.Adapter(){ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable d=iconCache.get(ri.activityInfo.packageName); if(d!=null) h.ic.setImageDrawable(d); h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[dockIdx], ri.activityInfo.packageName).apply(); dlg.dismiss(); setupDock(); }); } public int getItemCount(){ return list.size(); } }); dlg.show(); }
    void showBrowserChooser(String query){
        String url = query.startsWith("http")? query : "https://www.google.com/search?q="+Uri.encode(query);
        Intent base = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        List<ResolveInfo> browsers = getPackageManager().queryIntentActivities(base, 0);
        if(browsers.isEmpty()){ Toast.makeText(this,"Aucun navigateur",Toast.LENGTH_SHORT).show(); return; }
        String[] names=new String[browsers.size()];
        for(int i=0;i<browsers.size();i++) names[i]=browsers.get(i).loadLabel(getPackageManager()).toString();
        new android.app.AlertDialog.Builder(this).setTitle("Choisir navigateur: "+query)
          .setItems(names, (d,which)->{
                try{
                    ResolveInfo ri=browsers.get(which);
                    Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setPackage(ri.activityInfo.packageName);
                    prefs.edit().putString("default_browser", ri.activityInfo.packageName).apply();
                    startActivity(intent);
                }catch(Exception e){}
            }).setNegativeButton("Annuler", null).show();
    }
    void openDrawerWithQuery(String initial){
        if(!tap() && initial.isEmpty()) return;
        android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dlg.getWindow().setStatusBarColor(0); dlg.getWindow().setNavigationBarColor(0);
        dlg.setContentView(R.layout.drawer);
        RecyclerView rv=dlg.findViewById(R.id.recycler);
        EditText s=dlg.findViewById(R.id.search);
        rv.setHasFixedSize(true); rv.setItemViewCacheSize(50); rv.setItemAnimator(null); rv.setLayoutManager(new GridLayoutManager(this,5));
        final List<ResolveInfo> filt = new ArrayList<>();
        if(initial==null) initial="";
        if(initial.isEmpty()) filt.addAll(cache);
        else { String low=initial.toLowerCase(); for(ResolveInfo ri:cache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(low)) filt.add(ri); }
        final FastAdapter ad = new FastAdapter(filt,getPackageManager(),dlg);
        rv.setAdapter(ad);
        s.setText(initial);
        final Runnable[] runHolder = new Runnable[1];
        s.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence a,int b,int c,int d){}
            public void afterTextChanged(android.text.Editable e){}
            public void onTextChanged(CharSequence q,int b,int c,int dd){
                if(runHolder[0]!=null) main.removeCallbacks(runHolder[0]);
                runHolder[0]=new Runnable(){
                    public void run(){
                        String qq=q.toString().toLowerCase().trim();
                        List<ResolveInfo> r=new ArrayList<>();
                        if(qq.isEmpty()) r.addAll(cache);
                        else for(ResolveInfo ri:cache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(qq)) r.add(ri);
                        filt.clear(); filt.addAll(r);
                        ad.notifyDataSetChanged();
                    }
                };
                main.postDelayed(runHolder[0],80);
            }
        });
        dlg.findViewById(R.id.close).setOnClickListener(v->dlg.dismiss());
        dlg.show();
    }
    void launch(String pkg){ try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null) startActivity(it);}catch(Exception e){} }
    class FastAdapter extends RecyclerView.Adapter<FastAdapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; FastAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ View v=getLayoutInflater().inflate(R.layout.item_app,pa,false); v.setLayerType(View.LAYER_TYPE_HARDWARE,null); return new H(v); } public void onBindViewHolder(H h,int pos){ ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(pm)); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(cd); h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null) startActivity(it); dlg.dismiss();}catch(Exception e){}}); } public int getItemCount(){ return list.size(); } }
}
