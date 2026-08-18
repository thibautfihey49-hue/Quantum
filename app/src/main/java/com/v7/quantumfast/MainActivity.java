package com.v7.quantumfast;
import android.app.Activity;
import android.appwidget.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;
public class MainActivity extends Activity {
    AppWidgetManager mAppWidgetManager; AppWidgetHost mAppWidgetHost; LinearLayout widgetContainer;
    static final int HOST_ID=1024, REQ_PICK_WIDGET=100, REQ_CREATE_WIDGET=101;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        mAppWidgetManager=AppWidgetManager.getInstance(this); mAppWidgetHost=new AppWidgetHost(this,HOST_ID);
        widgetContainer=findViewById(R.id.widgetContainer);
        updateClock();
        findViewById(R.id.btnAddWidget).setOnClickListener(v->pickWidget());
        findViewById(R.id.dockPhone).setOnClickListener(v->launchDial());
        findViewById(R.id.dockMsg).setOnClickListener(v->launch("com.google.android.apps.messaging"));
        findViewById(R.id.dockDrawer).setOnClickListener(v->openDrawer());
        findViewById(R.id.dockCam).setOnClickListener(v->launch("com.google.android.GoogleCamera"));
        findViewById(R.id.dockChrome).setOnClickListener(v->launch("com.android.chrome"));
        findViewById(R.id.root).setOnLongClickListener(v->{pickWidget(); return true;});
    }
    void updateClock(){
        TextView clock=findViewById(R.id.txtClock); TextView date=findViewById(R.id.txtDate);
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm", Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE);
        clock.setText(tf.format(new Date())); date.setText(df.format(new Date()));
        clock.postDelayed(this::updateClock,60000);
    }
    @Override protected void onStart(){ super.onStart(); mAppWidgetHost.startListening(); }
    @Override protected void onStop(){ super.onStop(); mAppWidgetHost.stopListening(); }
    void pickWidget(){ int id=mAppWidgetHost.allocateAppWidgetId(); Intent pick=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(pick,REQ_PICK_WIDGET); }
    @Override protected void onActivityResult(int rc,int res,Intent data){
        super.onActivityResult(rc,res,data);
        if(rc==REQ_PICK_WIDGET && res==RESULT_OK){
            int appWidgetId=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
            AppWidgetProviderInfo info=mAppWidgetManager.getAppWidgetInfo(appWidgetId);
            if(info.configure!=null){ Intent intent=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); intent.setComponent(info.configure); intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId); startActivityForResult(intent,REQ_CREATE_WIDGET); }
            else createWidget(data);
        } else if(rc==REQ_CREATE_WIDGET && res==RESULT_OK) createWidget(data);
    }
    void createWidget(Intent data){
        int appWidgetId=data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
        AppWidgetProviderInfo info=mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        AppWidgetHostView hostView=mAppWidgetHost.createView(this,appWidgetId,info); hostView.setAppWidget(appWidgetId,info);
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(this);
        card.setCardBackgroundColor(0x22FFFFFF); card.setRadius(28f); card.setCardElevation(12f); card.setContentPadding(8,8,8,8);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,24); card.setLayoutParams(lp);
        card.setOnLongClickListener(v->{ widgetContainer.removeView(card); mAppWidgetHost.deleteAppWidgetId(appWidgetId); Toast.makeText(this,"Widget retiré",Toast.LENGTH_SHORT).show(); return true; });
        card.addView(hostView); widgetContainer.addView(card);
    }
    void openDrawer(){
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.drawer_layout);
        RecyclerView rv=dialog.findViewById(R.id.recycler); EditText search=dialog.findViewById(R.id.search);
        rv.setLayoutManager(new GridLayoutManager(this,4));
        PackageManager pm=getPackageManager(); Intent main=new Intent(Intent.ACTION_MAIN,null); main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=pm.queryIntentActivities(main,0); apps.sort((a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        List<ResolveInfo> filtered=new ArrayList<>(apps);
        AppAdapter adapter=new AppAdapter(filtered,pm,dialog); rv.setAdapter(adapter);
        search.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){ String q=s.toString().toLowerCase(); filtered.clear(); for(ResolveInfo r:apps) if(r.loadLabel(pm).toString().toLowerCase().contains(q)) filtered.add(r); adapter.notifyDataSetChanged(); } public void afterTextChanged(android.text.Editable e){} });
        dialog.findViewById(R.id.closeDrawer).setOnClickListener(v->dialog.dismiss()); dialog.show();
    }
    void launchDial(){ try{ startActivity(new Intent(Intent.ACTION_DIAL)); }catch(Exception e){} }
    void launch(String pkg){ try{ PackageManager pm=getPackageManager(); Intent it=pm.getLaunchIntentForPackage(pkg); if(it!=null) startActivity(it); else openDrawer(); }catch(Exception e){ openDrawer(); } }
    class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder>{
        List<ResolveInfo> list; PackageManager pm; android.app.Dialog dialog;
        AppAdapter(List<ResolveInfo> l, PackageManager p, android.app.Dialog d){ list=l; pm=p; dialog=d; }
        class Holder extends RecyclerView.ViewHolder{ ImageView icon; TextView label; Holder(View v){ super(v); icon=v.findViewById(R.id.icon); label=v.findViewById(R.id.label);} }
        public Holder onCreateViewHolder(ViewGroup parent,int viewType){ return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false)); }
        public void onBindViewHolder(Holder h,int pos){ ResolveInfo info=list.get(pos); h.label.setText(info.loadLabel(pm)); h.icon.setImageDrawable(info.loadIcon(pm)); h.itemView.setOnClickListener(v->{ try{ Intent launch=pm.getLaunchIntentForPackage(info.activityInfo.packageName); if(launch!=null) startActivity(launch); dialog.dismiss(); }catch(Exception e){} }); }
        public int getItemCount(){ return list.size(); }
    }
}
