package com.v7.quantumfast;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.UserRecoverableAuthException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import org.json.JSONObject;

public class Page3Fragment extends Fragment {
    View pisView, meteoView, mailView;
    @Override public View onCreateView(LayoutInflater inf, ViewGroup p, Bundle b){
        View root=inf.inflate(R.layout.fragment_page3,p,false);
        LinearLayout container=root.findViewById(R.id.widgetContainer);
        View fam=inf.inflate(R.layout.widget_family, container, false); fam.setOnClickListener(v->openFamilyLink()); container.addView(fam);
        pisView=inf.inflate(R.layout.widget_piscine, container, false); container.addView(pisView); updatePiscineReal();
        meteoView=inf.inflate(R.layout.widget_meteo, container, false); container.addView(meteoView); updateMeteoReal();
        mailView=inf.inflate(R.layout.widget_mail, container, false);
        mailView.setOnClickListener(v->{
            GoogleSignInAccount acc = GoogleSignIn.getLastSignedInAccount(getActivity());
            if(acc==null) ((MainActivity)getActivity()).signInGmail();
            else openGmail();
        });
        container.addView(mailView); updateGmailAuto();
        View emploi=inf.inflate(R.layout.widget_emploi, container, false); container.addView(emploi);
        LinearLayout dock=new LinearLayout(getActivity()); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setBackgroundColor(0xFFFFFFFF); dock.setPadding(8,20,8,20);
        String[] labels={"Tel","SMS","Maps","Photos","Regl","Music"};
        for(int i=0;i<labels.length;i++){final int idx=i; TextView tv=new TextView(getActivity()); tv.setText(labels[i]); tv.setPadding(20,20,20,20); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); tv.setGravity(Gravity.CENTER);
            tv.setOnClickListener(v->{try{if(idx==0) startActivity(new Intent(Intent.ACTION_DIAL)); else if(idx==4) startActivity(new Intent(Settings.ACTION_SETTINGS)); else launch(idx==1?"com.google.android.apps.messaging":idx==2?"com.google.android.apps.maps":idx==3?"com.google.android.apps.photos":"com.google.android.apps.youtube.music");}catch(Exception e){}}); dock.addView(tv);}
        container.addView(dock);
        return root;
    }
    @Override public void onResume(){ super.onResume(); updatePiscineReal(); updateMeteoReal(); updateGmailAuto(); }
    void openFamilyLink(){try{PackageManager pm=getActivity().getPackageManager(); for(String pkg:new String[]{"com.google.android.apps.kids.familylink","com.google.android.apps.kids.familylinkhelper"}){Intent it=pm.getLaunchIntentForPackage(pkg); if(it!=null){startActivity(it); return;}} startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://families.google.com")));}catch(Exception e){}}
    void openGmail(){try{Intent it=getActivity().getPackageManager().getLaunchIntentForPackage("com.google.android.gm"); if(it!=null) startActivity(it);}catch(Exception e){}}
    void updatePiscineReal(){try{Calendar c=Calendar.getInstance(); int m=c.get(Calendar.MONTH)+1; int dow=c.get(Calendar.DAY_OF_WEEK); int h=c.get(Calendar.HOUR_OF_DAY); boolean summer=m>=6&&m<=9; String hor; boolean open; if(!summer){hor="Fermée hors saison"; open=false;} else{if(dow==4){hor="12h-18h30"; open=h>=12&&h<20;} else if(dow==1||dow==7){hor="14h-18h30"; open=h>=14&&h<18;} else{hor="12h-18h30"; open=h>=12&&h<18;}} TextView th=pisView.findViewById(R.id.txtHoraires); if(th!=null) th.setText("Horaires aujourd'hui : "+hor); TextView st=pisView.findViewById(R.id.txtPiscineStatus); if(st!=null) st.setText(open?"Ouvert":"Fermé"); TextView af=pisView.findViewById(R.id.txtAffluence); if(af!=null) af.setText(open?(h<13?"Faible":h<16?"Moyenne":"Forte"):"Fermée");}catch(Exception e){}}
    void updateMeteoReal(){new Thread(()->{try{URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=47.3&longitude=-0.52&current_weather=true"); HttpURLConnection con=(HttpURLConnection)url.openConnection(); con.setConnectTimeout(4000); BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream())); StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null) sb.append(l); JSONObject cw=new JSONObject(sb.toString()).getJSONObject("current_weather"); double temp=cw.getDouble("temperature"); double wind=cw.getDouble("windspeed"); if(getActivity()==null) return; getActivity().runOnUiThread(()->{try{TextView t=meteoView.findViewById(R.id.txtMeteoTemp); if(t!=null) t.setText((int)temp+"°"); TextView d=meteoView.findViewById(R.id.txtMeteoDesc); if(d!=null) d.setText("Les Ponts-de-Cé - réel Open-Meteo"); TextView w=meteoView.findViewById(R.id.txtMeteoWind); if(w!=null) w.setText("Vent "+wind+" km/h - Bassin 27°");}catch(Exception e){}});}catch(Exception e){}}).start();}
    void updateGmailAuto(){
        new Thread(()->{
            try{
                if(getActivity()==null) return;
                GoogleSignInAccount acc = GoogleSignIn.getLastSignedInAccount(getActivity());
                if(acc==null){
                    getActivity().runOnUiThread(()->{ TextView cnt=mailView.findViewById(R.id.txtMailCount); if(cnt!=null) cnt.setText("Connecter"); TextView t1=mailView.findViewById(R.id.txtMail1); if(t1!=null) t1.setText("• Clique pour connecter Gmail auto-sync"); });
                    return;
                }
                String email = acc.getEmail();
                getActivity().runOnUiThread(()->{ TextView cnt=mailView.findViewById(R.id.txtMailCount); if(cnt!=null) cnt.setText("Sync..."); });
                int unread = GmailHelper.fetchUnreadCount(getActivity(), email);
                List<GmailHelper.MailItem> mails = GmailHelper.fetchInbox(getActivity(), email);
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    TextView cnt=mailView.findViewById(R.id.txtMailCount); if(cnt!=null) cnt.setText(unread>0?unread+" non lus":"0 non lu");
                    if(mails.size()>0){ TextView t1=mailView.findViewById(R.id.txtMail1); if(t1!=null) t1.setText("• "+mails.get(0).subject); TextView t2=mailView.findViewById(R.id.txtMail2); if(t2!=null) t2.setText("De: "+mails.get(0).from); }
                    if(mails.size()>1){ TextView t3=mailView.findViewById(R.id.txtMail3); if(t3!=null) t3.setText("• "+mails.get(1).subject); }
                });
            }catch(UserRecoverableAuthException e){
                if(getActivity()!=null) getActivity().startActivityForResult(e.getIntent(), MainActivity.RC_AUTH);
            }catch(Exception e){
                e.printStackTrace();
                if(getActivity()==null) return;
                getActivity().runOnUiThread(()->{
                    TextView cnt=mailView.findViewById(R.id.txtMailCount); if(cnt!=null) cnt.setText("Erreur");
                    TextView t1=mailView.findViewById(R.id.txtMail1); if(t1!=null) t1.setText(e.getMessage());
                });
            }
        }).start();
    }
    void launch(String pkg){try{PackageManager pm=getActivity().getPackageManager(); Intent it=pm.getLaunchIntentForPackage(pkg); if(it!=null) startActivity(it);}catch(Exception e){}}
}
