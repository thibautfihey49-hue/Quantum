package com.v7.quantumfast;
import android.app.Activity;
import android.appwidget.*;
import android.content.Intent;
import android.content.pm.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
public class MainActivity extends Activity {
    AppWidgetManager mgr; AppWidgetHost host; LinearLayout container;
    ExecutorService exec = Executors.newFixedThreadPool(2);
    Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> appsCache = Collections.synchronizedList(new ArrayList<>());
    long lastClick=0;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setBackgroundDrawable(null);
        setContentView(R.layout.activity_main);
        mgr=AppWidgetManager.getInstance(this); host=new AppWidgetHost(this,1024);
        container=findViewById(R.id.widgetContainer);
        // 100T optimization: hardware + no overdraw
        getWindow().getDecorView().setLayerType(View.LAYER_TYPE_HARDWARE,null);
        preload();
        findViewById(R.id.btnAddWidget).setOnClickListener(v->pick());
        findViewById(R.id.root).setOnLongClickListener(v->{pick(); return true;});
        findViewById(R.id.dockPhone).setOnClickListener(v->{dial();});
        findViewById(R.id.dockMsg).setOnClickListener(v->{launch("com.google.android.apps.messaging");});
        findViewById(R.id.dockDrawer).setOnClickListener(v->{drawer();});
        findViewById(R.id.dockCam).setOnClickListener(v->{launch("com.google.android.GoogleCamera");});
        findViewById(R.id.dockChrome).setOnClickListener(v->{launch("com.android.chrome");});
        tick();
    }
    void tick(){ TextView c=findViewById(R.id.txtClock); TextView d=findViewById(R.id.txtDate); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE d MMM",Locale.FRANCE); c.setText(tf.format(new Date())); d.setText(df.format(new Date())); main.postDelayed(this::tick,30000); }
    void preload(){ exec.execute(()->{ PackageManager pm=getPackageManager(); Intent i=new Intent(Intent.ACTION_MAIN,null); i.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(i,0); l.sort((a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString())); appsCache.clear(); appsCache.addAll(l); }); }
    @Override protected void onStart(){ super.onStart(); host.startListening(); }
    @Override protected void onStop(){ super.onStop(); host.stopListening(); }
    void pick(){ try{ int id=host.allocateAppWidgetId(); Intent p=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); p.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(p,100); }catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(data==null) return; if(rc==100 && res==RESULT_OK){ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf!=null && inf.configure!=null){ Intent cfg=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); cfg.setComponent(inf.configure); cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,wid); startActivityForResult(cfg,101); } else addWidget(data); } else if(rc==101 && res==RESULT_OK) addWidget(data); }
    void addWidget(Intent data){ exec.execute(()->{ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf==null) return; main.post(()->{ AppWidgetHostView hv=host.createView(this,wid,inf); hv.setAppWidget(wid,inf); hv.setLayerType(View.LAYER_TYPE_HARDWARE,null); FrameLayout card=new FrameLayout(this); card.setBackgroundResource(R.drawable.glass_bg); card.setElevation(2f); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,10); card.setLayoutParams(lp); card.setPadding(6,6,6,6); card.setOnLongClickListener(v->{ container.removeView(card); host.deleteAppWidgetId(wid); return true; }); card.addView(hv); container.addView(card,0); }); }); }
    void drawer(){
        if(SystemClock.uptimeMillis()-lastClick<300) return; lastClick=SystemClock.uptimeMillis();
        android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dlg.setContentView(R.layout.drawer_layout); RecyclerView rv=dlg.findViewById(R.id.recycler); EditText s=dlg.findViewById(R.id.search);
        rv.setHasFixedSize(true); rv.setItemViewCacheSize(40); rv.setLayoutManager(new GridLayoutManager(this,5));
        List<ResolveInfo> filt=new ArrayList<>(appsCache); AppAdapter ad=new AppAdapter(filt, getPackageManager(), dlg); rv.setAdapter(ad);
        if(filt.isEmpty()) exec.execute(()->{ PackageManager pm=getPackageManager(); Intent i=new Intent(Intent.ACTION_MAIN,null); i.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(i,0); l.sort((a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString())); main.post(()->{ appsCache.clear(); appsCache.addAll(l); filt.clear(); filt.addAll(l); ad.notifyDataSetChanged(); }); });
        s.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void onTextChanged(CharSequence q,int b,int c,int d){ exec.execute(()->{ String qq=q.toString().toLowerCase(); List<ResolveInfo> r=new ArrayList<>(); for(ResolveInfo ri: appsCache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(qq)) r.add(ri); main.post(()->{ filt.clear(); filt.addAll(r); ad.notifyDataSetChanged(); }); }); } public void afterTextChanged(android.text.Editable e){} });
        dlg.findViewById(R.id.closeDrawer).setOnClickListener(v->dlg.dismiss()); dlg.show();
    }
    void dial(){ try{ startActivity(new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }catch(Exception e){} }
    void launch(String pkg){ if(SystemClock.uptimeMillis()-lastClick<300) return; lastClick=SystemClock.uptimeMillis(); try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(it); } }catch(Exception e){} }
    class AppAdapter extends RecyclerView.Adapter<AppAdapter.H>{
        List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; AppAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;}
        class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
        public H onCreateViewHolder(ViewGroup pa,int t){ return new H(LayoutInflater.from(pa.getContext()).inflate(R.layout.item_app,pa,false)); }
        public void onBindViewHolder(H h,int pos){ ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(pm)); h.ic.setImageDrawable(ri.loadIcon(pm)); h.itemView.setOnClickListener(v->{ if(SystemClock.uptimeMillis()-lastClick<300) return; lastClick=SystemClock.uptimeMillis(); try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); v.getContext().startActivity(it); dlg.dismiss(); } }catch(Exception e){} }); }
        public int getItemCount(){ return list.size(); }
    }
}
