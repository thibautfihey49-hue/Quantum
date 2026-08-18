package com.v7.quantumfast;
import android.app.Activity; import android.app.WallpaperManager; import android.appwidget.*; import android.content.*; import android.content.pm.*; import android.graphics.drawable.Drawable; import android.os.*; import android.view.*; import android.widget.*; import androidx.recyclerview.widget.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.concurrent.*;
public class MainActivity extends Activity {
    AppWidgetManager mgr; AppWidgetHost host; LinearLayout container;
    ExecutorService exec = Executors.newFixedThreadPool(3); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"};
    String[] defaultPkgs={"com.google.android.dialer","com.google.android.apps.messaging","com.google.android.calendar","", "com.google.android.GoogleCamera","com.android.chrome"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); getWindow().setBackgroundDrawable(null); getWindow().getDecorView().setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setContentView(R.layout.activity_main); prefs=getSharedPreferences("dock",0);
        mgr=AppWidgetManager.getInstance(this); host=new AppWidgetHost(this,1024); container=findViewById(R.id.widgetContainer);
        preload(); clock(); setupDock();
        findViewById(R.id.btnMenu).setOnClickListener(v->showHomeMenu());
        findViewById(R.id.root).setOnLongClickListener(v->{ showHomeMenu(); return true;});
    }
    void showHomeMenu(){
        PopupMenu pm=new PopupMenu(this, findViewById(R.id.btnMenu));
        pm.getMenu().add("🎨 Changer fond d'écran"); pm.getMenu().add("🧩 Ajouter widget"); pm.getMenu().add("✏️ Changer dock (long press icône)"); pm.getMenu().add("🗂 Ouvrir tiroir");
        pm.setOnMenuItemClickListener(item->{
            String t=item.getTitle().toString();
            if(t.contains("fond")) pickWallpaper(); else if(t.contains("widget")) pick(); else if(t.contains("tiroir")) openDrawer();
            return true;
        }); pm.show();
    }
    void pickWallpaper(){ Intent i=new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*"); startActivityForResult(i,200); }
    @Override protected void onStart(){super.onStart(); host.startListening();} @Override protected void onStop(){super.onStop(); host.stopListening();}
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<200) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); main.postDelayed(this,30000);} }; r.run(); }
    void preload(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent ii=new Intent(Intent.ACTION_MAIN,null); ii.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(ii,0); l.sort((a,bb)->a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString())); cache.clear(); cache.addAll(l); for(ResolveInfo ri:l){ try{ if(!iconCache.containsKey(ri.activityInfo.packageName)) iconCache.put(ri.activityInfo.packageName, ri.loadIcon(pm)); }catch(Exception e){} } }catch(Exception e){}}); }
    void setupDock(){
        int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
        for(int i=0;i<ids.length;i++){ final int idx=i; View v=findViewById(ids[i]); String pkg=prefs.getString(dockKeys[i], defaultPkgs[i]);
            if(!pkg.isEmpty() && idx!=3) updateDockIcon((ImageView)findViewById(ids[i]), pkg);
            v.setOnClickListener(view->{ if(idx==3) openDrawer(); else { String p=prefs.getString(dockKeys[idx], defaultPkgs[idx]); launch(p);} });
            v.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; });
        }
    }
    void updateDockIcon(ImageView iv, String pkg){ exec.execute(()->{ try{ Drawable d=getPackageManager().getApplicationIcon(pkg); main.post(()->iv.setImageDrawable(d)); }catch(Exception e){}}); }
    void pickDockApp(int dockIdx){
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.picker_dock);
        RecyclerView rv=dlg.findViewById(R.id.recyclerDock); rv.setHasFixedSize(true); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.setItemAnimator(null);
        List<ResolveInfo> list=new ArrayList<>(cache);
        rv.setAdapter(new RecyclerView.Adapter(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(getPackageManager())); Drawable cached=iconCache.get(ri.activityInfo.packageName); if(cached!=null) h.ic.setImageDrawable(cached); else exec.execute(()->{ try{ Drawable d=ri.loadIcon(getPackageManager()); iconCache.put(ri.activityInfo.packageName,d); main.post(()->h.ic.setImageDrawable(d)); }catch(Exception e){}}); h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[dockIdx], ri.activityInfo.packageName).apply(); setupDock(); dlg.dismiss(); Toast.makeText(MainActivity.this,"Dock "+(dockIdx+1)+" → "+ri.loadLabel(getPackageManager()),Toast.LENGTH_SHORT).show(); }); }
            public int getItemCount(){ return list.size(); }
        }); dlg.show();
    }
    void pick(){ if(!tap()) return; try{ int id=host.allocateAppWidgetId(); Intent p=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); p.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(p,100);}catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){
        if(rc==200 && res==RESULT_OK && data!=null){ try{ WallpaperManager.getInstance(this).setStream(getContentResolver().openInputStream(data.getData())); Toast.makeText(this,"Fond changé ✓",Toast.LENGTH_SHORT).show(); }catch(Exception e){ Toast.makeText(this,"Erreur fond",Toast.LENGTH_SHORT).show(); } return; }
        if(data==null) return; if(rc==100 && res==RESULT_OK){ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf!=null && inf.configure!=null){ Intent cfg=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); cfg.setComponent(inf.configure); cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,wid); startActivityForResult(cfg,101);} else add(data);} else if(rc==101 && res==RESULT_OK) add(data);
    }
    void add(Intent data){ exec.execute(()->{ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf==null) return; main.post(()->{ AppWidgetHostView hv=host.createView(this,wid,inf); hv.setAppWidget(wid,inf); hv.setLayerType(View.LAYER_TYPE_HARDWARE,null); FrameLayout card=new FrameLayout(this); card.setBackgroundResource(R.drawable.glass); card.setPadding(8,8,8,8); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,12); card.setLayoutParams(lp); card.setOnLongClickListener(vv->{ new android.app.AlertDialog.Builder(this).setTitle("Widget").setItems(new String[]{"Supprimer"}, (d,w)->{ if(w==0){ container.removeView(card); host.deleteAppWidgetId(wid);} }).show(); return true;}); card.addView(hv); container.addView(card,0);});});}
    void openDrawer(){
        if(!tap()) return; android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.drawer);
        RecyclerView rv=dlg.findViewById(R.id.recycler); EditText s=dlg.findViewById(R.id.search); rv.setHasFixedSize(true); rv.setItemViewCacheSize(40); rv.setItemAnimator(null); rv.setLayoutManager(new GridLayoutManager(this,5)); rv.getRecycledViewPool().setMaxRecycledViews(0,60);
        List<ResolveInfo> filt=new ArrayList<>(cache); FastAdapter ad=new FastAdapter(filt,getPackageManager(),dlg); rv.setAdapter(ad);
        s.addTextChangedListener(new android.text.TextWatcher(){ Runnable run; public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void afterTextChanged(android.text.Editable e){} public void onTextChanged(CharSequence q,int b,int c,int dd){ if(run!=null) main.removeCallbacks(run); run=()->exec.execute(()->{ String qq=q.toString().toLowerCase().trim(); List<ResolveInfo> r=new ArrayList<>(); if(qq.isEmpty()) r.addAll(cache); else for(ResolveInfo ri:cache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(qq)) r.add(ri); main.post(()->{ filt.clear(); filt.addAll(r); ad.notifyDataSetChanged();});}); main.postDelayed(run,100); } });
        dlg.findViewById(R.id.close).setOnClickListener(v->dlg.dismiss()); dlg.show();
    }
    void launch(String pkg){ if(pkg==null||pkg.isEmpty()){openDrawer(); return;} if(!tap()) return; try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(it);} else openDrawer();}catch(Exception e){openDrawer();} }
    class FastAdapter extends RecyclerView.Adapter<FastAdapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; FastAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,pa,false)); } public void onBindViewHolder(H h,int pos){ ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(pm)); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(cd); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable d=ri.loadIcon(pm); iconCache.put(ri.activityInfo.packageName,d); main.post(()->{ if(h.getAdapterPosition()==pos) h.ic.setImageDrawable(d);}); }catch(Exception e){}}); } h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); v.getContext().startActivity(it); dlg.dismiss();}}catch(Exception e){}}); } public int getItemCount(){ return list.size(); } }
}
