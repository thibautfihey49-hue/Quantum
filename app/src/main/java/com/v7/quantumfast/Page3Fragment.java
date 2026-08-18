package com.v7.quantumfast;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
public class Page3Fragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);
        try{
            container.addView(inf.inflate(R.layout.widget_family, container, false));
            container.addView(inf.inflate(R.layout.widget_piscine, container, false));
            container.addView(inf.inflate(R.layout.widget_meteo, container, false));
            container.addView(inf.inflate(R.layout.widget_mail, container, false));
            container.addView(inf.inflate(R.layout.widget_emploi, container, false));

            TextView search=new TextView(getActivity());
            search.setText("🔍 Rechercher apps + web...");
            search.setPadding(32,32,32,32);
            search.setBackgroundColor(0xFFEFEFF0);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,16);
            search.setLayoutParams(lp);
            container.addView(search);

            LinearLayout dock=new LinearLayout(getActivity());
            dock.setOrientation(LinearLayout.HORIZONTAL);
            dock.setBackgroundColor(0xFFFFFFFF);
            dock.setPadding(8,20,8,20);
            String[] labels={"Tel","SMS","Maps","Photos","Regl","Music"};
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
                            PackageManager pm=getActivity().getPackageManager();
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
            TextView err=new TextView(getActivity()); err.setText("V7 loaded");
            container.addView(err);
        }
        return root;
    }
}
