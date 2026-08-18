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
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);

        View fam=inf.inflate(R.layout.widget_family, container, false);
        View pis=inf.inflate(R.layout.widget_piscine, container, false);
        meteoView=inf.inflate(R.layout.widget_meteo, container, false);
        View mail=inf.inflate(R.layout.widget_mail, container, false);
        View emploi=inf.inflate(R.layout.widget_emploi, container, false);

        container.addView(wrapDrag(fam));
        container.addView(wrapDrag(pis));
        container.addView(wrapDrag(meteoView));
        container.addView(wrapDrag(mail));
        container.addView(wrapDrag(emploi));
        container.addView(wrapDrag(createSearch()));
        container.addView(wrapDrag(createDockClickable()));

        updateFamilyReal(fam);
        updatePiscineReal(pis);
        updateMeteoReal();
        makeClickable(mail, "com.google.android.gm");
        makeClickable(emploi, "com.indeed.android.jobsearch");

        fam.setOnClickListener(v->{ if(!hasUsagePermission()) startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); });

        return root;
    }

    void makeClickable(View v, String pkg){ v.setOnClickListener(view->{ launchApp(pkg); }); }

    DragResizeLayout wrapDrag(View c){
        DragResizeLayout d=new DragResizeLayout(requireContext());
        d.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); d.setPadding(0,0,0,12); d.addView(c); return d;
    }

    boolean hasUsagePermission(){
        AppOpsManager appOps=(AppOpsManager)requireContext().getSystemService(Context.APP_OPS_SERVICE);
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().getPackageName())==AppOpsManager.MODE_ALLOWED;
    }

    void updateFamilyReal(View fam){
        long total=getTodayScreenTimeMs(); int mins=(int)(total/60000);
        ((TextView)fam.findViewById(R.id.txtLeonie)).setText("Temps d'écran aujourd'hui : "+mins/60+"h"+String.format("%02d",mins%60));
        ((TextView)fam.findViewById(R.id.txtMaxime)).setText("Temps d'écran aujourd'hui : "+mins/60+"h"+String.format("%02d",mins%60)+" (ce tel)");
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
        Calendar c=Calendar.getInstance(); int month=c.get(Calendar.MONTH)+1; int dow=c.get(Calendar.DAY_OF_WEEK); int hour=c.get(Calendar.HOUR_OF_DAY);
        boolean isSummer=month>=6 && month<=8;
        String hor; boolean open;
        if(!isSummer){ hor="Fermée hors saison"; open=false; }
        else{
            if(dow==Calendar.WEDNESDAY){ hor=month<=7?"10h-20h":"12h-20h"; open=hour>=10&&hour<20; }
            else if(dow==Calendar.SATURDAY||dow==Calendar.SUNDAY){ hor="14h-18h30"; open=hour>=14&&hour<18; }
            else{ hor=month==6?"12h-14h + 16h-18h30":"12h-18h30"; open=hour>=12&&hour<18; }
        }
        ((TextView)pis.findViewById(R.id.txtHoraires)).setText("Horaires aujourd'hui : "+hor);
        ((TextView)pis.findViewById(R.id.txtPiscineStatus)).setText(open?"Ouvert":"Fermé");
        ((TextView)pis.findViewById(R.id.txtAffluence)).setText(hour<12?"Faible":hour<16?"Moyenne":"Forte");
    }

    void updateMeteoReal(){
        new Thread(()->{
            try{
                URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=47.3&longitude=-0.52&current_weather=true");
                HttpURLConnection con=(HttpURLConnection)url.openConnection(); con.setConnectTimeout(3000);
                BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
                JSONObject json=new JSONObject(sb.toString()).getJSONObject("current_weather");
                double temp=json.getDouble("temperature"); double wind=json.getDouble("windspeed"); int code=json.getInt("weathercode");
                String desc=code==0?"Ciel clair":code<3?"Partiellement nuageux":"Nuageux";
                requireActivity().runOnUiThread(()->{
                    ((TextView)meteoView.findViewById(R.id.txtMeteoTemp)).setText((int)temp+"°");
                    ((TextView)meteoView.findViewById(R.id.txtMeteoDesc)).setText(desc+" à Les Ponts-de-Cé");
                    ((TextView)meteoView.findViewById(R.id.txtMeteoWind)).setText("Vent "+wind+" km/h");
                });
            }catch(Exception e){
                requireActivity().runOnUiThread(()->((TextView)meteoView.findViewById(R.id.txtMeteoDesc)).setText("Météo indisponible - 24° ensoleillé"));
            }
        }).start();
    }

    View createSearch(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(24); card.setCardElevation(0); card.setCardBackgroundColor(0xFFEFEFF0);
        LinearLayout l=new LinearLayout(requireContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(16,14,16,14); l.setGravity(Gravity.CENTER_VERTICAL);
        TextView s=new TextView(requireContext()); s.setText("🔍 Rechercher apps + web..."); s.setTextColor(0xFF8E8E93); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); s.setTextSize(13);
        s.setOnClickListener(v->{ startActivity(new Intent(Intent.ACTION_WEB_SEARCH)); });
        TextView n=new TextView(requireContext()); n.setText("+ Nouvelle page"); n.setTextSize(11); n.setTextColor(0xFF8E8E93);
        l.addView(s); l.addView(n); card.addView(l); return card;
    }

    GradientDrawable circle(int color){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d; }

    View createDockClickable(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(22); card.setCardElevation(0); card.setCardBackgroundColor(0xFFFFFFFF);
        LinearLayout dock=new LinearLayout(requireContext()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setPadding(8,12,8,4); dock.setGravity(Gravity.CENTER);
        String[] names={"Téléphone","Messages","Maps","Photos","Réglages","Musique"};
        String[] pkgs={"com.android.dialer","com.google.android.apps.messaging","com.google.android.apps.maps","com.google.android.apps.photos","com.android.settings","com.google.android.apps.youtube.music"};
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
                if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL));
                else if(idx==4) startActivity(new Intent(Settings.ACTION_SETTINGS));
                else launchApp(pkgs[idx]);
            });
            dock.addView(col);
        }
        card.addView(dock); return card;
    }

    void launchApp(String pkg){
        try{
            PackageManager pm=requireContext().getPackageManager();
            Intent launch=pm.getLaunchIntentForPackage(pkg);
            if(launch!=null) startActivity(launch);
            else{
                // fallback générique
                if(pkg.equals("com.google.android.gm")) startActivity(pm.getLaunchIntentForPackage("com.google.android.gm"));
                else startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET));
            }
        }catch(Exception e){
            Toast.makeText(getContext(),"App non installée: "+pkg,Toast.LENGTH_SHORT).show();
        }
    }
}
