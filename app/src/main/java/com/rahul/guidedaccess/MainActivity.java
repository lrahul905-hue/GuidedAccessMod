package com.rahul.guidedaccess;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check karega ki Floating Window permission hai ya nahi
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1);
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show();
        } else {
            // Permission milne par Floating Button start kar dega
            startService(new Intent(this, FloatingButtonService.class));
            Toast.makeText(this, "Gaming Button Activated!", Toast.LENGTH_SHORT).show();
            finish(); // App band karke background me chala jayega
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && Settings.canDrawOverlays(this)) {
            startService(new Intent(this, FloatingButtonService.class));
            finish();
        }
    }
}
