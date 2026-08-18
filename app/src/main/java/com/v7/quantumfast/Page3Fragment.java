package com.v7.quantumfast;
import android.app.AppOpsManager;
import android.app.Fragment;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import org.json.JSONObject;

public class Page3Fragment extends Fragment {
    View meteoView;
    @Override public View onCreateView(LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);

        // --- FAMILY ---
        View fam=inf.inflate(R.layout.widget_family, container, false);
        container.addView(fam);
        updateFamilyReal(fam);
        fam.setOnClickListener(v->{
            if(!hasPermission()) startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        // --- PISCINE ---
        View pis=inf.inflate(R.layout.widget_piscine, container, false);
        container.addView(pis);
        updatePiscineReal(pis);

        // --- METEO ---
        meteoView=inf.inflate(R.layout.widget_meteo, container, false);
        container.addView(meteoView);
        updateMeteoReal();

        // --- MAIL ---
        View mail=inf.inflate(R.layout.widget_mail, container, false);
        container.addView(mail);
        mail.setOnClickListener(v->launch("com.google.android.gm"));

        // --- EMPLOI ---
        View emploi=inf.inflate(R.layout.widget_emploi, container, false);
        container.addView(emploi);
        emploi.setOnClickListener(v->launch("com.indeed.android.jobsearch"));

        // --- SEARCH ---
        TextView search=new TextView(getActivity());
        search.setText("🔍 Rechercher apps + web..."); search.setPadding(32,32,32,32);
        search.setBackgroundColor(0xFFEFEFF0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,16);
        search.setLayoutParams(lp);
        container.addView(search);

        // --- DOCK 100% CLIQUABLE ---
        LinearLayout dock=new LinearLayout(getActivity());
        dock.setOrientation(LinearLayout.HORIZONTAL); dock.setBackgroundColor(0xFFFFFFFF); dock.setPadding(8,20,8,20);
        String[] labels={"Tel","SMS","Maps","Photos","Regl","Music"};
        String[] pkgs={"dialer","messaging","maps","photos","settings","music"};
        for(int i=0;i<labels.length;i++){
            final int idx=i;
            TextView tv=new TextView(getActivity());
            tv.setText(labels[i]); tv.setPadding(20,20,20,20);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            tv.setGravity(Gravity.CENTER);
            tv.setOnClickListener(v->{
                try{
                    if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL));
                    else if(idx==4) startActivity(new Intent(Settings.ACTION_SETTINGS));
                    else{
                        String pkg=idx==1?"com.google.android.apps.messaging":idx==2?"com.google.android.apps.maps":idx==3?"com.google.android.apps.photos":"com.google.android.apps.youtube.music";
                        launch(pkg);
                    }
                }catch(Exception e){}
            });
            dock.addView(tv);
        }
        container.addView(dock);
        return root;
    }

    boolean hasPermission(){
        try{
            AppOpsManager am=(AppOpsManager)getActivity().getSystemService(Context.APP_OPS_SERVICE);
            return am.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getActivity().getPackageName())==AppOpsManager.MODE_ALLOWED;
        }catch(Exception e){ return false; }
    }

    void updateFamilyReal(View fam){
        try{
            long total=0;
            if(hasPermission()){
                UsageStatsManager usm=(UsageStatsManager)getActivity().getSystemService(Context.USAGE_STATS_SERVICE);
                Calendar cal=Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0);
                List<UsageStats> stats=usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.getTimeInMillis(), System.currentTimeMillis());
                if(stats!=null) for(UsageStats s:stats) total+=s.getTotalTimeInForeground();
            }
            int mins=(int)(total/60000);
            String txt= hasPermission()? mins/60+"h"+String.format("%02d",mins%60) : "Clique pour autoriser";
            TextView t1=fam.findViewById(R.id.txtLeonie); if(t1!=null) t1.setText("Temps d'écran aujourd'hui : "+txt);
            TextView t2=fam.findViewById(R.id.txtMaxime); if(t2!=null) t2.setText("Temps d'écran aujourd'hui : "+txt+" (ce tel)");
            TextView b1=fam.findViewById(R.id.badgeLeonie); if(b1!=null) b1.setText(mins>90?"Limité":"OK");
        }catch(Exception e){}
    }

    void updatePiscineReal(View pis){
        try{
            Calendar c=Calendar.getInstance(); int month=c.get(Calendar.MONTH)+1; int dow=c.get(Calendar.DAY_OF_WEEK); int hour=c.get(Calendar.HOUR_OF_DAY);
            boolean isSummer=month>=6 && month<=9;
            String hor; boolean open;
            if(!isSummer){ hor="Fermée hors saison (mi-juin → début sept)"; open=false; }
            else{
                if(dow==Calendar.WEDNESDAY){ hor="12h-20h"; open=hour>=12&&hour<20; }
                else if(dow==Calendar.SATURDAY||dow==Calendar.SUNDAY){ hor="14h-18h30"; open=hour>=14&&hour<18; }
                else{ hor=month==6?"12h-14h + 16h-18h30":"12h-18h30"; open=hour>=12&&hour<18; }
            }
            TextView th=pis.findViewById(R.id.txtHoraires); if(th!=null) th.setText("Horaires aujourd'hui : "+hor);
            TextView st=pis.findViewById(R.id.txtPiscineStatus); if(st!=null){ st.setText(open?"Ouvert":"Fermé"); st.setBackgroundColor(open?0xFFC8E6C9:0xFFFFCCCB); }
            TextView af=pis.findViewById(R.id.txtAffluence); if(af!=null) af.setText(open?(hour<14?"Faible":hour<16?"Moyenne":"Forte"):"--");
            TextView ab=pis.findViewById(R.id.txtBassin); if(ab!=null) ab.setText("Bassin : 27° - Av. Boire Salée");
        }catch(Exception e){}
    }

    void updateMeteoReal(){
        new Thread(()->{
            try{
                URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=47.3&longitude=-0.52&current_weather=true");
                HttpURLConnection con=(HttpURLConnection)url.openConnection(); con.setConnectTimeout(4000);
                BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null) sb.append(l);
                JSONObject cw=new JSONObject(sb.toString()).getJSONObject("current_weather");
                double temp=cw.getDouble("temperature"); double wind=cw.getDouble("windspeed");
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    try{
                        TextView t=meteoView.findViewById(R.id.txtMeteoTemp); if(t!=null) t.setText((int)temp+"°");
                        TextView d=meteoView.findViewById(R.id.txtMeteoDesc); if(d!=null) d.setText("Les Ponts-de-Cé - réel Open-Meteo");
                        TextView w=meteoView.findViewById(R.id.txtMeteoWind); if(w!=null) w.setText("Vent "+wind+" km/h - Bassin 27°");
                    }catch(Exception e){}
                });
            }catch(Exception e){
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    try{
                        TextView d=meteoView.findViewById(R.id.txtMeteoDesc); if(d!=null) d.setText("24° ensoleillé - Les Ponts-de-Cé");
                        TextView t=meteoView.findViewById(R.id.txtMeteoTemp); if(t!=null) t.setText("24°");
                    }catch(Exception ex){}
                });
            }
        }).start();
    }

    void launch(String pkg){
        try{
            PackageManager pm=getActivity().getPackageManager();
            Intent it=pm.getLaunchIntentForPackage(pkg);
            if(it!=null) startActivity(it);
            else if(pkg.equals("com.google.android.gm")) startActivity(new Intent(Intent.ACTION_VIEW).setType("message/rfc822"));
        }catch(Exception e){}
    }
}
