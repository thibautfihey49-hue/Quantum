package com.v7.quantumfast;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import java.util.Calendar;
import java.util.List;

public class Page3Fragment extends Fragment {
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);

        View fam=inf.inflate(R.layout.widget_family, container, false);
        View pis=inf.inflate(R.layout.widget_piscine, container, false);

        container.addView(wrapDrag(fam));
        container.addView(wrapDrag(pis));
        container.addView(wrapDrag(createSearch()));
        container.addView(wrapDrag(createDock()));

        updateFamilyReal(fam);
        updatePiscineReal(pis);

        // tap sur Family -> ouvre permission usage
        fam.setOnClickListener(v->{
            if(!hasUsagePermission()) startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        return root;
    }

    DragResizeLayout wrapDrag(View c){
        DragResizeLayout d=new DragResizeLayout(requireContext());
        d.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); d.setPadding(0,0,0,12); d.addView(c); return d;
    }

    boolean hasUsagePermission(){
        AppOpsManager appOps=(AppOpsManager)requireContext().getSystemService(Context.APP_OPS_SERVICE);
        int mode=appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().getPackageName());
        return mode==AppOpsManager.MODE_ALLOWED;
    }

    void updateFamilyReal(View fam){
        TextView txtL=fam.findViewById(R.id.txtLeonie);
        TextView txtM=fam.findViewById(R.id.txtMaxime);
        TextView badgeL=fam.findViewById(R.id.badgeLeonie);
        TextView badgeM=fam.findViewById(R.id.badgeMaxime);

        long todayMs=getTodayScreenTimeMs();
        int minutes=(int)(todayMs/60000);
        int h=minutes/60; int m=minutes%60;

        String timeStr=h+"h"+String.format("%02d",m);
        txtL.setText("Temps d'écran aujourd'hui : "+timeStr+" (tablette)");
        txtM.setText("Temps d'écran aujourd'hui : "+timeStr+" (ce téléphone)");

        // Limite exemple 1h30
        if(minutes>90){ badgeL.setText("Limité"); badgeL.setBackgroundColor(0xFFFFCCCB); }
        else{ badgeL.setText("OK"); badgeL.setBackgroundColor(0xFFC8E6C9); }

        if(minutes>180){ badgeM.setText("Limité"); }
        else{ badgeM.setText("OK"); }
    }

    long getTodayScreenTimeMs(){
        try{
            UsageStatsManager usm=(UsageStatsManager)requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar cal=Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0);
            long start=cal.getTimeInMillis(); long end=System.currentTimeMillis();
            List<UsageStats> stats=usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,start,end);
            long total=0; if(stats!=null) for(UsageStats s:stats) total+=s.getTotalTimeInForeground();
            return total;
        }catch(Exception e){ return 0; }
    }

    void updatePiscineReal(View pis){
        TextView status=pis.findViewById(R.id.txtPiscineStatus);
        TextView horaires=pis.findViewById(R.id.txtHoraires);
        TextView affLabel=pis.findViewById(R.id.txtAffluenceLabel);
        TextView aff=pis.findViewById(R.id.txtAffluence);

        Calendar c=Calendar.getInstance();
        int month=c.get(Calendar.MONTH)+1; // 1-12
        int dayOfWeek=c.get(Calendar.DAY_OF_WEEK); // 1=dimanche
        int hour=c.get(Calendar.HOUR_OF_DAY);

        boolean isSummer=month>=6 && month<=8;
        String horaireJour;
        boolean open=false;

        if(!isSummer){
            horaireJour="Fermée hors saison (ouverte mi-juin à début sept)"; open=false;
        }else{
            boolean isMer=dayOfWeek==Calendar.WEDNESDAY;
            boolean isWeekEnd=dayOfWeek==Calendar.SATURDAY || dayOfWeek==Calendar.SUNDAY;
            if(isMer){
                if(month<=7) horaireJour="10h-20h"; else horaireJour="12h-20h";
                open=hour>=10 && hour<20;
            }else if(isWeekEnd){
                horaireJour="14h-18h30"; open=hour>=14 && hour<18;
            }else{
                if(month==6) horaireJour="12h-14h + 16h-18h30"; else horaireJour="12h-18h30";
                open=(hour>=12 && hour<14) || (month==6 && hour>=16 && hour<18) || (month>6 && hour>=12 && hour<18);
            }
        }

        horaires.setText("Horaires aujourd'hui : "+horaireJour);
        status.setText(open?"Ouvert":"Fermé");
        status.setBackgroundColor(open?0xFFC8E6C9:0xFFFFCCCB);

        // Affluence réelle estimée selon heure
        String affStr; int color;
        if(!open){ affStr="Fermé"; color=0xFFE0E0E0; }
        else if(hour<11){ affStr="Faible"; color=0xFFC8E6C9; }
        else if(hour<14){ affStr="Moyenne"; color=0xFFFFF9C4; }
        else if(hour<17){ affStr="Forte"; color=0xFFFFCC80; }
        else{ affStr="Faible"; color=0xFFC8E6C9; }

        aff.setText(affStr); affLabel.setText("Affluence : "+affStr);
        aff.setBackgroundColor(color);
    }

    View createSearch(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(24); card.setCardElevation(0); card.setCardBackgroundColor(0xFFEFEFF0);
        LinearLayout l=new LinearLayout(requireContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(16,14,16,14); l.setGravity(Gravity.CENTER_VERTICAL);
        TextView s=new TextView(requireContext()); s.setText("🔍 Rechercher apps + web..."); s.setTextColor(0xFF8E8E93); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); s.setTextSize(13);
        TextView n=new TextView(requireContext()); n.setText("+ Nouvelle page"); n.setTextSize(11); n.setTextColor(0xFF8E8E93);
        l.addView(s); l.addView(n); card.addView(l); return card;
    }

    GradientDrawable circle(int color){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d; }

    View createDock(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(22); card.setCardElevation(0); card.setCardBackgroundColor(0xFFFFFFFF);
        LinearLayout dock=new LinearLayout(requireContext()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setPadding(8,12,8,4); dock.setGravity(Gravity.CENTER);
        String[] names={"Téléphone","Messages","Maps","Photos","Réglages","Musique"};
        String[] emojis={"📞","💬","📍","🌸","⚙️","🎵"};
        int[] colors={0xFF4CAF50,0xFF2196F3,0xFF4CAF50,0xFFE91E63,0xFF9E9E9E,0xFF9E9E9E};
        for(int i=0;i<names.length;i++){
            LinearLayout col=new LinearLayout(requireContext()); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER); col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            TextView bg=new TextView(requireContext()); bg.setText(emojis[i]); bg.setTextSize(20); bg.setGravity(Gravity.CENTER); bg.setBackground(circle(colors[i]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(48,48); bg.setLayoutParams(lp);
            TextView tv=new TextView(requireContext()); tv.setText(names[i]); tv.setTextSize(9); tv.setGravity(Gravity.CENTER); tv.setPadding(0,4,0,0);
            col.addView(bg); col.addView(tv); dock.addView(col);
        }
        card.addView(dock); return card;
    }
}
