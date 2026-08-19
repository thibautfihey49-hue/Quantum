package com.v7.quantumfast;
import android.content.Context; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import java.io.File; import java.io.FileOutputStream;
public class DiskIconCache {
    static File dir(Context c){ File d=new File(c.getCacheDir(),"icons30"); if(!d.exists()) d.mkdirs(); return d; }
    public static Bitmap get(Context c, String pkg){
        try{ File f=new File(dir(c), pkg.replace(".","_")+".png"); if(!f.exists()) return null;
             BitmapFactory.Options o=new BitmapFactory.Options(); o.inPreferredConfig=Bitmap.Config.RGB_565; o.inSampleSize=2;
             return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        }catch(Exception e){ return null; }
    }
    public static void put(Context c, String pkg, Bitmap bmp){
        try{ File f=new File(dir(c), pkg.replace(".","_")+".png"); FileOutputStream out=new FileOutputStream(f); bmp.compress(Bitmap.CompressFormat.PNG, 80, out); out.close(); }catch(Exception e){}
    }
    public static void clear(Context c){ try{ File[] fs=dir(c).listFiles(); if(fs!=null) for(File f:fs) f.delete(); }catch(Exception e){} }
}
