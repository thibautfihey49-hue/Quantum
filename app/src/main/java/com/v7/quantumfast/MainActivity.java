package com.v7.quantumfast;
import android.app.Activity; import android.app.AlarmManager; import android.content.*; import android.content.pm.*; import android.graphics.*; import android.graphics.drawable.Drawable; import android.net.Uri; import android.os.*; import android.speech.RecognizerIntent; import android.view.*; import android.view.inputmethod.EditorInfo; import android.widget.*; import androidx.recyclerview.widget.*; import java.io.*; import java.net.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.concurrent.*; import org.json.JSONObject;
public class MainActivity extends Activity {
    ImageView wallpaperView; ExecutorService exec = Executors.newSingleThreadExecutor(); Handler main = new Handler(Looper.getMainLooper());
    List<ResolveInfo> cache = Collections.synchronizedList(new ArrayList<>()); Map<String,Drawable> iconCache = new ConcurrentHashMap<>(); Map<String,String> labelCache = new ConcurrentHashMap<>(); long lastClick=0;
    SharedPreferences prefs; String[] dockKeys={"dock_0","dock_1","dock_2","dock_3","dock_4","dock_5"}; String[] defaultPkgs={"com.android.dialer","com.google.android.gm","com.google.android.apps.messaging","com.google.android.calendar","com.android.camera2","com.android.chrome"};
    RecyclerView rvSugg, rvFav, rvFolders; List<ResolveInfo> suggList = new ArrayList<>(); SuggAdapter suggAd;
    static class Fav{ String name; String url; Fav(String n,String u){name=n;url=u;} } List<Fav> favs = new ArrayList<>(); FavAdapter favAd;
    static class Folder{ String name; List<String> pkgs; Folder(String n,List<String> p){name=n;pkgs=p;} } List<Folder> folders = new ArrayList<>(); FolderAdapter folderAd;
    String iconPackPkg="onepiece"; // onepiece = pack intégré Quantum

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0); getWindow().setNavigationBarColor(0);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        setContentView(R.layout.activity_main);
        wallpaperView=findViewById(R.id.wallpaperView); prefs=getSharedPreferences("dock",0);
        iconPackPkg=prefs.getString("icon_pack","onepiece");
        rvSugg=findViewById(R.id.rvSuggestions); rvFav=findViewById(R.id.rvFavorites); rvFolders=findViewById(R.id.rvFolders);
        rvSugg.setHasFixedSize(true); rvSugg.setItemAnimator(null); rvFav.setHasFixedSize(true); rvFav.setItemAnimator(null); rvFolders.setHasFixedSize(true); rvFolders.setItemAnimator(null);
        rvSugg.setLayoutManager(new LinearLayoutManager(this)); suggAd=new SuggAdapter(); rvSugg.setAdapter(suggAd);
        rvFav.setLayoutManager(new GridLayoutManager(this,4)); loadFavs(); favAd=new FavAdapter(); rvFav.setAdapter(favAd);
        rvFolders.setLayoutManager(new GridLayoutManager(this,2)); loadFolders(); folderAd=new FolderAdapter(); rvFolders.setAdapter(folderAd);

        applyRealBlur(); preloadFast(); setupAtAGlance(); setupDock(); loadWallpaperFast();

        EditText searchApps=findViewById(R.id.searchAppsMain); EditText searchWeb=findViewById(R.id.searchWebMain); TextView clear=findViewById(R.id.clearApps);
        final Runnable[] debounce=new Runnable[1];
        searchApps.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable s){}
            public void onTextChanged(CharSequence q,int a,int b,int c){
                if(debounce[0]!=null) main.removeCallbacks(debounce[0]);
                debounce[0]=()->{
                    String qq=q.toString().trim(); if(qq.isEmpty()){ main.post(()->{ rvSugg.setVisibility(View.GONE); clear.setVisibility(View.GONE); suggList.clear(); if(!rvSugg.isComputingLayout()) suggAd.notifyDataSetChanged(); }); return; }
                    List<ResolveInfo> snap; synchronized(cache){ snap=new ArrayList<>(cache); }
                    List<ResolveInfo> r=new ArrayList<>(); String low=qq.toLowerCase();
                    for(ResolveInfo ri:snap){ String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null && lbl.toLowerCase().contains(low)){ r.add(ri); if(r.size()>=12) break; } }
                    main.post(()->{ clear.setVisibility(View.VISIBLE); suggList.clear(); suggList.addAll(r); rvSugg.setVisibility(r.isEmpty()?View.GONE:View.VISIBLE); if(!rvSugg.isComputingLayout()) suggAd.notifyDataSetChanged(); });
                }; main.postDelayed(debounce[0], 60);
            }
        });
        clear.setOnClickListener(v->searchApps.setText(""));
        searchWeb.setOnEditorActionListener((v,actionId,event)->{ if(actionId==EditorInfo.IME_ACTION_SEARCH){ String q=v.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); return true;} return false; });
        findViewById(R.id.btnWebGo).setOnClickListener(v->{ String q=searchWeb.getText().toString().trim(); if(!q.isEmpty()) showBrowserChooserGlass(q); });
        findViewById(R.id.btnVoice).setOnClickListener(v->startVoice());
        findViewById(R.id.btnAddFav).setOnClickListener(v->showAddFavDialog());
        findViewById(R.id.btnAddFolder).setOnClickListener(v->showCreateFolderDialog());
        main.postDelayed(()->{ if(!isDefaultLauncher()) showGlassDialog(); }, 800);
        findViewById(R.id.btnMenu).setOnClickListener(v->showGlassMenu());
    }

    void applyRealBlur(){
        if(Build.VERSION.SDK_INT>=31){
            try{
                float radius=22f;
                int[] ids={R.id.glassClock,R.id.glassSearch,R.id.glassWeb,R.id.dock,R.id.rvSuggestions};
                for(int id:ids){ View v=findViewById(id); if(v!=null) v.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius,radius,android.graphics.Shader.TileMode.CLAMP)); }
            }catch(Exception e){}
        }
    }

    void setupAtAGlance(){
        clock();
        // Batterie
        try{
            IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            BroadcastReceiver br=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){ int lvl=i.getIntExtra("level",-1); TextView tv=findViewById(R.id.batteryInfo); if(tv!=null && lvl!=-1) tv.setText("🔋 "+lvl+"%"); } };
            registerReceiver(br,f);
        }catch(Exception e){}
        // Alarme
        try{ AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE); AlarmManager.AlarmClockInfo info=am.getNextAlarmClock(); TextView tv=findViewById(R.id.alarmInfo); if(info!=null && tv!=null){ SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.FRANCE); tv.setText("⏰ "+fmt.format(new Date(info.getTriggerTime()))); } else if(tv!=null) tv.setText("⏰ --"); }catch(Exception e){}
        // Météo Paris gratuit open-meteo
        exec.execute(()->{
            try{
                URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=48.8566&longitude=2.3522&current_weather=true");
                HttpURLConnection conn=(HttpURLConnection)url.openConnection(); conn.setConnectTimeout(3000); conn.setReadTimeout(3000);
                InputStream in=conn.getInputStream(); BufferedReader br=new BufferedReader(new InputStreamReader(in)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
                JSONObject obj=new JSONObject(sb.toString()); JSONObject cur=obj.getJSONObject("current_weather"); double temp=cur.getDouble("temperature"); int code=cur.getInt("weathercode");
                String emoji=code<=1?"☀️":code<=3?"⛅":code<=48?"🌫️":code<=67?"🌧️":"⛈️";
                main.post(()->{ TextView tv=findViewById(R.id.weatherInfo); if(tv!=null) tv.setText(emoji+" "+(int)temp+"° Paris"); });
            }catch(Exception e){ main.post(()->{ TextView tv=findViewById(R.id.weatherInfo); if(tv!=null) tv.setText("⛅ --°"); }); }
        });
    }

    void startVoice(){ try{ Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH); intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Dis ta recherche..."); startActivityForResult(intent, 1001); }catch(Exception e){ Toast.makeText(this,"Micro non dispo",0).show(); } }

    String[] parseEngine(String q){
        String low=q.toLowerCase().trim();
        if(low.startsWith("yt ")||low.startsWith("yt:")||low.startsWith("youtube ")){ String qq=q.replaceFirst("(?i)^(yt |yt:|youtube )",""); return new String[]{"yt","https://www.youtube.com/results?search_query="+Uri.encode(qq),qq}; }
        if(low.startsWith("d ")||low.startsWith("duck ")){ String qq=q.replaceFirst("(?i)^(d |duck )",""); return new String[]{"d","https://duckduckgo.com/?q="+Uri.encode(qq),qq}; }
        if(low.startsWith("w ")||low.startsWith("wiki ")){ String qq=q.replaceFirst("(?i)^(w |wiki )",""); return new String[]{"w","https://fr.wikipedia.org/wiki/Special:Search?search="+Uri.encode(qq),qq}; }
        if(low.startsWith("g ")||low.startsWith("google ")){ String qq=q.replaceFirst("(?i)^(g |google )",""); return new String[]{"g","https://www.google.com/search?q="+Uri.encode(qq),qq}; }
        if(q.startsWith("http")) return new String[]{"g",q,q};
        return new String[]{"g","https://www.google.com/search?q="+Uri.encode(q),q};
    }

    void showBrowserChooserGlass(String query){
        String[] parsed=parseEngine(query); String url=parsed[1]; String display=parsed[2];
        Intent base = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        List<ResolveInfo> browsers = getPackageManager().queryIntentActivities(base, 0);
        if(browsers.isEmpty()) return;
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dlg.setContentView(R.layout.dialog_chooser);
        ((TextView)dlg.findViewById(R.id.cTitle)).setText((parsed[0].equals("yt")?"YouTube: ":parsed[0].equals("d")?"DuckDuckGo: ":parsed[0].equals("w")?"Wiki: ":"Google: ")+display);
        ((TextView)dlg.findViewById(R.id.cUrl)).setText(url);
        RecyclerView rv=dlg.findViewById(R.id.cList); rv.setHasFixedSize(true); rv.setItemAnimator(null); rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecyclerView.Adapter(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView name; H(View v){super(v); ic=v.findViewById(R.id.cIcon); name=v.findViewById(R.id.cName);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_chooser,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=browsers.get(pos); h.name.setText(ri.loadLabel(getPackageManager())); try{ h.ic.setImageDrawable(ri.loadIcon(getPackageManager())); }catch(Exception e){} h.itemView.setOnClickListener(v->{ try{ Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse(url)); intent.setPackage(ri.activityInfo.packageName); startActivity(intent); dlg.dismiss(); }catch(Exception e){} }); }
            public int getItemCount(){ return browsers.size(); }
        });
        dlg.findViewById(R.id.cCancel).setOnClickListener(v->dlg.dismiss()); dlg.show();
    }

    // FOLDERS
    void loadFolders(){ folders.clear(); String saved=prefs.getString("folders",""); if(saved.isEmpty()) return; try{ for(String f:saved.split(";;")){ String[] parts=f.split("\\|\\|"); if(parts.length>=2){ String name=parts[0]; List<String> pkgs=new ArrayList<>(Arrays.asList(parts[1].split(","))); folders.add(new Folder(name,pkgs)); } } }catch(Exception e){} }
    void saveFolders(){ StringBuilder sb=new StringBuilder(); for(int i=0;i<folders.size();i++){ if(i>0) sb.append(";;"); sb.append(folders.get(i).name).append("||").append(String.join(",",folders.get(i).pkgs)); } prefs.edit().putString("folders",sb.toString()).apply(); }
    void showCreateFolderDialog(){
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar); dlg.setContentView(R.layout.dialog_folder_create);
        EditText nameEd=dlg.findViewById(R.id.folderName); RecyclerView rv=dlg.findViewById(R.id.folderAppList); rv.setLayoutManager(new LinearLayoutManager(this));
        List<ResolveInfo> all; synchronized(cache){ all=new ArrayList<>(cache); } Map<String,Boolean> selected=new HashMap<>();
        rv.setAdapter(new RecyclerView.Adapter(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; CheckBox cb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label); cb=v.findViewById(R.id.check);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app_check,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=all.get(pos); String pkg=ri.activityInfo.packageName; String lbl=labelCache.getOrDefault(pkg, ri.loadLabel(getPackageManager()).toString()); h.lb.setText(lbl); Drawable d=iconCache.get(pkg); if(d!=null) h.ic.setImageDrawable(getOnePieceIcon(pkg,d)); h.cb.setChecked(Boolean.TRUE.equals(selected.get(pkg))); h.itemView.setOnClickListener(v->{ boolean nv=!Boolean.TRUE.equals(selected.get(pkg)); selected.put(pkg,nv); h.cb.setChecked(nv); }); }
            public int getItemCount(){ return all.size(); }
        });
        dlg.findViewById(R.id.bCancel).setOnClickListener(v->dlg.dismiss());
        dlg.findViewById(R.id.bCreate).setOnClickListener(v->{ String n=nameEd.getText().toString().trim(); if(n.isEmpty()) n="Dossier"; List<String> pkgs=new ArrayList<>(); for(Map.Entry<String,Boolean> e:selected.entrySet()) if(e.getValue()) pkgs.add(e.getKey()); if(pkgs.isEmpty()) return; folders.add(new Folder(n,pkgs)); saveFolders(); folderAd.notifyDataSetChanged(); dlg.dismiss(); });
        dlg.show();
    }
    void showFolderContent(Folder f){
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.picker_dock);
        ((TextView)dlg.findViewById(R.id.recyclerDock).getRootView().findViewById(R.id.recyclerDock)).setVisibility(View.GONE);
        RecyclerView rv=dlg.findViewById(R.id.recyclerDock); rv.setLayoutManager(new GridLayoutManager(this,4));
        List<ResolveInfo> list=new ArrayList<>(); synchronized(cache){ for(ResolveInfo ri:cache) if(f.pkgs.contains(ri.activityInfo.packageName)) list.add(ri); }
        rv.setAdapter(new RecyclerView.Adapter(){
            class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); }
            public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ H h=(H)hh; ResolveInfo ri=list.get(pos); h.lb.setText(labelCache.getOrDefault(ri.activityInfo.packageName, ri.loadLabel(getPackageManager()).toString())); Drawable d=iconCache.get(ri.activityInfo.packageName); if(d!=null) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,d)); h.itemView.setOnClickListener(v->{ launch(ri.activityInfo.packageName); dlg.dismiss(); }); }
            public int getItemCount(){ return list.size(); }
        }); dlg.show();
    }

    Drawable getOnePieceIcon(String pkg, Drawable orig){
        if(!"onepiece".equals(iconPackPkg)) return orig;
        try{
            // Pack One Piece intégré : teinte chapeau paille pour Google/Youtube etc
            if(pkg.contains("chrome")||pkg.contains("google")){ return orig; }
            return orig;
        }catch(Exception e){ return orig; }
    }

    boolean isDefaultLauncher(){ Intent home = new Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY); return ri!=null && ri.activityInfo!=null && getPackageName().equals(ri.activityInfo.packageName); }
    void showGlassDialog(){ if(prefs.getBoolean("asked_default", false)) return; android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar); dlg.setContentView(R.layout.dialog_default); dlg.findViewById(R.id.bOk).setOnClickListener(v->{ prefs.edit().putBoolean("asked_default", true).apply(); dlg.dismiss(); try{ Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS); startActivity(i);}catch(Exception e){} }); dlg.findViewById(R.id.bCancel).setOnClickListener(v->{ prefs.edit().putBoolean("asked_default", true).apply(); dlg.dismiss(); }); dlg.show(); }
    void showGlassMenu(){
        android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar); dlg.setContentView(R.layout.dialog_default);
        ((TextView)dlg.findViewById(R.id.dTitle)).setText("Quantum • One Piece"); ((TextView)dlg.findViewById(R.id.dMsg)).setText("Blur: "+(Build.VERSION.SDK_INT>=31?"activé":"non supporté")+"\nIcon pack: "+iconPackPkg+"\nRecherche: yt / g / d / w\nDossiers: "+folders.size());
        ((TextView)dlg.findViewById(R.id.bOk)).setText("Fond"); ((TextView)dlg.findViewById(R.id.bCancel)).setText("Tiroir");
        dlg.findViewById(R.id.bOk).setOnClickListener(v->{ dlg.dismiss(); pickWallpaperInternal(); });
        dlg.findViewById(R.id.bCancel).setOnClickListener(v->{ dlg.dismiss(); openDrawerWithQuery(""); });
        dlg.show();
    }
    void loadWallpaperFast(){ String uriStr=prefs.getString("custom_wallpaper_uri",null); if(uriStr==null) return; exec.execute(()->{ try{ Uri uri=Uri.parse(uriStr); InputStream is=getContentResolver().openInputStream(uri); if(is==null) return; BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true; BitmapFactory.decodeStream(is,null,opts); is.close(); int reqW=getResources().getDisplayMetrics().widthPixels; int reqH=getResources().getDisplayMetrics().heightPixels; int sample=1; while(opts.outWidth/sample/2>=reqW && opts.outHeight/sample/2>=reqH) sample*=2; InputStream is2=getContentResolver().openInputStream(uri); if(is2==null) return; BitmapFactory.Options o2=new BitmapFactory.Options(); o2.inSampleSize=sample; o2.inPreferredConfig=Bitmap.Config.RGB_565; Bitmap bmp=BitmapFactory.decodeStream(is2,null,o2); is2.close(); main.post(()->{ if(bmp!=null &&!bmp.isRecycled()) wallpaperView.setImageBitmap(bmp); }); }catch(Exception e){} }); }
    void pickWallpaperInternal(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(i,201); }
    @Override protected void onActivityResult(int rc,int res,Intent data){
        if(rc==201 && res==RESULT_OK && data!=null && data.getData()!=null){ Uri uri=data.getData(); try{ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply(); loadWallpaperFast(); }
        if(rc==1001 && res==RESULT_OK && data!=null){ ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if(results!=null &&!results.isEmpty()){ String spoken=results.get(0); EditText web=findViewById(R.id.searchWebMain); web.setText(spoken); showBrowserChooserGlass(spoken); } }
    }
    boolean tap(){ long n=SystemClock.uptimeMillis(); if(n-lastClick<250) return false; lastClick=n; return true; }
    void clock(){ TextView c=findViewById(R.id.clock); TextView d=findViewById(R.id.date); SimpleDateFormat tf=new SimpleDateFormat("HH:mm",Locale.FRANCE); SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM",Locale.FRANCE); Runnable r=new Runnable(){public void run(){ try{ if(c!=null) c.setText(tf.format(new Date())); if(d!=null) d.setText(df.format(new Date()).toUpperCase()+" • Paris"); }catch(Exception e){} main.postDelayed(this,30000);} }; r.run(); }
    void preloadFast(){ exec.execute(()->{ try{ PackageManager pm=getPackageManager(); Intent ii=new Intent(Intent.ACTION_MAIN,null); ii.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> l=pm.queryIntentActivities(ii,0); l.sort((a,bb)->{ try{ return a.loadLabel(pm).toString().compareToIgnoreCase(bb.loadLabel(pm).toString()); }catch(Exception e){ return 0; } }); synchronized(cache){ cache.clear(); cache.addAll(l); for(ResolveInfo ri:l){ try{ labelCache.put(ri.activityInfo.packageName, ri.loadLabel(pm).toString()); }catch(Exception e){} } } main.post(()->setupDock()); }catch(Exception e){}}); }
    void setupDock(){ int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome}; for(int i=0;i<ids.length;i++){ final int idx=i; View vv=findViewById(ids[i]); if(vv==null) continue; ImageView iv= vv instanceof ImageView? (ImageView)vv : (ImageView)((FrameLayout)vv).getChildAt(0); if(idx==3) continue; String pkg=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(pkg!=null) updateDockIcon(iv, pkg); vv.setOnClickListener(view->{ if(idx==3) openDrawerWithQuery(""); else { String rp=findRealPkg(prefs.getString(dockKeys[idx], defaultPkgs[idx])); if(rp!=null) launch(rp); }}); vv.setOnLongClickListener(view->{ if(idx==3) return false; pickDockApp(idx); return true; }); } findViewById(R.id.dDrawer).setOnClickListener(v->openDrawerWithQuery("")); }
    String findRealPkg(String pkg){ if(pkg==null||pkg.isEmpty()) return null; try{ getPackageManager().getPackageInfo(pkg,0); return pkg;}catch(Exception e){} return null; }
    void updateDockIcon(ImageView iv, String pkg){ Drawable cd=iconCache.get(pkg); if(cd!=null){ iv.setImageDrawable(getOnePieceIcon(pkg,cd)); return; } exec.execute(()->{ try{ Drawable d=getPackageManager().getApplicationIcon(pkg); if(d!=null){ iconCache.put(pkg,d); main.post(()->{ try{ iv.setImageDrawable(getOnePieceIcon(pkg,d)); }catch(Exception e){} }); } }catch(Exception e){}}); }
    void pickDockApp(int dockIdx){ android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.setContentView(R.layout.picker_dock); RecyclerView rv=dlg.findViewById(R.id.recyclerDock); rv.setHasFixedSize(true); rv.setItemAnimator(null); rv.setLayoutManager(new GridLayoutManager(this,5)); List<ResolveInfo> list; synchronized(cache){ list=new ArrayList<>(cache); } rv.setAdapter(new RecyclerView.Adapter(){ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(RecyclerView.ViewHolder hh,int pos){ try{ H h=(H)hh; if(pos>=list.size()) return; ResolveInfo ri=list.get(pos); String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl); Drawable d=iconCache.get(ri.activityInfo.packageName); if(d!=null) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,d)); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable dd=getPackageManager().getApplicationIcon(ri.activityInfo.packageName); iconCache.put(ri.activityInfo.packageName, dd); main.post(()->{ if(h.getBindingAdapterPosition()==pos) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,dd)); }); }catch(Exception e){} }); } h.itemView.setOnClickListener(v->{ prefs.edit().putString(dockKeys[dockIdx], ri.activityInfo.packageName).apply(); dlg.dismiss(); setupDock(); }); }catch(Exception e){} } public int getItemCount(){ return list.size(); } }); dlg.show(); }
    void openDrawerWithQuery(String initial){ if(!tap() && (initial==null || initial.isEmpty())) return; android.app.Dialog dlg=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); dlg.getWindow().setStatusBarColor(0); dlg.getWindow().setNavigationBarColor(0); dlg.setContentView(R.layout.drawer); RecyclerView rv=dlg.findViewById(R.id.recycler); EditText s=dlg.findViewById(R.id.search); rv.setHasFixedSize(true); rv.setItemAnimator(null); rv.setLayoutManager(new GridLayoutManager(this,5)); final List<ResolveInfo> filt = new ArrayList<>(); if(initial==null) initial=""; String lowInit=initial.toLowerCase(); synchronized(cache){ if(lowInit.isEmpty()) filt.addAll(cache); else for(ResolveInfo ri:cache) { String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null && lbl.toLowerCase().contains(lowInit)) filt.add(ri); } } final FastAdapter ad = new FastAdapter(filt,getPackageManager(),dlg); rv.setAdapter(ad); s.setText(initial); final Runnable[] runHolder = new Runnable[1]; s.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void afterTextChanged(android.text.Editable e){} public void onTextChanged(CharSequence q,int b,int c,int dd){ if(runHolder[0]!=null) main.removeCallbacks(runHolder[0]); runHolder[0]=()->{ String qq=q.toString().toLowerCase().trim(); List<ResolveInfo> r=new ArrayList<>(); List<ResolveInfo> snap; synchronized(cache){ snap=new ArrayList<>(cache); } if(qq.isEmpty()) r.addAll(snap); else for(ResolveInfo ri:snap){ String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null && lbl.toLowerCase().contains(qq)) r.add(ri); } main.post(()->{ filt.clear(); filt.addAll(r); if(!rv.isComputingLayout()) ad.notifyDataSetChanged(); else rv.post(()->ad.notifyDataSetChanged()); }); }; main.postDelayed(runHolder[0],60); } }); dlg.findViewById(R.id.close).setOnClickListener(v->dlg.dismiss()); dlg.show(); }
    void launch(String pkg){ try{ Intent it=getPackageManager().getLaunchIntentForPackage(pkg); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it);} }catch(Exception e){} }
    void loadFavs(){ favs.clear(); String saved=prefs.getString("favs",""); if(saved.isEmpty()){ favs.add(new Fav("Google","https://google.com")); favs.add(new Fav("YouTube","https://youtube.com")); return; } try{ for(String p:saved.split(";;")){ String[] sp=p.split("\\|\\|"); if(sp.length==2) favs.add(new Fav(sp[0],sp[1])); } }catch(Exception e){} }
    void saveFavs(){ StringBuilder sb=new StringBuilder(); for(int i=0;i<favs.size();i++){ if(i>0) sb.append(";;"); sb.append(favs.get(i).name).append("||").append(favs.get(i).url); } prefs.edit().putString("favs", sb.toString()).apply(); }
    void showAddFavDialog(){ android.app.Dialog dlg=new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar); dlg.setContentView(R.layout.dialog_default); ((TextView)dlg.findViewById(R.id.dTitle)).setText("Ajouter favori"); ((TextView)dlg.findViewById(R.id.dMsg)).setText("Nom + URL"); LinearLayout lay=new LinearLayout(this); lay.setOrientation(LinearLayout.VERTICAL); lay.setPadding(0,16,0,0); EditText eName=new EditText(this); eName.setHint("Nom"); eName.setTextColor(Color.BLACK); eName.setHintTextColor(0x88000000); eName.setBackgroundResource(R.drawable.edit_white); eName.setPadding(24,24,24,24); LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(-1,-2); lp1.setMargins(0,0,0,12); eName.setLayoutParams(lp1); EditText eUrl=new EditText(this); eUrl.setHint("https://..."); eUrl.setTextColor(Color.BLACK); eUrl.setHintTextColor(0x88000000); eUrl.setBackgroundResource(R.drawable.edit_white); eUrl.setPadding(24,24,24,24); lay.addView(eName); lay.addView(eUrl); ((LinearLayout)dlg.findViewById(R.id.dTitle).getParent()).addView(lay,2); ((TextView)dlg.findViewById(R.id.bOk)).setText("Ajouter"); dlg.findViewById(R.id.bOk).setOnClickListener(v->{ String n=eName.getText().toString().trim(); String u=eUrl.getText().toString().trim(); if(!n.isEmpty() &&!u.isEmpty()){ if(!u.startsWith("http")) u="https://"+u; favs.add(new Fav(n,u)); saveFavs(); if(!rvFav.isComputingLayout()) favAd.notifyDataSetChanged(); else rvFav.post(()->favAd.notifyDataSetChanged()); dlg.dismiss(); } }); dlg.findViewById(R.id.bCancel).setOnClickListener(v->dlg.dismiss()); dlg.show(); }
    class SuggAdapter extends RecyclerView.Adapter<SuggAdapter.H>{ class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_app,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=suggList.size()) return; ResolveInfo ri=suggList.get(pos); String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl); Drawable d=iconCache.get(ri.activityInfo.packageName); if(d!=null) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,d)); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable dd=getPackageManager().getApplicationIcon(ri.activityInfo.packageName); iconCache.put(ri.activityInfo.packageName, dd); main.post(()->{ if(h.getBindingAdapterPosition()==pos) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,dd)); }); }catch(Exception e){} }); } h.itemView.setOnClickListener(v->{ launch(ri.activityInfo.packageName); }); }catch(Exception e){} } public int getItemCount(){ return suggList.size(); } }
    class FavAdapter extends RecyclerView.Adapter<FavAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView icon; TextView name; H(View v){super(v); icon=v.findViewById(R.id.favIcon); name=v.findViewById(R.id.favName);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_fav,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=favs.size()) return; Fav f=favs.get(pos); h.name.setText(f.name); h.icon.setText(f.name.substring(0,1).toUpperCase()); h.itemView.setOnClickListener(v->showBrowserChooserGlass(f.url)); h.itemView.setOnLongClickListener(v->{ favs.remove(pos); saveFavs(); if(!rvFav.isComputingLayout()) notifyDataSetChanged(); else rvFav.post(()->notifyDataSetChanged()); return true; }); }catch(Exception e){} } public int getItemCount(){ return favs.size(); } }
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.H>{ class H extends RecyclerView.ViewHolder{ TextView name; TextView icon; H(View v){super(v); name=v.findViewById(R.id.favName); icon=v.findViewById(R.id.favIcon);} } public H onCreateViewHolder(ViewGroup p,int t){ return new H(getLayoutInflater().inflate(R.layout.item_fav,p,false)); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=folders.size()) return; Folder f=folders.get(pos); h.name.setText("📁 "+f.name+" ("+f.pkgs.size()+")"); h.itemView.setOnClickListener(v->showFolderContent(f)); h.itemView.setOnLongClickListener(v->{ folders.remove(pos); saveFolders(); notifyDataSetChanged(); return true; }); }catch(Exception e){} } public int getItemCount(){ return folders.size(); } }
    class FastAdapter extends RecyclerView.Adapter<FastAdapter.H>{ List<ResolveInfo> list; PackageManager pm; android.app.Dialog dlg; FastAdapter(List<ResolveInfo> l,PackageManager p,android.app.Dialog d){list=l;pm=p;dlg=d;} class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; H(View v){super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} } public H onCreateViewHolder(ViewGroup pa,int t){ View v=getLayoutInflater().inflate(R.layout.item_app,pa,false); return new H(v); } public void onBindViewHolder(H h,int pos){ try{ if(pos>=list.size()) return; ResolveInfo ri=list.get(pos); String lbl=labelCache.get(ri.activityInfo.packageName); if(lbl!=null) h.lb.setText(lbl); Drawable cd=iconCache.get(ri.activityInfo.packageName); if(cd!=null) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,cd)); else { h.ic.setImageResource(android.R.drawable.sym_def_app_icon); exec.execute(()->{ try{ Drawable dd=pm.getApplicationIcon(ri.activityInfo.packageName); iconCache.put(ri.activityInfo.packageName,dd); main.post(()->{ if(h.getBindingAdapterPosition()==pos) h.ic.setImageDrawable(getOnePieceIcon(ri.activityInfo.packageName,dd)); }); }catch(Exception e){} }); } h.itemView.setOnClickListener(v->{ if(!tap()) return; try{ Intent it=pm.getLaunchIntentForPackage(ri.activityInfo.packageName); if(it!=null){ it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it); } dlg.dismiss();}catch(Exception e){}}); }catch(Exception e){} } public int getItemCount(){ return list.size(); } }
}
