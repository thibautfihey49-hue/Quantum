package com.v7.quantumfast;
import android.app.AppOpsManager; import android.app.Activity; import android.app.ActivityManager; import android.app.admin.DevicePolicyManager; import android.app.usage.StorageStats; import android.app.usage.StorageStatsManager; import android.content.*; import android.content.pm.*; import android.graphics.*; import android.graphics.drawable.Drawable; import android.net.Uri; import android.os.*; import android.os.storage.StorageManager; import android.provider.Settings; import android.view.*; import android.view.inputmethod.EditorInfo; import android.widget.*; import androidx.core.app.ActivityCompat; import androidx.core.content.ContextCompat; import androidx.recyclerview.widget.*; import java.io.*; import java.util.*; import java.util.concurrent.*; import java.text.SimpleDateFormat;
public class MainActivity extends Activity {
    android.content.BroadcastReceiver timeReceiver;
    android.content.BroadcastReceiver pkgReceiver;
    android.content.BroadcastReceiver batteryReceiver;
    boolean cacheLoaded=false;

 EditText searchApps; EditText searchWeb;
    ImageView wallpaperView; ExecutorService exec = Executors.newSingleThreadExecutor(); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); Map<String,String> labelCache = new ConcurrentHashMap<>(); Map<String,Long> usageMap = new HashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"}; String[] defaultPkgs={"com.android.dialer","com.google.android.gm","com.google.android.apps.messaging","com.google.android.calendar","com.android.camera2","com.android.chrome"};
    RecyclerView rvSugg, rvFav, rvFolders; List<ResolveInfo> suggList = new ArrayList<>(); SuggAdapter suggAd;
    static class Fav{ String name; String url; Fav(String n,String u){name=n;url=u;} } List<Fav> favs = new ArrayList<>(); FavAdapter favAd;
    static class Folder{ String name; List<String> pkgs; Folder(String n,List<String> p){name=n;pkgs=p;} } List<Folder> folders = new ArrayList<>(); FolderAdapter folderAd;
    List<String> bigCachePkgs=new ArrayList<>();
    @Override protected void onResume(){ super.onResume(); clearSearchNow(); }
protected void onCreate(Bundle b){
        super.onCreate(b);
 try{ getWindow().setBackgroundDrawable(null); }catch(Exception e){}
 registerTimeTick();
 registerPkgReceiver();
 registerBatteryReceiver();
        getWindow().setStatusBarColor(0); getWindow().setNavigationBarColor(0);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        setContentView(R.layout.activity_main);
        requestAllPerms();
        wallpaperView=findViewById(R.id.wallpaperView); prefs=getSharedPreferences("dock",0); loadUsage();
        rvSugg=findViewById(R.id.rvSuggestions); rvFav=findViewById(R.id.rvFavorites); rvFolders=findViewById(R.id.rvFolders);
        rvSugg.setHasFixedSize(true); rvSugg.setItemAnimator(null); rvFav.setHasFixedSize(true); rvFav.setItemAnimator(null); rvFolders.setHasFixedSize(true); rvFolders.setItemAnimator(null);
        rvSugg.setLayoutManager(new LinearLayoutManager(this)); suggAd=new SuggAdapter(); rvSugg.setAdapter(suggAd);
        rvFav.setLayoutManager(new GridLayoutManager(this,4)); loadFavs(); favAd=new FavAdapter(); rvFav.setAdapter(favAd);
        rvFolders.setLayoutManager(new GridLayoutManager(this,2)); loadFolders(); ensureLowRamDefaults(); folderAd=new FolderAdapter(); rvFolders.setAdapter(folderAd);
        if(!cacheLoaded) preloadFast(); setupAtAGlanceSimple(); setupDock(); loadWallpaperFast(); setupGestures();
        searchApps=findViewById(R.id.searchAppsMain); searchWeb=findViewById(R.id.searchWebMain); TextView clear=findViewById(R.id.clearApps);
        final Runnable[] debounce=new Runnable[1];
        searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void afterTextChanged(android.text.Editable s){}
            public void onTextChanged(CharSequence q,int bb,int cc,int dd){
                if(debounce[0]!=null) main.removeCallbacks(debounce[0]);
                debounce[0]=()->{
                    String qq=q.toString().trim(); if(qq.isEmpty()){ main.post(()->{ rvSugg.setVisibility(View.GONE); clear.setVisibility(View.GONE); suggList.clear(); if(!rvSugg.isComputingLayout()) suggAd.notifyDataSetChanged(); }); return; }
                    List<ResolveInfo> snap; synchronized(cache){ snap=new ArrayList<>(cache); }
                    List<ResolveInfo> r=new ArrayList<>(); String low=qq.toLowerCase();
                    for(ResolveInfo ri:snap){ String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null && lbl.toLowerCase().contains(low)){ r.add(ri); if(r.size()>=12) break; } }
                    r.sort((x,y)-> Long.compare(usageMap.getOrDefault(y.activityInfo.packageName,0L), usageMap.getOrDefault(x.activityInfo.packageName,0L)));
                    main.post(()->{ clear.setVisibility(View.VISIBLE); suggList.clear(); suggList.addAll(r); rvSugg.setVisibility(r.isEmpty()?View.GONE:View.VISIBLE); if(!rvSugg.isComputingLayout()) suggAd.notifyDataSetChanged(); });
                }; main.postDelayed(debounce[0], 50);
            }
        });
        clear.setOnClickListener(v->searchApps.setText(""));
        searchWeb.setOnEditorActionListener((v,actionId,event)->{ if(actionId==EditorInfo.IME_ACTION_SEARCH){ String q=v.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); return true;} return false; });
        findViewById(R.id.btnWebGo).setOnClickListener(v->{ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); });
        findViewById(R.id.btnAddFav).setOnClickListener(v->showAddFavDialog());
        findViewById(R.id.btnAddFolder).setOnClickListener(v->showCreateFolderDialog());
        main.postDelayed(()->{ if(!isDefaultLauncher()) showGlassDialog(); }, 800);
        findViewById(R.id.btnMenu).setOnClickListener(v->showGlassMenu());
    }
    void setupGestures(){ GestureDetector gd=new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy){ float dy=e2.getY()-e1.getY(); if(Math.abs(dy)>200){ if(dy<0){ openDrawerWithQuery(""); return true; } } return false; }
        @Override public boolean onDoubleTap(MotionEvent e){ try{ DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE); if(dpm.isAdminActive(new ComponentName(MainActivity.this, AdminReceiver.class))) dpm.lockNow(); }catch(Exception ex){} return true; }
    }); View root=findViewById(R.id.root); root.setOnTouchListener((v,ev)->{ gd.onTouchEvent(ev); return false; }); }
    void loadUsage(){ Map<String,?> all=prefs.getAll(); for(Map.Entry<String,?> e:all.entrySet()) if(e.getKey().startsWith("use_")&& e.getValue() instanceof Long) usageMap.put(e.getKey().substring(4),(Long)e.getValue()); }
    void trackUsage(String pkg){ long now=System.currentTimeMillis(); usageMap.put(pkg,now); prefs.edit().putLong("use_"+pkg,now).apply(); }
    void requestAllPerms(){ List<String> need=new ArrayList<>(); String[] all={android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.POST_NOTIFICATIONS}; for(String p:all){ if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED) need.add(p); } if(!need.isEmpty()) ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 999); }
        exec.execute(()->{
            long[] res=scanRealCache();
            final long fTotal=res[0]; final int fCount=(int)res[1];
            List<File> junks=scanJunk(); long jSize=0; for(File f:junks) jSize+=f.length();
            final long fJSize=jSize;
            StringBuilder sb=new StringBuilder(); for(int i=0;i<Math.min(6,junks.size());i++) sb.append("• ").append(junks.get(i).getName()).append("\n");
            final String fSb=sb.toString();
            main.post(()->{
                cacheCount.setText(fCount+" apps");
                junkList.setText(fSb.isEmpty()?"Aucun":fSb);
            });
        });
            if(!isAccessibilityEnabled()){ Toast.makeText(this,"Active accessibilité",1).show(); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return; }
            Toast.makeText(this,"Nettoyage auto lancé",1).show();
            exec.execute(()->{ for(String pkg:bigCachePkgs){ try{ Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.setData(Uri.parse("package:"+pkg)); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); CacheCleanerService.isCleaning=true; main.post(()->startActivity(intent)); Thread.sleep(3500); }catch(Exception e){} } main.post(()->Toast.makeText(this,"Terminé ✅",1).show()); });
        });
        dlg.findViewById(R.id.bCloseBooster).setOnClickListener(v->dlg.dismiss());
        dlg.show();
    }
    boolean isAccessibilityEnabled(){ String pref=Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return pref!=null && pref.contains(getPackageName()+"/"+getPackageName()+".CacheCleanerService"); }
    void setupAtAGlanceSimple(){ clock(); try{ IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED); BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){ int lvl=i.getIntExtra("level",-1); TextView tv=findViewById(R.id.batteryInfo); if(tv!=null && lvl!=-1) tv.setText("🔋 "+lvl+"%"); } }; registerReceiver(br,f); }catch(Exception e){} }
    List<File> scanJunk(){ List<File> out=new ArrayList<>(); try{ List<File> roots=new ArrayList<>(); roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)); roots.add(new File(Environment.getExternalStorageDirectory(),"Download")); String[] exts={".tmp",".temp",".log",".bak",".cache"}; for(File root:roots){ if(root==null||!root.exists()) continue; File[] files=root.listFiles(); if(files==null) continue; for(File f:files){ if(f.isFile()){ String n=f.getName().toLowerCase(); for(String ex:exts) if(n.endsWith(ex)){ out.add(f); break; } } } } }catch(Exception e){} return out; }
    long getFolderSize(File dir){ long s=0; if(dir==null||!dir.exists()) return 0; try{ File[] files=dir.listFiles(); if(files==null) return 0; for(File f:files){ if(f.isFile()) s+=f.length(); else s+=getFolderSize(f); } }catch(Exception e){} return s; }
    String[] parseEngine(String q){ String low=q.toLowerCase().trim(); if(low.startsWith("yt ")||low.startsWith("yt:")||low.startsWith("youtube ")){ String qq=q.replaceFirst("(?i)^(yt |yt:|youtube )",""); return new String[]{"yt","https://www.youtube.com/results?search_query="+Uri.encode(qq),qq}; } if(low.startsWith("d ")||low.startsWith("duck ")){ String qq=q.replaceFirst("(?i)^(d |duck )",""); return new String[]{"d","https://duckduckgo.com/?q="+Uri.encode(qq),qq}; } if(low.startsWith("w ")||low.startsWith("wiki ")){ String qq=q.replaceFirst("(?i)^(w |wiki )",""); return new String[]{"w","https://fr.wikipedia.org/wiki/Special:Search?search="+Uri.encode(qq),qq}; } if(q.startsWith("http")) return new String[]{"g",q,q}; return new String[]{"g","https://www.google.com/search?q="+Uri.encode(q),q}; }
    void loadFolders(){ folders.clear(); String saved=prefs.getString("folders",""); if(saved.isEmpty()) return; try{ for(String f:saved.split(";;")){ String[] parts=f.split("\\|\\|"); if(parts.length>=2){ String name=parts[0]; List<String> pkgs=new ArrayList<>(Arrays.asList(parts[1].split(","))); folders.add(new Folder(name,pkgs)); } } }catch(Exception e){} }
    
private java.util.List<String> lowRamDefaultPkgs(){
 return java.util.Arrays.asList(
  "org.fossify.phone",
  "org.fossify.messages",
  "org.fossify.contacts",
  "org.fossify.calendar",
  "org.fossify.clock",
  "org.fossify.calculator",
  "org.fossify.gallery",
  "org.fossify.filemanager",
  "org.fossify.musicplayer",
  "org.fossify.voicerecorder",
  "org.fossify.notes",
  "org.fossify.keyboard",
  "de.baumann.browser",
  "mark.via.gp",
  "app.organicmaps",
  "org.schabi.newpipe",
  "net.sourceforge.opencamera"
 );
}
private void ensureLowRamDefaults(){
 try{
  if(folders==null || folders.isEmpty()){
   Folder f=new Folder("Tout Low-RAM", lowRamDefaultPkgs());
   folders.add(f);
   saveFolders();
  }
 }catch(Exception e){}
}





void clearSearchNow(){
 try{
  if(searchApps!=null){ searchApps.setText(""); searchApps.clearFocus(); }
  if(searchWeb!=null){ searchWeb.setText(""); searchWeb.clearFocus(); }
  android.widget.EditText a = findViewById(R.id.searchAppsMain);
  if(a!=null){ a.setText(""); a.clearFocus(); }
  android.widget.EditText w = findViewById(R.id.searchWebMain);
  if(w!=null){ w.setText(""); w.clearFocus(); }
  android.widget.EditText d = findViewById(R.id.search);
  if(d!=null){ d.setText(""); d.clearFocus(); }
 }catch(Exception e){}
 try{
  android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
  if(imm!=null){
   if(searchApps!=null) imm.hideSoftInputFromWindow(searchApps.getWindowToken(),0);
   if(searchWeb!=null) imm.hideSoftInputFromWindow(searchWeb.getWindowToken(),0);
   android.view.View cf = getCurrentFocus();
   if(cf!=null) imm.hideSoftInputFromWindow(cf.getWindowToken(),0);
  }
 }catch(Exception e){}
}


void registerTimeTick(){
    try{ }catch(Exception e){}
 timeReceiver = new android.content.BroadcastReceiver(){
  public void onReceive(android.content.Context c, android.content.Intent i){
   try{ updateClock(); }catch(Exception e){}
  }
 };
 registerReceiver(timeReceiver, new android.content.IntentFilter(android.content.Intent.ACTION_TIME_TICK));
}
void updateClock(){
 try{
  android.widget.TextView tv=findViewById(R.id.clock);
  if(tv!=null) tv.setText(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(new java.util.Date()));
 }catch(Exception e){}
}

void registerPkgReceiver(){
 try{ if(pkgReceiver!=null) unregisterReceiver(pkgReceiver); }catch(Exception e){}
 pkgReceiver = new android.content.BroadcastReceiver(){
  public void onReceive(android.content.Context c, android.content.Intent i){
   cacheLoaded=false;
   try{ preloadFast(); }catch(Exception e){}
  }
 };
 android.content.IntentFilter f=new android.content.IntentFilter();
 f.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
 f.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);
 f.addDataScheme("package");
 registerReceiver(pkgReceiver, f);
}

void registerBatteryReceiver(){
 try{ if(batteryReceiver!=null) unregisterReceiver(batteryReceiver); }catch(Exception e){}
 batteryReceiver = new android.content.BroadcastReceiver(){
  public void onReceive(android.content.Context c, android.content.Intent i){
   try{
    int level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
    int scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
    int pct = level>=0 && scale>0 ? (level*100)/scale : 100;
    updateClock();
   }catch(Exception e){}
  }
 };
 registerReceiver(batteryReceiver, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
}

android.graphics.drawable.Drawable getCachedIcon(String pkg, android.content.pm.PackageManager pm){
 try{
  android.graphics.drawable.Drawable d = iconCache.get(pkg);
  if(d!=null) return d;
  d = pm.getApplicationIcon(pkg);
  if(d!=null) iconCache.put(pkg,d);
  return d;
 }catch(Exception e){ return null; }
}
void saveFolders(){ StringBuilder sb=new StringBuilder(); for(int i=0;i<folders.size();i++){ if(i>0) sb.append(";;"); sb.append(folders.get(i).name).append("||").append(String.join(",",folders.get(i).pkgs)); } prefs.edit().putString("folders",sb.toString()).apply(); }
    boolean isDefaultLauncher(){ Intent home = new Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY); return ri!=null && ri.activityInfo!=null && getPackageName().equals(ri.activityInfo.packageName); }
    public void onTrimMemory(int level){ super.onTrimMemory(level); if(iconCache!=null) iconCache.clear(); if(level>=40) DiskIconCache.clear(this); }
 void loadWallpaperFast(){ String uriStr=prefs.getString("custom_wallpaper_uri",null); if(uriStr==null) return; exec.execute(()->{ try{ Uri uri=Uri.parse(uriStr); InputStream is=getContentResolver().openInputStream(uri); if(is==null) return; BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true; BitmapFactory.decodeStream(is,null,opts); is.close(); int reqW=getResources().getDisplayMetrics().widthPixels; int reqH=getResources().getDisplayMetrics().heightPixels; int sample=1; while(opts.outWidth/sample/2>=reqW && opts.outHeight/sample/2>=reqH) sample*=2; InputStream is2=getContentResolver().openInputStream(uri); if(is2==null) return; BitmapFactory.Options o2=new BitmapFactory.Options(); o2.inSampleSize=sample; o2.inPreferredConfig=Bitmap.Config.RGB_565; Bitmap bmp=BitmapFactory.decodeStream(is2,null,o2); is2.close(); main.post(()->{ if(bmp!=null &&!bmp.isRecycled()) wallpaperView.setImageBitmap(bmp); }); }catch(Exception e){} }); }
    void pickWallpaperInternal(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i,201); }
    @Override protected void onActivityResult(int rc,int res,Intent data){ if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri uri=data.getData(); try{ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply(); loadWallpaperFast(); } }
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<250) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} main.postDelayed(this,30000);} }; r.run(); }
    void preloadFast(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent ii=new Intent(Intent.ACTION_MAIN,null); ii.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(ii,0); l.sort((a,bb)->{ try{ return a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString()); }catch(Exception e){ return 0; } }); cacheLoaded=true; synchronized(cache){ cache.clear(); cache.addAll(l); for(ResolveInfo ri:l){ try{ labelCache.put(ri.activityInfo.packageName, ri.loadLabel(pm).toString()); }catch(Exception e){} } } main.post(()->setupDock()); }catch(Exception e){}}); }
    void setupDock(){ int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome}; for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((FrameLayout)vv).getChildAt(0); if(idx==3) continue; String pkg=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(pkg!=null) updateDockIcon(iv, pkg); vv.setOnClickListener(view->{ if(idx==3) openDrawerWithQuery(""); else { String rp=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(rp!=null) launch(rp); }}); vv.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; }); } findViewById(R.id.dDrawer).setOnClickListener(v->openDrawerWithQuery("")); }

 void pickDockApp(int dockIdx){
        dlg.setContentView(R.layout.picker_dock);
        androidx.recyclerview.widget.RecyclerView rv=dlg.findViewById(R.id.recyclerDock);
        rv.setHasFixedSize(true);
        rv.setItemAnimator(null);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this,5));
        java.util.List<android.content.pm.ResolveInfo> list;
        synchronized(cache){ list=new java.util.ArrayList<>(cache); }
        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter(){
            class H extends androidx.recyclerview.widget.RecyclerView.ViewHolder{ android.widget.ImageView ic; android.widget.TextView lb; H(android.view.View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int viewType){ return new H(getLayoutInflater().inflate(R.layout.item_app,parent,false)); }
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder hh,int pos){
                try{
                    H h=(H)hh; if(pos>=list.size()) return;
                    android.content.pm.ResolveInfo ri=list.get(pos);
                    String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl);
                    android.graphics.drawable.Drawable d=iconCache.get(ri.activityInfo.packageName);
                    if(d!=null) h.ic.setImageDrawable(d);
                    else {
                        h.ic.setImageResource(android.R.drawable.sym_def_app_icon);
                        exec.execute(()->{ try{ android.graphics.drawable.Drawable dd=getPackageManager().getApplicationIcon(ri.activityInfo.packageName); iconCache.put(ri.activityInfo.packageName, dd); main.post(()->{ if(h.getBindingAdapterPosition()==pos) h.ic.setImageDrawable(dd); }); }catch(Exception e){} });
                    }
                    h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[dockIdx], ri.activityInfo.packageName).apply(); dlg.dismiss(); setupDock(); });
                }catch(Exception e){}
            }
            public int getItemCount(){ return list.size(); }
        });
        dlg.show();
    }
    String findRealPkg(String pkg){ if(pkg==null||pkg.isEmpty()) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg;}catch(Exception e){} return null; }
 void updateDockIcon(ImageView iv, String pkg){
        try{
            android.graphics.drawable.Drawable cd=iconCache.get(pkg);
            if(cd!=null){ iv.setImageDrawable(cd); return; }
            android.graphics.Bitmap disk=DiskIconCache.get(this,pkg);
            if(disk!=null){ iv.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(disk,96,96,true)); return; }
            android.graphics.drawable.Drawable d=getPackageManager().getApplicationIcon(pkg);
            android.graphics.Bitmap bmp;
            if(d instanceof android.graphics.drawable.BitmapDrawable){ bmp=((android.graphics.drawable.BitmapDrawable)d).getBitmap(); }
            else { bmp=android.graphics.Bitmap.createBitmap(96,96, android.graphics.Bitmap.Config.RGB_565); android.graphics.Canvas c=new android.graphics.Canvas(bmp); d.setBounds(0,0,96,96); d.draw(c); }
            DiskIconCache.put(this,pkg,bmp);
            iv.setImageDrawable(d);
            if(iconCache.size()<5) iconCache.put(pkg,d);
        }catch(Exception e){ try{ iv.setImageResource(android.R.drawable.sym_def_app_icon); }catch(Exception ee){} }
    }
    void launch(String pkg){ try{ android.content.Intent ii=getPackageManager().getLaunchIntentForPackage(pkg); if(ii!=null){ ii.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); clearSearchNow(); startActivity(ii); return; } }catch(Exception e){}  try{ trackUsage(pkg); Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); clearSearchNow(); startActivity(it);} }catch(Exception e){} }
    void loadFavs(){ favs.clear(); String saved=prefs.getString("favs",""); if(saved.isEmpty()){ favs.add(new Fav("Google","https://google.com")); favs.add(new Fav("YouTube","https://youtube.com")); return; } try{ for(String p:saved.split(";;")){ String[] sp=p.split("\\|\\|"); if(sp.length==2) favs.add(new Fav(sp[0],sp[1])); } }catch(Exception e){} }
    void saveFavs(){ StringBuilder sb=new StringBuilder(); for(int i=0;i<favs.size();i++){ if(i>0) sb.append(";;"); sb.append(favs.get(i).name).append("||").append(favs.get(i).url); } prefs.edit().putString("favs", sb.toString()).apply(); }
    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=suggList.size()) return; ResolveInfo ri=suggList.get(pos); String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl); Drawable d=iconCache.get(ri.activityInfo.packageName); if(d!=null) h.ic.setImageDrawable(d); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable dd=getPackageManager().getApplicationIcon(ri.activityInfo.packageName); iconCache.put(ri.activityInfo.packageName, dd); main.post(()->{ if(h.getBindingAdapterPosition()==pos) h.ic.setImageDrawable(dd); }); }catch(Exception e){} }); } h.itemView.setOnClickListener(v->{ launch(ri.activityInfo.packageName); }); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView icon; TextView name; H(View v){super(v); icon=v.findViewById(R.id.favIcon); name=v.findViewById(R.id.favName);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_fav,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=favs.size()) return; Fav f=favs.get(pos); h.name.setText(f.name); h.icon.setText(f.name.substring(0,1).toUpperCase()); h.itemView.setOnClickListener(v->showBrowserChooserGlass(f.url)); h.itemView.setOnLongClickListener(v->{ favs.remove(pos); saveFavs(); if(!rvFav.isComputingLayout()) notifyDataSetChanged(); else rvFav.post(()->notifyDataSetChanged()); return true; }); }catch(Exception e){} } public int getItemCount(){ return favs.size(); } }
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView name; TextView icon; H(View v){super(v); name=v.findViewById(R.id.favName); icon=v.findViewById(R.id.favIcon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_fav,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=folders.size()) return; Folder f=folders.get(pos); h.name.setText("📁 "+f.name+" ("+f.pkgs.size()+")"); h.icon.setText("📁"); h.itemView.setOnClickListener(v->showFolderContent(f)); h.itemView.setOnLongClickListener(v->{ folders.remove(pos); saveFolders(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return folders.size(); } }
    class FastAdapter extends RecyclerView.Adapter<FastAdapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; FastAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ View v=getLayoutInflater().inflate(R.layout.item_app,pa,false); return new H(v); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=list.size()) return; ResolveInfo ri=list.get(pos); String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(cd); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable dd=pm.getApplicationIcon(ri.activityInfo.packageName); iconCache.put(ri.activityInfo.packageName,dd); main.post(()->{ if(h.getBindingAdapterPosition()==pos) h.ic.setImageDrawable(dd); }); }catch(Exception e){} }); } h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ launch(ri.activityInfo.packageName); dlg.dismiss();}catch(Exception e){}}); }catch(Exception e){} } public int getItemCount(){ return list.size(); } }
    public static class AdminReceiver extends android.app.admin.DeviceAdminReceiver {}

    androidx.recyclerview.widget.RecyclerView.RecycledViewPool sharedPool = QuantumApp.sharedPool;
    void boostAllApps(){
        try{
            getWindow().setBackgroundDrawable(null);
            if(wallpaperView!=null && QuantumApp.wallpaperCache!=null) wallpaperView.setImageDrawable(QuantumApp.wallpaperCache);
            try{
                rvFolders.setRecycledViewPool(sharedPool);
                rvFolders.setItemViewCacheSize(1000);
                rvFolders.setHasFixedSize(true);
                rvFolders.setItemAnimator(null);
                rvFolders.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);
                rvFolders.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
                ((androidx.recyclerview.widget.LinearLayoutManager)rvFolders.getLayoutManager()).setInitialPrefetchItemCount(1000);
            }catch(Exception e){}
            try{
                rvSuggestions.setRecycledViewPool(sharedPool);
                rvSuggestions.setItemViewCacheSize(1000);
                rvSuggestions.setHasFixedSize(true);
                rvSuggestions.setItemAnimator(null);
                rvSuggestions.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);
                rvSuggestions.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
                ((androidx.recyclerview.widget.LinearLayoutManager)rvSuggestions.getLayoutManager()).setInitialPrefetchItemCount(1000);
            }catch(Exception e){}
            android.os.Process.setThreadPriority(-20);
        }catch(Exception e){}
        QuantumApp.boot();
    }

}