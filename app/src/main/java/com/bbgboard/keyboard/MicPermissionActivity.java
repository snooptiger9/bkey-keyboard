package com.bbgboard.keyboard;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;

public class MicPermissionActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 91);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }
}
