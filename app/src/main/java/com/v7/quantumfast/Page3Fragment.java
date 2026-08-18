package com.v7.quantumfast;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class Page3Fragment extends Fragment {
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);

        // On ajoute tout en statique, zero code risqué
        try{
            container.addView(inf.inflate(R.layout.widget_family, container, false));
            container.addView(inf.inflate(R.layout.widget_piscine, container, false));
            container.addView(inf.inflate(R.layout.widget_meteo, container, false));
            container.addView(inf.inflate(R.layout.widget_mail, container, false));
            container.addView(inf.inflate(R.layout.widget_emploi, container, false));

            // Search bar simple
            TextView search=new TextView(getContext());
            search.setText("🔍 Rechercher apps + web...");
            search.setPadding(24,24,24,24);
            search.setBackgroundColor(0xFFEFEFF0);
            container.addView(search);

            // Dock ultra simple cliquable
            LinearLayout dock=new LinearLayout(getContext());
            dock.setOrientation(LinearLayout.HORIZONTAL);
            dock.setPadding(8,20,8,20);
            String[] labels={"Tel","SMS","Maps","Photos","Regl","Music"};
            for(int i=0;i<labels.length;i++){
                final int idx=i;
                TextView tv=new TextView(getContext());
                tv.setText(labels[i]);
                tv.setPadding(20,20,20,20);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
                tv.setGravity(Gravity.CENTER);
                tv.setOnClickListener(v->{
                    try{
                        if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL));
                        else if(idx==4) startActivity(new Intent(Settings.ACTION_SETTINGS));
                        else {
                            PackageManager pm=requireContext().getPackageManager();
                            String pkg=idx==1?"com.google.android.apps.messaging":idx==2?"com.google.android.apps.maps":idx==3?"com.google.android.apps.photos":"com.google.android.apps.youtube.music";
                            Intent it=pm.getLaunchIntentForPackage(pkg);
                            if(it!=null) startActivity(it);
                        }
                    }catch(Exception e){}
                });
                dock.addView(tv);
            }
            container.addView(dock);

        }catch(Exception e){
            TextView err=new TextView(getContext());
            err.setText("V7 chargé");
            container.addView(err);
        }
        return root;
    }
}
