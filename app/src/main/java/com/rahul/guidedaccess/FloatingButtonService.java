package com.rahul.guidedaccess;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import java.io.DataOutputStream;

public class FloatingButtonService extends Service {
    private WindowManager windowManager;
    private Button floatingBtn;
    private boolean isActive = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // UI Design for Button
        floatingBtn = new Button(this);
        floatingBtn.setText("🎮 OFF");
        floatingBtn.setBackgroundColor(Color.parseColor("#80000000")); 
        floatingBtn.setTextColor(Color.WHITE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 100;
        params.y = 200;

        windowManager.addView(floatingBtn, params);

        floatingBtn.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        int diffX = (int) (event.getRawX() - initialTouchX);
                        int diffY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) isMoving = true;
                        params.x = initialX + diffX;
                        params.y = initialY + diffY;
                        windowManager.updateViewLayout(floatingBtn, params);
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        if (!isMoving) { 
                            isActive = !isActive;
                            floatingBtn.setText(isActive ? "🔴 ON" : "🎮 OFF");
                            floatingBtn.setBackgroundColor(isActive ? Color.parseColor("#CCFF0000") : Color.parseColor("#80000000"));
                            
                            // LEVEL 3.1 PRO: Send Broadcast via Root Shell
                            // Bypasses Android 16 Background Activity Limits perfectly
                            try {
                                Process p = Runtime.getRuntime().exec("su");
                                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                                // Send the precise toggle signal to our Xposed Framework hook
                                os.writeBytes("am broadcast -a com.rahul.guidedaccess.TOGGLE\n");
                                os.writeBytes("exit\n");
                                os.flush();
                                os.close();
                            } catch (Exception e) {
                                // Fallback if Root fails for some reason
                                Intent toggleIntent = new Intent("com.rahul.guidedaccess.TOGGLE");
                                sendBroadcast(toggleIntent);
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBtn != null) windowManager.removeView(floatingBtn);
    }
}
