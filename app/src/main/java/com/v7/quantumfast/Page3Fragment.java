package com.v7.quantumfast;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class Page3Fragment extends Fragment {
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);

        // 1. Family
        View fam=inf.inflate(R.layout.widget_family, container, false);
        container.addView(wrapDrag(fam));

        // 2. Piscine
        View pis=inf.inflate(R.layout.widget_piscine, container, false);
        container.addView(wrapDrag(pis));

        // 3. Search
        container.addView(wrapDrag(createSearch()));

        // 4. Dock
        container.addView(wrapDrag(createDock()));

        return root;
    }

    DragResizeLayout wrapDrag(View content){
        DragResizeLayout drag=new DragResizeLayout(getContext());
        drag.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        drag.setPadding(0,0,0,12);
        drag.addView(content);
        return drag;
    }

    View createSearch(){
        androidx.cardview.widget.CardView c=new androidx.cardview.widget.CardView(getContext());
        c.setRadius(24); c.setCardElevation(0); c.setCardBackgroundColor(0xFFEFEFF0);
        LinearLayout l=new LinearLayout(getContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(16,14,16,14); l.setGravity(Gravity.CENTER_VERTICAL);
        TextView s=new TextView(getContext()); s.setText("🔍 Rechercher apps + web..."); s.setTextColor(0xFF8E8E93); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); s.setTextSize(13);
        TextView n=new TextView(getContext()); n.setText("+ Nouvelle page"); n.setTextSize(11); n.setTextColor(0xFF8E8E93);
        l.addView(s); l.addView(n); c.addView(l); return c;
    }

    View createDock(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(getContext()); card.setRadius(22); card.setCardElevation(0); card.setCardBackgroundColor(0xFFFFFFFF);
        LinearLayout dock=new LinearLayout(getContext()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setPadding(8,12,8,4); dock.setGravity(Gravity.CENTER);
        String[] names={"Téléphone","Messages","Maps","Photos","Réglages","Musique"};
        String[] emojis={"📞","💬","📍","🌸","⚙️","🎵"};
        for(int i=0;i<names.length;i++){
            LinearLayout col=new LinearLayout(getContext()); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER); col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            TextView bg=new TextView(getContext()); bg.setText(emojis[i]); bg.setTextSize(20); bg.setGravity(Gravity.CENTER);
            bg.setBackgroundResource(i==0?R.drawable.bg_circle_green:i==1?R.drawable.bg_circle_blue:R.drawable.bg_circle_gray);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(48,48); bg.setLayoutParams(lp);
            TextView tv=new TextView(getContext()); tv.setText(names[i]); tv.setTextSize(9); tv.setGravity(Gravity.CENTER); tv.setPadding(0,4,0,0);
            col.addView(bg); col.addView(tv); dock.addView(col);
        }
        card.addView(dock); return card;
    }
}
