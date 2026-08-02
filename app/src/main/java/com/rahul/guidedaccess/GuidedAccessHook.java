package com.rahul.guidedaccess;

import android.view.KeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GuidedAccessHook implements IXposedHookLoadPackage {

    private static boolean isGuidedAccessActive = false;
    private static boolean isVolUpPressed = false;
    private static boolean isVolDownPressed = false;
    private static long lastTriggerTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        if (!lpparam.packageName.equals("android")) return;

        // Tracker 1: Module Loaded Successfully
        XposedBridge.log("GuidedAccess [DEBUG]: System Framework Hooked Successfully!");

        // ==========================================
        // 1. TRIGGER BLOCK (PhoneWindowManager)
        // ==========================================
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.policy.PhoneWindowManager",
                lpparam.classLoader,
                "interceptKeyBeforeQueueing",
                "android.view.KeyEvent",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        KeyEvent event = (KeyEvent) param.args[0];
                        int keyCode = event.getKeyCode();
                        int action = event.getAction();

                        // Track volume key presses
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            XposedBridge.log("GuidedAccess [DEBUG]: Key Registered - Code: " + keyCode + " Action: " + action);
                        }

                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            isVolUpPressed = (action == KeyEvent.ACTION_DOWN);
                        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            isVolDownPressed = (action == KeyEvent.ACTION_DOWN);
                        }

                        if (isVolUpPressed && isVolDownPressed) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastTriggerTime > 1000) { 
                                lastTriggerTime = currentTime;
                                isGuidedAccessActive = !isGuidedAccessActive;
                                
                                // Tracker 2: Did the trigger fire?
                                XposedBridge.log("GuidedAccess [DEBUG]: MODE TOGGLED! Is Active now? = " + isGuidedAccessActive);
                                
                                param.setResult(0); 
                                return;
                            }
                        }

                        if (isGuidedAccessActive) {
                            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                                param.setResult(0);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // Tracker 3: Did PhoneWindowManager fail?
            XposedBridge.log("GuidedAccess [ERROR]: PhoneWindowManager Hook Failed! " + t.getMessage());
        }

        // ==========================================
        // 2. TRANSIENT BARS BLOCKER
        // ==========================================
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.wm.InsetsPolicy",
                lpparam.classLoader,
                "showTransient",
                int.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isGuidedAccessActive) {
                            XposedBridge.log("GuidedAccess [DEBUG]: Blocked Transient Bars (Edge Swipe)");
                            param.setResult(null);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: InsetsPolicy Hook Failed! " + t.getMessage());
        }

        // ==========================================
        // 3. NOTIFICATION SHADE BLOCKER
        // ==========================================
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.statusbar.StatusBarManagerService",
                lpparam.classLoader,
                "expandNotificationsPanel",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isGuidedAccessActive) {
                            XposedBridge.log("GuidedAccess [DEBUG]: Blocked Notification Pull-down");
                            param.setResult(null); 
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: StatusBar Hook Failed! " + t.getMessage());
        }
    }
}
