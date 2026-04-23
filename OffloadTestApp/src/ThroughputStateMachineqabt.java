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
 */
 /* Changes from Qualcomm Technologies, Inc. are provided under the following license:
    Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 SPDX-License-Identifier: BSD-3-Clause-Clear
  */
package org.codeaurora.bluetooth.offload_testapp;

import android.util.Log;

import android.content.Context;

import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Message;

import java.util.UUID;
import java.util.Arrays;
import java.lang.*;
import java.io.*;

import libcore.io.IoUtils;

import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattDescriptor;

class ThroughputStateMachine {
    public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
                                 = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String TAG = "ThroughputStateMachine";
    public static int LOG_LEVEL = 6;

    public BleConnectionClass mBleConnect = null;
    private Context mcontext = null;
    public static TestAppThroughputStateMachine mStateMachine;
    public BluetoothDevice mDevice = null;

    /* Mutex required for writing characteristics and descriptors */
    private final Object write_char_mutex = new Object();
    private final Object write_desc_mutex = new Object();
    private final Object service_discovery_mutex = new Object();
    private final Object notification_mutex = new Object();

    public boolean gatt_discovery_done = false;
    public static boolean write_char_wait_signalled = false;
    public static boolean write_desc_wait_signalled = false;
    public static boolean notify_wait_signalled = false;

    /* Variable required for calculating DataRx Tput*/
    public static int num_of_notifications = 0;
    public static long rx_start_time_stamp = 0x0;
    public static long rx_end_time_stamp = 0x0;
    public static long rx_end_time_stamp1 = 0x0;
    public static long rx_end_time_stamp2 = 0x0;
    public static long pkt_cnt = 0x0;

    private static final int WRITE_DESC_MAX_RETRIES = 5;
    private static final int WRITE_DESC_TIME_TO_WAIT = 25; // milliseconds

    /* MTU size required for MTU exchange */
    public static final int MTU_SIZE_MIN = 23;
    public static final int MTU_SIZE_MAX = 512;
    public int mtu_size = MTU_SIZE_MIN;
    private int tx_rx_mtu_intr_size = 244;
    private char[] tx_rx_str = new char[tx_rx_mtu_intr_size];

    public static final int TRANSPORT_LE = 2;

    /* Variables to update connection interval before Data Tx */
    public static int connIntervalReq;
    public static final int CONN_INTERVAL_MIN = 6;
    public static final int CONN_INTERVAL_MIN_COEX = 16;

    /* Macros required for phy update */
    public static int txPhyReq = 1;
    public static int rxPhyReq = 1;
    public static int LE_CODED_PHY = 4;
    public static int ALL_PHY = 7;

    private static DataTx DataTxClass;
    private static DataTx DataTxRxClass;
    private static DataRx DataRxClass;
    private static LatencyTest LatencyTestClass;


    public ThroughputStateMachine(Context mcontext) {
        this.mcontext = mcontext;
        /* Initialize classes */
        mBleConnect = new BleConnectionClass(mcontext);
        mStateMachine = new TestAppThroughputStateMachine(mcontext);
    }

    /* Connection Class */
    public class BleConnectionClass {
        private static final String TAG = "BleConnectionClass";

        private BluetoothGatt mBluetoothGatt;
        private BluetoothGattService mService;
        private BluetoothGattCharacteristic mCharacteristic;
        private BluetoothGattCharacteristic mreadChar;
        private Context context;
        private int mState;
        private int GATT_SUCCESS = 0x00;

        PhyUpdate tmp_phy;
        Message msg;
        StringBuilder PrintStr = new StringBuilder();

        public BleConnectionClass(Context context) {
            this.context = context;
        }

        /**
         * GATT callbacks
         */
        private final BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Log.i(TAG, "onConnectionStateChange device :" + gatt.getDevice() +
                      " status :" + status + " newState :" + newState);
                mState = newState;
                int bondState = mDevice.getBondState();
                if (gatt.getDevice() == null || (status != GATT_SUCCESS)&&
                    (mStateMachine.getCurrentState() == mStateMachine.mTAConnectPending)) {
                    Log.e(TAG, "onConnectionStateChange:Unexpected error! mstate: " +  mState);
                    mStateMachine.sendMessage(
                                    TestAppThroughputStateMachine.MSG_TA_SM_DEV_FAILED_TO_CONNECT);
                    return;
                }
                if (status != GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "onConnectionStateChange:DISCONNECTED "
                            + " remoteDevice: " + gatt.getDevice().getAddress());
                    /*Send Message to SM */
                    mStateMachine.sendMessage(
                                    TestAppThroughputStateMachine.MSG_TA_SM_DEV_DISCONNECTED);
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "onConnectionStateChange:CONNECTED "
                            + " remoteDevice: " + gatt.getDevice().getAddress());
                    /*Send Message to SM*/
                    String name = (String) gatt.getDevice().getName();
                    msg = mStateMachine.obtainMessage(
                    mStateMachine.MSG_TA_SM_REM_DEV_CONNECTED, name);
                    mStateMachine.sendMessage(msg);
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                       Log.i(TAG, "Device paired");
                    }
                    if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                        Log.d(TAG, "starting discover services");
                    }
                    /* Remote is connected start service discovery */
                    gatt.discoverServices();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onService discovery success");
                    gatt_discovery_done = true;
                    /* Release service discovery mutex */
                    synchronized (service_discovery_mutex) {
                        service_discovery_mutex.notify();
                    }
                } else {
                    Log.d(TAG, "onServicesDiscovered received: " + status);
                    PrintStr.setLength(0);
                    PrintStr.append("Service discovery failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy,
                                        int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "on Phy updated:"
                         + " tx phy " + txPhy + " rx phy " + rxPhy +" status " + status);
                    if((txPhyReq == txPhy && rxPhyReq == rxPhy) ||
                        (txPhyReq == ALL_PHY && rxPhyReq == ALL_PHY)){
                        PhyUpdate tmp_phy = new PhyUpdate();
                        tmp_phy.txPhy = txPhy;
                        tmp_phy.rxPhy = rxPhy;
                        msg = mStateMachine.obtainMessage(
                                       mStateMachine.MSG_TA_SM_PHY_UPDATED, tmp_phy);
                        mStateMachine.sendMessage(msg);
                        txPhyReq = rxPhyReq = 0;
                    }
                } else {
                    Log.i(TAG, "phy update failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Phy Update failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic,
                                                int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "onCharacteristicWrite: " + status);
                } else {
                    Log.i(TAG, "write characteristic failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Write Characteristic failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
                /* Release write mutex */
                synchronized (write_char_mutex) {
                    write_char_wait_signalled = true;
                    write_char_mutex.notify();
                }
             }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt,
                                            BluetoothGattDescriptor desc, int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "onDescriptorWrite: " + status);
                } else {
                    Log.i(TAG, "write descriptor failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Write Descriptor failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
                /* Release write mutex */
                synchronized (write_desc_mutex) {
                    write_desc_wait_signalled = true;
                    write_desc_mutex.notifyAll();
                    Log.d(TAG, "Mutex unlock");
                }
             }

            @Override
            public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                if(status == GATT_SUCCESS){
                    Log.i(TAG, "Read Phy: Tx Phy-"+txPhy+"Rx Phy:"+rxPhy);
                    PhyUpdate tmp_phy = new PhyUpdate();
                    tmp_phy.txPhy = txPhy;
                    tmp_phy.rxPhy = rxPhy;
                    /* Send Message to SM */
                    tmp_phy.txPhy = txPhy;
                    tmp_phy.rxPhy = rxPhy;
                    msg = mStateMachine.obtainMessage(
                               mStateMachine.MSG_TA_SM_PHY_READ_DONE, tmp_phy);
                    mStateMachine.sendMessage(msg);
                } else{
                    Log.i(TAG, "Read Phy failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Read Phy failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                num_of_notifications ++;
                String charValue = characteristic.getStringValue(0);
                Log.i(TAG, "onCharacteristicChanged, value: " ); //+ charValue);
                // PrintStr.setLength(0);
                // PrintStr.append("\nonCharacteristicChanged, Value:  ");
                // PrintStr.append(charValue);
                // SocketServer.sendSocketData(PrintStr.toString());
                /* Read the start time once we receive notification with "start" in it */
                if (charValue.contains("start")) {
                    rx_start_time_stamp = SystemClock.elapsedRealtime();
                    if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                        Log.d(TAG, "rx_start_time_stamp:"+rx_start_time_stamp);
                    }
                } else if (Arrays.equals(tx_rx_str, charValue.toCharArray())) {
                   Log.i(TAG, "onCharacteristicChanged releasing mutex");
                  /* Release write mutex */
                   synchronized (notification_mutex) {
                       notify_wait_signalled = true;
                       notification_mutex.notifyAll();
                   }
                } else if (charValue.contains("77777")) {
                    /* Keep reading the end time until we receive last notification */
                    rx_end_time_stamp1 = rx_end_time_stamp;
                    rx_end_time_stamp = SystemClock.elapsedRealtime();
                    rx_end_time_stamp2 = (rx_end_time_stamp - rx_end_time_stamp1);
                    pkt_cnt++;
                    Log.d(TAG, "rx time : " + rx_end_time_stamp2 + " packet number : " +pkt_cnt);
                    if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                 //       Log.d(TAG, "rx_end_time_stamp:"+rx_end_time_stamp);
                    }
                } else {
                    Log.i(TAG,"data integrity failed");
                    PrintStr.setLength(0);
                    PrintStr.append("DataTxRx: data integrity check failed");
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onMtuChanged (BluetoothGatt gatt, int mtu, int status) {
                if (status == GATT_SUCCESS) {
                    Log.i(TAG, "Gatt updated MTU" + mtu);
                    mtu_size = mtu;
                    PrintStr.setLength(0);
                    PrintStr.append("Mtu Update done, Mtu:");
                    PrintStr.append(mtu_size);
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.d(TAG, "onMtuChanged failed " + status);
                    PrintStr.setLength(0);
                    PrintStr.append("MTU Exchange failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }
        };

        public void connect(BluetoothDevice device, int initPhy){
            if(BleAppService.bleAdapter!=null) {
                Log.i(TAG, "Gatt Connect");
                mDevice = device;
                mBluetoothGatt = mDevice.connectGatt(mcontext, false, mGattCallbacks,TRANSPORT_LE, initPhy);
            }
        }

        public void writeDescriptor(BluetoothGattDescriptor descriptor) {
            for (int i = 0; i < WRITE_DESC_MAX_RETRIES; i++) {
                boolean requestStatus = mBleConnect.mBluetoothGatt.writeDescriptor(descriptor);
                if (requestStatus != false) {
                    break;
                }
                try {
                    Thread.sleep(WRITE_DESC_TIME_TO_WAIT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "", e);
                }
            }
        }

        public void disconnect() {
            mBleConnect.mBluetoothGatt.disconnect();
        }

        public void pair(){
            if(mDevice.getBondState() != BluetoothDevice.BOND_BONDED){
                Log.i(TAG, "Pairing!");
                if(!mDevice.createBond(TRANSPORT_LE)) {
                    Log.i(TAG, "couldn't start pairing");
                }
            }
            else {
                Log.i(TAG, "Already Paired!");
            }
        }

        public void unpair() {
            if(mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                Log.i(TAG, "Unpairing!");
                mDevice.removeBond();
                Log.i(TAG, "Device unpaired");
                StringBuilder PrintStr = new StringBuilder();
                PrintStr.setLength(0);
                PrintStr.append("Device unpaired");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        public BluetoothGattService getGattService(UUID serv_uuid){
            BluetoothGattService mService = mBluetoothGatt.getService(serv_uuid);
            if(mService != null){
                Log.d(TAG, "Get Gatt Service");
            } else {
                Log.d(TAG, "Get Gatt Service not found");
            }
            return mService;
        }
    }

    /* function to start service discovery if not yet started */
    public void wait_for_gatt_service_discovery() {
        Log.d(TAG, "Wait for service disc, thread name:" + Thread.currentThread().getName() +
                "thread id:" + Thread.currentThread().getId());
        if(!gatt_discovery_done){
            /* Wait for service discovery done callback */
            synchronized (service_discovery_mutex) {
                try {
                    service_discovery_mutex.wait();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Interrupted while waiting for operation to complete");
                }
            }
        }
    }

    public class TestAppThroughputStateMachine extends StateMachine {

        public static final int MSG_TA_SM_DEV_FOUND = 0;
        public static final int MSG_TA_SM_BT_ADAPTER_STATE_CHANGED = 1;
        public static final int MSG_TA_SM_CONNECT = 2;
        public static final int MSG_TA_SM_REM_DEV_CONNECTED = 3;
        public static final int MSG_TA_SM_DEV_FAILED_TO_CONNECT = 4;
        public static final int MSG_TA_SM_DISCONNECT = 5;
        public static final int MSG_TA_SM_DEV_DISCONNECTED = 6;
        public static final int MSG_TA_SM_TX_TEST_DONE = 7;
        public static final int MSG_TA_SM_RX_TEST_DONE = 8;
        public static final int MSG_TA_SM_LAT_TEST_DONE = 9;
        public static final int MSG_TA_SM_CONNECTION_UPDATED = 10;
        public static final int MSG_TA_SM_PHY_UPDATED = 11;
        public static final int MSG_TA_SM_PHY_READ_DONE = 12;
        public static final int MSG_TA_SM_CONN_UPDATE = 14;
        public static final int MSG_TA_SM_UNPAIR_DEV = 15;
        public static final int MSG_TA_SM_PHY_UPDATE = 16;
        public static final int MSG_TA_SM_READ_PHY = 17;
        public static final int MSG_TA_SM_DATA_TX_TEST = 18;
        public static final int MSG_TA_SM_DATA_RX_TEST = 19;
        public static final int MSG_TA_SM_LATENCY_TEST = 20;
        public static final int MSG_TA_SM_PAIR_DEV = 21;
        public static final int MSG_TA_SM_CONNECT_TO_BDADDR = 22;
        public static final int MSG_TA_SM_CANCEL_CONNECT = 23;

        public static final int MSG_TA_SM_DATA_TX_RX_TEST = 24;
        public static final int MSG_TA_SM_TX_RX_TEST_DONE = 25;
        public static final int MSG_TA_SM_CONFIGURE_MTU = 26;
        public static final int MSG_TA_SM_TX_TEST_FAILED = 27;
        public static final int MSG_TA_SM_RX_TEST_FAILED = 28;
        public static final int MSG_TA_SM_TX_RX_TEST_FAILED = 29;
        public static final int MSG_TA_SM_LAT_TEST_FAILED = 30;

        /* Test App Connection states.*/
        private TAIdle mTAIdle;
        private TAConnectPending mTAConnectPending;
        private TAConnected mTAConnected;
        private TADataTx mTADataTx;
        private TADataRx mTADataRx;
        private TADataTxRx mTADataTxRx;
        private TALatencyMeasurement mTALatencyMeasurement;
        private TADisconnect mTADisconnect;
        private Context mContext;

        StringBuilder PrintStr = new StringBuilder();

        private TestAppThroughputStateMachine(Context context) {
            super("TestAppThroughputStateMachine");
            mContext = context;
            BleAppService.stateMachinestarted = true;

            mTAIdle = new TAIdle();
            mTAConnectPending = new TAConnectPending();
            mTAConnected = new TAConnected();
            mTADataTx = new TADataTx();
            mTADataRx = new TADataRx();
            mTADataTxRx = new TADataTxRx();
            mTALatencyMeasurement = new TALatencyMeasurement();
            mTADisconnect = new TADisconnect();

            addState(mTAIdle);
            addState(mTAConnectPending);
            addState(mTAConnected);
            addState(mTADataTx);
            addState(mTADataRx);
            addState(mTADataTxRx);
            addState(mTALatencyMeasurement);
            addState(mTADisconnect);

            setInitialState(mTAIdle);
        }

        public void doQuit() {
            Log.i("TestAppThroughputStateMachine", "Quit");
            synchronized (TestAppThroughputStateMachine.this) {
                BleAppService.stateMachinestarted = false;
                quitNow();
            }
        }

        private void showAdapterMessage(int state) {
            if(state == BluetoothAdapter.STATE_ON) {
                Log.i(TAG, "BT Adapter is on");
                PrintStr.setLength(0);
                PrintStr.append("BT Adapter is turned on");
                SocketServer.sendSocketData(PrintStr.toString());
            } else if ((state == BluetoothAdapter.STATE_OFF)) {
                Log.i(TAG, "BT Adapter is off");
                PrintStr.setLength(0);
                PrintStr.append("BT Adapter is turned off");
                SocketServer.sendSocketData(PrintStr.toString());
                transitionTo(mTAIdle);
            }
         }

        private class TAIdle extends State {
            private static final String TAG = "TAIdle";

            @Override
            public void enter() {
                Log.i(TAG, "Enter" + getCurrentMessage().what);
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit" + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);

                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_CONNECT:
                        Scan scn = (Scan)message.obj;
                        if(BleAppService.mScannerService.mScanstatus) {
                            PrintStr.setLength(0);
                            PrintStr.append("Connect failed, there is an ongoing scan");
                            SocketServer.sendSocketData(PrintStr.toString());
                        }else {
                            Log.i(TAG, "starting scanning");
                            BleAppService.mScannerService.set_scan_parameters(scn);
                        }
                        break;
                    case MSG_TA_SM_CONNECT_TO_BDADDR:
                        Scan init = (Scan) message.obj;
                        processConnectToBdaddr(init);
                        break;
                    case MSG_TA_SM_CANCEL_CONNECT:
                        processCancelConnect();
                        transitionTo(mTAIdle);
                        break;
                    case MSG_TA_SM_DEV_FOUND:
                        int primaryphy = (int) message.arg1;
                        BluetoothDevice device = (BluetoothDevice) message.obj;
                        processSMDevFoundEvent(device, primaryphy);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }

            private void processConnectToBdaddr(Scan init) {
                if(BleAppService.bleAdapter != null) {
                    Log.i(TAG, "Connect to Address: " + init.DeviceAddress);
                    BluetoothDevice remoteDevice =
                            BleAppService.bleAdapter.getRemoteDevice(bdAddr);
                    mBleConnect.connect(remoteDevice, init.initPhy);
                    transitionTo(mTAConnectPending);
                }
            }

            private void processSMDevFoundEvent(BluetoothDevice device, int primaryphy) {
                Log.i(TAG, "matchFoundEvent Address:" + device.getAddress());
                if(BleAppService.mScannerService.mScanstatus) {
                    BleAppService.mScannerService.stopScan();
                }
                mBleConnect.connect(device, primaryphy);
                transitionTo(mTAConnectPending);
            }

            private void processCancelConnect() {
                if(BleAppService.mScannerService.mScanstatus) {
                    BleAppService.mScannerService.stopScan();
                    PrintStr.setLength(0);
                    PrintStr.append("Scan Stopped for connect");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    PrintStr.setLength(0);
                    PrintStr.append("No Connection in Pending");
                    SocketServer.sendSocketData(PrintStr.toString());
                }

            }
        }

        private class TAConnectPending extends State {
            private static final String TAG = "TAConnectPending";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);
                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_CANCEL_CONNECT:
                        processCancelConnect();
                        transitionTo(mTAIdle);
                        break;
                    case MSG_TA_SM_REM_DEV_CONNECTED:
                        PrintStr.setLength(0);
                        String name = (String) message.obj;
                        PrintStr.append("Connected to ");
                        PrintStr.append(name);
                        SocketServer.sendSocketData(PrintStr.toString());
                        transitionTo(mTAConnected);
                        break;
                    case MSG_TA_SM_DEV_FAILED_TO_CONNECT:
                        Log.i(TAG, "Connection failed to establish, please try again");
                        PrintStr.setLength(0);
                        PrintStr.append("Connection failed to establish, please try again!!");
                        SocketServer.sendSocketData(PrintStr.toString());
                        transitionTo(mTAIdle);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }

            private void processCancelConnect() {
                mBleConnect.disconnect();
                PrintStr.setLength(0);
                PrintStr.append("Connection cancelled!");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        private class TAConnected extends State {
            private static final String TAG = "TAConnected";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
                if(BleAppService.isServiceRunning == false) {
                    transitionTo(mTADisconnect);
                }
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);

                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_CANCEL_CONNECT:
                        PrintStr.setLength(0);
                        PrintStr.append("CancelConnect: Already Connected");
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    case MSG_TA_SM_CONN_UPDATE:
                        Log.d(TAG,"Connection Priority : " +(int)message.obj);
                        int conn_priority = (int) message.obj;
                        processConnPriorityReq(conn_priority);
                        PrintStr.setLength(0);
                        PrintStr.append("Connection Priority UPDATED :");
                        PrintStr.append(conn_priority);
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    case MSG_TA_SM_PAIR_DEV:
                        mBleConnect.pair();
                        break;
                    case MSG_TA_SM_UNPAIR_DEV:
                        mBleConnect.unpair();
                        break;
                    case MSG_TA_SM_PHY_UPDATE:
                        PhyUpdate phyUpdateObj = (PhyUpdate) message.obj;
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"PhyUpdateflag");
                        processPhyUpdateReq(phyUpdateObj);
                        break;
                    case MSG_TA_SM_CONFIGURE_MTU:
                        int Mtu_Size = (int) message.obj;
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"MTUUpdateflag");
                        processMtuUpdateReq(Mtu_Size);
                        break;
                    case MSG_TA_SM_READ_PHY:
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"ReadPhyflag");
                        processReadPhyReq();
                        break;
                    case MSG_TA_SM_DATA_TX_TEST:
                        DataTxClass = (DataTx)message.obj;
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"DataTxflag");
                        transitionTo(mTADataTx);
                        break;
                    case MSG_TA_SM_DATA_RX_TEST:
                        DataRxClass = (DataRx)message.obj;
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"DataRxflag");
                        transitionTo(mTADataRx);
                        break;
                    case MSG_TA_SM_DATA_TX_RX_TEST:
                        DataTxRxClass = (DataTx)message.obj;
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"DataTxRxflag");
                        transitionTo(mTADataTxRx);
                        break;
                    case MSG_TA_SM_LATENCY_TEST:
                        LatencyTestClass = (LatencyTest)message.obj;
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"LatencyTestflag");
                        transitionTo(mTALatencyMeasurement);
                        break;
                    case MSG_TA_SM_DISCONNECT:
                        /*Disconnect*/
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG,"Disconnectflag");
                        transitionTo(mTADisconnect);
                        break;
                    case MSG_TA_SM_DEV_DISCONNECTED:
                        PrintStr.setLength(0);
                        PrintStr.append("Remote disconnected");
                        SocketServer.sendSocketData(PrintStr.toString());
                        transitionTo(mTAIdle);
                        break;
                   case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int) message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_CONNECTION_UPDATED:
                        Log.d(TAG, "CONNECTION PARAM UPDATED");
                        PrintStr.setLength(0);
                        String interal = (String) message.obj;
                        PrintStr.append("Connection Updated to");
                        PrintStr.append(interal);
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    case MSG_TA_SM_PHY_UPDATED:
                        Log.d(TAG, "PHY UPDATED");
                        PrintStr.setLength(0);
                        PhyUpdate phyUpdate = (PhyUpdate) message.obj;
                        PrintStr.append("Phy Update done, Tx Phy :");
                        PrintStr.append(phyUpdate.txPhy);
                        PrintStr.append(" Rx Phy :");
                        PrintStr.append(phyUpdate.rxPhy);
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    case MSG_TA_SM_PHY_READ_DONE:
                        Log.d(TAG, "PHY READ Done");
                        PrintStr.setLength(0);
                        PhyUpdate readphy = (PhyUpdate) message.obj;
                        PrintStr.append("Current Phy: Tx Phy :");
                        PrintStr.append(readphy.txPhy);
                        PrintStr.append(" Rx Phy :");
                        PrintStr.append(readphy.rxPhy);
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    default:
                        Log.d(TAG, "Not handled");
                        return NOT_HANDLED;
                }
                return retValue;
            }
            private void processConnPriorityReq(int conn_pri) {
                Log.i(TAG, "Request connection priority");
                mBleConnect.mBluetoothGatt.requestConnectionPriority(conn_pri);
            }

            private void processReadPhyReq(){
                Log.i(TAG, "Read Phy");
                mBleConnect.mBluetoothGatt.readPhy();
            }

            private void processMtuUpdateReq(int Mtu_size) {
                Log.i(TAG, "MTU Update");
                mBleConnect.mBluetoothGatt.requestMtu(Mtu_size);
            }

            private void processPhyUpdateReq(PhyUpdate phyUpdate){
                Log.i(TAG, "Phy Update");
                txPhyReq = phyUpdate.txPhy;
                rxPhyReq = phyUpdate.rxPhy;
                if(phyUpdate.txPhy == LE_CODED_PHY)
                    txPhyReq -= 1;
                if(phyUpdate.rxPhy == LE_CODED_PHY)
                    rxPhyReq -= 1;
                mBleConnect.mBluetoothGatt.setPreferredPhy(phyUpdate.txPhy,
                                            phyUpdate.rxPhy, phyUpdate.phyOpt);
            }
        }

        private class TADataTx extends State {
            private static final String TAG = "TADataTx";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
                 /* Start tx thread */
                DataTxthread tt = new DataTxthread();
                Thread t = new Thread(tt);
                if(!t.isAlive()) {
                    t.start();
                    MainActivity.wl.acquire();
                    MainActivity.wl_acquired = true;
                    Log.d(TAG,"acquire wakelock");
                    PrintStr.setLength(0);
                    PrintStr.append("Data Tx Thread Started");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Thread is already running");
                }
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);
                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_DISCONNECT:
                        transitionTo(mTADisconnect);
                        break;
                    case MSG_TA_SM_UNPAIR_DEV:
                        mBleConnect.unpair();
                        break;
                    case MSG_TA_SM_TX_TEST_DONE:
                        MainActivity.wl.release();
                        MainActivity.wl_acquired = false;
                        write_char_wait_signalled = false;
                        Log.d(TAG,"Release wakelock");
                        PrintStr.setLength(0);
                        String tput = (String) message.obj;
                        PrintStr.append("Data Tx Throughput in kbps:");
                        PrintStr.append(tput);
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Data Tx done, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    case MSG_TA_SM_TX_TEST_FAILED:
                        MainActivity.wl.release();
                        MainActivity.wl_acquired = false;
                        write_char_wait_signalled = false;
                        Log.d(TAG,"Release wakelock");
                        PrintStr.setLength(0);
                        PrintStr.append("Data Tx Test failed, either service or char is null");
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Data Tx failed, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }

            public class DataTxthread implements Runnable {

                @Override
                public void run() {
                    Log.i(TAG, "data tx thread start");
                    final UUID UUID_TX_SERVICE = UUID.fromString(DataTxClass.txService);
                    final UUID UUID_TX_CHAR = UUID.fromString(DataTxClass.txChar);
                    float txTputkr = 0;
                    Message msg;

                    wait_for_gatt_service_discovery();
                    /* wait for 500ms for DLE event to be received */
                    try {
                        Thread.sleep(500);
                    } catch(InterruptedException e){
                        Log.e(TAG, "error in thread sleep");
                    }

                    int mtu_intr_size = DataTxClass.Packet_Size;
                    /* Set the packet size to maximum possible if it exceeds mtu size */
                    if(mtu_intr_size > (mtu_size - 3)) {
                        mtu_intr_size = mtu_size - 3;
                    }
                    char[] str = new char[mtu_intr_size];
                    long length = mtu_intr_size * (DataTxClass.Num_Packets);
                    BluetoothGattService mService =
                                            mBleConnect.mBluetoothGatt.getService(UUID_TX_SERVICE);
                    if (mService != null) {
                        BluetoothGattCharacteristic mCharacteristic =
                                                        mService.getCharacteristic(UUID_TX_CHAR);
                        if (mCharacteristic != null) {
                            /* Filling the array with data */
                            str[0]  = 41; str[1] = 42; str[2] = 43;
                            Arrays.fill(str,3, mtu_intr_size-1,(char)'d');
                            try {
                                Process proc =
                                    Runtime.getRuntime().exec("/system/bin/getprop"+" "
                                                            +"tx_test.enable");
                                BufferedReader reader =
                                                new BufferedReader(
                                                    new InputStreamReader(proc.getInputStream()));
                                String readLine = reader.readLine();
                                if(readLine.equals("true")) {
                                    /* If Property is set to true, send one packet from app,
                                       rest of the packets from bta */
                                     Log.d(TAG, "system property is true");
                                    mCharacteristic.setWriteType(
                                              BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                    mCharacteristic.setValue(String.valueOf(str));
                                    mBleConnect.mBluetoothGatt.writeCharacteristic(
                                                                    mCharacteristic);
                                    synchronized (write_char_mutex) {
                                        // Wait for write response
                                        if(!write_char_wait_signalled){
                                            try {
                                                write_char_mutex.wait();
                                            } catch (InterruptedException e) {
                                                Log.d(TAG, "Interrupted while waiting");
                                            }
                                        }
                                        write_char_wait_signalled = false;
                                    }
                                }
                                /* If system property is set to false or is not set,
                                   continue with sending the data from the app */
                                else {
                                    Log.d(TAG, "system property is false");
                                    long tx_start_time_stamp = SystemClock.elapsedRealtime();
                                    mCharacteristic.setWriteType(
                                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                    if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                                        Log.d(TAG, "chardata len:"+length+
                                            "tx start time:"+tx_start_time_stamp);
                                    }
                                    for (long i = 1; i <= (DataTxClass.Num_Packets - 1) ; i++) {
                                        /*Max packet size that can be sent using
                                         write without response is MTU-3 Bytes*/
                                        mCharacteristic.setValue(String.valueOf(str));
                                        mBleConnect.mBluetoothGatt.writeCharacteristic(
                                                                            mCharacteristic);
                                        synchronized (write_char_mutex) {
                                            // Wait for write response
                                            if(!write_char_wait_signalled) {
                                                try {
                                                    write_char_mutex.wait();
                                                } catch (InterruptedException e) {
                                                    Log.d(TAG, "Interrupted while waiting");
                                                }
                                            }
                                            write_char_wait_signalled = false;
                                        }
                                     }
                                     /* Write the last packet with response */
                                    mCharacteristic.setWriteType(
                                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                    mCharacteristic.setValue(String.valueOf(str));
                                    mBleConnect.mBluetoothGatt.writeCharacteristic(
                                                                    mCharacteristic);
                                    long tx_intr_time_stamp = SystemClock.elapsedRealtime();
                                    Log.d(TAG, "Intr time stamp:"+tx_intr_time_stamp);
                                    synchronized (write_char_mutex) {
                                        // Wait for write response
                                        if(!write_char_wait_signalled){
                                            try {
                                                write_char_mutex.wait();
                                            } catch (InterruptedException e) {
                                                Log.d(TAG, "Interrupted while waiting");
                                            }
                                        }
                                        write_char_wait_signalled = false;
                                    }
                                    long tx_end_time_stamp = SystemClock.elapsedRealtime();
                                    if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                                        Log.d(TAG, "chardata len:"+length +
                                            "tx end time:"+tx_end_time_stamp);
                                    }
                                    float txTput = (((float) length /
                                            ((float) (tx_end_time_stamp - tx_start_time_stamp)))
                                                *8 *1000);
                                    float txTputk = txTput / 1000;
                                    float txTputm = txTputk / 1000;
                                    Log.i(TAG, "Tx tput in kbps: " + txTputk +
                                            " in mbps: " + txTputm);
                                    /* Another Tx Throughput value,
                                        since response could take some time */
                                    Log.d(TAG, "chardata len:"+length + "tx intr time:"
                                                            +tx_intr_time_stamp);
                                    float txTputr = (((float) length /
                                            ((float) (tx_intr_time_stamp - tx_start_time_stamp)))
                                            *8 *1000);
                                    txTputkr = txTputr / 1000;
                                    float txTputmr = txTputkr / 1000;
                                    Log.i(TAG, "Intr Tx tput in kbps: "+txTputkr+
                                                " in mbps: "+txTputmr);
                                }
                                /* Signal SM that TX Test is done*/
                                msg = mStateMachine.obtainMessage(
                                mStateMachine.MSG_TA_SM_TX_TEST_DONE,Float.toString(txTputkr));
                                mStateMachine.sendMessage(msg);
                            } catch (IOException e) {
                                Log.e(TAG, "Exception handling");
                                msg = mStateMachine.obtainMessage(
                                mStateMachine.MSG_TA_SM_TX_TEST_FAILED,null);
                                mStateMachine.sendMessage(msg);
                            }
                        } else {
                            Log.e(TAG, "Characteristic is null!");
                            msg = mStateMachine.obtainMessage(
                                mStateMachine.MSG_TA_SM_TX_TEST_FAILED,null);
                                mStateMachine.sendMessage(msg);
                        }
                    } else {
                        Log.d(TAG, "Service with UUID not found");
                        msg = mStateMachine.obtainMessage(
                                mStateMachine.MSG_TA_SM_TX_TEST_FAILED,null);
                                mStateMachine.sendMessage(msg);
                    }
                }
            }
        }

        private class TADataTxRx extends State {
            private static final String TAG = "TADataTxRx";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
                /* Start tx-rx thread */
                DataTxRxthread tt = new DataTxRxthread();
                Thread t = new Thread(tt);
                if(!t.isAlive()) {
                    t.start();
                    MainActivity.wl.acquire();
                    MainActivity.wl_acquired = true;
                    Log.d(TAG,"acquire wakelock");
                    PrintStr.setLength(0);
                    PrintStr.append("Data Tx Rx Thread Started");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Thread is already running");
                }
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);
                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_DISCONNECT:
                        transitionTo(mTADisconnect);
                        break;
                    case MSG_TA_SM_UNPAIR_DEV:
                        mBleConnect.unpair();
                        break;
                    case MSG_TA_SM_TX_RX_TEST_DONE:
                        MainActivity.wl.release();
                        MainActivity.wl_acquired = false;
                        write_char_wait_signalled = false;
                        notify_wait_signalled = false;
                        Log.d(TAG,"Release wakelock");
                        PrintStr.setLength(0);
                        PrintStr.append("Data Tx Rx done - Data Integrity check passed");
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Data Tx Rx done, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    case MSG_TA_SM_TX_RX_TEST_FAILED:
                        MainActivity.wl.release();
                        MainActivity.wl_acquired = false;
                        write_char_wait_signalled = false;
                        Log.d(TAG,"Release wakelock");
                        PrintStr.setLength(0);
                        PrintStr.append("Data Tx Rx Test failed, either service or char is null");
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Data Tx Rx failed, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }

            public class DataTxRxthread implements Runnable {

                @Override
                public void run() {
                    Log.i(TAG, "data tx rx thread start");
                    final UUID UUID_TX_SERVICE = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB");
                    final UUID UUID_TX_CHAR = UUID.fromString("0000FF05-0000-1000-8000-00805F9B34FB");
                    final UUID UUID_CCCD = CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR;
                    Message msg;

                    int mtu_intr_size = 244;
                    char[] str = new char[mtu_size];
                    BluetoothGattService mService =
                                            mBleConnect.mBluetoothGatt.getService(UUID_TX_SERVICE);
                    if (mService != null) {
                        BluetoothGattCharacteristic mCharacteristic =
                                                        mService.getCharacteristic(UUID_TX_CHAR);
                        if (mCharacteristic != null) {
                            mCharacteristic.setWriteType(
                                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            //enable cccd to send notification
                            mBleConnect.mBluetoothGatt.setCharacteristicNotification(
                                                                    mCharacteristic, true);
                            BluetoothGattDescriptor descriptor =
                                                            mCharacteristic.getDescriptor(UUID_CCCD);

                            if (descriptor != null) {
                                descriptor.setValue(
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                                mBleConnect.writeDescriptor(descriptor);
                                synchronized (write_desc_mutex) {
                                    // Wait for write response
                                    if(!write_desc_wait_signalled){
                                        try {
                                            write_desc_mutex.wait();
                                        } catch (InterruptedException e) {
                                            Log.d(TAG, "Interrupted while waiting");
                                        }
                                    }
                                    write_desc_wait_signalled = false;
                                }
                            } else {
                                Log.e(TAG, "Descriptor not found");
                            }

                             /* Filling the array with data */
                            Arrays.fill(tx_rx_str,0, tx_rx_mtu_intr_size-1,(char)'A');

                            for (long i = 1; i <= (DataTxRxClass.Num_Packets) ; i++) {
                                mCharacteristic.setValue(String.valueOf(tx_rx_str));
                                mBleConnect.mBluetoothGatt.writeCharacteristic(
                                                                    mCharacteristic);
                                synchronized (notification_mutex) {
                                    // Wait for write response
                                    if(!notify_wait_signalled) {
                                        try {
                                            notification_mutex.wait();
                                        } catch (InterruptedException e) {
                                            Log.d(TAG, "Interrupted while waiting");
                                        }
                                    }
                                    notify_wait_signalled = false;
                                }
                            }

                            //disable cccd
                            mBleConnect.mBluetoothGatt.setCharacteristicNotification(
                                                    mCharacteristic, false);
                            descriptor.setValue(
                                       BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                            mBleConnect.writeDescriptor(descriptor);
                            synchronized (write_desc_mutex) {
                            //Wait for write response
                                if(!write_desc_wait_signalled){
                                    try {
                                        write_desc_mutex.wait();
                                    }
                                    catch (InterruptedException e) {
                                        Log.d(TAG, "Interrupted while waiting");
                                    }
                                }
                                write_desc_wait_signalled = false;
                            }

                            /* Signal SM that TX Test is done*/
                            msg = mStateMachine.obtainMessage(
                            mStateMachine.MSG_TA_SM_TX_RX_TEST_DONE,null);
                            mStateMachine.sendMessage(msg);
                        } else {
                            Log.e(TAG, "Characteristic is null!");
                            msg = mStateMachine.obtainMessage(
                            mStateMachine.MSG_TA_SM_TX_RX_TEST_FAILED,null);
                            mStateMachine.sendMessage(msg);
                        }
                    } else {
                        Log.d(TAG, "Service with UUID not found");
                        msg = mStateMachine.obtainMessage(
                        mStateMachine.MSG_TA_SM_TX_RX_TEST_FAILED,null);
                        mStateMachine.sendMessage(msg);
                    }
                }
            }
        }

        private class TADataRx extends State {
            private static final String TAG = "TADataRx";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
                /* start rx thread */
                DataRxthread rt = new DataRxthread();
                Thread t = new Thread(rt);
                if(!t.isAlive()) {
                    t.start();
                    MainActivity.wl.acquire();
                    MainActivity.wl_acquired = true;
                    Log.d(TAG,"acquire wakelock");
                    PrintStr.setLength(0);
                    PrintStr.append("Data Rx Thread Started");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Thread is already running");
                }
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);
                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_DISCONNECT:
                        transitionTo(mTADisconnect);
                        break;
                    case MSG_TA_SM_UNPAIR_DEV:
                        mBleConnect.unpair();
                        break;
                    case MSG_TA_SM_RX_TEST_DONE:
                        MainActivity.wl.release();
                        MainActivity.wl_acquired = false;
                        write_char_wait_signalled = false;
                        write_desc_wait_signalled = false;
                        PrintStr.setLength(0);
                        String rxtput = (String) message.obj;
                        PrintStr.append("Data Rx Throughput in kbps:");
                        PrintStr.append(rxtput);
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Rx Data Done, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    case MSG_TA_SM_RX_TEST_FAILED:
                        MainActivity.wl.release();
                        MainActivity.wl_acquired = false;
                        write_char_wait_signalled = false;
                        Log.d(TAG,"Release wakelock");
                        PrintStr.setLength(0);
                        PrintStr.append("Data Rx Test failed, either service or char is null");
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Data Rx failed, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }

            public class DataRxthread implements Runnable {

                public void run() {
                    Log.i(TAG, "data rx thread start");
                    final UUID UUID_RX_SERVICE = UUID.fromString(DataRxClass.rxService);
                    final UUID UUID_RX_CHAR = UUID.fromString(DataRxClass.rxChar);
                    final UUID UUID_CCCD = CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR;
                    int rx_data_size = 244;
                    Message msg;
                    /* Set the packet size to maximum possible if it exceeds mtu size */
                    if(rx_data_size > (mtu_size - 3)) {
                        rx_data_size = mtu_size - 3;
                    }
                    wait_for_gatt_service_discovery();
                    //battery service and characteristic
                    BluetoothGattService mService =
                                            mBleConnect.mBluetoothGatt.getService(UUID_RX_SERVICE);
                    if (mService != null) {
                        BluetoothGattCharacteristic mreadChar =
                                                        mService.getCharacteristic(UUID_RX_CHAR);
                        if (mreadChar != null) {
                            if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                                Log.d(TAG, "Found Characteristic: " +
                                    mreadChar.getUuid().toString());
                            }
                            //enable cccd to send notification
                            mBleConnect.mBluetoothGatt.setCharacteristicNotification(
                                                                    mreadChar, true);
                            BluetoothGattDescriptor descriptor =
                                                            mreadChar.getDescriptor(UUID_CCCD);
                            if (descriptor != null) {
                                //start of writing
                                descriptor.setValue(
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                mBleConnect.writeDescriptor(descriptor);
                                synchronized (write_desc_mutex) {
                                    // Wait for write response
                                    if(!write_desc_wait_signalled){
                                        try {
                                            write_desc_mutex.wait();
                                        } catch (InterruptedException e) {
                                            Log.d(TAG, "Interrupted while waiting");
                                        }
                                    }
                                    write_desc_wait_signalled = false;
                                }
                            } else {
                                Log.e(TAG, "Descriptor not found");
                            }
                            long NotificationsTime = ((DataRxClass.NotificationsTimeInMin * 60) +
                                                    DataRxClass.NotificationsTimeInSec);
                            /* write notifications time to the characteristic */
                            mreadChar.setValue(
                                    (int)NotificationsTime,
                                    BluetoothGattCharacteristic.FORMAT_UINT32,0);
                            mBleConnect.mBluetoothGatt.writeCharacteristic(mreadChar);
                            synchronized (write_char_mutex) {
                                // Wait for write response
                                if(!write_char_wait_signalled) {
                                    try {
                                        write_char_mutex.wait();
                                    } catch (InterruptedException e) {
                                        Log.d(TAG, "Interrupted while waiting");
                                    }
                                }
                                write_char_wait_signalled = false;
                            }
                            /* wait for DataRxClass.NotificationsTime seconds
                               before disabling notifications */
                            try {
                                if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                                    Log.d(TAG, "Sleep for :"+NotificationsTime+
                                        " in sec");
                                }
                                Thread.sleep((NotificationsTime)*1000);
                                }
                            catch(InterruptedException e){
                                Log.e(TAG, "error in thread sleep");
                            }
                            /* wait for extra 20 seconds incase of l2cap congestion on the remote */
                            try {
                                Log.d(TAG, "Sleep for 20 sec");
                                Thread.sleep(20000);
                            }
                            catch(InterruptedException e){
                                Log.e(TAG, "error in thread sleep");
                            }

                            //disable cccd
                            mBleConnect.mBluetoothGatt.setCharacteristicNotification(
                                                    mreadChar, false);
                            descriptor.setValue(
                                       BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                            mBleConnect.writeDescriptor(descriptor);
                            synchronized (write_desc_mutex) {
                            //Wait for write response
                                if(!write_desc_wait_signalled){
                                    try {
                                        write_desc_mutex.wait();
                                    }
                                    catch (InterruptedException e) {
                                        Log.d(TAG, "Interrupted while waiting");
                                    }
                                }
                                write_desc_wait_signalled = false;
                            }
                            if(ThroughputStateMachine.LOG_LEVEL >= 2) {
                                Log.d(TAG, "start time"+rx_start_time_stamp+"end time:"+
                                rx_end_time_stamp+"num of notifications:"+num_of_notifications);
                            }

                            float rxTput = (((float) num_of_notifications * rx_data_size *8*1000)
                                        /((float) (rx_end_time_stamp - rx_start_time_stamp)));
                            float rxTputk = rxTput / 1000;
                            float rxTputm = rxTputk / 1000;
                            Log.i(TAG, "Rx tput in kbps: " + rxTputk + " in mbps: " + rxTputm);
                            rx_start_time_stamp = 0;
                            rx_end_time_stamp = 0;
                            num_of_notifications = 0;
                            pkt_cnt = 0;
                            /* Signal SM that RX Test is done*/
                            msg = mStateMachine.obtainMessage(
                            mStateMachine.MSG_TA_SM_RX_TEST_DONE,Float.toString(rxTputk));
                            mStateMachine.sendMessage(msg);
                        } else {
                            Log.d(TAG, "Characteristic is null!");
                            msg = mStateMachine.obtainMessage(
                            mStateMachine.MSG_TA_SM_RX_TEST_FAILED,null);
                            mStateMachine.sendMessage(msg);
                        }
                    } else {
                        Log.d(TAG, "Service with uuid is not found");
                        msg = mStateMachine.obtainMessage(
                        mStateMachine.MSG_TA_SM_RX_TEST_FAILED,null);
                        mStateMachine.sendMessage(msg);
                    }
                }
            }
        }

        private class TALatencyMeasurement extends State {
            private static final String TAG = "TALatencyMeasurement";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
                /* start latency thread */
                LatencyTestthread lt = new LatencyTestthread();
                Thread t = new Thread(lt);
                if(!t.isAlive()) {
                    t.start();
                    PrintStr.setLength(0);
                    PrintStr.append("Latency Measurement Started");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Thread is already running");
                }
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);
                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_DISCONNECT:
                        transitionTo(mTADisconnect);
                        break;
                    case MSG_TA_SM_UNPAIR_DEV:
                        mBleConnect.unpair();
                        break;
                    case MSG_TA_SM_LAT_TEST_DONE:
                        PrintStr.setLength(0);
                        String latency = (String) message.obj;
                        PrintStr.append("Latency in ms:");
                        PrintStr.append(latency);
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Latency test done, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    case MSG_TA_SM_LAT_TEST_FAILED:
                        PrintStr.setLength(0);
                        PrintStr.append("Latency Test failed, either service or char is null");
                        SocketServer.sendSocketData(PrintStr.toString());
                        if(ThroughputStateMachine.LOG_LEVEL >= 2)
                            Log.d(TAG, "Latency test failed, state change to connected");
                        transitionTo(mTAConnected);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }
            class LatencyTestthread implements Runnable {

                public void run() {
                    final UUID UUID_LAT_SERVICE = UUID.fromString(LatencyTestClass.latService);
                    final UUID UUID_LAT_CHAR = UUID.fromString(LatencyTestClass.latChar);
                    Message msg;
                    Log.i(TAG, "Latency test start");
                    wait_for_gatt_service_discovery();
                    String str = "LatencyTest";
                    BluetoothGattService mService =
                                        mBleConnect.mBluetoothGatt.getService(UUID_LAT_SERVICE);
                    if (mService != null) {
                        BluetoothGattCharacteristic mCharacteristic =
                                                    mService.getCharacteristic(UUID_LAT_CHAR);
                        if (mCharacteristic != null) {
                            mCharacteristic.setWriteType(
                                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            mCharacteristic.setValue(str);
                            long latency_start_time_stamp = SystemClock.elapsedRealtime();
                            mBleConnect.mBluetoothGatt.writeCharacteristic(mCharacteristic);
                            synchronized (write_char_mutex) {
                                // Wait for write response
                                if(!write_char_wait_signalled) {
                                    try {
                                        write_char_mutex.wait();
                                    } catch (InterruptedException e) {
                                        Log.d(TAG, "Interrupted while waiting");
                                    }
                                }
                                write_char_wait_signalled = false;
                            }
                            long latency_end_time_stamp = SystemClock.elapsedRealtime();
                            float latency = (float) (latency_end_time_stamp -
                                                        latency_start_time_stamp);
                            Log.i(TAG, "Latency in msec " + latency);
                            msg = mStateMachine.obtainMessage(
                            mStateMachine.MSG_TA_SM_LAT_TEST_DONE,Float.toString(latency));
                            mStateMachine.sendMessage(msg);
                        } else {
                            Log.e(TAG, "Characteristic is null!");
                            msg = mStateMachine.obtainMessage(
                            mStateMachine.MSG_TA_SM_LAT_TEST_FAILED,null);
                            mStateMachine.sendMessage(msg);
                        }
                    } else {
                        Log.d(TAG, "Service with UUID not found ");
                        msg = mStateMachine.obtainMessage(
                        mStateMachine.MSG_TA_SM_LAT_TEST_FAILED,null);
                        mStateMachine.sendMessage(msg);
                    }
                }
            }
        }

        private class TADisconnect extends State {
            private static final String TAG = "TADisconnect";

            @Override
            public void enter() {
                Log.i(TAG, "Enter: " + getCurrentMessage().what);
                disconnect();
            }

            @Override
            public void exit() {
                Log.i(TAG, "Exit: " + getCurrentMessage().what);
            }

            private void disconnect() {
                mBleConnect.mBluetoothGatt.disconnect();
            }

            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "processMessage: " + message.what);
                boolean retValue = HANDLED;
                switch (message.what) {
                    case MSG_TA_SM_BT_ADAPTER_STATE_CHANGED:
                        int state = (int)message.obj;
                        showAdapterMessage(state);
                        break;
                    case MSG_TA_SM_UNPAIR_DEV:
                        mBleConnect.unpair();
                        break;
                    case MSG_TA_SM_DEV_DISCONNECTED:
                        transitionTo(mTAIdle);
                        PrintStr.setLength(0);
                        PrintStr.append("Device disconnected");
                        SocketServer.sendSocketData(PrintStr.toString());
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return retValue;
            }
        }
    }

    protected void finalize() {

    }
}
