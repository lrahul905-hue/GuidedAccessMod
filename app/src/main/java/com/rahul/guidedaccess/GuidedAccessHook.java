package com.rahul.guidedaccess;

import android.view.KeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GuidedAccessHook implements IXposedHookLoadPackage {

    // Ab IPC ki zarurat nahi, simple boolean perfect kaam karega
    private static boolean isGuidedAccessActive = false;
    private static boolean isVolUpPressed = false;
    private static boolean isVolDownPressed = false;
    private static long lastTriggerTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        // Kewal System Framework ('android') ko target kar rahe hain
        if (!lpparam.packageName.equals("android")) return;

        // ==========================================
        // 1. TRIGGER & GESTURE BLOCKER (PhoneWindowManager)
        // ==========================================
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

                    // Volume Keys Tracking
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        isVolUpPressed = (action == KeyEvent.ACTION_DOWN);
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        isVolDownPressed = (action == KeyEvent.ACTION_DOWN);
                    }

                    // Trigger Logic
                    if (isVolUpPressed && isVolDownPressed) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTriggerTime > 1000) { 
                            lastTriggerTime = currentTime;
                            isGuidedAccessActive = !isGuidedAccessActive; // Toggle mode
                            param.setResult(0); // Volume change hone se roko
                            return;
                        }
                    }

                    // Block Back Gesture & Recents button actions at framework level
                    if (isGuidedAccessActive) {
                        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                            param.setResult(0); // Event consume kar lo
                        }
                    }
                }
            }
        );

        // ==========================================
        // 2. TRANSIENT BARS BLOCKER (Edge Swipe Overlays)
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
                            param.setResult(null); // Bars popup hone se roko
                        }
                    }
                }
            );
        } catch (Throwable t) { }

        // ==========================================
        // 3. NOTIFICATION SHADE BLOCKER (StatusBarManagerService)
        // ==========================================
        try {
            // Block Notification Pull-down
            XposedHelpers.findAndHookMethod(
                "com.android.server.statusbar.StatusBarManagerService",
                lpparam.classLoader,
                "expandNotificationsPanel",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isGuidedAccessActive) {
                            param.setResult(null); 
                        }
                    }
                }
            );
            
            // Block Quick Settings Pull-down (Dual safety)
            XposedHelpers.findAndHookMethod(
                "com.android.server.statusbar.StatusBarManagerService",
                lpparam.classLoader,
                "expandSettingsPanel",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isGuidedAccessActive) {
                            param.setResult(null); 
                        }
                    }
                }
            );
        } catch (Throwable t) { }
    }
}
