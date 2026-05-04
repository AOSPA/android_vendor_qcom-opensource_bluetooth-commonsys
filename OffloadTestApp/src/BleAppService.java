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
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 *
 */

package org.codeaurora.bluetooth.offload_testapp;

import android.app.ActivityManager;
import android.os.Build;
import android.widget.Toast;
import android.util.Log;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.os.SystemProperties;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.HandlerThread;
import android.os.Looper;

import java.lang.*;
import java.util.List;
import java.util.Set;

import libcore.io.IoUtils;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import androidx.core.app.NotificationCompat;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.NotificationChannel;
import android.app.NotificationManager;


public class BleAppService extends Service {
    private static final String TAG = "BleAppService";
    public static int LOG_LEVEL = 6;

    public static BluetoothAdapter bleAdapter = null;

    public static ThroughputStateMachine throughputSMClass = null;
    public static GattClient mgattclient = null;
    public static GattServer mgattserver = null;
    public static BleAppServiceMessageHandler msghandler = null;

    public Context mAppContext = null;
    public Looper mlooper;

    public static boolean stateMachinestarted = false;

    /* Flag indicating whether we have called bind on the service. */
    public static boolean boundA = false;
    public static  boolean boundS = false;

    public static boolean mReceiverRegistered = false;
    public static boolean mAdapterReceiverRegistered = false;
    public static boolean isServiceRunning = false;

    public static AdvertiserService mAdvertiseService = null;
    public static ScannerService mScannerService = null;

    /* Variable to keep track of calling source of scan
     (MainActivity or Gatt Client or Throughput SM) */
    public static int scan_called = 0;
    public static final int SCAN_CALLED_FROM_MAIN_ACTIVITY = 1;
    private static final int SCAN_CALLED_FROM_GATT_CLIENT = 2;
    private static final int SCAN_CALLED_FROM_THROUGHPUT_SM = 3;

    /* Main Activity Actions */
    public static final int MSG_MA_START_BLE_ADV = 0;
    public static final int MSG_MA_STOP_BLE_ADV = 1;
    public static final int MSG_MA_START_BLE_SCAN = 2;
    public static final int MSG_MA_STOP_BLE_SCAN = 3;
    public static final int MSG_MA_SCAN_DEV_FOUND = 4;
    public static final int MSG_MA_ADV_STARTED = 5;
    public static final int MSG_MA_ADV_STOPPED = 6;
    public static final int MSG_MA_GET_CONNECTED_DEVICES = 7;
    public static final int MSG_MA_START_BLE_PAIR = 8;
    public static final int MSG_MA_GET_PAIRED_DEVICES = 9;
    public static final int MSG_MA_START_BLE_UNPAIR = 10;
    public static final int MSG_MA_START_BLE_DISCONNECT = 11;
    public static final int MSG_MA_BLE_ENABLE_ADV = 12;
    public static final int MSG_MA_BLE_SET_ADV_DATA = 13;
    public static final int MSG_MA_BLE_SET_SCAN_RESP_DATA = 14;
    public static final int MSG_MA_BLE_SET_ADV_PARAM = 15;
    public static final int MSG_MA_BLE_SET_PERIODIC_ADV_PARAM = 16;
    public static final int MSG_MA_BLE_SET_PERIODIC_DATA = 17;
    public static final int MSG_MA_BLE_ENABLE_PERIODIC_ADV = 18;
    public static final int MSG_MA_BLE_GET_OWN_ADDRESS = 19;
    public static final int MSG_MA_BLE_ADV_ENABLED_EVENT = 20;
    public static final int MSG_MA_BLE_ADV_DATA_EVENT = 21;
    public static final int MSG_MA_BLE_SCAN_RESP_DATA_EVENT = 22;
    public static final int MSG_MA_BLE_ADV_PARAM_UPDATED_EVENT = 23;
    public static final int MSG_MA_BLE_PERIODIC_ADV_PARAM_UPDATED_EVENT = 24;
    public static final int MSG_MA_BLE_PERIODIC_ADV_DATA_EVENT = 25;
    public static final int MSG_MA_BLE_PERIODIC_ADV_ENABLED_EVENT = 26;
    public static final int MSG_MA_BLE_GET_OWN_ADDRESS_EVENT = 27;
    public static final int MSG_MA_MAX_ACTION_VALUE = MSG_MA_BLE_GET_OWN_ADDRESS_EVENT;

    /* Gatt Client Actions */
    public static final int MSG_GC_START_BLE_CONNECT = MSG_MA_MAX_ACTION_VALUE + 1;
    public static final int MSG_GC_START_BLE_CONN_UPDATE = MSG_MA_MAX_ACTION_VALUE + 2;
    public static final int MSG_GC_START_BLE_PHY_UPDATE = MSG_MA_MAX_ACTION_VALUE + 3;
    public static final int MSG_GC_START_BLE_GATT_CONFIGURE_MTU_SIZE = MSG_MA_MAX_ACTION_VALUE + 4;
    public static final int MSG_GC_START_BLE_READ_PHY = MSG_MA_MAX_ACTION_VALUE + 5;
    public static final int MSG_GC_START_BLE_GATT_DISCOVER = MSG_MA_MAX_ACTION_VALUE + 6;
    public static final int MSG_GC_START_BLE_GATT_REFRESH_SERVICES = MSG_MA_MAX_ACTION_VALUE + 7;
    public static final int MSG_GC_START_BLE_GATT_WRITE_READ_CHAR = MSG_MA_MAX_ACTION_VALUE + 8;
    public static final int MSG_GC_START_BLE_GATT_WRITE_READ_DESC = MSG_MA_MAX_ACTION_VALUE + 9;
    public static final int MSG_GC_REGISTER_BLE_GATT_NOTIFICATIONS = MSG_MA_MAX_ACTION_VALUE + 10;
    public static final int MSG_GC_DEREGISTER_BLE_GATT_NOTIFICATIONS = MSG_MA_MAX_ACTION_VALUE + 11;
    public static final int MSG_GC_START_BLE_GATT_RELIABLE_WRITE = MSG_MA_MAX_ACTION_VALUE + 12;
    public static final int MSG_GC_START_BLE_GATT_EXECUTE_ABORT_RELIABLE_WRITE = MSG_MA_MAX_ACTION_VALUE + 13;
    public static final int MSG_GC_START_BLE_GATT_DISC = MSG_MA_MAX_ACTION_VALUE + 14;
    public static final int MSG_GC_START_BLE_GATT_CANCEL_CONNECT = MSG_MA_MAX_ACTION_VALUE + 15;
    public static final int MSG_GC_BLE_GATT_REQ_CONN_PRIORITY = MSG_MA_MAX_ACTION_VALUE + 16;
    public static final int MSG_GC_START_BLE_CONNECT_TO_BDADDR = MSG_MA_MAX_ACTION_VALUE + 17;
    public static final int MSG_GC_START_BREDR_DISC = MSG_MA_MAX_ACTION_VALUE + 18;
    public static final int MSG_GC_READ_REMOTE_RSSI = MSG_MA_MAX_ACTION_VALUE + 19;
    public static final int MSG_GC_READ_CHAR_UUID = MSG_MA_MAX_ACTION_VALUE + 20;
    public static final int MSG_GC_DISC_SRVC_UUID = MSG_MA_MAX_ACTION_VALUE + 21;
    public static final int MSG_GC_START_BLE_GATT_UNREG = MSG_MA_MAX_ACTION_VALUE + 22;
    public static final int MSG_GC_START_BLE_LISTEN = MSG_MA_MAX_ACTION_VALUE + 23;
    public static final int MSG_GC_START_BLE_COC_WRITE = MSG_MA_MAX_ACTION_VALUE + 24;
    public static final int MSG_GC_START_BLE_COC_CONNECT = MSG_MA_MAX_ACTION_VALUE + 25;
    public static final int MSG_GC_START_BLE_COC_CLOSE = MSG_MA_MAX_ACTION_VALUE + 26;
    public static final int MSG_GC_START_BLE_COC_DATA_TX = MSG_MA_MAX_ACTION_VALUE + 27;
    public static final int MSG_GC_START_BLE_COC_OFFLOAD_CONNECT = MSG_MA_MAX_ACTION_VALUE + 28;
    public static final int MSG_GC_START_BLE_COC_OFFLOAD_LISTEN = MSG_MA_MAX_ACTION_VALUE + 29;
    public static final int MSG_GC_START_BLE_COC_SERVER_CLOSE = MSG_MA_MAX_ACTION_VALUE + 30;
    public static final int MSG_GC_MAX_ACTION_VALUE = MSG_GC_START_BLE_COC_SERVER_CLOSE;

    /* State Machine Actions */
    public static final int MSG_SM_START_BLE_CONNECT = MSG_GC_MAX_ACTION_VALUE + 1;
    public static final int MSG_SM_START_BLE_CONN_UPDATE = MSG_GC_MAX_ACTION_VALUE + 2;
    public static final int MSG_SM_START_BLE_PHY_UPDATE = MSG_GC_MAX_ACTION_VALUE + 3;
    public static final int MSG_SM_START_BLE_READ_PHY = MSG_GC_MAX_ACTION_VALUE + 4;
    public static final int MSG_SM_START_BLE_PAIR = MSG_GC_MAX_ACTION_VALUE + 5;
    public static final int MSG_SM_START_BLE_UNPAIR = MSG_GC_MAX_ACTION_VALUE + 6;
    public static final int MSG_SM_START_BLE_DATA_TX_TEST = MSG_GC_MAX_ACTION_VALUE + 7;
    public static final int MSG_SM_START_BLE_DATA_RX_TEST = MSG_GC_MAX_ACTION_VALUE + 8;
    public static final int MSG_SM_START_BLE_LATENCY_TEST = MSG_GC_MAX_ACTION_VALUE + 9;
    public static final int MSG_SM_START_BLE_GATT_DISC = MSG_GC_MAX_ACTION_VALUE + 10;
    public static final int MSG_SM_BLE_CONNECT_TO_BDADDR = MSG_GC_MAX_ACTION_VALUE + 11;
    public static final int MSG_SM_BLE_GATT_CANCEL_CONNECT = MSG_GC_MAX_ACTION_VALUE + 12;
    public static final int MSG_SM_START_REQ_CONN_PRIORITY = MSG_GC_MAX_ACTION_VALUE + 13;
    public static final int MSG_SM_START_BLE_TX_RX_TEST = MSG_GC_MAX_ACTION_VALUE + 14;
    public static final int MSG_SM_START_BLE_GATT_CONFIGURE_MTU_SIZE = MSG_GC_MAX_ACTION_VALUE + 15;
    public static final int MSG_SM_ADAPTER_STATE_CHANGED = MSG_GC_MAX_ACTION_VALUE + 16;
    public static final int MSG_SM_MAX_ACTION_VALUE = MSG_SM_ADAPTER_STATE_CHANGED;

    /* GATT Server Actions */
    public static final int MSG_GS_START_BLE_ADD_SERVICE = MSG_SM_MAX_ACTION_VALUE + 1;
    public static final int MSG_GS_START_BLE_REMOVE_SERVICE = MSG_SM_MAX_ACTION_VALUE + 2;
    public static final int MSG_GS_START_BLE_SET_PHY = MSG_SM_MAX_ACTION_VALUE + 3;
    public static final int MSG_GS_START_BLE_READ_PHY = MSG_SM_MAX_ACTION_VALUE + 4;
    public static final int MSG_GS_START_BLE_GET_SERVICES = MSG_SM_MAX_ACTION_VALUE + 5;
    public static final int MSG_GS_START_BLE_CLEAR_SERVICES = MSG_SM_MAX_ACTION_VALUE + 6;
    public static final int MSG_GS_START_BLE_CONNECT = MSG_SM_MAX_ACTION_VALUE + 7;
    public static final int MSG_GS_START_BLE_PHY_UPDATE = MSG_SM_MAX_ACTION_VALUE + 8;
    public static final int MSG_GS_START_BLE_DISCONNECT = MSG_SM_MAX_ACTION_VALUE + 9;
    public static final int MSG_GS_START_BLE_REGISTER = MSG_SM_MAX_ACTION_VALUE + 10;
    public static final int MSG_GS_START_BLE_DEREGISTER = MSG_SM_MAX_ACTION_VALUE + 11;
    public static final int MSG_GS_MAX_ACTION_VALUE = MSG_GS_START_BLE_DEREGISTER;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = this;
        isServiceRunning = true;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            String NOTIFICATION_CHANNEL_ID = "org.codeaurora.bluetooth.offload_testapp";
            String channelName = "Wearos TestApp Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(chan);
            Notification.Builder notificationBuilder = new Notification.Builder(this,NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                                        .setContentTitle("Wearos BLE Test App")
                                        .setPriority(NotificationManager.IMPORTANCE_MIN)
                                        .setCategory(Notification.CATEGORY_SERVICE)
                                        .build();
            startForeground(1337, notification);
         }else{

            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                            notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(this)
                            .setContentTitle("BLE Test App")
                            .setContentText("Running...!!!")
                            .setContentIntent(pendingIntent).build();

            startForeground(1337, notification);
         }

        /* Check and prompt to user if bluetooth in not turned on */
        if (!initAdapter()) {
            Log.e(TAG, "Bluetooth is not turned on");
            showMessage("Bluetooth is not turned ON");
            return;
        }

        // Bind to the "SCANNER" service
        Log.d("CREATION", "BINDING TO SCANNER ");
        Intent intent = new Intent(this, ScannerService.class);
        startService(intent);
        bindService(intent, mscannerConnection, Context.BIND_AUTO_CREATE);

        // Bind to the "ADVERTISER" service
        Log.d("CREATION", "BINDING TO ADVERTISER");
        Intent adv_intent = new Intent(this, AdvertiserService.class);
        startService(adv_intent);
        bindService(adv_intent, madvertiserConnection, Context.BIND_AUTO_CREATE);

        IntentFilter Pairingfilter = new IntentFilter();
        Pairingfilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        Pairingfilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        registerReceiver(mPairingReceiver, Pairingfilter);
        mReceiverRegistered = true;

        IntentFilter Adapterfilter = new IntentFilter();
        Adapterfilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mAdapterReceiver, Adapterfilter);
        mAdapterReceiverRegistered = true;

        HandlerThread Thread = new HandlerThread("BleAppServiceHandler");
        Thread.start();

        mlooper = Thread.getLooper();
        /* start main activity message handler */
        msghandler = new BleAppServiceMessageHandler(mAppContext, mlooper);

        /* start throughput state machine */
        start_testapp_tput_state_machine();

        /* start gatt client */
        mgattclient = new GattClient(mAppContext);

        /*start gatt server*/
        mgattserver = new GattServer(mAppContext);
    }

    /* function to check if bluetooth is turned on */
    private boolean initAdapter() {
        bleAdapter = MainActivity.mBluetoothManager.getAdapter();
        if (bleAdapter == null) {
            Log.e(TAG, "bleAdapter is null");
            return false;
        }
        boolean isBtEnabled = bleAdapter.isEnabled();
        if (!isBtEnabled) {
            return false;
        } else {
            Log.e(TAG, "bt is enabled");
            showMessage("Bluetooth is ON");
        }
        return true;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service :
            manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public final BroadcastReceiver mAdapterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                Log.d(TAG, "Previous state: " + previousState + " New state: " + newState);
                if((newState == BluetoothAdapter.STATE_OFF) ||
                   (newState == BluetoothAdapter.STATE_ON)) {
                    Message msg = BleAppService.msghandler.obtainMessage(
                                     BleAppService.MSG_SM_ADAPTER_STATE_CHANGED, newState);
                    BleAppService.msghandler.sendMessage(msg);
                }
            }
        }
    };

    public final BroadcastReceiver mPairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR);
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    BluetoothDevice bluetoothDevice = intent.getParcelableExtra(
                                                        BluetoothDevice.EXTRA_DEVICE);
                    StringBuilder PrintStr = new StringBuilder();
                    Log.i(TAG, "Device paired!!");
                    PrintStr.setLength(0);
                    PrintStr.append("Device is paired ");
                    PrintStr.append(bluetoothDevice.getAddress());
                    SocketServer.sendSocketData(PrintStr.toString());
                } else if (bondState == BluetoothDevice.BOND_NONE) {
                    BluetoothDevice bluetoothDevice = intent.getParcelableExtra(
                                                        BluetoothDevice.EXTRA_DEVICE);
                    StringBuilder PrintStr = new StringBuilder();
                    PrintStr.setLength(0);
                    PrintStr.append("Device Bond state changed to BOND_NONE ");
                    PrintStr.append(bluetoothDevice.getAddress());
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            } else if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                Log.i(TAG, "Incoming pairing request");
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        int variant = intent.getIntExtra(
                                        "android.bluetooth.device.extra.PAIRING_VARIANT",
                                        BluetoothDevice.ERROR);
                        int index = 0;
                        Log.i(TAG, "Pairing Variant " + variant);
                        BluetoothDevice bluetoothDevice = intent.getParcelableExtra(
                                                              BluetoothDevice.EXTRA_DEVICE);

                        /**
                         * Handle incoming pairing from connected devices only.
                         * getConnectedDevices() will get connected devices from
                         * both client and server
                         */
                        List<BluetoothDevice> connectedDevices = MainActivity.mBluetoothManager
                                .getConnectedDevices(BluetoothProfile.GATT);

                        for (index = 0; index < connectedDevices.size(); index++) {
                            if (connectedDevices.get(index).getAddress().equals(
                                    bluetoothDevice.getAddress())) {
                                break;
                            }
                        }

                        if ((connectedDevices.size() > 0)
                                && (index == connectedDevices.size())) {
                            Log.i(TAG, "Pairing device not found " + bluetoothDevice.getAddress());
                            return;
                        }

                        if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
                            bluetoothDevice.setPin(new byte[]{'1', '2', '3', '4', '5', '6'});
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error occured when trying to auto pair");
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "Service onStartCommand");
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        Log.d(TAG, "onDestroy");

        /* Unbind from the "ADVERTISER" service */
        if (boundA) {
            unbindService(madvertiserConnection);
            boundA = false;
        }
        if(isMyServiceRunning(ScannerService.class)) {
            stopService(new Intent(this, ScannerService.class));
        }
        /* Unbind from the "SCANNER" service */
        if (boundS) {
            unbindService(mscannerConnection);
            boundS = false;
        }
        if(isMyServiceRunning(AdvertiserService.class)) {
            stopService(new Intent(this, AdvertiserService.class));
        }
        /* Stopping throughput state machine */
        if (throughputSMClass.mStateMachine != null) {
            throughputSMClass.mStateMachine.doQuit();
        }

        /* Unregistering Paring Receiver */
        try{
            if(mReceiverRegistered) {
                unregisterReceiver(mPairingReceiver);
                mReceiverRegistered = false;
            }
        }catch(Exception E) {
            Log.d(TAG, "not able to unregister");
        }
        /* Unregistering Adapter Receiver */
        try{
           if(mAdapterReceiverRegistered) {
                unregisterReceiver(mAdapterReceiver);
                mAdapterReceiverRegistered = false;
            }
        }catch(Exception E) {
            Log.d(TAG, "not able to unregister Adapter receiver");
        }

        /* stop Gatt Client handler */
        mgattclient.cleanup();

        /* stop Ble App Service msg hdlr looper*/
        mlooper.quitSafely();
    }

    private final IBinder localBinder = new MyBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    public class MyBinder extends Binder {

        public BleAppService getService() {
            return BleAppService.this;

        }
    }

    /* function to start testapp throughput state machine */
    private void start_testapp_tput_state_machine() {
        if(stateMachinestarted == false){
            throughputSMClass = new ThroughputStateMachine(mAppContext);
            Log.i("TestAppThroughputStateMachine", "make");
            throughputSMClass.mStateMachine.start();
            stateMachinestarted = true;
        }
    }


    /* Advertiser connection service */
    private ServiceConnection madvertiserConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            AdvertiserService.LocalBinder binderA=(AdvertiserService.LocalBinder) service;
            mAdvertiseService = binderA.getService();
            boundA = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mAdvertiseService = null;
            boundA = false;
        }
    };

    private ServiceConnection mscannerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the scanner service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a scanner, so here we get a client-side
            // representation of that from the raw IBinder object.
            ScannerService.LocalBinder binderS = (ScannerService.LocalBinder) service;
            mScannerService = binderS.getService();
            boundS = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mScannerService = null;
            boundS = false;
        }
    };

    /* function to print the message on display */
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /* Main Activity Message Handler */
    public class BleAppServiceMessageHandler extends Handler {
        Context mMsgContext;
        private static final String TAG = "BleAppServiceMessageHandler";

        int operation_request;
        Message msg;
        StringBuilder PrintStr = new StringBuilder();

        public BleAppServiceMessageHandler(Context contxt, Looper looper) {
            super(looper);
            mMsgContext = contxt;
            if(BleAppService.LOG_LEVEL >= 2)
                Log.d(TAG, "BleAppServiceMessageHandler");
        }

        @Override
        public void handleMessage(Message message) {
            if (BleAppService.LOG_LEVEL >= 2)
                Log.d(TAG, "Handler(): msg = " + message.what);
            int status;
            ReadWriteOp RdWrClass;
            Scan scnObj;
            Scan initObj;
            int Mtu_Size;
            AddServices AddServ;
            PhyUpdate phyUpdateObj;
            ConnUpdate ConnUpdateObj;
            DataTx TxClass;
            String bdAddr;

            switch (message.what) {
                case MSG_MA_START_BLE_ADV:
                    Adv adv = (Adv)message.obj;
                    mAdvertiseService.startAdvertising(adv);
                    break;
                case MSG_MA_START_BLE_SCAN:
                    scnObj = (Scan)message.obj;
                    scan_called = SCAN_CALLED_FROM_MAIN_ACTIVITY;
                    mScannerService.set_scan_parameters(scnObj);
                    break;
                case MSG_MA_STOP_BLE_ADV:
                    int advId = (int)message.obj;
                    mAdvertiseService.stopAdvertising(advId);
                    break;
                case MSG_MA_BLE_ENABLE_ADV:
                    EnableAdv Enadv = (EnableAdv)message.obj;
                    mAdvertiseService.enableAdvSet(Enadv);
                    break;
                case MSG_MA_BLE_SET_ADV_DATA:
                    AdvDataInfo advdata = (AdvDataInfo)message.obj;
                    mAdvertiseService.setAdverData(advdata);
                    break;
                case MSG_MA_BLE_SET_SCAN_RESP_DATA:
                    AdvDataInfo scanrespdata = (AdvDataInfo)message.obj;
                    mAdvertiseService.setScanData(scanrespdata);
                    break;
                case MSG_MA_BLE_SET_ADV_PARAM:
                    SetAdvParam advparam = (SetAdvParam)message.obj;
                    mAdvertiseService.setAdvParam(advparam);
                    break;
                case MSG_MA_BLE_SET_PERIODIC_ADV_PARAM:
                    SetPerAdvParam peradvparam = (SetPerAdvParam)message.obj;
                    mAdvertiseService.setPeriAdvParam(peradvparam);
                    break;
                case MSG_MA_BLE_SET_PERIODIC_DATA:
                    SetPerAdvData peradvdata = (SetPerAdvData)message.obj;
                    mAdvertiseService.setPeriAdvData(peradvdata);
                    break;
                case MSG_MA_BLE_ENABLE_PERIODIC_ADV:
                    EnablePerAdv enperadv = (EnablePerAdv)message.obj;
                    mAdvertiseService.EnablePeriAdv(enperadv);
                    break;
                case MSG_MA_BLE_GET_OWN_ADDRESS:
                    int advId1 = (int)message.obj;
                    mAdvertiseService.getOwnaddset(advId1);
                    break;
                case MSG_MA_STOP_BLE_SCAN:
                    scan_called = 0;
                    Log.d(TAG, "scan stop(main activity)");
                    mScannerService.stopScan();
                    break;
                case MSG_MA_SCAN_DEV_FOUND:
                    Log.d(TAG, "scan dev found(main activity)");
                    int primaryphy = (int)message.arg1;
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    processScanCb(device, primaryphy);
                    break;
                case MSG_MA_ADV_STARTED:
                    PrintStr.setLength(0);
                    String enableId = (String) message.obj;
                    PrintStr.append("Advertising started! Instance Id:");
                    PrintStr.append(enableId);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_ADV_STOPPED:
                    PrintStr.setLength(0);
                    String disableId = (String) message.obj;
                    PrintStr.append("Advertising stopped for instance Id:");
                    PrintStr.append(disableId);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_ADV_ENABLED_EVENT:
                    PrintStr.setLength(0);
                    String instId = (String) message.obj;
                    PrintStr.append("Advertising set Enabled Event instance Id:");
                    PrintStr.append(instId);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_ADV_DATA_EVENT:
                    PrintStr.setLength(0);
                    String instId1 = (String) message.obj;
                    PrintStr.append("Advertising Data Event instance Id:");
                    PrintStr.append(instId1);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_SCAN_RESP_DATA_EVENT:
                    PrintStr.setLength(0);
                    String instId2 = (String) message.obj;
                    PrintStr.append("Scan Resp Data Event instance Id:");
                    PrintStr.append(instId2);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_ADV_PARAM_UPDATED_EVENT:
                    PrintStr.setLength(0);
                    String instId3 = (String) message.obj;
                    PrintStr.append("Advertising Parameters Updated Event instance Id:");
                    PrintStr.append(instId3);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_PERIODIC_ADV_PARAM_UPDATED_EVENT:
                    PrintStr.setLength(0);
                    String instId4 = (String) message.obj;
                    PrintStr.append("Periodic Adv Param Updated Event instance Id:");
                    PrintStr.append(instId4);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_PERIODIC_ADV_DATA_EVENT:
                    PrintStr.setLength(0);
                    String instId5 = (String) message.obj;
                    PrintStr.append("Periodic Adv Data Event instance Id:");
                    PrintStr.append(instId5);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_PERIODIC_ADV_ENABLED_EVENT:
                    PrintStr.setLength(0);
                    String instId6 = (String) message.obj;
                    PrintStr.append("Periodic Advertising Enable Event instance Id:");
                    PrintStr.append(instId6);
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_BLE_GET_OWN_ADDRESS_EVENT:
                    PrintStr.setLength(0);
                    bdAddr = (String) message.obj;
                    PrintStr.append("Get Own Address Event: ");
                    PrintStr.append(bdAddr);
                    PrintStr.append("  ");
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_MA_GET_CONNECTED_DEVICES:
                    processGetConnectedDevices();
                    break;
                case MSG_MA_START_BLE_PAIR:
                    bdAddr = (String) message.obj;
                    processPairRequest(bdAddr);
                    break;
                case MSG_MA_GET_PAIRED_DEVICES:
                    processGetBondedDevices();
                    break;
                case MSG_MA_START_BLE_UNPAIR:
                    bdAddr = (String) message.obj;
                    processUnPairRequest(bdAddr);
                    break;
                case MSG_MA_START_BLE_DISCONNECT:
                    bdAddr = (String) message.obj;
                    processDisconnectRequest(bdAddr);
                    break;
                case MSG_GC_START_BLE_CONNECT:
                    scnObj = (Scan) message.obj;
                    scan_called = SCAN_CALLED_FROM_GATT_CLIENT;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_CONNECT, scnObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_CONNECT_TO_BDADDR:
                    initObj = (Scan) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_CONNECT_TO_BDADDR, initObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BREDR_DISC:
                    bdAddr = (String) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BREDR_DISC, bdAddr);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_READ_REMOTE_RSSI:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_READ_REMOTE_RSSI, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_READ_CHAR_UUID:
                    RdWrClass = (ReadWriteOp) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_READ_CHAR_UUID, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_DISC_SRVC_UUID:
                    RdWrClass = (ReadWriteOp) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_DISC_SRVC_UUID, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_CANCEL_CONNECT:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                            mgattclient.MSG_START_CANCEL_CONNECT, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_CONN_UPDATE:
                    ConnUpdateObj = (ConnUpdate) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_CONN_UPDATE, ConnUpdateObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_PHY_UPDATE:
                    phyUpdateObj = (PhyUpdate) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_PHY_UPDATE, phyUpdateObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_CONFIGURE_MTU_SIZE:
                    Mtu_Size = (int) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_GATT_CONFIGURE_MTU_SIZE, Mtu_Size);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_BLE_GATT_REQ_CONN_PRIORITY:
                    int conn_priority = (int) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_BLE_GATT_REQ_CONN_PRIORITY, conn_priority);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_READ_PHY:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_READ_PHY, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_DISCOVER:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_GATT_DISC, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_REFRESH_SERVICES:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                                        mgattclient.MSG_START_BLE_GATT_REFRESH_SERVICES, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_WRITE_READ_CHAR:
                    RdWrClass = (ReadWriteOp) message.obj;
                    Log.d(TAG, "MSG_GC_START_BLE_GATT_WRITE_READ_CHAR value = " + RdWrClass.Value);
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                                        mgattclient.MSG_START_BLE_GATT_WRITE_READ_CHAR, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_WRITE_READ_DESC:
                    RdWrClass = (ReadWriteOp) message.obj;
                    Log.d(TAG, "MSG_GC_START_BLE_GATT_WRITE_READ_DESC value = " + RdWrClass.Value);
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                                        mgattclient.MSG_START_BLE_GATT_WRITE_READ_DESC, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_REGISTER_BLE_GATT_NOTIFICATIONS:
                    RdWrClass = (ReadWriteOp) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_REGISTER_BLE_GATT_NOTIFICATIONS, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_DEREGISTER_BLE_GATT_NOTIFICATIONS:
                    RdWrClass = (ReadWriteOp) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_DEREGISTER_BLE_GATT_NOTIFICATIONS, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_RELIABLE_WRITE:
                    RdWrClass = (ReadWriteOp) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_GATT_RELIABLE_WRITE, RdWrClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_EXECUTE_ABORT_RELIABLE_WRITE:
                    int operation = (int) message.obj;
                    if(operation == 1) {
                        msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_GATT_EXECUTE_WRITE, null);
                        mgattclient.mGattClientHandler.sendMessage(msg);
                    } else {
                        msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_GATT_ABORT_RELIABLE_WRITE, null);
                        mgattclient.mGattClientHandler.sendMessage(msg);
                    }
                    break;
                case MSG_GC_START_BLE_GATT_DISC:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_GATT_DISCONNECT, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_GATT_UNREG:
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_GATT_UNREG, null);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_LISTEN:
                    boolean SecureFlag = (boolean) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_LISTEN, SecureFlag);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_CLOSE:
                    int pfd = (int) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_COC_CLOSE, pfd);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_SERVER_CLOSE:
                    int psm = (int) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_COC_SERVER_CLOSE, psm);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_WRITE:
                    TxClass = (DataTx) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_COC_WRITE, TxClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_CONNECT:
                    LecocConnect LecocConnObj = (LecocConnect) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                          mgattclient.MSG_START_BLE_COC_CONNECT, LecocConnObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_CONNECT:
                    scnObj = (Scan) message.obj;
                    scan_called = SCAN_CALLED_FROM_THROUGHPUT_SM;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                          throughputSMClass.mStateMachine.MSG_TA_SM_CONNECT, scnObj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_BLE_CONNECT_TO_BDADDR:
                    initObj = (Scan) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                              throughputSMClass.mStateMachine.MSG_TA_SM_CONNECT_TO_BDADDR, initObj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_ADAPTER_STATE_CHANGED:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                              throughputSMClass.mStateMachine.MSG_TA_SM_BT_ADAPTER_STATE_CHANGED, message.obj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    int state = (int) message.obj;
                    mAdvertiseService.adapterChangedEvent(state);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        mgattclient.setConnectionStatus(GattClient.BLE_STATE_DISCONNECTED);
                    }
                    break;
                case MSG_SM_BLE_GATT_CANCEL_CONNECT:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                            throughputSMClass.mStateMachine.MSG_TA_SM_CANCEL_CONNECT, null);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_CONN_UPDATE:
                    ConnUpdateObj = (ConnUpdate) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_CONN_UPDATE, ConnUpdateObj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_GATT_CONFIGURE_MTU_SIZE:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_CONFIGURE_MTU, message.obj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_PHY_UPDATE:
                    phyUpdateObj = (PhyUpdate) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_PHY_UPDATE, phyUpdateObj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_TX_RX_TEST:
                    DataTx DataTxRxClass = (DataTx) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                          throughputSMClass.mStateMachine.MSG_TA_SM_DATA_TX_RX_TEST, DataTxRxClass);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_REQ_CONN_PRIORITY:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_CONN_UPDATE, message.obj);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_READ_PHY:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_READ_PHY, null);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_PAIR:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_PAIR_DEV, null);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_UNPAIR:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_UNPAIR_DEV, null);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_DATA_TX_TEST:
                    DataTx DataTxClass = (DataTx) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_DATA_TX_TEST, DataTxClass);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_DATA_RX_TEST:
                    DataRx DataRxClass = (DataRx) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_DATA_RX_TEST, DataRxClass);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_LATENCY_TEST:
                    LatencyTest LatencyTestClass = (LatencyTest) message.obj;
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_LATENCY_TEST, LatencyTestClass);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_SM_START_BLE_GATT_DISC:
                    msg = throughputSMClass.mStateMachine.obtainMessage(
                    throughputSMClass.mStateMachine.MSG_TA_SM_DISCONNECT, null);
                    throughputSMClass.mStateMachine.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_ADD_SERVICE:
                    AddServ = (AddServices) message.obj;
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_ADD_SERVICE, AddServ);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_REMOVE_SERVICE:
                    String uuid=(String)message.obj;
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_REMOVE_SERVICE, uuid);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_CLEAR_SERVICES:
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_CLEAR_SERVICES, null);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_GET_SERVICES:
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_GET_SERVICES, null);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_PHY_UPDATE:
                    phyUpdateObj = (PhyUpdate) message.obj;
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                             mgattserver.MSG_START_BLE_PHY_UPDATE, phyUpdateObj);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_READ_PHY:
                    bdAddr = (String) message.obj;
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                              mgattserver.MSG_START_BLE_READ_PHY, bdAddr);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_REGISTER:
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_REGISTER, null);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_DEREGISTER:
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_DEREGISTER, null);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GS_START_BLE_DISCONNECT:
                    bdAddr = (String) message.obj;
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                            mgattserver.MSG_START_BLE_DISCONNECT, bdAddr);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_DATA_TX:
                    TxClass = (DataTx) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                             mgattclient.MSG_START_BLE_COC_DATA_TX, TxClass);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_OFFLOAD_CONNECT:
                    LecocOffloadConnect LecocOffloadConnObj = (LecocOffloadConnect) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                        mgattclient.MSG_START_BLE_COC_OFFLOAD_CONNECT, LecocOffloadConnObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                case MSG_GC_START_BLE_COC_OFFLOAD_LISTEN:
                    LecocOffloadListen LecocOffloadListenObj = (LecocOffloadListen) message.obj;
                    msg = mgattclient.mGattClientHandler.obtainMessage(
                        mgattclient.MSG_START_BLE_COC_OFFLOAD_LISTEN, LecocOffloadListenObj);
                    mgattclient.mGattClientHandler.sendMessage(msg);
                    break;
                default:
                    Log.e(TAG, "Unknown Operation");
                    break;
            }
        }

        private void processScanCb(BluetoothDevice device, int primaryphy) {
            Log.d(TAG, "processScanCb(main activity) primaryphy: "+scan_called +primaryphy);
            if(primaryphy == BluetoothDevice.PHY_LE_CODED){
               primaryphy = 4 ; //converstion connect to coded phy value = 4
               Log.d(TAG, "processScanCb after conversion primary phy: "+primaryphy);
            }
            if(scan_called == SCAN_CALLED_FROM_GATT_CLIENT){
                msg = mgattclient.mGattClientHandler.obtainMessage(
                            mgattclient.MSG_BLE_SCAN_DEV_FOUND, primaryphy, 0, device);
                mgattclient.mGattClientHandler.sendMessage(msg);
            } else if (scan_called == SCAN_CALLED_FROM_THROUGHPUT_SM){
                msg = throughputSMClass.mStateMachine.obtainMessage(
                        throughputSMClass.mStateMachine.MSG_TA_SM_DEV_FOUND, primaryphy, 0, device);
                throughputSMClass.mStateMachine.sendMessage(msg);
            } else {
               /* do nothing */
            }
        }

        private void processGetConnectedDevices() {
             /* All connections from client are also done with server */
             List<BluetoothDevice> connDevices =
                            MainActivity.mBluetoothManager.getConnectedDevices(
                            BluetoothProfile.GATT_SERVER);
             PrintStr.setLength(0);
             if (connDevices.size() != 0) {
                 PrintStr.append("Connected Device:");
                 for (int i = 0; i < connDevices.size(); i++)  {
                     PrintStr.append(connDevices.get(i).getAddress());
                     PrintStr.append("  ");
                 }
             } else {
                 PrintStr.append("No Connected Device");
             }
             SocketServer.sendSocketData(PrintStr.toString());
        }

        private void processPairRequest(String bdAddr) {
            BluetoothDevice mdevice = getDevice(bdAddr);
            if (mdevice != null) {
                if(mdevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                    Log.i(TAG, "Pairing! " + bdAddr);
                    if(!mdevice.createBond(BluetoothDevice.TRANSPORT_LE)) {
                        Log.i(TAG, "Couldn't start pairing");
                        PrintStr.setLength(0);
                        PrintStr.append("Pairing failed!");
                        SocketServer.sendSocketData(PrintStr.toString());
                    }
                } else {
                    Log.i(TAG, "Device already bonded");
                    PrintStr.setLength(0);
                    PrintStr.append("Device already bonded!");
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            } else {
                PrintStr.setLength(0);
                PrintStr.append("Device not in connected list");
                PrintStr.append(bdAddr);
                PrintStr.append("  ");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        private BluetoothDevice getDevice(String address) {
            BluetoothDevice mdevice = null;
            Log.i(TAG,"address: " + address);
            List<BluetoothDevice> remoteDevices =
                            MainActivity.mBluetoothManager.getConnectedDevices(
                            BluetoothProfile.GATT_SERVER);
            for (int i = 0; i < remoteDevices.size(); i++)  {
                 if (remoteDevices.get(i).getAddress().equals(address)) {
                     Log.i(TAG, "Found match");
                     mdevice = remoteDevices.get(i);
                     break;
                 }
            }
            return mdevice;
        }

        private void processGetBondedDevices() {
             Set<BluetoothDevice> pairedDevices =
                            bleAdapter.getBondedDevices();
             PrintStr.setLength(0);
             if (pairedDevices.size() != 0) {
                 PrintStr.append("Paired Device:");
                 for (BluetoothDevice mdevice: pairedDevices)  {
                     PrintStr.append("Device Address:  ");
                     PrintStr.append(mdevice.getAddress());
                     PrintStr.append("    Device name: ");
                     PrintStr.append(mdevice.getName());
                     PrintStr.append("\n");
                 }
             } else {
                 PrintStr.append("No Paired Devices");
             }
             SocketServer.sendSocketData(PrintStr.toString());
        }

        private void processUnPairRequest(String bdAddr) {
            boolean device_found = false;
            Set<BluetoothDevice> pairedDevices =
                            bleAdapter.getBondedDevices();
            for (BluetoothDevice mdevice: pairedDevices)  {
                if (mdevice.getAddress().equals(bdAddr)) {
                    device_found = true;
                    if(!mdevice.removeBond()) {
                        Log.i(TAG, "couldn't start unpairing");
                        PrintStr.setLength(0);
                        PrintStr.append("Unpairing failed!");
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    } else {
                        Log.i(TAG, "unpairing" + bdAddr);
                        break;
                    }
                }
            }
            if (!device_found) {
                PrintStr.setLength(0);
                PrintStr.append("Device not found in bonded list");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }


        private void processDisconnectRequest(String bdAddr){
            BluetoothDevice mdevice = getDevice(bdAddr);
            if (mdevice != null) {
                if (mgattclient.mDevice != null) {
                    if (mgattclient.mDevice.getAddress().equals(bdAddr)) {
                        msg = mgattclient.mGattClientHandler.obtainMessage(
                              mgattclient.MSG_START_BLE_GATT_DISCONNECT, null);
                        mgattclient.mGattClientHandler.sendMessage(msg);
                    }
                }
                if (mgattserver.connectedDevices.contains(mdevice)) {
                    msg = mgattserver.mGattServerHandler.obtainMessage(
                                mgattserver.MSG_START_BLE_DISCONNECT, bdAddr);
                    mgattserver.mGattServerHandler.sendMessage(msg);
                }
                if (throughputSMClass.mDevice != null) {
                    if (throughputSMClass.mDevice.getAddress().equals(bdAddr)) {
                        msg = throughputSMClass.mStateMachine.obtainMessage(
                            throughputSMClass.mStateMachine.MSG_TA_SM_DISCONNECT, null);
                        throughputSMClass.mStateMachine.sendMessage(msg);
                    }
                }
            } else {
                PrintStr.setLength(0);
                PrintStr.append("Device not in connected list");
                PrintStr.append(bdAddr);
                PrintStr.append("  ");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }
    }
}
