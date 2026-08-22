package com.v7.quantumfast;

import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    AppWidgetHost mAppWidgetHost;
    AppWidgetHost awHost;
    SharedPreferences prefs;
    SharedPreferences glassPrefs;
    ViewGroup mainRoot;
    GridLayout appsGrid;
    LinearLayout dockContainer;
    EditText searchEdit;
    ImageView wallpaperView;
    List<ResolveInfo> allApps = new ArrayList<>();

    Context getDialogContext(){ return new ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("quantum", MODE_PRIVATE);
        glassPrefs = getSharedPreferences("glass", MODE_PRIVATE);
        if(mAppWidgetHost==null){ mAppWidgetHost = new AppWidgetHost(this, 1); mAppWidgetHost.startListening(); awHost=mAppWidgetHost; }

        FrameLayout root = new FrameLayout(this);
        mainRoot = root;

        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        root.addView(wallpaperView);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        root.addView(content);

        TextView title = new TextView(this);
        title.setText("Quantum Ultra - Menu");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(30,60,30,20);
        title.setOnClickListener(v-> showQuantumUltra());
        content.addView(title);

        searchEdit = new EditText(this);
        searchEdit.setHint("Rechercher apps...");
        searchEdit.setTextColor(Color.WHITE);
        searchEdit.setHintTextColor(0x88FFFFFF);
        searchEdit.setBackground(glassBg(Color.BLACK, 20, 60));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(20,0,20,20);
        searchEdit.setLayoutParams(sp);
        searchEdit.setPadding(30,20,30,20);
        searchEdit.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ filterApps(s.toString()); }
            public void afterTextChanged(android.text.Editable s){}
        });
        content.addView(searchEdit);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(4);
        appsGrid.setPadding(10,10,10,10);
        sv.addView(appsGrid);
        content.addView(sv);

        dockContainer = new LinearLayout(this);
        dockContainer.setOrientation(LinearLayout.HORIZONTAL);
        dockContainer.setGravity(Gravity.CENTER);
        dockContainer.setPadding(10,10,10,30);
        dockContainer.setBackground(glassBg(Color.BLACK, 20, 80));
        content.addView(dockContainer);

        setContentView(root);
        loadApps();
        loadDock();
        restoreWidgets();
    }

    @Override protected void onStart(){ super.onStart(); if(mAppWidgetHost!=null) mAppWidgetHost.startListening(); }
    @Override protected void onStop(){ super.onStop(); if(mAppWidgetHost!=null) mAppWidgetHost.stopListening(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==9002 && resultCode==RESULT_OK && data!=null){
            try{
                int id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                AppWidgetManager awm = AppWidgetManager.getInstance(this);
                AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
                if(info!=null) addWidgetView(id, info);
            }catch(Exception e){}
        }
    }

    View findV(String... names){
        if(mainRoot==null) return wallpaperView;
        for(int i=0;i<mainRoot.getChildCount();i++){
            View v = mainRoot.getChildAt(i);
            if(v instanceof ViewGroup){
                View f = findInGroup((ViewGroup)v, names);
                if(f!=null) return f;
            }
        }
        return wallpaperView;
    }
    View findInGroup(ViewGroup vg, String... names){
        for(int i=0;i<vg.getChildCount();i++){
            View v = vg.getChildAt(i);
            String tag = v.getTag()!=null? v.getTag().toString().toLowerCase() : "";
            for(String n: names){ if(tag.contains(n)) return v; }
            if(v instanceof ViewGroup){ View f=findInGroup((ViewGroup)v,names); if(f!=null) return f; }
        }
        return null;
    }

    GradientDrawable glassBg(int color, float radius, int alpha){
        GradientDrawable gd=new GradientDrawable();
        gd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        gd.setCornerRadius(radius); gd.setStroke(1,0x22FFFFFF); return gd;
    }
    GradientDrawable glassBg(int color, float radius, float alpha){ return glassBg(color,radius,(int)alpha); }

    AlertDialog createModernDialog(String title, View content){
        Context ctx=getDialogContext();
        LinearLayout wrap=new LinearLayout(ctx); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(30,20,30,20);
        TextView tv=new TextView(ctx); tv.setText(title); tv.setTextColor(Color.WHITE); tv.setTextSize(18); tv.setPadding(0,0,0,20); wrap.addView(tv); wrap.addView(content);
        return new AlertDialog.Builder(ctx).setView(wrap).create();
    }
    void applyGlassTheme(int col){ if(mainRoot!=null) mainRoot.setBackgroundColor(col); }
    void ensureFullCache(){}
 public void launchInstant(String pkg){ try{ android.content.Intent i=getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null) startActivity(i); }catch(Exception e){} }

    void refreshFromSystemTheme(){ Toast.makeText(this,"Utilise Thèmes gratuits",0).show(); }

    void loadApps(){
        PackageManager pm=getPackageManager();
        Intent main=new Intent(Intent.ACTION_MAIN,null); main.addCategory(Intent.CATEGORY_LAUNCHER);
        allApps=pm.queryIntentActivities(main,0);
        allApps.sort((a,b)-> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        filterApps("");
    }
    void filterApps(String q){
        if(appsGrid==null) return; appsGrid.removeAllViews(); PackageManager pm=getPackageManager();
        String iconPack=prefs.getString("icon_pack","");
        for(ResolveInfo ri: allApps){
            String label=ri.loadLabel(pm).toString(); if(!q.isEmpty() &&!label.toLowerCase().contains(q.toLowerCase())) continue;
            LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=-2; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(10,10,10,10); item.setLayoutParams(lp);
            ImageView iv=new ImageView(this); iv.setLayoutParams(new LinearLayout.LayoutParams(120,120));
            try{
                android.graphics.drawable.Drawable d=null;
                if(!iconPack.isEmpty()){
                    try{ android.content.res.Resources res=pm.getResourcesForApplication(iconPack); int id=res.getIdentifier(ri.activityInfo.packageName.replace(".","_"),"drawable",iconPack); if(id==0) id=res.getIdentifier(ri.activityInfo.packageName,"drawable",iconPack); if(id!=0) d=res.getDrawable(id,null); }catch(Exception e){}
                }
                if(d==null) d=ri.loadIcon(pm); iv.setImageDrawable(d);
            }catch(Exception e){ iv.setImageDrawable(ri.loadIcon(pm)); }
            TextView tv=new TextView(this); tv.setText(label); tv.setTextColor(Color.WHITE); tv.setTextSize(10); tv.setGravity(Gravity.CENTER); tv.setMaxLines(1);
            item.addView(iv); item.addView(tv);
            item.setOnClickListener(v->{ try{ Intent launch=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(launch!=null) startActivity(launch); }catch(Exception e){} });
            appsGrid.addView(item);
        }
    }
    void loadDock(){
        if(dockContainer==null) return; dockContainer.removeAllViews(); String saved=prefs.getString("dock_apps",""); if(saved.isEmpty()) return; PackageManager pm=getPackageManager();
        for(String pkg: saved.split(",")){
            if(pkg.trim().isEmpty()) continue;
            try{ Intent li=pm.getLaunchIntentForPackage(pkg.trim()); if(li==null) continue; ResolveInfo ri=null; for(ResolveInfo r: allApps) if(r.activityInfo.packageName.equals(pkg.trim())){ ri=r; break; } if(ri==null) continue; ImageView iv=new ImageView(this); iv.setImageDrawable(ri.loadIcon(pm)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(130,130); lp.setMargins(10,0,10,0); iv.setLayoutParams(lp); iv.setOnClickListener(v->{ try{ startActivity(li); }catch(Exception e){} }); dockContainer.addView(iv); }catch(Exception e){}
        }
    }
    void showManualTopPicker(){ Context ctx=getDialogContext(); LinearLayout list=new LinearLayout(ctx); list.setOrientation(LinearLayout.VERTICAL); TextView r=new TextView(ctx); r.setText("Vider dock"); r.setTextColor(Color.WHITE); r.setPadding(30,30,30,30); r.setOnClickListener(v->{ prefs.edit().remove("dock_apps").apply(); loadDock(); }); list.addView(r); createModernDialog("Mes apps fusée", list).show(); }

    void showQuantumUltra(){
        float dens=getResources().getDisplayMetrics().density; LinearLayout list=new LinearLayout(getDialogContext()); list.setOrientation(LinearLayout.VERTICAL);
        String[] opts={"🎨 Couleur thème","🖼️ Fond d'écran","🧹 Effacer fond","⭐ Mes apps fusée","🎨 Thèmes d'icônes gratuits","🔤 Polices + Fonds HD","🧩 Widget draggable"};
        for(int i=0;i<opts.length;i++){ final int idx=i; TextView row=new TextView(getDialogContext()); row.setText(opts[i]); row.setTextSize(16); row.setTextColor(Color.WHITE); row.setPadding((int)(14*dens),(int)(16*dens),(int)(14*dens),(int)(16*dens)); row.setBackground(glassBg(Color.BLACK,14*dens,70)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,(int)(10*dens)); row.setLayoutParams(lp); row.setOnClickListener(v->{ if(idx==0) showPaletteModern(); else if(idx==1) pickWallpaper(); else if(idx==2){ prefs.edit().remove("custom_wallpaper_uri").apply(); if(wallpaperView!=null) wallpaperView.setImageDrawable(null); } else if(idx==3) showManualTopPicker(); else if(idx==4) showIconPackPicker(); else if(idx==5) showFontsWallpapersPicker(); else if(idx==6) pickWidget(); }); list.addView(row); }
        createModernDialog("Quantum Ultra", list).show();
    }
    void showPaletteModern(){
        float dens=getResources().getDisplayMetrics().density; GridLayout grid=new GridLayout(getDialogContext()); grid.setColumnCount(5);
        int[] cols={0xFF7C4DFF,0xFF00E5FF,0xFF00FF94,0xFFFF3D8B,0xFFFFAB00,0xFF6B4C8A,0xFF2196F3,0xFF212121,0xFFFFFFFF};
        for(int col: cols){ View v=new View(getDialogContext()); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(int)(56*dens); lp.height=(int)(56*dens); lp.setMargins((int)(8*dens),(int)(8*dens),(int)(8*dens),(int)(8*dens)); v.setLayoutParams(lp); GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(16*dens); bg.setColor(col); if(col==0xFFFFFFFF) bg.setStroke((int)dens,0xFFCCCCCC); v.setBackground(bg); v.setOnClickListener(vw->{ glassPrefs.edit().putInt("glass_color",col).apply(); applyGlassTheme(col); }); grid.addView(v); }
        createModernDialog("Thème Ultra", grid).show();
    }
    void pickWallpaper(){ Intent i=new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*"); startActivityForResult(Intent.createChooser(i,"Fond"),1001); }

    void showIconPackPicker(){
        try{
            Context ctx=getDialogContext(); LinearLayout list=new LinearLayout(ctx); list.setOrientation(LinearLayout.VERTICAL); int pad=(int)(getResources().getDisplayMetrics().density*12);
            PackageManager pm=getPackageManager(); List<ResolveInfo> packs=new ArrayList<>(); try{ packs.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"),0)); }catch(Exception e){} try{ packs.addAll(pm.queryIntentActivities(new Intent("com.novalauncher.THEME"),0)); }catch(Exception e){}
            HashSet<String> seen=new HashSet<>();
            for(ResolveInfo ri: packs){ String pkg=ri.activityInfo.packageName; if(!seen.add(pkg)) continue; TextView row=new TextView(ctx); try{ row.setText(ri.loadLabel(pm)); }catch(Exception e){ row.setText(pkg); } row.setTextSize(15); row.setTextColor(Color.WHITE); row.setPadding(pad,pad,pad,pad); row.setOnClickListener(v->{ prefs.edit().putString("icon_pack",pkg).apply(); Toast.makeText(this,"Pack: "+pkg,0).show(); loadApps(); }); list.addView(row); }
            if(packs.isEmpty()){ TextView tv=new TextView(ctx); tv.setText("Aucun pack - installe Delta, Pix, Arcticons gratuit"); tv.setTextColor(Color.WHITE); tv.setPadding(pad,pad,pad,pad); list.addView(tv); }
            TextView more=new TextView(ctx); more.setText("➕ Play Store packs gratuits"); more.setTextColor(0xFF00E5FF); more.setPadding(pad,pad*2,pad,pad); more.setOnClickListener(v->{ try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=free icon pack material you"))); }catch(Exception e){ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=free%20icon%20pack"))); } }); list.addView(more);
            ScrollView sv=new ScrollView(ctx); sv.addView(list); createModernDialog("Thèmes d'icônes gratuits", sv).show();
        }catch(Exception e){ Toast.makeText(this,"Icon: "+e.getMessage(),1).show(); }
    }
    void showFontsWallpapersPicker(){
        try{
            Context ctx=getDialogContext(); LinearLayout root=new LinearLayout(ctx); root.setOrientation(LinearLayout.VERTICAL); int pad=(int)(getResources().getDisplayMetrics().density*12);
            TextView h1=new TextView(ctx); h1.setText("🔤 Polices Google"); h1.setTextColor(Color.WHITE); h1.setTextSize(16); h1.setPadding(pad,pad,pad,pad/2); root.addView(h1);
            String[][] fonts={{"Poppins","https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Regular.ttf"},{"Montserrat","https://github.com/google/fonts/raw/main/ofl/montserrat/Montserrat-Regular.ttf"},{"Nunito","https://github.com/google/fonts/raw/main/ofl/nunito/Nunito-Regular.ttf"}};
            for(String[] f: fonts){ TextView row=new TextView(ctx); row.setText("• "+f[0]); row.setTextColor(Color.WHITE); row.setPadding(pad*2,pad/2,pad,pad/2); row.setOnClickListener(v-> downloadFont(f[0], f[1])); root.addView(row); }
            TextView h2=new TextView(ctx); h2.setText("\n🖼️ Fonds HD"); h2.setTextColor(Color.WHITE); h2.setTextSize(16); h2.setPadding(pad,pad,pad,pad/2); root.addView(h2);
            TextView rw=new TextView(ctx); rw.setText("• Aléatoire HD"); rw.setTextColor(0xFF00E5FF); rw.setPadding(pad*2,pad/2,pad,pad/2); rw.setOnClickListener(v-> downloadWallpaper("https://picsum.photos/1080/1920")); root.addView(rw);
            ScrollView sv=new ScrollView(ctx); sv.addView(root); createModernDialog("Polices + Fonds", sv).show();
        }catch(Exception e){}
    }
    void downloadFont(String name, String url){
        new Thread(()->{ try{ File dir=new File(getFilesDir(),"fonts"); dir.mkdirs(); File out=new File(dir,name+".ttf"); InputStream in=new URL(url).openStream(); FileOutputStream fos=new FileOutputStream(out); byte[] b=new byte[8192]; int r; while((r=in.read(b))!=-1) fos.write(b,0,r); fos.close(); in.close(); runOnUiThread(()->{ prefs.edit().putString("custom_font_path",out.getAbsolutePath()).apply(); Toast.makeText(this,"Police "+name+" OK",0).show(); }); }catch(Exception e){ runOnUiThread(()-> Toast.makeText(this,"Font err: "+e.getMessage(),1).show()); } }).start();
    }
    void downloadWallpaper(String url){
        new Thread(()->{ try{ InputStream in=new URL(url).openStream(); Bitmap bmp=BitmapFactory.decodeStream(in); in.close(); runOnUiThread(()->{ if(wallpaperView!=null) wallpaperView.setImageBitmap(bmp); Toast.makeText(this,"Fond appliqué",0).show(); }); }catch(Exception e){ runOnUiThread(()-> Toast.makeText(this,"Wall err: "+e.getMessage(),1).show()); } }).start();
    }

    void pickWidget(){
        try{
            AppWidgetManager awm=AppWidgetManager.getInstance(this); List<AppWidgetProviderInfo> providers=awm.getInstalledProviders(); if(providers.isEmpty()){ Toast.makeText(this,"Aucun widget",0).show(); return; }
            Context ctx=getDialogContext(); LinearLayout list=new LinearLayout(ctx); list.setOrientation(LinearLayout.VERTICAL); int pad=(int)(getResources().getDisplayMetrics().density*12);
            if(mAppWidgetHost==null){ mAppWidgetHost=new AppWidgetHost(this,1); mAppWidgetHost.startListening(); } awHost=mAppWidgetHost;
            for(AppWidgetProviderInfo info: providers){
                TextView row=new TextView(ctx); try{ row.setText(info.loadLabel(getPackageManager())); }catch(Exception e){ row.setText(info.provider.getPackageName()); } row.setTextSize(15); row.setTextColor(Color.WHITE); row.setPadding(pad,pad,pad,pad);
                row.setOnClickListener(v->{ try{ int id=mAppWidgetHost.allocateAppWidgetId(); boolean b=awm.bindAppWidgetIdIfAllowed(id, info.provider); if(!b){ Intent bi=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND); bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider); startActivityForResult(bi,9002); } else{ addWidgetView(id, info); } }catch(Exception e){ Toast.makeText(this,"W: "+e.getMessage(),1).show(); } });
                list.addView(row);
            }
            ScrollView sv=new ScrollView(ctx); sv.addView(list); createModernDialog("Choisir widget (sans crash Oppo)", sv).show();
        }catch(Exception e){ Toast.makeText(this,"Widget: "+e.getMessage(),1).show(); }
    }
    void addWidgetView(int appWidgetId, AppWidgetProviderInfo info){
        try{ AppWidgetHostView hv=mAppWidgetHost.createView(this, appWidgetId, info); hv.setAppWidget(appWidgetId, info); if(mainRoot!=null){ mainRoot.addView(hv); makeWidgetDraggable(hv); saveWidget(appWidgetId); } }catch(Exception e){}
    }
    void makeWidgetDraggable(View v){ v.setOnTouchListener(new View.OnTouchListener(){ float dx,dy; public boolean onTouch(View vv, android.view.MotionEvent ev){ switch(ev.getAction()){ case android.view.MotionEvent.ACTION_DOWN: dx=vv.getX()-ev.getRawX(); dy=vv.getY()-ev.getRawY(); return true; case android.view.MotionEvent.ACTION_MOVE: vv.setX(ev.getRawX()+dx); vv.setY(ev.getRawY()+dy); return true; } return false; } }); }
    void makeDraggable(View v, int id){ makeWidgetDraggable(v); }
    void makeDraggable(View v){ makeWidgetDraggable(v); }
    void saveWidget(int id){ String cur=prefs.getString("widgets",""); if(!cur.contains(String.valueOf(id))){ if(!cur.isEmpty()) cur+=","; cur+=id; prefs.edit().putString("widgets",cur).apply(); } }
    void restoreWidgets(){
        try{ String ids=prefs.getString("widgets",""); if(ids.isEmpty()) return; AppWidgetManager awm=AppWidgetManager.getInstance(this); for(String s: ids.split(",")){ try{ int id=Integer.parseInt(s.trim()); AppWidgetProviderInfo info=awm.getAppWidgetInfo(id); if(info!=null){ AppWidgetHostView hv=mAppWidgetHost.createView(this,id,info); hv.setAppWidget(id,info); mainRoot.addView(hv); makeWidgetDraggable(hv); } }catch(Exception e){} } }catch(Exception e){}
    }
}
