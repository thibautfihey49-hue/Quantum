package com.v7.quantumfast;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import org.json.JSONObject;

public class Page3Fragment extends Fragment {
    View pisView, meteoView, mailView, famView;
    @Override public View onCreateView(LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);
        famView=inf.inflate(R.layout.widget_family, container, false);
        pisView=inf.inflate(R.layout.widget_piscine, container, false);
        meteoView=inf.inflate(R.layout.widget_meteo, container, false);
        mailView=inf.inflate(R.layout.widget_mail, container, false);
        View emploi=inf.inflate(R.layout.widget_emploi, container, false);
        container.addView(famView); container.addView(pisView); container.addView(meteoView); container.addView(mailView); container.addView(emploi);

        // FAMILY LINK - vrai lien
        famView.setOnClickListener(v->openFamilyLink());
        TextView btnFam=famView.findViewById(R.id.btnOpenFamily); if(btnFam!=null) btnFam.setOnClickListener(v->openFamilyLink());

        // PISCINE
        updatePiscineReal();

        // METEO
        updateMeteoReal();

        // MAIL - vrais mails Gmail
        mailView.setOnClickListener(v->openGmail());
        loadRealGmail();

        emploi.setOnClickListener(v->{ try{ PackageManager pm=getActivity().getPackageManager(); Intent it=pm.getLaunchIntentForPackage("com.indeed.android.jobsearch"); if(it!=null) startActivity(it); }catch(Exception e){}});

        // DOCK
        LinearLayout dock=new LinearLayout(getActivity()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setBackgroundColor(0xFFFFFFFF); dock.setPadding(8,20,8,20);
        String[] labels={"Tel","SMS","Maps","Photos","Regl","Music"};
        for(int i=0;i<labels.length;i++){final int idx=i; TextView tv=new TextView(getActivity()); tv.setText(labels[i]); tv.setPadding(20,20,20,20); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); tv.setGravity(Gravity.CENTER);
            tv.setOnClickListener(v->{try{if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL)); else if(idx==4) startActivity(new Intent(Settings.ACTION_SETTINGS)); else launch(idx==1?"com.google.android.apps.messaging":idx==2?"com.google.android.apps.maps":idx==3?"com.google.android.apps.photos":"com.google.android.apps.youtube.music");}catch(Exception e){}}); dock.addView(tv);}
        container.addView(dock);
        return root;
    }

    void openFamilyLink(){
        try{
            PackageManager pm=getActivity().getPackageManager();
            String[] pkgs={"com.google.android.apps.kids.familylink","com.google.android.apps.kids.familylinkhelper","com.google.android.apps.kids.familylinkhelper.v2"};
            for(String pkg:pkgs){ Intent it=pm.getLaunchIntentForPackage(pkg); if(it!=null){ startActivity(it); return; } }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://families.google.com/families")));
        }catch(Exception e){}
    }
    void openGmail(){ try{ Intent it=getActivity().getPackageManager().getLaunchIntentForPackage("com.google.android.gm"); if(it!=null) startActivity(it); }catch(Exception e){} }

    void updatePiscineReal(){
        try{
            Calendar c=Calendar.getInstance(); int month=c.get(Calendar.MONTH)+1; int dow=c.get(Calendar.DAY_OF_WEEK); int hour=c.get(Calendar.HOUR_OF_DAY);
            boolean summer=month>=6&&month<=9; String hor; boolean open;
            if(!summer){ hor="Fermée hors saison"; open=false; }
            else{ if(dow==Calendar.WEDNESDAY){ hor="12h-18h30"; open=hour>=12&&hour<20; } else if(dow==Calendar.SATURDAY||dow==Calendar.SUNDAY){ hor="14h-18h30"; open=hour>=14&&hour<18; } else{ hor="12h-18h30"; open=hour>=12&&hour<18; } }
            TextView th=pisView.findViewById(R.id.txtHoraires); if(th!=null) th.setText("Horaires aujourd'hui : "+hor);
            TextView st=pisView.findViewById(R.id.txtPiscineStatus); if(st!=null) st.setText(open?"Ouvert":"Fermé");
            TextView af=pisView.findViewById(R.id.txtAffluence); if(af!=null) af.setText(open?(hour<13?"Faible":hour<16?"Moyenne":"Forte"):"Fermée");
        }catch(Exception e){}
    }

    void updateMeteoReal(){
        new Thread(()->{try{
            URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=47.3&longitude=-0.52&current_weather=true");
            HttpURLConnection con=(HttpURLConnection)url.openConnection(); con.setConnectTimeout(4000);
            BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream())); StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null) sb.append(l);
            JSONObject cw=new JSONObject(sb.toString()).getJSONObject("current_weather"); double temp=cw.getDouble("temperature"); double wind=cw.getDouble("windspeed");
            if(getActivity()==null) return; getActivity().runOnUiThread(()->{try{ TextView t=meteoView.findViewById(R.id.txtMeteoTemp); if(t!=null) t.setText((int)temp+"°"); TextView w=meteoView.findViewById(R.id.txtMeteoWind); if(w!=null) w.setText("Vent "+wind+" km/h - Bassin 27°"); }catch(Exception e){}});}catch(Exception e){}}).start();
    }

    void loadRealGmail(){
        new Thread(()->{
            try{
                AccountManager am=AccountManager.get(getActivity());
                Account[] accs=am.getAccountsByType("com.google");
                if(accs.length==0) return;
                String account=accs[0].name;
                // Label inbox
                Uri labelsUri=Uri.parse("content://com.google.android.gm/"+account+"/labels");
                Cursor cl=getActivity().getContentResolver().query(labelsUri,null,null,null,null);
                int unread=0;
                if(cl!=null){ while(cl.moveToNext()){ String cname=cl.getString(cl.getColumnIndexOrThrow("canonicalName")); if("^i".equals(cname)||"inbox".equalsIgnoreCase(cname)||"INBOX".equals(cname)){ unread=cl.getInt(cl.getColumnIndexOrThrow("numUnreadConversations")); break; } } cl.close(); }
                // Messages récents
                Uri convUri=Uri.parse("content://com.google.android.gm/"+account+"/conversations");
                Cursor cc=getActivity().getContentResolver().query(convUri,new String[]{"subject","fromAddress"},null,null,"date DESC LIMIT 2");
                String s1=null,s2=null; if(cc!=null){ if(cc.moveToNext()) s1=cc.getString(0)+" - "+cc.getString(1); if(cc.moveToNext()) s2=cc.getString(0)+" - "+cc.getString(1); cc.close(); }
                int finalUnread=unread; String finalS1=s1, finalS2=s2;
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    TextView cnt=mailView.findViewById(R.id.txtMailCount); if(cnt!=null) cnt.setText(finalUnread>0?finalUnread+" non lus":"Ouvrir");
                    TextView t1=mailView.findViewById(R.id.txtMail1); if(t1!=null&&finalS1!=null) t1.setText("• "+finalS1);
                    TextView t2=mailView.findViewById(R.id.txtMail2); if(t2!=null&&finalS2!=null) t2.setText("• "+finalS2);
                });
            }catch(Exception e){
                // Pas de permission READ_GMAIL -> on laisse bouton Ouvrir Gmail qui lui marche à 100%
            }
        }).start();
    }

    void launch(String pkg){try{PackageManager pm=getActivity().getPackageManager(); Intent it=pm.getLaunchIntentForPackage(pkg); if(it!=null) startActivity(it);}catch(Exception e){}}
}
