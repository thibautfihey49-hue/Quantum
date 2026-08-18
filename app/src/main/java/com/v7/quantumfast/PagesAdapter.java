package com.v7.quantumfast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
public class PagesAdapter extends FragmentStateAdapter {
    public PagesAdapter(FragmentActivity fa){super(fa);}
    @NonNull @Override public Fragment createFragment(int pos){
        if(pos==0) return new Page3Fragment(); // <-- DEVIENT PRINCIPALE
        if(pos==1) return new Page1Fragment(); // apps
        return new Page2Fragment(); // widgets drag
    }
    @Override public int getItemCount(){return 3;}
}
