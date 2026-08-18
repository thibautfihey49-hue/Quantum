package com.v7.quantumfast;
import android.app.Activity; import android.app.WallpaperManager; import android.appwidget.*; import android.content.*; import android.content.pm.*; import android.graphics.*; import android.graphics.drawable.Drawable; import android.net.Uri; import android.os.*; import android.provider.MediaStore; import android.view.*; import android.widget.*; import androidx.recyclerview.widget.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.concurrent.*;
public class MainActivity extends Activity {
    AppWidgetManager mgr; AppWidgetHost host; LinearLayout container;
    ExecutorService exec = Executors.newFixedThreadPool(3); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"};
    String[] defaultPkgs={"com.android.dialer","com.google.android.gm","com.android.calendar","", "com.android.camera2","com.android.chrome"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); getWindow().setBackgroundDrawable(null);
        setContentView(R.layout.activity_main); prefs=getSharedPreferences("dock",0);
        mgr=AppWidgetManager.getInstance(this); host=new AppWidgetHost(this,1024); container=findViewById(R.id.widgetContainer);
        preload(); clock(); setupDock();
        findViewById(R.id.btnMenu).setOnClickListener(v->showHomeMenu());
        findViewById(R.id.root).setOnLongClickListener(v->{ showHomeMenu(); return true;});
        // Demande à être launcher par défaut pour widgets
        if(!isDefaultLauncher()){ Toast.makeText(this,"Définis Quantum comme launcher par défaut pour activer widgets + fond",Toast.LENGTH_LONG).show(); Intent i=new Intent(android.provider.Settings.ACTION_HOME_SETTINGS); try{startActivity(i);}catch(Exception e){} }
    }
    boolean isDefaultLauncher(){ IntentFilter f=new IntentFilter(Intent.ACTION_MAIN); f.addCategory(Intent.CATEGORY_HOME); List<IntentFilter> filters=new ArrayList<>(); filters.add(f); List<ComponentName> comps=new ArrayList<>(); getPackageManager().getPreferredActivities(filters,comps,null); for(ComponentName cn:comps) if(getPackageName().equals(cn.getPackageName())) return true; return false; }

    void showHomeMenu(){
        PopupMenu pm=new PopupMenu(this, findViewById(R.id.btnMenu));
        pm.getMenu().add("🎨 Changer fond d'écran"); pm.getMenu().add("🧩 Ajouter widget"); pm.getMenu().add("🗂 Ouvrir tiroir");
        pm.setOnMenuItemClickListener(item->{
            String t=item.getTitle().toString();
            if(t.contains("fond")) pickWallpaper(); else if(t.contains("widget")) pick(); else if(t.contains("tiroir")) openDrawer();
            return true;
        }); pm.show();
    }
    void pickWallpaper(){
        // Méthode qui marche sur Android 10-14
        Intent i=new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i,200);
    }

    @Override protected void onStart(){super.onStart(); host.startListening();} @Override protected void onStop(){super.onStop(); host.stopListening();}
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<200) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); main.postDelayed(this,30000);} }; r.run(); }
    void preload(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent ii=new Intent(Intent.ACTION_MAIN,null); ii.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(ii,0); l.sort((a,bb)->a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString())); cache.clear(); cache.addAll(l); for(ResolveInfo ri:l){ try{ if(!iconCache.containsKey(ri.activityInfo.packageName)) iconCache.put(ri.activityInfo.packageName, ri.loadIcon(pm)); }catch(Exception e){} } main.post(()->setupDock()); }catch(Exception e){}}); }
    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((FrameLayout)vv).getChildAt(0); if(idx==3) continue;
            String pkg=prefs.getString(dockKeys[idx], defaultPkgs[idx]); String realPkg=findRealPkg(pkg);
            if(realPkg!=null){ updateDockIcon(iv, realPkg); iv.setImageTintList(null); } else { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
            vv.setOnClickListener(view->{ if(idx==3) openDrawer(); else { String p=prefs.getString(dockKeys[idx], defaultPkgs[idx]); String rp=findRealPkg(p); if(rp!=null) launch(rp); else Toast.makeText(this,"App non trouvée, long press pour changer",Toast.LENGTH_SHORT).show(); } });
            vv.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; });
        }
        findViewById(R.id.dDrawer).setOnClickListener(v->openDrawer());
    }
    String findRealPkg(String pkg){ if(pkg==null||pkg.isEmpty()) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg; }catch(Exception e){} // fallback dialer/gm variations
        if(pkg.contains("dialer")){ for(String s:new String[]{"com.android.dialer","com.google.android.dialer","com.samsung.android.dialer"}) try{getPackageManager().getPackageInfo(s,0); return s;}catch(Exception ee){} }
        if(pkg.contains("gm")||pkg.contains("messaging")){ for(String s:new String[]{"com.google.android.gm","com.google.android.apps.messaging","com.samsung.android.messaging"}) try{getPackageManager().getPackageInfo(s,0); return s;}catch(Exception ee){} }
        return null;
    }
    void updateDockIcon(ImageView iv, String pkg){ exec.execute(()->{ try{ Drawable d=getPackageManager().getApplicationIcon(pkg); main.post(()->{ iv.setImageDrawable(d); iv.setImageTintList(null); }); }catch(Exception e){}}); }
    void pickDockApp(int dockIdx){
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.picker_dock);
        RecyclerView rv=dlg.findViewById(R.id.recyclerDock); rv.setHasFixedSize(true); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setItemAnimator(null);
        List<ResolveInfo> list=new ArrayList<>(cache);
        rv.setAdapter(new RecyclerView.Adapter(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(cd); h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[dockIdx], ri.activityInfo.packageName).apply(); dlg.dismiss(); setupDock(); Toast.makeText(MainActivity.this,"Dock "+(dockIdx+1)+" → "+ri.loadLabel(getPackageManager()),Toast.LENGTH_SHORT).show(); }); }
            public int getItemCount(){ return list.size(); }
        }); dlg.show();
    }
    void pick(){ try{ int id=host.allocateAppWidgetId(); Intent p=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); p.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(p,100);}catch(Exception e){ Toast.makeText(this,"Activer Quantum comme launcher par défaut dans Paramètres > Apps > App par défaut > Accueil",Toast.LENGTH_LONG).show(); } }
    @Override protected void onActivityResult(int rc,int res,Intent data){
        if(rc==200){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                try{
                    Uri uri=data.getData();
                    Bitmap bmp=MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    WallpaperManager.getInstance(this).setBitmap(bmp);
                    Toast.makeText(this,"Fond changé ✓",Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    try{
                        Intent wIntent=new Intent(Intent.ACTION_SET_WALLPAPER); wIntent.setData(data.getData()); startActivity(Intent.createChooser(wIntent,"Définir fond"));
                    }catch(Exception ee){ Toast.makeText(this,"Erreur: "+ee.getMessage(),Toast.LENGTH_LONG).show(); }
                }
            } else Toast.makeText(this,"Annulé",Toast.LENGTH_SHORT).show();
            return;
        }
        if(data==null) return; if(rc==100 && res==RESULT_OK){ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf!=null && inf.configure!=null){ Intent cfg=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); cfg.setComponent(inf.configure); cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,wid); startActivityForResult(cfg,101);} else add(data);} else if(rc==101 && res==RESULT_OK) add(data);
    }
    void add(Intent data){ exec.execute(()->{ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf==null) return; main.post(()->{ AppWidgetHostView hv=host.createView(this,wid,inf); hv.setAppWidget(wid,inf); FrameLayout card=new FrameLayout(this); card.setBackgroundResource(R.drawable.glass); card.setPadding(8,8,8,8); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,12); card.setLayoutParams(lp); card.setOnLongClickListener(vv->{ container.removeView(card); host.deleteAppWidgetId(wid); Toast.makeText(this,"Widget retiré",Toast.LENGTH_SHORT).show(); return true;}); card.addView(hv); container.addView(card,0);});});}
    void openDrawer(){
        if(!tap()) return; android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.drawer);
        RecyclerView rv=dlg.findViewById(R.id.recycler); EditText s=dlg.findViewById(R.id.search); rv.setHasFixedSize(true); rv.setItemViewCacheSize(40); rv.setItemAnimator(null); rv.setLayoutManager(new GridLayoutManager(this,5));
        List<ResolveInfo> filt=new ArrayList<>(cache); FastAdapter ad=new FastAdapter(filt,getPackageManager(),dlg); rv.setAdapter(ad);
        s.addTextChangedListener(new android.text.TextWatcher(){ Runnable run; public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void afterTextChanged(android.text.Editable e){} public void onTextChanged(CharSequence q,int b,int c,int dd){ if(run!=null) main.removeCallbacks(run); run=()->exec.execute(()->{ String qq=q.toString().toLowerCase().trim(); List<ResolveInfo> r=new ArrayList<>(); if(qq.isEmpty()) r.addAll(cache); else for(ResolveInfo ri:cache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(qq)) r.add(ri); main.post(()->{ filt.clear(); filt.addAll(r); ad.notifyDataSetChanged();});}); main.postDelayed(run,100); } });
        dlg.findViewById(R.id.close).setOnClickListener(v->dlg.dismiss()); dlg.show();
    }
    void launch(String pkg){ if(pkg==null||pkg.isEmpty()){openDrawer(); return;} if(!tap()) return; try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(it);} else openDrawer();}catch(Exception e){openDrawer();} }
    class FastAdapter extends RecyclerView.Adapter<FastAdapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; FastAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,pa,false)); } public void onBindViewHolder(H h,int pos){ ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(pm)); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(cd); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable d=ri.loadIcon(pm); iconCache.put(ri.activityInfo.packageName,d); main.post(()->h.ic.setImageDrawable(d)); }catch(Exception e){}}); } h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); v.getContext().startActivity(it); dlg.dismiss();}}catch(Exception e){}}); } public int getItemCount(){ return list.size(); } }
}
