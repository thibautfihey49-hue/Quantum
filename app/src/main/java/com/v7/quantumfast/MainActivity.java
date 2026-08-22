package com.v7.quantumfast;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;
import android.content.Intent;
import android.provider.Settings;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        TextView tv=new TextView(this);
        tv.setText("QUANTUM OK\n02:56\nSAM. 22 AOUT\n\nSi tu vois ça, le noir est réparé\nOn reconstruit après");
        tv.setTextSize(24);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(40,200,40,200);
        tv.setOnClickListener(v->{
            try{ startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }catch(Exception e){}
        });
        setContentView(tv);
    }
}
