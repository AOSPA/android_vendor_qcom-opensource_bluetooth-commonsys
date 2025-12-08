/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *            notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *            notice, this list of conditions and the following disclaimer in the
 *            documentation and/or other materials provided with the distribution.
 *        * Neither the name of The Linux Foundation nor
 *            the names of its contributors may be used to endorse or promote
 *            products derived from this software without specific prior written
 *            permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.bluetooth.bttestapp;

import org.codeaurora.bluetooth.bttestapp.hidd.HidDeviceActivity;
import org.codeaurora.bluetooth.bttestapp.lecoc.L2capCocActivity;
import org.codeaurora.bluetooth.bttestapp.R;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "BtTestMainActivity";
    private static boolean DBG = true;
    private final int PERMISSION_REQUEST = 10001;
    private boolean isBtPermssionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(DBG) Log.v(TAG, TAG);
        setContentView(R.layout.activity_main);
        checkBTPermissions();
    }

    private boolean checkBTPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!permissionsNeeded.isEmpty()) {
            Log.d(TAG, "Requesting " + permissionsNeeded.size() + " permissions");
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST);
            return false;
        }
        isBtPermssionGranted = true;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission denied: " + permissions[i]);
                    allGranted = false;
                } else {
                    Log.d(TAG, "Permission granted: " + permissions[i]);
                }
            }
            if (allGranted) {
                isBtPermssionGranted = true;
                Log.d(TAG, "All permissions granted successfully");
            } else {
                isBtPermssionGranted = false;
                Log.d(TAG, "Some permissions were denied");
            }
        }
    }

    public void showHidHost(View v) {
        if (!isBtPermssionGranted) {
            Toast.makeText(this, "Permissions not granted. Please grant all required permissions.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Cannot open HID Host - permissions not granted");
            return;
        }
        Log.i(TAG," showHidHost");
        startActivity(new Intent(this, HidTestApp.class));
    }

    public void showHidDevice(View v) {
        if (!isBtPermssionGranted) {
            Toast.makeText(this, "Permissions not granted. Please grant all required permissions.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Cannot open HID Device - permissions not granted");
            return;
        }
        Log.i(TAG," showHidDevice");
        startActivity(new Intent(this, HidDeviceActivity.class));
    }

    public void showL2capCoc(View v) {
        if (!isBtPermssionGranted) {
            Toast.makeText(this, "Permissions not granted. Please grant all required permissions.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Cannot open L2CAP CoC - permissions not granted");
            return;
        }
        Log.i(TAG," showL2capCoc");
        startActivity(new Intent(this, L2capCocActivity.class));
    }
}
