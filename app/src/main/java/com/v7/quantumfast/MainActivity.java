package com.v7.quantumfast;
import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.*;
import java.util.*;
import android.util.LruCache;
import android.content.SharedPreferences;
import android.graphics.drawable.*;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
public class MainActivity extends Activity {
    ViewGroup mainRoot; ImageView wallpaperView;
    TextView clockView, dateView;
    SharedPreferences prefs, glassPrefs;
    int getNavBarH(){ try{ android.content.res.Resources res=getResources(); int id=res.getIdentifier("navigation_bar_height","dimen","android"); if(id>0) return res.getDimensionPixelSize(id); }catch(Exception e){} return 0; }
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        try{ getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN); }catch(Exception e){}
        setContentView(R.layout.activity_main);
        try{
            mainRoot=findViewById(R.id.root); wallpaperView=findViewById(R.id.wallpaper);
            clockView=findViewById(R.id.clock); dateView=findViewById(R.id.dateInfo);
            prefs=getSharedPreferences("quantum", MODE_PRIVATE);
            glassPrefs=getSharedPreferences("glass", MODE_PRIVATE);
            Handler h=new Handler();
            Runnable r=new Runnable(){ public void run(){
                try{
                    if(clockView!=null) clockView.setText(new SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(new java.util.Date()));
                    if(dateView!=null) dateView.setText(new SimpleDateFormat("EEE d MMM", java.util.Locale.FRANCE).format(new java.util.Date()).toUpperCase());
                }catch(Exception e){}
                h.postDelayed(this,1000);
            }};
            h.post(r);
            View d=findViewById(R.id.dock); if(d!=null) d.setVisibility(View.VISIBLE);
            int[] ids={R.id.dPhone,R.id.dMsg,R.id.dExtra,R.id.dDrawer,R.id.dCam,R.id.dChrome};
            for(int id:ids){ View v=findViewById(id); if(v!=null){ v.setOnClickListener(x-> Toast.makeText(this,"Dock OK - no black after perm",0).show()); }}
            TextView menu=findViewById(R.id.btnMenu);
            if(menu!=null) menu.setOnClickListener(v->{
                new AlertDialog.Builder(this).setTitle("NO BLACK OK").setMessage("Si tu vois ça après autorisations, le noir est fixé. On remet les favs après.").setPositiveButton("OK",null).show();
            });
        }catch(Exception e){ Toast.makeText(this,"CRASH: "+e.getMessage(),1).show(); }
    }
    public void launchInstant(String pkg){ try{ Intent i=getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null) startActivity(i);}catch(Exception e){} }
}
