/*
 * Copyright (c) 2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * 
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 *
 */

package org.codeaurora.bluetooth.offload_testapp;

import android.app.Activity;
import android.Manifest;

import android.content.ServiceConnection;
import android.content.Intent;

import android.os.Bundle;
import android.os.IBinder;

import android.util.Log;

import java.lang.*;

import libcore.io.IoUtils;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import android.os.Build;
import android.bluetooth.BluetoothManager;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    public static int LOG_LEVEL = 6;

    private Context mAppContext = null;
    public static WakeLock wl;
    public static boolean wl_acquired = false;
    public static BluetoothManager mBluetoothManager;

    /* Flag indicating whether we have called bind on the service. */
    public static boolean isBound = false;

    /* Location permissions */
    private static final int PERMISSION_REQUEST = 2;

    public static SocketServer socServer;
    public static BleAppService appService = null;
    //Handler mainThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAppContext = getApplicationContext();

        if (savedInstanceState != null) {
            // Restore value of members from saved state
            wl_acquired = savedInstanceState.getBoolean("wl_acquired");
            Log.d(TAG, "on create savedInstance not null");
        } else {
            Log.d(TAG, "on create savedInstance null");
            PowerManager pm = (PowerManager)mAppContext.getSystemService(
                                              Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLock");

            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "mBluetoothManager is null");
                return;
            }

            /* Request for location access */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH,
                    },PERMISSION_REQUEST);
            }

            socServer = SocketServer.getInstance(mAppContext);
            Intent intent = new Intent(this, BleAppService.class);
            //bindService(intent, appServiceConnection, BIND_AUTO_CREATE);
            this.startService(intent);
        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                            int[] grantResults) {
        switch (requestCode) {
                case PERMISSION_REQUEST: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission granted!");
                } else {
                    Log.e(TAG, "Needs required permission");
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onSaveInstanceState");
        if(wl_acquired) {
            savedInstanceState.putBoolean("wl_acquired", true);
            Log.d(TAG, "onSaveInstanceState:wl acquired -true");
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState called");
        wl_acquired = savedInstanceState.getBoolean("wl_acquired");
    }

    @Override
    protected void onNewIntent (Intent intent){
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ContextHub.contextHubCleanup();
        Log.d(TAG, "onDestroy");
        /* if wakelock is acquired data tx/rx is going on,
           so don't stop SM */
        if(!wl_acquired) {
            if (isBound) {
                //unbindService(appServiceConnection);
                isBound = false;
            }
            Intent intent = new Intent(this, BleAppService.class);
            this.stopService(intent);

            /* Stop SocketServer */
            SocketServer.sendSocketData("Application is Closed... Please restart");
            socServer.closeSocketServer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    /* BleApp service */
    private ServiceConnection appServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            BleAppService.MyBinder binderT=(BleAppService.MyBinder) service;
            appService = binderT.getService();
            isBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            appService = null;
            isBound = false;
        }
    };
}
