package com.v7.quantumfast;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Process;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class QuantumApp extends Application {
    static QuantumApp inst;
    static ConcurrentHashMap<String, Bitmap> iconCache = new ConcurrentHashMap<>(5000);
    static ConcurrentHashMap<String, String> labelCache = new ConcurrentHashMap<>(5000);
    static ConcurrentHashMap<String, Intent> launchCache = new ConcurrentHashMap<>(5000);
    static Drawable wallpaperCache;
    static boolean booted=false;
    @Override public void onCreate(){
        super.onCreate();
        inst=this;
        try{ wallpaperCache=android.app.WallpaperManager.getInstance(this).getDrawable(); }catch(Exception e){}
        Process.setThreadPriority(-20);
        boot();
    }
    static Bitmap to565(Drawable d){ try{ Bitmap b=Bitmap.createBitmap(96,96, Bitmap.Config.RGB_565); Canvas c=new Canvas(b); d.setBounds(0,0,96,96); d.draw(c); return b; }catch(Exception e){return null;} }
    public static void boot(){
        if(booted && inst!=null) {} // continue anyway
        booted=true;
        ExecutorService exec = Executors.newFixedThreadPool(32);
        exec.execute(() -> {
            try{
                if(inst==null) return;
                android.content.pm.PackageManager pm=inst.getPackageManager();
                android.content.Intent main=new android.content.Intent(android.content.Intent.ACTION_MAIN,null); main.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all=pm.queryIntentActivities(main, 0);
                for(ResolveInfo ri: all){
                    try{
                        String pkg=ri.activityInfo.packageName; if(pkg==null) continue;
                        if(!launchCache.containsKey(pkg)){
                            Intent li=pm.getLaunchIntentForPackage(pkg);
                            if(li!=null){ li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED|Intent.FLAG_ACTIVITY_NO_ANIMATION); launchCache.put(pkg,li); }
                        }
                        if(!labelCache.containsKey(pkg)) labelCache.put(pkg, ri.loadLabel(pm).toString());
                    }catch(Exception e){}
                }
            }catch(Exception e){}
        });
        for(int p=0;p<31;p++){ final int part=p; exec.execute(() -> {
            try{
                if(inst==null) return;
                android.content.pm.PackageManager pm=inst.getPackageManager();
                android.content.Intent main=new android.content.Intent(android.content.Intent.ACTION_MAIN,null); main.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all=pm.queryIntentActivities(main, 0);
                int chunk=all.size()/31+1; int s=part*chunk; int e2=Math.min(s+chunk,all.size());
                for(int i=s;i<e2;i++){ try{ ResolveInfo ri=all.get(i); String pkg=ri.activityInfo.packageName; if(pkg==null) continue;
                    if(!iconCache.containsKey(pkg)){ Drawable d=ri.loadIcon(pm); Bitmap b=to565(d); if(b!=null) iconCache.put(pkg,b); }
                }catch(Exception ex){} }
            }catch(Exception ex){} }); }
    }
}
