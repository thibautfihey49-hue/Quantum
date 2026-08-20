package com.v7.quantumfast;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
public class HuntrxIconHelper {
    public static Drawable getThemedIcon(Context ctx, String pkg, String appName){
        int size=(int)(96*ctx.getResources().getDisplayMetrics().density);
        Bitmap bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient grad=new LinearGradient(0,0,size,size,new int[]{Color.parseColor("#FFD4E7FF"),Color.parseColor("#FFC7B6FF")},null,Shader.TileMode.CLAMP);
        bg.setShader(grad);
        float r=size*0.28f;
        RectF rect=new RectF(0,0,size,size);
        c.drawRoundRect(rect,r,r,bg);
        Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(size*0.025f);
        stroke.setColor(Color.parseColor("#FFFFD87A"));
        c.drawRoundRect(rect,r,r,stroke);
        Paint sym=new Paint(Paint.ANTI_ALIAS_FLAG);
        sym.setColor(Color.WHITE);
        sym.setTextSize(size*0.5f);
        sym.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        sym.setShadowLayer(size*0.06f,0,0,Color.parseColor("#9B8EC4"));
        sym.setTextAlign(Paint.Align.CENTER);
        String txt="?";
        if(pkg.contains("dialer")||pkg.contains("phone")) txt="☎";
        else if(pkg.contains("mms")||pkg.contains("messag")) txt="💬";
        else if(pkg.contains("chrome")) txt="🌐";
        else if(pkg.contains("camera")) txt="📷";
        else if(pkg.contains("gmail")) txt="✉";
        else if(pkg.contains("youtube")) txt="▶";
        else if(pkg.equals("com.google.android.googlequicksearchbox")) txt="G";
        else if(appName!=null && appName.length()>0) txt=appName.substring(0,1).toUpperCase();
        c.drawText(txt,size/2f,size/2f+size*0.18f,sym);
        return new BitmapDrawable(ctx.getResources(),bmp);
    }
}
