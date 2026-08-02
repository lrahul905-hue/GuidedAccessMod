package com.rahul.guidedaccess;

import android.view.KeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.lang.reflect.Method;

public class GuidedAccessHook implements IXposedHookLoadPackage {

    private static boolean isGuidedAccessActive = false;
    private static boolean isVolUpPressed = false;
    private static boolean isVolDownPressed = false;
    private static long lastTriggerTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        if (!lpparam.packageName.equals("android")) return;

        XposedBridge.log("GuidedAccess [DEBUG]: Universal Framework Hook Started!");

        // ==========================================
        // 1. TRIGGER & GESTURE BLOCKER (Universal Hook)
        // ==========================================
        try {
            Class<?> pwmClass = XposedHelpers.findClass("com.android.server.policy.PhoneWindowManager", lpparam.classLoader);
            
            XC_MethodHook keyHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    KeyEvent event = null;
                    
                    // Dynamically find KeyEvent in arguments (Bulletproof against OS updates)
                    for (Object arg : param.args) {
                        if (arg instanceof KeyEvent) {
                            event = (KeyEvent) arg;
                            break;
                        }
                    }
                    if (event == null) return;

                    int keyCode = event.getKeyCode();
                    int action = event.getAction();

                    // Track volume keys
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        isVolUpPressed = (action == KeyEvent.ACTION_DOWN);
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        isVolDownPressed = (action == KeyEvent.ACTION_DOWN);
                    }

                    // Get return type to consume events safely without crashing
                    Class<?> returnType = (param.method instanceof Method) ? ((Method) param.method).getReturnType() : null;

                    // Trigger Activation Logic
                    if (isVolUpPressed && isVolDownPressed) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTriggerTime > 1000) { 
                            lastTriggerTime = currentTime;
                            isGuidedAccessActive = !isGuidedAccessActive;
                            
                            XposedBridge.log("GuidedAccess [DEBUG]: MODE TOGGLED! Active = " + isGuidedAccessActive);
                            
                            // Consume the trigger event
                            if (returnType == long.class) param.setResult(-1L);
                            else if (returnType == int.class) param.setResult(0);
                            else param.setResult(null);
                            return;
                        }
                    }

                    // Block Navigation Gestures (Back / Recents)
                    if (isGuidedAccessActive) {
                        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                            XposedBridge.log("GuidedAccess [DEBUG]: Blocked Navigation Gesture");
                            if (returnType == long.class) param.setResult(-1L);
                            else if (returnType == int.class) param.setResult(0);
                            else param.setResult(null);
                        }
                    }
                }
            };

            // Hook both Queueing and Dispatching to guarantee we catch the keys
            XposedBridge.hookAllMethods(pwmClass, "interceptKeyBeforeQueueing", keyHook);
            XposedBridge.hookAllMethods(pwmClass, "interceptKeyBeforeDispatching", keyHook);
            
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: PWM Hook Failed! " + t.getMessage());
        }

        // ==========================================
        // 2. TRANSIENT BARS BLOCKER (Universal Hook)
        // ==========================================
        try {
            Class<?> insetsPolicyClass = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy", lpparam.classLoader);
            XposedBridge.hookAllMethods(insetsPolicyClass, "showTransient", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        XposedBridge.log("GuidedAccess [DEBUG]: Blocked Edge Swipe Bar");
                        param.setResult(null); 
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: InsetsPolicy Hook Failed! " + t.getMessage());
        }

        // ==========================================
        // 3. NOTIFICATION SHADE BLOCKER (Universal Hook)
        // ==========================================
        try {
            Class<?> statusBarClass = XposedHelpers.findClass("com.android.server.statusbar.StatusBarManagerService", lpparam.classLoader);
            
            XC_MethodHook shadeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        XposedBridge.log("GuidedAccess [DEBUG]: Blocked Notification Panel");
                        param.setResult(null); 
                    }
                }
            };

            XposedBridge.hookAllMethods(statusBarClass, "expandNotificationsPanel", shadeHook);
            XposedBridge.hookAllMethods(statusBarClass, "expandSettingsPanel", shadeHook);
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: StatusBar Hook Failed! " + t.getMessage());
        }
    }
}
