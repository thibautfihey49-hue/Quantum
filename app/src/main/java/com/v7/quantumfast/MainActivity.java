package com.v7.quantumfast;
import android.app.Activity; import android.appwidget.*; import android.content.Intent; import android.content.pm.*; import android.os.*; import android.view.*; import android.widget.*; import androidx.recyclerview.widget.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.concurrent.*;
public class MainActivity extends Activity {
    AppWidgetManager mgr; AppWidgetHost host; LinearLayout container;
    ExecutorService exec = Executors.newFixedThreadPool(2); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); long lastClick=0;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setBackgroundDrawable(null);
        getWindow().getDecorView().setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setContentView(R.layout.activity_main);
        mgr=AppWidgetManager.getInstance(this); host=new AppWidgetHost(this,1024); container=findViewById(R.id.widgetContainer);
        preload(); clock();
        findViewById(R.id.root).setOnLongClickListener(v->{pick(); return true;});
        findViewById(R.id.dPhone).setOnClickListener(v->fast(new Intent(Intent.ACTION_DIAL)));
        findViewById(R.id.dMsg).setOnClickListener(v->launch("com.google.android.apps.messaging"));
        findViewById(R.id.dDrawer).setOnClickListener(v->openDrawer());
        findViewById(R.id.dCam).setOnClickListener(v->launch("com.google.android.GoogleCamera"));
        findViewById(R.id.dChrome).setOnClickListener(v->launch("com.android.chrome"));
    }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); main.postDelayed(this,30000);} }; r.run(); }
    void preload(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent i=new Intent(Intent.ACTION_MAIN,null); i.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(i,0); l.sort((a,bb)->a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString())); cache.clear(); cache.addAll(l);}catch(Exception e){}}); }
    @Override protected void onStart(){super.onStart(); host.startListening();} @Override protected void onStop(){super.onStop(); host.stopListening();}
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<250) return false; lastClick=n; return true; }
    void pick(){ if(!tap()) return; try{ int id=host.allocateAppWidgetId(); Intent p=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); p.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(p,100);}catch(Exception e){} }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(data==null) return; if(rc==100 && res==RESULT_OK){ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf!=null && inf.configure!=null){ Intent cfg=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); cfg.setComponent(inf.configure); cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,wid); startActivityForResult(cfg,101);} else add(data);} else if(rc==101 && res==RESULT_OK) add(data); }
    void add(Intent data){ exec.execute(()->{ int wid=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); AppWidgetProviderInfo inf=mgr.getAppWidgetInfo(wid); if(inf==null) return; main.post(()->{ AppWidgetHostView hv=host.createView(this,wid,inf); hv.setAppWidget(wid,inf); hv.setLayerType(View.LAYER_TYPE_HARDWARE,null); FrameLayout card=new FrameLayout(this); card.setBackgroundResource(R.drawable.glass); card.setPadding(4,4,4,4); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,8); card.setLayoutParams(lp); card.setOnLongClickListener(v->{ container.removeView(card); host.deleteAppWidgetId(wid); Toast.makeText(this,"Widget retiré",Toast.LENGTH_SHORT).show(); return true;}); card.addView(hv); container.addView(card,0);});});}
    void openDrawer(){
        if(!tap()) return; android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.drawer);
        RecyclerView rv=dlg.findViewById(R.id.recycler); EditText s=dlg.findViewById(R.id.search); rv.setHasFixedSize(true); rv.setItemViewCacheSize(50); rv.setLayoutManager(new GridLayoutManager(this,5));
        List<ResolveInfo> filt=new ArrayList<>(cache); Adapter ad=new Adapter(filt,getPackageManager(),dlg); rv.setAdapter(ad);
        if(filt.isEmpty()) exec.execute(()->{ PackageManager pm=getPackageManager(); Intent i=new Intent(Intent.ACTION_MAIN,null); i.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(i,0); l.sort((a,bb)->a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString())); main.post(()->{ cache.clear(); cache.addAll(l); filt.clear(); filt.addAll(l); ad.notifyDataSetChanged();});});
        s.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void onTextChanged(CharSequence q,int b,int c,int dd){ exec.execute(()->{ String qq=q.toString().toLowerCase(); List<ResolveInfo> r=new ArrayList<>(); for(ResolveInfo ri:cache) if(ri.loadLabel(getPackageManager()).toString().toLowerCase().contains(qq)) r.add(ri); main.post(()->{ filt.clear(); filt.addAll(r); ad.notifyDataSetChanged();});});} public void afterTextChanged(android.text.Editable e){}});
        dlg.findViewById(R.id.close).setOnClickListener(v->dlg.dismiss()); dlg.show();
    }
    void fast(Intent i){ if(!tap()) return; try{ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(i);}catch(Exception e){} }
    void launch(String pkg){ if(!tap()) return; try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(it);} else openDrawer();}catch(Exception e){openDrawer();} }
    class Adapter extends RecyclerView.Adapter<Adapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; Adapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ return new H(LayoutInflater.from(pa.getContext()).inflate(R.layout.item_app,pa,false)); } public void onBindViewHolder(H h,int pos){ ResolveInfo ri=list.get(pos); h.lb.setText(ri.loadLabel(pm)); try{ h.ic.setImageDrawable(ri.loadIcon(pm)); }catch(Exception e){} h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION); v.getContext().startActivity(it); dlg.dismiss();}}catch(Exception e){}}); } public int getItemCount(){ return list.size(); } }
}
