package com.v7.quantumfast;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        ViewPager2 vp=findViewById(R.id.viewPager);
        vp.setAdapter(new PagesAdapter(this));
        vp.setCurrentItem(0,false); // Page3 = principale
        vp.setOffscreenPageLimit(3);
    }
}
