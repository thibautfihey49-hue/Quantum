package com.v7.quantumfast;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("QUANTUM OK - si tu vois ça, MainActivity marche");
        tv.setTextSize(20);
        tv.setPadding(50,200,50,50);
        setContentView(tv);
    }
    public void launchInstant(String pkg){}
}
