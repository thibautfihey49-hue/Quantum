package com.v7.quantumfast;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class Page3Fragment extends Fragment {
    @Override public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);

        container.addView(wrapDrag(inf.inflate(R.layout.widget_family, container, false)));
        container.addView(wrapDrag(inf.inflate(R.layout.widget_piscine, container, false)));
        container.addView(wrapDrag(createSearch()));
        container.addView(wrapDrag(createDock()));
        return root;
    }

    DragResizeLayout wrapDrag(View content){
        DragResizeLayout drag=new DragResizeLayout(requireContext());
        drag.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        drag.setPadding(0,0,0,12);
        drag.addView(content);
        return drag;
    }

    View createSearch(){
        androidx.cardview.widget.CardView c=new androidx.cardview.widget.CardView(requireContext());
        c.setRadius(24); c.setCardElevation(0); c.setCardBackgroundColor(0xFFEFEFF0);
        LinearLayout l=new LinearLayout(requireContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(16,14,16,14); l.setGravity(Gravity.CENTER_VERTICAL);
        TextView s=new TextView(requireContext()); s.setText("🔍 Rechercher apps + web..."); s.setTextColor(0xFF8E8E93); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); s.setTextSize(13);
        TextView n=new TextView(requireContext()); n.setText("+ Nouvelle page"); n.setTextSize(11); n.setTextColor(0xFF8E8E93);
        l.addView(s); l.addView(n); c.addView(l); return c;
    }

    GradientDrawable circle(int color){
        GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d;
    }

    View createDock(){
        androidx.cardview.widget.CardView card=new androidx.cardview.widget.CardView(requireContext()); card.setRadius(22); card.setCardElevation(0); card.setCardBackgroundColor(0xFFFFFFFF);
        LinearLayout dock=new LinearLayout(requireContext()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setPadding(8,12,8,4); dock.setGravity(Gravity.CENTER);
        String[] names={"Téléphone","Messages","Maps","Photos","Réglages","Musique"};
        String[] emojis={"📞","💬","📍","🌸","⚙️","🎵"};
        int[] colors={0xFF4CAF50,0xFF2196F3,0xFF4CAF50,0xFFE91E63,0xFF9E9E9E,0xFF9E9E9E};
        for(int i=0;i<names.length;i++){
            LinearLayout col=new LinearLayout(requireContext()); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER); col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            TextView bg=new TextView(requireContext()); bg.setText(emojis[i]); bg.setTextSize(20); bg.setGravity(Gravity.CENTER);
            bg.setBackground(circle(colors[i]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(48,48); bg.setLayoutParams(lp);
            TextView tv=new TextView(requireContext()); tv.setText(names[i]); tv.setTextSize(9); tv.setGravity(Gravity.CENTER); tv.setPadding(0,4,0,0);
            col.addView(bg); col.addView(tv); dock.addView(col);
        }
        card.addView(dock); return card;
    }
}
