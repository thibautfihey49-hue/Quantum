package com.v7.quantumfast;
import android.app.Activity;
import android.os.Bundle;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        if(b==null){
            getFragmentManager().beginTransaction()
               .replace(R.id.mainContainer, new Page3Fragment())
               .commit();
        }
    }
}
