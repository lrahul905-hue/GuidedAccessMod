package com.rahul.guidedaccess;

import android.view.KeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GuidedAccessHook implements IXposedHookLoadPackage {

    private static boolean isGuidedAccessActive = false;
    private static boolean isVolUpPressed = false;
    private static boolean isVolDownPressed = false;
    private static long lastTriggerTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        // 1. Android System Hook (Volume Toggle & Transient Bars)
        if (lpparam.packageName.equals("android")) {
            
            // Trigger Toggle (Volume Up + Down)
            XposedHelpers.findAndHookMethod(
                "com.android.server.policy.PhoneWindowManager",
                lpparam.classLoader,
                "interceptKeyBeforeDispatching",
                "android.view.WindowManagerPolicy.WindowState",
                "android.view.KeyEvent",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        KeyEvent event = (KeyEvent) param.args[1];
                        int keyCode = event.getKeyCode();
                        int action = event.getAction();

                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            isVolUpPressed = (action == KeyEvent.ACTION_DOWN);
                        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            isVolDownPressed = (action == KeyEvent.ACTION_DOWN);
                        }

                        if (isVolUpPressed && isVolDownPressed) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastTriggerTime > 500) {
                                lastTriggerTime = currentTime;
                                isGuidedAccessActive = !isGuidedAccessActive;
                                param.setResult(-1L); // Consume events
                            }
                        }
                    }
                }
            );

            // Block Transient Bars (Edge Swipe in Games)
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
                                param.setResult(null); // Cancel showing bars
                            }
                        }
                    }
                );
            } catch (Throwable t) {
                // Ignore if method mapping slightly differs in Voltage OS
            }
        }

        // 2. System UI Hook (Notifications & Navigation Gestures)
        if (lpparam.packageName.equals("com.android.systemui")) {
            
            // Block Notification Shade
            try {
                XposedHelpers.findAndHookMethod(
                    "com.android.systemui.statusbar.CommandQueue",
                    lpparam.classLoader,
                    "disable",
                    int.class,
                    int.class,
                    int.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isGuidedAccessActive) {
                                int state1 = (int) param.args[1];
                                state1 |= 0x00010000; // DISABLE_EXPAND flag
                                param.args[1] = state1;
                            }
                        }
                    }
                );
            } catch (Throwable t) {}

            // Block Back Gestures
            try {
                XposedHelpers.findAndHookMethod(
                    "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler",
                    lpparam.classLoader,
                    "onInputEvent",
                    "android.view.InputEvent",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isGuidedAccessActive) {
                                param.setResult(null);
                            }
                        }
                    }
                );
            } catch (Throwable t) {}
        }
    }
}
