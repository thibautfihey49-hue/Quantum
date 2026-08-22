package com.v7.quantumfast;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("QUANTUM OK - QuantumApp fixé");
        tv.setTextSize(22);
        tv.setPadding(50,300,50,50);
        setContentView(tv);
    }
    public void launchInstant(String pkg){}
}
