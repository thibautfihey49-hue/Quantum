package com.v7.quantumfast;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import org.json.JSONObject;

public class Page3Fragment extends Fragment {
    View meteoView;
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle b){
        try{
            View root=inf.inflate(R.layout.fragment_page3,p,false);
            LinearLayout container=root.findViewById(R.id.widgetContainer);
            if(container==null) return root;

            View fam=safeInflate(inf,R.layout.widget_family,container);
            View pis=safeInflate(inf,R.layout.widget_piscine,container);
            meteoView=safeInflate(inf,R.layout.widget_meteo,container);
            View mail=safeInflate(inf,R.layout.widget_mail,container);
            View emploi=safeInflate(inf,R.layout.widget_emploi,container);

            if(fam!=null) container.addView(wrapDrag(fam));
            if(pis!=null) container.addView(wrapDrag(pis));
            if(meteoView!=null) container.addView(wrapDrag(meteoView));
            if(mail!=null){ container.addView(wrapDrag(mail)); makeClickable(mail,"com.google.android.gm"); }
            if(emploi!=null){ container.addView(wrapDrag(emploi)); makeClickable(emploi,"com.indeed.android.jobsearch"); }
            container.addView(wrapDrag(createSearch()));
            container.addView(wrapDrag(createDockClickable()));

            if(fam!=null) safeRun(()->updateFamilyReal(fam));
            if(pis!=null) safeRun(()->updatePiscineReal(pis));
            if(meteoView!=null) safeRun(()->updateMeteoReal());

            if(fam!=null) fam.setOnClickListener(v->{ if(!hasUsagePermission()) startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); });

            return root;
        }catch(Exception e){
            TextView tv=new TextView(getContext()); tv.setText("Erreur V7: "+e.getMessage()); return tv;
        }
    }

    View safeInflate(LayoutInflater inf,int res,ViewGroup parent){
        try{ return inf.inflate(res,parent,false); }catch(Exception e){ return null; }
    }
    void safeRun(Runnable r){ try{ r.run(); }catch(Exception ignored){} }

    void makeClickable(View v,String pkg){ try{ v.setOnClickListener(view->launchApp(pkg)); }catch(Exception ignored){} }

    DragResizeLayout wrapDrag(View c){
        try{
            DragResizeLayout d=new DragResizeLayout(requireContext());
            d.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); d.setPadding(0,0,0,12); d.addView(c); return d;
        }catch(Exception e){ DragResizeLayout d=new DragResizeLayout(getContext()); d.addView(c); return d; }
    }

    boolean hasUsagePermission(){
        try{
            AppOpsManager appOps=(AppOpsManager)requireContext().getSystemService(Context.APP_OPS_SERVICE);
            return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().getPackageName())==AppOpsManager.MODE_ALLOWED;
        }catch(Exception e){ return false; }
    }

    void updateFamilyReal(View fam){
        try{
            long total=getTodayScreenTimeMs(); int mins=(int)(total/60000);
            TextView tL=fam.findViewById(R.id.txtLeonie); if(tL!=null) tL.setText("Aujourd'hui : "+mins/60+"h"+String.format("%02d",mins%60));
            TextView tM=fam.findViewById(R.id.txtMaxime); if(tM!=null) tM.setText("Aujourd'hui : "+mins/60+"h"+String.format("%02d",mins%60)+" (ce tel)");
        }catch(Exception ignored){}
    }
    long getTodayScreenTimeMs(){
        try{
            UsageStatsManager usm=(UsageStatsManager)requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar cal=Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0);
            List<UsageStats> stats=usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.getTimeInMillis(), System.currentTimeMillis());
            long t=0; if(stats!=null) for(UsageStats s:stats) t+=s.getTotalTimeInForeground(); return t;
        }catch(Exception e){return 0;}
    }

    void updatePiscineReal(View pis){
        try{
            Calendar c=Calendar.getInstance(); int month=c.get(Calendar.MONTH)+1; int dow=c.get(Calendar.DAY_OF_WEEK); int hour=c.get(Calendar.HOUR_OF_DAY);
            boolean isSummer=month>=6 && month<=8; String hor; boolean open;
            if(!isSummer){ hor="Fermée hors saison"; open=false; }
            else{
                if(dow==Calendar.WEDNESDAY){ hor=month<=7?"10h-20h":"12h-20h"; open=hour>=10&&hour<20; }
                else if(dow==Calendar.SATURDAY||dow==Calendar.SUNDAY){ hor="14h-18h30"; open=hour>=14&&hour<18; }
                else{ hor=month==6?"12h-14h + 16h-18h30":"12h-18h30"; open=hour>=12&&hour<18; }
            }
            TextView hView=pis.findViewById(R.id.txtHoraires); if(hView!=null) hView.setText("Horaires aujourd'hui : "+hor);
            TextView sView=pis.findViewById(R.id.txtPiscineStatus); if(sView!=null){ sView.setText(open?"Ouvert":"Fermé"); }
            TextView aView=pis.findViewById(R.id.txtAffluence); if(aView!=null) aView.setText(hour<12?"Faible":hour<16?"Moyenne":"Forte");
        }catch(Exception ignored){}
    }

    void updateMeteoReal(){
        new Thread(()->{
            try{
                URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=47.3&longitude=-0.52&current_weather=true");
                HttpURLConnection con=(HttpURLConnection)url.openConnection(); con.setConnectTimeout(4000);
                BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
                JSONObject json=new JSONObject(sb.toString()).getJSONObject("current_weather");
                double temp=json.getDouble("temperature"); double wind=json.getDouble("windspeed");
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    try{
                        TextView t=meteoView.findViewById(R.id.txtMeteoTemp); if(t!=null) t.setText((int)temp+"°");
                        TextView d=meteoView.findViewById(R.id.txtMeteoDesc); if(d!=null) d.setText("Les Ponts-de-Cé - Vent "+wind+" km/h");
                    }catch(Exception ignored){}
                });
            }catch(Exception e){
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    try{ TextView d=meteoView.findViewById(R.id.txtMeteoDesc); if(d!=null) d.setText("24° ensoleillé - Ponts-de-Cé"); }catch(Exception ignored){}
                });
            }
        }).start();
    }

    View createSearch(){
        try{
            androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(24); card.setCardElevation(0); card.setCardBackgroundColor(0xFFEFEFF0);
            LinearLayout l=new LinearLayout(requireContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(16,14,16,14); l.setGravity(Gravity.CENTER_VERTICAL);
            TextView s=new TextView(requireContext()); s.setText("🔍 Rechercher apps + web..."); s.setTextColor(0xFF8E8E93); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); s.setTextSize(13);
            s.setOnClickListener(v->{ try{ startActivity(new Intent(Intent.ACTION_WEB_SEARCH)); }catch(Exception ignored){} });
            TextView n=new TextView(requireContext()); n.setText("+ Nouvelle page"); n.setTextSize(11); n.setTextColor(0xFF8E8E93);
            l.addView(s); l.addView(n); card.addView(l); return card;
        }catch(Exception e){ return new View(getContext()); }
    }

    GradientDrawable circle(int color){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d; }

    View createDockClickable(){
        try{
            androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(22); card.setCardElevation(0); card.setCardBackgroundColor(0xFFFFFFFF);
            LinearLayout dock=new LinearLayout(requireContext()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setPadding(8,12,8,4); dock.setGravity(Gravity.CENTER);
            String[] names={"Téléphone","Messages","Maps","Photos","Réglages","Musique"};
            String[] emojis={"📞","💬","📍","🌸","⚙️","🎵"};
            int[] colors={0xFF4CAF50,0xFF2196F3,0xFF4CAF50,0xFFE91E63,0xFF9E9E9E,0xFF9E9E9E};
            for(int i=0;i<names.length;i++){
                final int idx=i;
                LinearLayout col=new LinearLayout(requireContext()); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER); col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
                TextView bg=new TextView(requireContext()); bg.setText(emojis[i]); bg.setTextSize(20); bg.setGravity(Gravity.CENTER); bg.setBackground(circle(colors[i]));
                bg.setLayoutParams(new LinearLayout.LayoutParams(48,48));
                TextView tv=new TextView(requireContext()); tv.setText(names[i]); tv.setTextSize(9); tv.setGravity(Gravity.CENTER); tv.setPadding(0,4,0,0);
                col.addView(bg); col.addView(tv);
                col.setOnClickListener(v->{
                    try{
                        if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL));
                        else if(idx==4) startActivity(new Intent(Settings.ACTION_SETTINGS));
                        else launchApp(idx==1?"com.google.android.apps.messaging":idx==2?"com.google.android.apps.maps":idx==3?"com.google.android.apps.photos":"com.google.android.apps.youtube.music");
                    }catch(Exception ignored){}
                });
                dock.addView(col);
            }
            card.addView(dock); return card;
        }catch(Exception e){ return new View(getContext()); }
    }

    void launchApp(String pkg){
        try{
            PackageManager pm=requireContext().getPackageManager();
            Intent launch=pm.getLaunchIntentForPackage(pkg);
            if(launch!=null) startActivity(launch);
        }catch(Exception e){ Toast.makeText(getContext(),"App non installée",Toast.LENGTH_SHORT).show(); }
    }
}
