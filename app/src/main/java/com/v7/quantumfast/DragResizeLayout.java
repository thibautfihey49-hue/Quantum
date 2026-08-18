package com.v7.quantumfast;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
public class DragResizeLayout extends FrameLayout {
    float dX,dY; boolean resizing=false;
    public DragResizeLayout(Context c){ super(c); }
    public DragResizeLayout(Context c, AttributeSet a){ super(c,a); }
    public DragResizeLayout(Context c, AttributeSet a, int s){ super(c,a,s); }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(getChildCount()==0) return false;
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:
                dX=e.getRawX()-getX(); dY=e.getRawY()-getY();
                resizing = e.getX() > getWidth()*0.75f && e.getY() > getHeight()*0.75f;
                return true;
            case MotionEvent.ACTION_MOVE:
                if(resizing){
                    int nw=(int)(e.getRawX()-getX());
                    int nh=(int)(e.getRawY()-getY());
                    if(nw>160 && nh>100){
                        ViewGroup.LayoutParams lp=getLayoutParams();
                        lp.width=nw; lp.height=nh;
                        setLayoutParams(lp);
                    }
                }else{
                    setX(e.getRawX()-dX); setY(e.getRawY()-dY);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }
}
