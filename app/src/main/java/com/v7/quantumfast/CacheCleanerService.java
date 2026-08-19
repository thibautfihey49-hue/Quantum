package com.v7.quantumfast;
import android.accessibilityservice.AccessibilityService; import android.view.accessibility.AccessibilityEvent; import android.view.accessibility.AccessibilityNodeInfo; import java.util.List;
public class CacheCleanerService extends AccessibilityService {
    public static boolean isCleaning=false; public static String currentPkg="";
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(!isCleaning) return;
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null) return;
        List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText("Vider le cache");
        if(nodes.isEmpty()) nodes=root.findAccessibilityNodeInfosByText("Clear cache");
        if(nodes.isEmpty()) nodes=root.findAccessibilityNodeInfosByText("EFFACER LE CACHE");
        for(AccessibilityNodeInfo n:nodes){
            if(n.isEnabled() && n.isClickable()){ n.performAction(AccessibilityNodeInfo.ACTION_CLICK); try{ Thread.sleep(600);}catch(Exception e){} isCleaning=false; performGlobalAction(GLOBAL_ACTION_BACK); return; }
            AccessibilityNodeInfo p=n.getParent(); while(p!=null){ if(p.isClickable()){ p.performAction(AccessibilityNodeInfo.ACTION_CLICK); try{ Thread.sleep(600);}catch(Exception e){} isCleaning=false; performGlobalAction(GLOBAL_ACTION_BACK); return; } p=p.getParent(); }
        }
    }
    @Override public void onInterrupt(){}
}
