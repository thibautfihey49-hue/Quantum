package com.v7.quantumfast;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class Page3Fragment extends Fragment {
    LinearLayout container;
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        container=root.findViewById(R.id.widgetContainer);

        // 21:09 header déjà dans layout
        // Widgets drag
        addDrag(inf.inflate(R.layout.widget_family, container, false));
        addDrag(inf.inflate(R.layout.widget_piscine, container, false));
        addDrag(createSearchBar());
        addDrag(createDock());

        return root;
    }

    void addDrag(View content){
        DragResizeLayout drag=new DragResizeLayout(getContext());
        drag.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        drag.setPadding(0,0,0,12);
        drag.addView(content);
        container.addView(drag);
    }

    View createSearchBar(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(getContext());
        card.setRadius(24); card.setCardElevation(2);
        LinearLayout l=new LinearLayout(getContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(20,16,20,16);
        TextView t=new TextView(getContext()); t.setText("🔍 Rechercher apps + web..."); t.setTextColor(0xFF888888); t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1));
        TextView plus=new TextView(getContext()); plus.setText("+ Nouvelle page"); plus.setTextSize(11);
        l.addView(t); l.addView(plus); card.addView(l);
        return card;
    }

    View createDock(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(getContext());
        card.setRadius(20); card.setCardElevation(0); card.setCardBackgroundColor(0xFFF5F5F5);
        LinearLayout dock=new LinearLayout(getContext()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setGravity(Gravity.CENTER); dock.setPadding(8,12,8,12);
        String[] names={"Téléphone","Messages","Maps","Photos","Réglages","Musique"};
        String[] icons={"📞","💬","🗺️","🌸","⚙️","🎵"};
        for(int i=0;i<names.length;i++){
            LinearLayout item=new LinearLayout(getContext()); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1));
            TextView ic=new TextView(getContext()); ic.setText(icons[i]); ic.setTextSize(22); ic.setGravity(Gravity.CENTER);
            TextView tv=new TextView(getContext()); tv.setText(names[i]); tv.setTextSize(9); tv.setGravity(Gravity.CENTER);
            item.addView(ic); item.addView(tv);
            final int idx=i;
            item.setOnClickListener(v->{
                try{
                    if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL));
                    else if(idx==1) startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.google.android.apps.messaging"));
                    else if(idx==2) startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps"));
                }catch(Exception e){}
            });
            dock.addView(item);
        }
        card.addView(dock);
        return card;
    }
}
