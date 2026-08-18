package com.v7.quantumfast;
import android.app.Activity; import android.app.WallpaperManager; import android.appwidget.*; import android.content.*; import android.content.pm.*; import android.graphics.*; import android.graphics.drawable.Drawable; import android.net.Uri; import android.os.*; import android.provider.MediaStore; import android.view.*; import android.widget.*; import androidx.recyclerview.widget.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.concurrent.*;
public class MainActivity extends Activity {
    AppWidgetManager mgr; AppWidgetHost host; LinearLayout container; ImageView wallpaperView; View addHint;
    ExecutorService exec = Executors.newFixedThreadPool(3); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"}; String[] defaultPkgs={"com.android.dialer","com.google.android.gm","com.android.calendar","", "com.android.camera2","com.android.chrome"};
    int pendingWidgetId=-1; AppWidgetProviderInfo pendingInfo;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER); getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setContentView(R.layout.activity_main);
        wallpaperView=findViewById(R.id.wallpaperView); container=findViewById(R.id.widgetContainer); addHint=findViewById(R.id.addWidgetHint);
        prefs=getSharedPreferences("dock",0); mgr=AppWidgetManager.getInstance(this); host=new AppWidgetHost(this,1024); host.startListening();
        preload(); clock(); setupDock(); loadSavedWallpaper();
        findViewById(R.id.btnMenu).setOnClickListener(v->showHomeMenu());
        addHint.setOnClickListener(v->showWidgetPickerSafe());
    }
    void loadSavedWallpaper(){ String uriStr=prefs.getString("custom_wallpaper_uri",null); if(uriStr!=null){ try{ Uri u=Uri.parse(uriStr); wallpaperView.setImageURI(u);}catch(Exception e){} } }
    void showHomeMenu(){ PopupMenu pm=new PopupMenu(this, findViewById(R.id.btnMenu)); pm.getMenu().add("🎨 Changer fond Quantum"); pm.getMenu().add("🖼 Changer fond système"); pm.getMenu().add("🧩 Ajouter widget"); pm.getMenu().add("🗂 Ouvrir tiroir"); pm.setOnMenuItemClickListener(item->{ String t=item.getTitle().toString(); if(t.contains("Quantum")) pickWallpaperInternal(); else if(t.contains("système")) pickWallpaperSystem(); else if(t.contains("widget")) showWidgetPickerSafe(); else openDrawer(); return true; }); pm.show(); }
    void pickWallpaperInternal(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i,201); }
    void pickWallpaperSystem(){ Intent i=new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); startActivityForResult(i,200); }
    @Override protected void onStart(){super.onStart(); try{host.startListening();}catch(Exception e){}} @Override protected void onStop(){super.onStop(); try{host.stopListening();}catch(Exception e){}}
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<180) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); main.postDelayed(this,30000);} }; r.run(); }
    void preload(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent ii=new Intent(Intent.ACTION_MAIN,null); ii.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(ii,0); l.sort((a,bb)->a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString())); cache.clear(); cache.addAll(l); for(ResolveInfo ri:l){ try{ if(!iconCache.containsKey(ri.activityInfo.packageName)) iconCache.put(ri.activityInfo.packageName, ri.loadIcon(pm)); }catch(Exception e){} } main.post(()->setupDock()); }catch(Exception e){}}); }
    void setupDock(){ int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome}; for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((FrameLayout)vv).getChildAt(0); if(idx==3) continue; String pkg=prefs.getString(dockKeys[idx], defaultPkgs[idx]); String realPkg=findRealPkg(pkg); if(realPkg!=null) updateDockIcon(iv, realPkg); else iv.setImageResource(android.R.drawable.sym_def_app_icon); vv.setOnClickListener(view->{ if(idx==3) openDrawer(); else { String p=prefs.getString(dockKeys[idx], defaultPkgs[idx]); String rp=findRealPkg(p); if(rp!=null) launch(rp); }}); vv.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; }); } findViewById(R.id.dDrawer).setOnClickListener(v->openDrawer()); }
    String findRealPkg(String pkg){ if(pkg==null||pkg.isEmpty()) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg;}catch(Exception e){} return null; }
    void updateDockIcon(ImageView iv, String pkg){ Drawable cd=iconCache.get(pkg); if(cd!=null){ iv.setImageDrawable(cd); return; } exec.execute(()->{ try{ Drawable d=getPackageManager().getApplicationIcon(pkg); iconCache.put(pkg,d); main.post(()->iv.setImageDrawable(d)); }catch(Exception e){}}); }
    void pickDockApp(int dockIdx){ android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.picker_dock); RecyclerView rv=dlg.findViewById(R.id.recyclerDock); rv.setHasFixedSize(true); rv.setLayoutManager(new GridLayoutManager(this,5)); List<ResolveInfo> list=new ArrayList<>(cache); rv.setAdapter(new RecyclerView.Adapter(){ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable d=iconCache.get(ri.activityInfo.packageName); if(d!=null) h.ic.setImageDrawable(d); h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[dockIdx], ri.activityInfo.packageName).apply(); dlg.dismiss(); setupDock(); }); } public int getItemCount(){ return list.size(); } }); dlg.show(); }

    void showWidgetPickerSafe(){
        try{
            List<AppWidgetProviderInfo> providers=mgr.getInstalledProviders();
            if(providers.isEmpty()){ Toast.makeText(this,"Aucun widget dispo",Toast.LENGTH_SHORT).show(); return; }
            String[] names=new String[providers.size()];
            for(int i=0;i<providers.size();i++){ try{ names[i]=providers.get(i).loadLabel(getPackageManager()); }catch(Exception e){ names[i]=providers.get(i).provider.getPackageName(); } }
            new android.app.AlertDialog.Builder(this).setTitle("Choisir un widget").setItems(names, (d,which)->{
                try{
                    AppWidgetProviderInfo inf=providers.get(which);
                    pendingWidgetId=host.allocateAppWidgetId();
                    pendingInfo=inf;
                    if(inf.configure!=null){
                        Intent cfg=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); cfg.setComponent(inf.configure); cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId); startActivityForResult(cfg,101);
                    } else {
                        boolean allowed=mgr.bindAppWidgetIdIfAllowed(pendingWidgetId, inf.provider);
                        if(!allowed){
                            Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND); bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId); bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, inf.provider); startActivityForResult(bind,102);
                        } else addWidgetDirect(pendingWidgetId, inf);
                    }
                }catch(Exception e){ Toast.makeText(MainActivity.this,"Erreur: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
            }).setNegativeButton("Annuler",null).show();
        }catch(Exception e){ Toast.makeText(this,"Erreur picker: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    void addWidgetDirect(int wid, AppWidgetProviderInfo inf){ exec.execute(()->{ main.post(()->{ addHint.setVisibility(View.GONE); AppWidgetHostView hv=host.createView(this,wid,inf); hv.setAppWidget(wid,inf); FrameLayout card=new FrameLayout(this); card.setBackgroundResource(R.drawable.glass); card.setPadding(4,4,4,4); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,12); card.setLayoutParams(lp); card.setOnLongClickListener(vv->{ container.removeView(card); host.deleteAppWidgetId(wid); if(container.getChildCount()==0) addHint.setVisibility(View.VISIBLE); return true;}); card.addView(hv); container.addView(card,0);});});}

    @Override protected void onActivityResult(int rc,int res,Intent data){
        if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri uri=data.getData(); try{ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply(); wallpaperView.setImageURI(uri); Toast.makeText(this,"Fond Quantum ✓",Toast.LENGTH_SHORT).show(); return; }
        if(rc==200 && res==RESULT_OK && data!=null && data.getData()!=null){ try{ Bitmap bmp=MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData()); WallpaperManager.getInstance(this).setBitmap(bmp); Toast.makeText(this,"Fond système ✓",Toast.LENGTH_SHORT).show(); }catch(Exception e){} return; }
        if(rc==101 && res==RESULT_OK){ if(pendingWidgetId!=-1 && pendingInfo!=null) addWidgetDirect(pendingWidgetId, pendingInfo); pendingWidgetId=-1; return; }
        if(rc==102 && res==RESULT_OK){ if(pendingWidgetId!=-1 && pendingInfo!=null) addWidgetDirect(pendingWidgetId, pendingInfo); pendingWidgetId=-1; return; }
    }

    void openDrawer(){ if(!tap()) return; android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.getWindow().setStatusBarColor(Color.TRANSPARENT); dlg.getWindow().setNavigationBarColor(Color.TRANSPARENT); dlg.setContentView(R.layout.drawer); RecyclerView rv=dlg.findViewById(R.id.recycler); EditText s=dlg.findViewById(R.id.search); rv.setHasFixedSize(true); rv.setItemViewCacheSize(50); rv.setItemAnimator(null); rv.setLayoutManager(new GridLayoutManager(this,5)); List<ResolveInfo> filt=new ArrayList<>(cache); FastAdapter ad=new FastAdapter(filt,getPackageManager(),dlg); rv.setAdapter(ad); s.addTextChangedListener(new android.text.TextWatcher(){ Runnable run; public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void afterTextChanged(android.text.Editable e){} public void onTextChanged(CharSequence q,int b,int c,int dd){ if(run!=null) main.removeCallbacks(run); run=()->exec.execute(()->{ String qq=q.toString().toLowerCase().trim(); List<ResolveInfo> r=new ArrayList<>(); if(qq.isEmpty()) r.addAll(cache); else for(ResolveInfo ri:cache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(qq)) r.add(ri); main.post(()->{ filt.clear(); filt.addAll(r); ad.notifyDataSetChanged();});}); main.postDelayed(run,80); } }); dlg.findViewById(R.id.close).setOnClickListener(v->dlg.dismiss()); dlg.show(); }
    void launch(String pkg){ try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null) startActivity(it);}catch(Exception e){} }
    class FastAdapter extends RecyclerView.Adapter<FastAdapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; FastAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ View v=getLayoutInflater().inflate(R.layout.item_app,pa,false); v.setLayerType(View.LAYER_TYPE_HARDWARE,null); return new H(v); } public void onBindViewHolder(H h,int pos){ ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(pm)); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(cd); h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null) startActivity(it); dlg.dismiss();}catch(Exception e){}}); } public int getItemCount(){ return list.size(); } }
}
