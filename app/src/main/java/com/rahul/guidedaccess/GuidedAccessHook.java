package com.rahul.guidedaccess;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GuidedAccessHook implements IXposedHookLoadPackage {

    private static boolean isGuidedAccessActive = false;
    private static int triggerState = 0;
    private static long lastKeystrokeTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        if (!lpparam.packageName.equals("android")) return;

        XposedBridge.log("GuidedAccess [DEBUG]: AudioService & CheatCode Hook Started!");

        // ==========================================
        // 1. CHEAT CODE TRIGGER (Vol UP -> DOWN -> UP)
        // Target: AudioService
        // ==========================================
        try {
            Class<?> audioServiceClass = XposedHelpers.findClass("android.media.AudioService", lpparam.classLoader);
            
            XC_MethodHook volumeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof Integer) {
                        int direction = (Integer) param.args[0];
                        
                        // AudioManager.ADJUST_RAISE = 1, ADJUST_LOWER = -1
                        if (direction != 1 && direction != -1) return;

                        long currentTime = System.currentTimeMillis();
                        
                        // Agar buttons dabane me 1.5 seconds se zyada gap hua, sequence reset kar do
                        if (currentTime - lastKeystrokeTime > 1500) {
                            triggerState = 0; 
                        }
                        lastKeystrokeTime = currentTime;

                        // State Machine Logic
                        if (triggerState == 0 && direction == 1) {
                            triggerState = 1;
                            XposedBridge.log("GuidedAccess [DEBUG]: Sequence 1 (UP)");
                        } else if (triggerState == 1 && direction == -1) {
                            triggerState = 2;
                            XposedBridge.log("GuidedAccess [DEBUG]: Sequence 2 (DOWN)");
                        } else if (triggerState == 2 && direction == 1) {
                            triggerState = 0; // Sequence Complete
                            isGuidedAccessActive = !isGuidedAccessActive; // Toggle State
                            XposedBridge.log("GuidedAccess [DEBUG]: MODE TOGGLED! Is Active = " + isGuidedAccessActive);
                            
                            param.setResult(null); // Last volume up sound ko block kar do
                            return;
                        } else {
                            triggerState = 0; // Galat sequence hone par reset
                        }
                    }
                }
            };

            XposedBridge.hookAllMethods(audioServiceClass, "adjustSuggestedStreamVolume", volumeHook);
            XposedBridge.hookAllMethods(audioServiceClass, "adjustStreamVolume", volumeHook);
            
        } catch (Throwable t) {
            XposedBridge.log("GuidedAccess [ERROR]: AudioService Hook Failed! " + t.getMessage());
        }

        // ==========================================
        // 2. TRANSIENT BARS BLOCKER (Edge Swipe)
        // ==========================================
        try {
            Class<?> insetsPolicyClass = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy", lpparam.classLoader);
            XposedBridge.hookAllMethods(insetsPolicyClass, "showTransient", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        param.setResult(null); // Block popups
                    }
                }
            });
        } catch (Throwable t) {}

        // ==========================================
        // 3. NOTIFICATION SHADE BLOCKER
        // ==========================================
        try {
            Class<?> statusBarClass = XposedHelpers.findClass("com.android.server.statusbar.StatusBarManagerService", lpparam.classLoader);
            XC_MethodHook shadeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive) {
                        param.setResult(null); // Block shade
                    }
                }
            };
            XposedBridge.hookAllMethods(statusBarClass, "expandNotificationsPanel", shadeHook);
            XposedBridge.hookAllMethods(statusBarClass, "expandSettingsPanel", shadeHook);
        } catch (Throwable t) {}

        // ==========================================
        // 4. BACK & RECENTS GESTURE BLOCKER
        // ==========================================
        try {
            Class<?> pwmClass = XposedHelpers.findClass("com.android.server.policy.PhoneWindowManager", lpparam.classLoader);
            XposedBridge.hookAllMethods(pwmClass, "interceptKeyBeforeQueueing", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isGuidedAccessActive && param.args.length > 0 && param.args[0] instanceof android.view.KeyEvent) {
                        android.view.KeyEvent event = (android.view.KeyEvent) param.args[0];
                        int keyCode = event.getKeyCode();
                        
                        if (keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_APP_SWITCH) {
                            param.setResult(0); // Block navigation
                        }
                    }
                }
            });
        } catch (Throwable t) {}
    }
}
