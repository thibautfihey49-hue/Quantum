package com.v7.quantumfast;
import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
public class RecentsHookService extends AccessibilityService {
 @Override public void onAccessibilityEvent(AccessibilityEvent e){}
 @Override public void onInterrupt(){}
 @Override protected boolean onKeyEvent(KeyEvent event){
  try{
   if(event.getKeyCode()==KeyEvent.KEYCODE_APP_SWITCH && event.getAction()==KeyEvent.ACTION_UP){
    Intent i=new Intent(this, MainActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    i.putExtra("showGrid",true);
    startActivity(i);
    return true; // bloque le carousel système, ouvre notre grille
   }
  }catch(Exception ex){}
  return super.onKeyEvent(event);
 }
}
