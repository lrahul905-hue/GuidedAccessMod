package com.rahul.guidedaccess;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GuidedAccessHook implements IXposedHookLoadPackage {

    private static boolean isGuidedAccessActive = false;
    private static boolean isReceiverRegistered = false;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        // Kewal System Framework ('android') ko target karna hai
        if (!lpparam.packageName.equals("android")) return;

        XposedBridge.log("GuidedAccess [DEBUG]: Root IPC Listener Hook Started!");

        // ==========================================
        // 1. SIGNAL RECEIVER (Listens to the Floating Button Root Command)
        // ==========================================
        try {
            // WindowManagerService jab ready hota hai tab hum apna receiver register karte hain
            Class<?> wmsClass = XposedHelpers.findClass("com.android.server.wm.WindowManagerService", lpparam.classLoader);
            XposedBridge.hookAllMethods(wmsClass, "systemReady", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isReceiverRegistered) return;
                    
                    Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    IntentFilter filter = new IntentFilter("com.rahul.guidedaccess.TOGGLE");
                    
                    // Android 14/15/16 requires RECEIVER_EXPORTED flag (value 2)
                    mContext.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            isGuidedAccessActive = !isGuidedAccessActive;
                            XposedBridge.log("GuidedAccess [DEBUG]: Mode Toggled via Root Shell! Active = " + isGuidedAccessActive);
                        }
                    }, filter, 2); 
                    
                    isReceiverRegistered = true;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: Broadcast Receiver Failed! " + t.getMessage());
        }

        // ==========================================
        // 2. THE BGMI OVERLAY KILLER (Bottom Line / Edge Gestures)
        // ==========================================
        try {
            Class<?> sysGesturesClass = XposedHelpers.findClass("com.android.server.wm.SystemGesturesPointerEventListener", lpparam.classLoader);
            XposedBridge.hookAllMethods(sysGesturesClass, "onPointerEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        // Consumes the touch event so OS doesn't trigger the nav bar overlay
                        param.setResult(null); 
                    }
                }
            });
        } catch (Throwable t) {}

        // ==========================================
        // 3. TRANSIENT BARS BLOCKER (Backup Blocker)
        // ==========================================
        try {
            Class<?> insetsPolicyClass = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy", lpparam.classLoader);
            XposedBridge.hookAllMethods(insetsPolicyClass, "showTransient", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        param.setResult(null); 
                    }
                }
            });
        } catch (Throwable t) {}

        // ==========================================
        // 4. NOTIFICATION SHADE BLOCKER
        // ==========================================
        try {
            Class<?> statusBarClass = XposedHelpers.findClass("com.android.server.statusbar.StatusBarManagerService", lpparam.classLoader);
            XC_MethodHook shadeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        param.setResult(null); 
                    }
                }
            };
            XposedBridge.hookAllMethods(statusBarClass, "expandNotificationsPanel", shadeHook);
            XposedBridge.hookAllMethods(statusBarClass, "expandSettingsPanel", shadeHook);
        } catch (Throwable t) {}
    }
}
