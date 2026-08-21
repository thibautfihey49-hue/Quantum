package com.v7.quantumfast;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Intent;
import android.view.KeyEvent;
public class RecentsHookService extends AccessibilityService {
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  try{
   if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
    String pkg = event.getPackageName()!=null?event.getPackageName().toString():"";
    String cls = event.getClassName()!=null?event.getClassName().toString():"";
    if(pkg.contains("systemui") && (cls.toLowerCase().contains("recents") || cls.toLowerCase().contains("recent"))){
     Intent i=new Intent(this, MainActivity.class);
     i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
     i.putExtra("showGrid",true);
     startActivity(i);
    }
   }
  }catch(Exception e){}
 }
 @Override public void onInterrupt(){}
 @Override protected boolean onKeyEvent(KeyEvent event){
  // garde le court si possible sur certains tel
  if(event.getKeyCode()==KeyEvent.KEYCODE_APP_SWITCH && event.getAction()==KeyEvent.ACTION_UP){
   Intent i=new Intent(this, MainActivity.class);
   i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   i.putExtra("showGrid",true);
   startActivity(i);
   return true;
  }
  return super.onKeyEvent(event);
 }
}
