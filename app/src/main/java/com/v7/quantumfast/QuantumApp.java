package com.v7.quantumfast;
import android.app.Application;
public class QuantumApp extends Application {
    @Override
    public void onCreate(){
        super.onCreate();
        try{
            // tout ton ancien code était ici et crashait
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
