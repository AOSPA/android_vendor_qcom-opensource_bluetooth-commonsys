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

import android.util.Log;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Build;
import android.os.SystemProperties;
import android.os.Message;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.lang.*;

import libcore.io.IoUtils;

import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattDescriptor;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocketSettings;
import java.io.OutputStream;

class GattClient {
    public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
                                 = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String TAG = "GattClient";
    public static int LOG_LEVEL = 6;

    public static final byte[] ENABLE_NOTIFICATION_INDICATION_VALUE = {0x03, 0x00};

    /* MTU size required for MTU exchange */
    public static final int MTU_SIZE_MIN = 23;
    public static final int TRANSPORT_LE = 2;

    public int mtu_size = MTU_SIZE_MIN;

    public Looper glooper;

    public BleGattClient mgattClient = null;
    private Context mcontext = null;
    public BluetoothDevice mDevice = null;

    //Actions
    public GattClientMessageHandler mGattClientHandler = null;
    public static final int MSG_BLE_SCAN_DEV_FOUND = 0;
    public static final int MSG_START_BLE_CONNECT = 1;
    public static final int MSG_START_BLE_CONN_UPDATE = 2;
    public static final int MSG_START_BLE_PHY_UPDATE = 3;
    public static final int MSG_START_BLE_READ_PHY = 4;
    public static final int MSG_START_BLE_GATT_DISC = 5;
    public static final int MSG_START_BLE_GATT_WRITE_READ_CHAR = 6;
    public static final int MSG_START_BLE_GATT_WRITE_READ_DESC = 7;
    public static final int MSG_START_BLE_GATT_CONFIGURE_MTU_SIZE = 8;
    public static final int MSG_START_BLE_GATT_REFRESH_SERVICES = 9;
    public static final int MSG_START_BLE_GATT_DISCONNECT = 10;
    public static final int MSG_REGISTER_BLE_GATT_NOTIFICATIONS = 11;
    public static final int MSG_DEREGISTER_BLE_GATT_NOTIFICATIONS = 12;
    public static final int MSG_START_BLE_GATT_RELIABLE_WRITE = 13;
    public static final int MSG_START_BLE_GATT_ABORT_RELIABLE_WRITE = 14;
    public static final int MSG_START_CANCEL_CONNECT = 15;
    public static final int MSG_BLE_GATT_REQ_CONN_PRIORITY = 16;
    public static final int MSG_START_BLE_CONNECT_TO_BDADDR = 17;
    public static final int MSG_START_BREDR_DISC = 18;
    public static final int MSG_READ_REMOTE_RSSI = 19;
    public static final int MSG_READ_CHAR_UUID = 20;
    public static final int MSG_DISC_SRVC_UUID = 21;
    public static final int MSG_START_BLE_GATT_UNREG = 22;
    public static final int MSG_START_BLE_GATT_EXECUTE_WRITE = 23;
	public static final int MSG_START_BLE_COC_CONNECT = 24;
	public static final int MSG_START_BLE_COC_WRITE = 25;
	public static final int MSG_START_BLE_LISTEN = 26;
	public static final int MSG_START_BLE_COC_CLOSE = 27;
    public static final int MSG_START_BLE_COC_DATA_TX = 28;
    public static final int MSG_START_BLE_COC_OFFLOAD_CONNECT = 29;
    public static final int MSG_START_BLE_COC_OFFLOAD_LISTEN = 30;
    public static final int MSG_GC_ACTION_MAX_VALUE = MSG_START_BLE_COC_OFFLOAD_LISTEN;

    public static final int LE_COC_HDR_LEN = 4;

    private static final int GATT_WRITE = 1;
    private static final int GATT_READ = 2;
    private static final int GATT_FORMAT_STRING = 1;
    private static final int GATT_FORMAT_INT = 2;

    private static final int OP_NOTIFICATIONS = 1;
    private static final int OP_INDICATIONS = 2;
    private static final int OP_NOTIFICATIONS_INDICATIONS = 3;

    // Connection States
    public static final int BLE_STATE_CONNECTING = 1;
    public static final int BLE_STATE_CONNECTED = 2;
    public static final int BLE_STATE_DISCONNECTING = 3;
    public static final int BLE_STATE_DISCONNECTED = 4;
	private BluetoothSocket mSocket;
	private BluetoothServerSocket mmServerSocket;

    private static int length_offset = 0;
    private static String offset_value = null;
    private boolean reliable_write = false;
    private static int total_length = 0;
    private boolean reliable_write_no_more_data = false;
    private boolean is_op_in_progress = false;
    private ReadWriteOp RdWrReliableClass = null;
    private ReadWriteOp RdWrClass = null;
	private static BluetoothAdapter bluetoothAdapter = BleAppService.bleAdapter;
    private static int mConnectionStatus = BLE_STATE_DISCONNECTED;

    private List<UUID> mServiceUUID;
    private List<UUID> mCharUUID;
    private List<UUID> mDescUUID;
    private List<BluetoothGattService> mServices;
    private List<BluetoothGattCharacteristic> mCharacteristics;
    private List<BluetoothGattDescriptor> mDescriptors;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectedThread mConnectedThread;
    private TxOperationRunnable mtxOperationRunnable;
    StringBuilder PrintStr = new StringBuilder();
    private boolean mOffloadedSocket = false;

	public class TxOperationRunnable implements Runnable {
        int mChunkSize;
        private OutputStream mOutputStream;
        public TxOperationRunnable(int ChunkSize) {
            mChunkSize = ChunkSize;
        }
        public void run() {
            try {
				 StringBuilder sb = new StringBuilder(mChunkSize);
                 Log.d(TAG, "TxOperationRunnable ");
				 mOutputStream = mSocket.getOutputStream();
				 for (int i = 0 ; i < mChunkSize; i++ ) {
					 sb.append('a');
				 }
                 mOutputStream.write(sb.toString().getBytes());
                 mOutputStream.flush();
            } catch (Exception e) {
                  Log.e(TAG, "ServerConnectedThread: could not write the message" + e.getMessage());
            }
        }
    }

    public class AcceptThread extends Thread {
        boolean mSecureFlag;
        private InputStream mInputStream;
        private BluetoothServerSocket mmServerSocket;
		private BluetoothSocket socket;
        boolean mOffload;
        boolean mEncryption;
        boolean mAuthentication;
        String mSockName;
        long mHubId;
        long mEndpointId;
        int mMaxPacketSize;
        public AcceptThread(boolean SecureFlag) {
            mSecureFlag = SecureFlag;
            mOffload = false;
        }

        public AcceptThread(LecocOffloadListen LecocOffloadListenObj) {
            mOffload = true;
            mEncryption = LecocOffloadListenObj.Encryption;
            mAuthentication = LecocOffloadListenObj.Authentication;
            mSockName = LecocOffloadListenObj.SockName;
            mHubId = LecocOffloadListenObj.HubId;
            mEndpointId = LecocOffloadListenObj.EndpointId;
            mMaxPacketSize = LecocOffloadListenObj.MaxPacketSize;
        }

        public void createoffloadserversocket() {
            BluetoothSocketSettings.Builder builder = new BluetoothSocketSettings.Builder();
            builder.setSocketType(BluetoothSocket.TYPE_LE);
            builder.setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD);
            builder.setEncryptionRequired(mEncryption);
            builder.setAuthenticationRequired(mAuthentication);
            builder.setSocketName(mSockName);
            builder.setHubId(mHubId);
            builder.setEndpointId(mEndpointId);
            builder.setRequestedMaximumPacketSize(mMaxPacketSize);
            BluetoothSocketSettings settings = builder.build();
            try {
                Log.d(TAG, "Offload server socket creation ");
                mmServerSocket = bluetoothAdapter.listenUsingSocketSettings(settings);
                PrintStr.setLength(0);
                PrintStr.append("listening at channel:" +mmServerSocket.getPsm());
                SocketServer.sendSocketData(PrintStr.toString());
                Log.d(TAG,"Server psm" + mmServerSocket.getPsm());
                mSocket = mmServerSocket.accept();
                mOffloadedSocket = true;
            } catch(Exception e) {
                Log.w(TAG,"exception caught in listen" + e );
                return ;
            }
        }
        public void createserversocket() {
            try {
                if (mSecureFlag == true)
                {
                    Log.d(TAG, "RxThread secure L2CAP Channel ");
                    mmServerSocket = bluetoothAdapter.listenUsingL2capChannel();
                }
                else
                {
                    Log.d(TAG, "RxThread In secure L2CAP Channel ");
                    mmServerSocket = bluetoothAdapter.listenUsingInsecureL2capChannel();
                }
                PrintStr.setLength(0);
                PrintStr.append("listening at channel:" +mmServerSocket.getPsm());
                SocketServer.sendSocketData(PrintStr.toString());
                Log.d(TAG,"Server psm" + mmServerSocket.getPsm());
                mSocket = mmServerSocket.accept();
            } catch(Exception e) {
                Log.d(TAG,"exception caught in listen" + e );
                return ;
            }

        }
        public void run() {
            if (mOffload == true) {
                createoffloadserversocket();
            } else {
                createserversocket();
            }

            byte[] buffer = new byte[1024];
            int bytes;
            while(true) {
                try {
                    mInputStream = mSocket.getInputStream();
                    bytes = mInputStream.read(buffer);
                    String incomingMsg = new String(buffer, 0, bytes);
                    PrintStr.setLength(0);
                    PrintStr.append("Received data in Server socket :" +incomingMsg );
                    SocketServer.sendSocketData(PrintStr.toString());
                    Log.d(TAG, "Received data in Server socket "+ incomingMsg);
                } catch (Exception e) {
                    Log.e(TAG, "Accept Thread ServerConnectedThread: could not read any more data" + e.getMessage());
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mSocket.close();
                PrintStr.setLength(0);
                PrintStr.append("Server Socket Disconnected" + mSecureFlag);
                SocketServer.sendSocketData(PrintStr.toString());
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
                PrintStr.setLength(0);
                PrintStr.append("close() of connect socket failed");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }
    }

    public class ConnectedThread extends Thread {
            private InputStream mmInStream;
            boolean mSecureFlag;
            String DeviceAddress;
            boolean mOffload;
            int mpsm;
            boolean mEncryption;
            boolean mAuthentication;
            String mSockName;
            int mHubId;
            int mEndpointId;
            int mMaxPacketSize;

            public ConnectedThread(LecocConnect LecocConnClass) {
                DeviceAddress = LecocConnClass.DeviceAddress;
                mSecureFlag   = LecocConnClass.secure_flag;
                mpsm   = LecocConnClass.psm;
                mOffload = false;
            }

            public ConnectedThread(LecocOffloadConnect LecocConnClass) {
                DeviceAddress = LecocConnClass.DeviceAddress;
                mOffload = true;
                mpsm   = LecocConnClass.psm;
                mEncryption = LecocConnClass.Encryption;
                mAuthentication = LecocConnClass.Authentication;
                mSockName = LecocConnClass.SockName;
                mHubId = LecocConnClass.HubId;
                mEndpointId = LecocConnClass.EndpointId;
                mMaxPacketSize = LecocConnClass.MaxPacketSize;
            }

            public void CreateOffloadClientSocket() {
                Log.d(TAG, "create ConnectedThread" );
                try {
                    BluetoothDevice remoteDevice = BleAppService.bleAdapter.getRemoteDevice(DeviceAddress);
                    Log.d(TAG, "processGattLeCocOffloadConnect ");
                    BluetoothSocketSettings.Builder builder = new BluetoothSocketSettings.Builder();
                    builder.setSocketType(BluetoothSocket.TYPE_LE);
                    builder.setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD);
                    builder.setL2capPsm(mpsm);
                    builder.setEncryptionRequired(mEncryption);
                    builder.setAuthenticationRequired(mAuthentication);
                    builder.setSocketName(mSockName);
                    builder.setHubId(mHubId);
                    builder.setEndpointId(mEndpointId);
                    builder.setRequestedMaximumPacketSize(mMaxPacketSize);
                    BluetoothSocketSettings settings = builder.build();
                    try {
                        mSocket = remoteDevice.createUsingSocketSettings(settings);
                        Log.d(TAG,"Calling connect on BT Address ::  " + remoteDevice.getAddress());
                    } catch(Exception e) {
                        Log.e(TAG,
                                    "There is an exception when opening offload client socket");
                            e.printStackTrace();
                    }
                    mSocket.connect();
                } catch (Exception e) {
                    Log.e(TAG,"got error while opening offload client socket" + e);
                    // Close the socket
                    try {
                        mSocket.close();
                    } catch (Exception e2) {
                        Log.e(TAG, "unable to close() socket during connection failure"+ e2);
                        return;
                    }

                }
                if(mSocket != null) {
                    InputStream tmpIn = null;

                    // Get the BluetoothSocket input stream
                    try {
                        tmpIn = mSocket.getInputStream();
                    } catch (Exception e) {
                        Log.e(TAG, "temp sockets not created", e);
                        return;
                    }
                    mmInStream = tmpIn;
                    PrintStr.setLength(0);
                    PrintStr.append("LE COC Connect Successfull" +mpsm);
                    SocketServer.sendSocketData(PrintStr.toString());
                    mOffloadedSocket = true;
                }
            }

            public void CreateClientSocket() {
                Log.d(TAG, "create ConnectedThread" );
                try {
                    BluetoothDevice remoteDevice = BleAppService.bleAdapter.getRemoteDevice(DeviceAddress);
                    if(mSecureFlag == true)
                    {
                        Log.d(TAG, "processGattLeCocConnect - Secure  ");
                        mSocket = remoteDevice.createL2capChannel(mpsm);
                    }
                    else{
                        Log.d(TAG, "processGattLeCocConnect - In Secure  ");
                        mSocket = remoteDevice.createInsecureL2capChannel(mpsm);
                    }
                    mSocket.connect();
                } catch(Exception e) {
                    Log.e(TAG,"got error while executing createL2CapChannel" + e);
                    // Close the socket
                    try {
                        mSocket.close();
                    } catch (Exception e2) {
                        Log.e(TAG, "unable to close() socket during connection failure"+ e2);
                        return;
                    }

                }
                if(mSocket != null) {
                    InputStream tmpIn = null;

                    // Get the BluetoothSocket input stream
                    try {
                        tmpIn = mSocket.getInputStream();
                    } catch (Exception e) {
                        Log.e(TAG, "temp sockets not created", e);
                        return;
                    }
                    mmInStream = tmpIn;
                    PrintStr.setLength(0);
                    PrintStr.append("LE COC Connect Successfull" +mpsm);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            public void run() {
                if (mOffload == true) {
                    Log.i(TAG, "BEGIN mOffload mConnectedThread");
                    CreateOffloadClientSocket();
                } else {
                    Log.i(TAG, "BEGIN mConnectedThread");
                    CreateClientSocket();
                }
                int bytes;

                // Keep listening to the InputStream while connected
                while (true) {
                    try {
                        byte[] buffer = new byte[1024];
                        // Read from the InputStream
                        bytes = mmInStream.read(buffer);
                        String incomingMsg = new String(buffer, 0, bytes);
                        PrintStr.setLength(0);
                        PrintStr.append("Received data in Client socket :" +incomingMsg );
                        SocketServer.sendSocketData(PrintStr.toString());
                        Log.d(TAG, "Received data in Client socket "+ incomingMsg);
                    } catch (Exception e) {
                        Log.e(TAG, "Client Thread - ConnectedThread: could not read any more data", e);
                        break;
                    }
                }
            }

            public void cancel() {
                try {
                    mSocket.close();
                    PrintStr.setLength(0);
                    PrintStr.append(" Client Socket Disconnected");
                    SocketServer.sendSocketData(PrintStr.toString());
                } catch (Exception e) {
                    Log.e(TAG, "close() of connect socket failed", e);
                    PrintStr.setLength(0);
                    PrintStr.append("close() of connect socket failed");
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }
        }



    public GattClient(Context mcontext) {
        this.mcontext = mcontext;
        /* Initialize classes */
        mgattClient = new BleGattClient(mcontext);
        /* Start Message handler */
        HandlerThread thread = new HandlerThread("GattClientHandler");
        thread.start();
        glooper = thread.getLooper();

        mGattClientHandler = new GattClientMessageHandler(mcontext, glooper);

        mServices = new ArrayList<BluetoothGattService>();
        mCharacteristics = new ArrayList<BluetoothGattCharacteristic>();
        mDescriptors = new ArrayList<BluetoothGattDescriptor>();

        mServiceUUID = new ArrayList<UUID>();
        mCharUUID = new ArrayList<UUID>();
        mDescUUID = new ArrayList<UUID>();

        IntentFilter intentFilter = new IntentFilter();
         intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
         intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
         intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
         mcontext.registerReceiver(eventReceiver, intentFilter);
    }

    private BroadcastReceiver eventReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG,"Inside onReceive");
        StringBuilder PrintStr = new StringBuilder();
        String action = intent.getAction();
         // When discovery finds a device
         if (BluetoothDevice.ACTION_FOUND.equals(action)) {
             // Get the BluetoothDevice object from the Intent
             BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
             PrintStr.setLength(0);
             PrintStr.append("==================================================\n");
             PrintStr.append("device type : ");
             PrintStr.append(device.getType());
             if(device.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                PrintStr.append("\nDiscovery Results: \nDevice Name : ");
                PrintStr.append(device.getName());
                PrintStr.append("\t Device Address : ");
                PrintStr.append(device.getAddress());
                SocketServer.sendSocketData(PrintStr.toString());
             }
         // When discovery is finished, change the Activity title
         } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
            SocketServer.sendSocketData("Discovery Started");
        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                .equals(action)) {
            SocketServer.sendSocketData("Discovery Ended");
        }
    }
};

    public void cleanup() {
        Log.i(TAG, "cleanup");
        /* disconnect the link */
        if(mConnectionStatus == BLE_STATE_CONNECTED) {
            Log.e(TAG, "in cleanup disconnect");
            mGattClientHandler.processDisconnectReq();
        }
        /* stop the looper */
        glooper.quitSafely();
        mGattClientHandler.processCloseReq();
    }

    /* Connection Class */
    public class BleGattClient {
        private static final String TAG = "BleGattClient";

        private BluetoothGatt mBluetoothGatt;
        private BluetoothGattService mService;
        private BluetoothGattCharacteristic mCharacteristic;
        private Context context;
        private int GATT_SUCCESS = 0x00;
        Message msg;
        StringBuilder PrintStr = new StringBuilder();

        public BleGattClient(Context context) {
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
                if ((gatt.getDevice() == null || status != GATT_SUCCESS) &&
                                               (mConnectionStatus == BLE_STATE_DISCONNECTED)) {
                    if(GattClient.LOG_LEVEL >= 1) {
                        Log.e(TAG, "onConnectionStateChange:Unexpected error! state: " + newState);
                    }
                    mConnectionStatus = BLE_STATE_DISCONNECTED;
                    PrintStr.setLength(0);
                    PrintStr.append("Failed to connect, please try again!!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    return;
                }

                int bondState = mDevice.getBondState();

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "onConnectionStateChange:DISCONNECTED "
                            + " remoteDevice: " + gatt.getDevice().getAddress());
                    mConnectionStatus = BLE_STATE_DISCONNECTED;
                    PrintStr.setLength(0);
                    PrintStr.append("Disconnected with remote device: ");
                    PrintStr.append(gatt.getDevice().getAddress());
                    SocketServer.sendSocketData(PrintStr.toString());
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "onConnectionStateChange:CONNECTED "
                            + " remoteDevice: " + gatt.getDevice().getAddress());
                    mConnectionStatus = BLE_STATE_CONNECTED;
                    PrintStr.setLength(0);
                    PrintStr.append("Connected to remote device: ");
                    PrintStr.append(gatt.getDevice().getName());
                    SocketServer.sendSocketData(PrintStr.toString());
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                       Log.i(TAG, "Device paired");
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                PrintStr.setLength(0);
                PrintStr.append("Gatt Service discovery!!");
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onService discovery success");
                    mServices = gatt.getServices();
                    if (mServices == null || mServices.size() <= 0) {
                        Log.e(TAG, "no services found");
                        PrintStr.setLength(0);
                        PrintStr.append("No services found!");
                        SocketServer.sendSocketData(PrintStr.toString());
                        return;
                    }
                    for (BluetoothGattService service : mServices) {
                        Log.d(TAG, "Found service: " + service.getUuid());
                        PrintStr.append("\n------------------------------------------------\n");
                        PrintStr.append("Service UUID:");
                        PrintStr.append(service.getUuid());
                        mServiceUUID.add(service.getUuid());
                        mCharacteristics = service.getCharacteristics();
                        for (BluetoothGattCharacteristic
                                      characteristic : mCharacteristics) {
                            Log.d(TAG, "Found Char: " + characteristic.getUuid());
                            PrintStr.append("\nCharacteristic UUID:");
                            PrintStr.append(characteristic.getUuid());
                            mCharUUID.add(characteristic.getUuid());
                            mDescriptors = characteristic.getDescriptors();
                            for (BluetoothGattDescriptor descriptor : mDescriptors) {
                                Log.d(TAG, "Found Desc: " + descriptor.getUuid());
                                PrintStr.append("\nDescriptor UUID:");
                                PrintStr.append(descriptor.getUuid());
                                mDescUUID.add(descriptor.getUuid());
                             }
                         }
                    }
                    PrintStr.append("\nGatt Service discovery done!!");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.d(TAG, "onServicesDiscovered received: " + status);
                    PrintStr.setLength(0);
                    PrintStr.append("Service Discovery failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
                is_op_in_progress = false;
            }


            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy,
                                        int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "on Phy updated:"
                         + " tx phy " + txPhy + " rx phy " + rxPhy +" status " + status);
                    PrintStr.setLength(0);
                    PrintStr.append("Phy Update done, Tx Phy :");
                    PrintStr.append(txPhy);
                    PrintStr.append(" Rx Phy :");
                    PrintStr.append(rxPhy);
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "phy update failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Phy Update failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic,
                                                int status){
                if ((status == GATT_SUCCESS)) {
                    /* get value based on format type */
                    String value = fetchFormatValue(characteristic, GATT_FORMAT_STRING);
                    PrintStr.setLength(0);
                    PrintStr.append("Char Value(string) is :");
                    PrintStr.append(value);
                    SocketServer.sendSocketData(PrintStr.toString());
                    String strvalue = fetchFormatValue(characteristic, GATT_FORMAT_INT);
                    PrintStr.setLength(0);
                    PrintStr.append("Char Value(int) is :");
                    PrintStr.append(strvalue);
                    SocketServer.sendSocketData(PrintStr.toString());
                    String hexvalue = fetchFormatValue(characteristic, 0);
                    PrintStr.setLength(0);
                    PrintStr.append("Char Value(hex) is :");
                    PrintStr.append(hexvalue);
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Char read failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic Read failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
                is_op_in_progress = false;
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic,
                                                int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "onCharacteristicWrite: " + status);
                    if(!reliable_write){
                        String strvalue = fetchFormatValue(characteristic, GATT_FORMAT_STRING);
                        PrintStr.setLength(0);
                        PrintStr.append("Characteristic Value (string) Written to ");
                        PrintStr.append(strvalue);
                        SocketServer.sendSocketData(PrintStr.toString());
                        String Value = fetchFormatValue(characteristic, GATT_FORMAT_INT);
                        PrintStr.setLength(0);
                        PrintStr.append("Characteristic Value (int) Written to ");
                        PrintStr.append(Value);
                        SocketServer.sendSocketData(PrintStr.toString());
                        String hexvalue = fetchFormatValue(characteristic, 0);
                        PrintStr.setLength(0);
                        PrintStr.append("Characteristic Value (hex) Written to ");
                        PrintStr.append(hexvalue);
                        SocketServer.sendSocketData(PrintStr.toString());
                    }
                    if(reliable_write) {
                        String value = new String(characteristic.getValue());
                        /* check the value written is correct or not */
                        if(offset_value.equals(value)) {
                            Log.d(TAG, "Data matched, proceeding!!");
                            if(!reliable_write_no_more_data) {
                                /*check if the total data is written, if no write*/
                                if(total_length > length_offset + mtu_size-5) {
                                    offset_value = RdWrReliableClass.Value.substring(
                                           length_offset,(length_offset + mtu_size-5));
                                    length_offset +=  (mtu_size - 5);
                                    /* set value according to format type */
                                    if(RdWrReliableClass.Format_type == GATT_FORMAT_STRING) {
                                         characteristic.setValue(new String(offset_value));
                                    }
                                    else if(RdWrReliableClass.Format_type == GATT_FORMAT_INT){
                                         characteristic.setValue(offset_value.getBytes());
                                    }
                                    else{
                                        Log.e(TAG, "using default format");
                                        characteristic.setValue(offset_value.getBytes());
                                    }
                                    mgattClient.mBluetoothGatt.writeCharacteristic(
                                                characteristic);
                                } else if(total_length <= length_offset + mtu_size - 5) {
                                    /* last chunk */
                                    offset_value = RdWrReliableClass.Value.substring(length_offset,
                                          total_length);
                                    reliable_write_no_more_data = true;
                                    length_offset = total_length - (mtu_size - 5);
                                    /* set value according to format type */
                                    if(RdWrReliableClass.Format_type == GATT_FORMAT_STRING) {
                                        characteristic.setValue(new String(offset_value));
                                    }
                                    else if(RdWrReliableClass.Format_type == GATT_FORMAT_INT){
                                        characteristic.setValue(offset_value.getBytes());
                                    }
                                    else{
                                        Log.e(TAG, "using default format");
                                        characteristic.setValue(offset_value.getBytes());
                                    }
                                    mgattClient.mBluetoothGatt.writeCharacteristic(
                                                characteristic);
                                }
                            }
                        } else {
                            /* abort reliable write if the value written doesn't match*/
                            Log.e(TAG, "Data doesn't match");
                            /*abort*/
                            mgattClient.mBluetoothGatt.abortReliableWrite();
                            reliable_write = false;
                            length_offset = 0;
                            reliable_write_no_more_data = false;
                            offset_value = null;
                        }
                    }
                } else {
                    Log.i(TAG, "write characteristic failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic Write failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                    reliable_write = false;
                    length_offset = 0;
                    reliable_write_no_more_data = false;
                    offset_value = null;
                }
                is_op_in_progress = false;
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                int status) {
                if ((status == GATT_SUCCESS)) {
                    byte[] value = descriptor.getValue();
                    StringBuilder result = new StringBuilder();
                    for (byte temp : value) {
                        result.append(String.format("%d ", temp));
                    }
                    Log.i(TAG, "Descriptor value is "+ result.toString());
                    PrintStr.setLength(0);
                    PrintStr.append("Descriptor Value Read is :");
                    PrintStr.append(result.toString());
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "read descriptor failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Descriptor Read failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
                is_op_in_progress = false;
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt,
                                            BluetoothGattDescriptor desc, int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "onDescriptorWrite: " + status);
                    byte[] value = desc.getValue();
                    StringBuilder result = new StringBuilder();
                    for (byte temp : value) {
                        result.append(String.format("%d ", temp));
                    }
                    Log.i(TAG, "Descriptor value is "+ result.toString());
                    PrintStr.setLength(0);
                    PrintStr.append("Descriptor Value Written to ");
                    PrintStr.append(result.toString());
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "write descriptor failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Descriptor Write failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
                is_op_in_progress = false;
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                if (status == GATT_SUCCESS) {
                    Log.i(TAG, "onReliableWriteCompleted: " + status);
                    PrintStr.setLength(0);
                    PrintStr.append("onReliableWriteCompleted completed with success");
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "onReliableWriteCompleted failed");
                    PrintStr.setLength(0);
                    PrintStr.append("reliable write failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                if(status == GATT_SUCCESS){
                    Log.i(TAG, "Read Phy: Tx Phy: "+txPhy+"Rx Phy: "+rxPhy);
                    PrintStr.setLength(0);
                    PrintStr.append("Current Phy: Tx Phy :");
                    PrintStr.append(txPhy);
                    PrintStr.append(" Rx Phy :");
                    PrintStr.append(rxPhy);
                    SocketServer.sendSocketData(PrintStr.toString());
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
                Log.i(TAG, "onCharacteristicChanged");
                String value = new String(characteristic.getValue());
                PrintStr.setLength(0);
                PrintStr.append("Characteristic value changed to ");
                PrintStr.append(value);
                SocketServer.sendSocketData(PrintStr.toString());
            }

            @Override
            public void onMtuChanged (BluetoothGatt gatt, int mtu, int status) {
               if (status == GATT_SUCCESS) {
                    Log.i(TAG, "Gatt updated MTU" + mtu);
                    mtu_size = mtu;
                    PrintStr.setLength(0);
                    PrintStr.append("MTU updated to :");
                    PrintStr.append(mtu);
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Failed to change Mtu size");
                    PrintStr.setLength(0);
                    PrintStr.append("MTU Exchange failed with status: "+ status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onReadRemoteRssi (BluetoothGatt gatt, int rssi, int status) {
               if (status == GATT_SUCCESS) {
                    Log.i(TAG, "Rssi:" + rssi);
                    PrintStr.setLength(0);
                    PrintStr.append("RSSI :");
                    PrintStr.append(rssi);
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Failed to read rssi");
                    PrintStr.setLength(0);
                    PrintStr.append("RSSI read with status: ");
                    PrintStr.append(status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onServiceChanged (BluetoothGatt gatt) {
               Log.i(TAG, "OnServiceChanged");
               PrintStr.setLength(0);
               PrintStr.append("One of the Services is changed!!\n");
               PrintStr.append("Please re-discover services as gatt db is out of sync");
               SocketServer.sendSocketData(PrintStr.toString());
            }
        };

        public void connect(BluetoothDevice device, int initPhy, boolean autoConnect){
            if((BleAppService.bleAdapter!=null) && (mConnectionStatus == BLE_STATE_DISCONNECTED)) {
                Log.i(TAG, "Gatt Connect");
                mDevice = device;
                int deviceType = mDevice.getType();
                Log.i(TAG, "deviceType: " + deviceType);
                PrintStr.setLength(0);
                PrintStr.append("Remote deviceType: ");
                PrintStr.append(deviceType);
                PrintStr.append("\ninit phy: ");
                PrintStr.append(initPhy);
                PrintStr.append("\nauto connect: ");
                PrintStr.append(autoConnect);
                SocketServer.sendSocketData(PrintStr.toString());
                mConnectionStatus = BLE_STATE_CONNECTING;
                mBluetoothGatt = mDevice.connectGatt(mcontext, autoConnect, mGattCallbacks,TRANSPORT_LE, initPhy);
            }
        }

        public void disconnect() {
            mgattClient.mBluetoothGatt.disconnect();
        }

        public String fetchFormatValue(BluetoothGattCharacteristic characteristic, int format_type) {
            /* String format */
            if(format_type == GATT_FORMAT_STRING) {
                String value = characteristic.getStringValue(0);
                Log.i(TAG, "Characteristic value(string) is "+ value);
                return value;
            }
            /* Int format */
            else if(format_type == GATT_FORMAT_INT){
                byte[] value = characteristic.getValue();
                StringBuilder result = new StringBuilder();
                for (byte temp : value) {
                    result.append(String.format("%d ", temp));
                }
                Log.i(TAG, "Characteristic value(int) is "+ result.toString());
                return (result.toString());
            }
            /* Default format - Hex */
            else{
                Log.e(TAG, "Default format");
                byte[] value = characteristic.getValue();
                StringBuilder result = new StringBuilder();
                for (byte temp : value) {
                    result.append(String.format("%02x ", temp));
                }
                Log.i(TAG, "Characteristic value(hex) is "+ result.toString());
                return (result.toString());
            }
        }
    }

    public class GattClientMessageHandler extends Handler {
        Context mMsgContext;
        private static final String TAG = "GattClientMessageHandler";

        public GattClientMessageHandler(Context contxt, Looper looper) {
            super(looper);
            mMsgContext = contxt;
            if(GattClient.LOG_LEVEL >= 2)
                Log.d(TAG, "GattClientMessageHandler");
        }

        @Override
        public void handleMessage(Message msg) {
            if (GattClient.LOG_LEVEL >= 2)
                Log.d(TAG, "Handler(): msg = " + msg.what);

            switch (msg.what) {
                case MSG_START_BLE_CONNECT:
                    /* start scan with filters and initiate conn with the result */
                    Scan scn = (Scan) msg.obj;
                    if(BleAppService.mScannerService.mScanstatus) {
                        PrintStr.setLength(0);
                        PrintStr.append("Connect failed, there is an ongoing scan");
                        SocketServer.sendSocketData(PrintStr.toString());
                    } else {
                        processCheckAndStartBleScan(scn);
                    }
                    break;
              case MSG_START_BLE_CONNECT_TO_BDADDR:
                    Scan init = (Scan) msg.obj;
                    processConnectToBdaddr(init);
                    break;
                case MSG_START_CANCEL_CONNECT:
                    processCancelConnect();
                    break;
                case MSG_BLE_SCAN_DEV_FOUND:
                    int  primaryphy= (int) msg.arg1;
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    processScanDevFound(device, primaryphy);
                    break;
                case MSG_START_BLE_PHY_UPDATE:
                    PhyUpdate phyUpdate = (PhyUpdate) msg.obj;
                    processPhyUpdateReq(phyUpdate);
                    break;
                case MSG_START_BLE_GATT_CONFIGURE_MTU_SIZE:
                    int Mtu_Size = (int) msg.obj;
                    processConfigureMtuReq(Mtu_Size);
                    break;
                case MSG_BLE_GATT_REQ_CONN_PRIORITY:
                    int conn_priority = (int) msg.obj;
                    processConnPriorityReq(conn_priority);
                    break;
               case MSG_START_BLE_READ_PHY:
                    processReadPhyReq();
                    break;
                case MSG_START_BLE_GATT_DISC:
                    processGattDiscovery();
                    break;
                case MSG_START_BREDR_DISC:
                    processBREDRDiscovery();
                    break;
                case MSG_READ_REMOTE_RSSI:
                    mgattClient.mBluetoothGatt.readRemoteRssi();
                    break;
                case MSG_READ_CHAR_UUID:
                    Log.i(TAG, "Not Supported!!");
                    break;
                case MSG_DISC_SRVC_UUID:
                    Log.i(TAG, "Not Supported!!");
                    break;
                case MSG_START_BLE_GATT_DISCONNECT:
                    processDisconnectReq();
                    break;
                case MSG_START_BLE_GATT_UNREG:
                    processCloseReq();
                    break;
                case MSG_START_BLE_GATT_WRITE_READ_CHAR:
                    RdWrClass = (ReadWriteOp) msg.obj;
                    processGattReadWriteCharReq(RdWrClass);
                    break;
                case MSG_START_BLE_GATT_WRITE_READ_DESC:
                    RdWrClass = (ReadWriteOp) msg.obj;
                    processGattReadWriteDescReq(RdWrClass);
                    break;
                case MSG_REGISTER_BLE_GATT_NOTIFICATIONS:
                    RdWrClass = (ReadWriteOp) msg.obj;
                    processGattRegisterNotifications(RdWrClass);
                    break;
                case MSG_DEREGISTER_BLE_GATT_NOTIFICATIONS:
                    RdWrClass = (ReadWriteOp) msg.obj;
                    processGattDeregisterNotifications(RdWrClass);
                    break;
                case MSG_START_BLE_GATT_RELIABLE_WRITE:
                    RdWrReliableClass = (ReadWriteOp) msg.obj;
                    processGattStartReliableWrite(RdWrReliableClass);
                    break;
                case MSG_START_BLE_GATT_ABORT_RELIABLE_WRITE:
                    processGattAbortReliableWrite();
                    break;
                case MSG_START_BLE_GATT_EXECUTE_WRITE:
                    processGattExecuteWrite();
                    break;
                case MSG_START_BLE_COC_CONNECT:
                    LecocConnect LecocConnClass = (LecocConnect) msg.obj;
					processGattLeCocConnect(LecocConnClass);
					break;
                case MSG_START_BLE_COC_OFFLOAD_CONNECT:
                    LecocOffloadConnect LecocOffloadConnClass = (LecocOffloadConnect) msg.obj;
                    processGattLeCocOffloadConnect(LecocOffloadConnClass);
                    break;
				case MSG_START_BLE_COC_WRITE:
					processGattLeCocWrite((int)msg.obj);
					break;
				case MSG_START_BLE_LISTEN:
                    boolean SecureFlag = (boolean) msg.obj;
				    processGattLeCocListen(SecureFlag);
					break;
                case MSG_START_BLE_COC_CLOSE:
                    boolean SecureFlag1 = (boolean) msg.obj;
				    processGattLeCocClose(SecureFlag1);
					break;
                case MSG_START_BLE_COC_OFFLOAD_LISTEN:
                    LecocOffloadListen LecocOffloadListenClass = (LecocOffloadListen) msg.obj;
                    processGattLeCocOffloadListen(LecocOffloadListenClass);
                    break;
                default:
                    Log.e(TAG, "Unknown Operation");
                    break;
            }
        }

        private void processCheckAndStartBleScan(Scan scn) {
            Log.i(TAG, "starting scanning");
            BleAppService.mScannerService.set_scan_parameters(scn);
        }

        private void processConnectToBdaddr(Scan init) {
            if(BleAppService.bleAdapter != null) {
                Log.i(TAG, "Connect to Address: " + init.DeviceAddress);
                BluetoothDevice remoteDevice = BleAppService.bleAdapter.getRemoteDevice(init.DeviceAddress);
                mgattClient.connect(remoteDevice, init.initPhy, init.autoConnect);
            }
        }

        private void processCancelConnect() {
            Log.i(TAG, "processCancelConnect mConnectionStatus: " + mConnectionStatus
                    + " mScanStatus: "+ BleAppService.mScannerService.mScanstatus);
            if(BleAppService.mScannerService.mScanstatus) {
                BleAppService.mScannerService.stopScan();
                PrintStr.setLength(0);
                PrintStr.append("Scan Stopped!");
                SocketServer.sendSocketData(PrintStr.toString());
            } else if (mConnectionStatus == BLE_STATE_CONNECTING) {
                mConnectionStatus = BLE_STATE_DISCONNECTED;
                mgattClient.disconnect();
                PrintStr.setLength(0);
                PrintStr.append("Connection cancelled!");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        private void processScanDevFound(BluetoothDevice device, int primaryphy) {
            Log.i(TAG, "matchFoundEvent Address:" + device.getAddress());
            if(BleAppService.mScannerService.mScanstatus) {
                BleAppService.mScannerService.stopScan();
            }
            mgattClient.connect(device, primaryphy, false);
        }


        private void processReadPhyReq(){
            Log.i(TAG, "Read Phy");
            mgattClient.mBluetoothGatt.readPhy();
        }

        private void processDisconnectReq() {
            Log.i(TAG, "Disconnecting!");
            mConnectionStatus = BLE_STATE_DISCONNECTING;
            mgattClient.disconnect();
        }

        private void processCloseReq() {
            Log.i(TAG, "Unregistering!");
            PrintStr.setLength(0);
            PrintStr.append("Unregistering!");
            SocketServer.sendSocketData(PrintStr.toString());
            mgattClient.mBluetoothGatt.close();
        }

        private void processPhyUpdateReq(PhyUpdate phyUpdate){
            Log.i(TAG, "Phy Update");
            mgattClient.mBluetoothGatt.setPreferredPhy(phyUpdate.txPhy,
                                        phyUpdate.rxPhy, phyUpdate.phyOpt);
        }

        private void processConfigureMtuReq(int Mtu_Size) {
            Log.i(TAG, "Configure mtu");
            mgattClient.mBluetoothGatt.requestMtu(Mtu_Size);
        }

        private void processConnPriorityReq(int conn_pri) {
            Log.i(TAG, "Request connection priority");
            mgattClient.mBluetoothGatt.requestConnectionPriority(conn_pri);
        }

        private void processGattDiscovery() {
            Log.i(TAG, "Gatt Service Discovery");
            mgattClient.mBluetoothGatt.discoverServices();
        }

        private void processBREDRDiscovery() {
            Log.i(TAG, "BREDR Discovery");
            BleAppService.bleAdapter.startDiscovery();
        }

        private void processGattReadWriteCharReq(ReadWriteOp RdWrClass) {
            BluetoothGattCharacteristic mCharacteristic;
            BluetoothGattService mService;
            boolean status = true;

            if(is_op_in_progress) {
                Log.d(TAG, "Operation in progress");
                PrintStr.setLength(0);
                PrintStr.append("Operation in Progress, please wait until it is finished!!");
                SocketServer.sendSocketData(PrintStr.toString());
                return;
            }
            is_op_in_progress = true;

            Log.d(TAG, "Service UUID:"+RdWrClass.Srvc_uuid);

            if(mServiceUUID.contains(UUID.fromString(RdWrClass.Srvc_uuid))) {
                Log.d(TAG, "Service is found");
                if (!mCharUUID.contains(UUID.fromString(RdWrClass.Char_uuid))) {
                    Log.e(TAG, "Characteristic not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    is_op_in_progress = false;
                    return;
                }
            } else {
                Log.e(TAG, "Service is not found");
                PrintStr.setLength(0);
                PrintStr.append("Service not found!");
                SocketServer.sendSocketData(PrintStr.toString());
                is_op_in_progress = false;
                return;
            }
            mService = mgattClient.mBluetoothGatt.getService(UUID.fromString(
                                   RdWrClass.Srvc_uuid));
            mCharacteristic = mService.getCharacteristic(UUID.fromString(RdWrClass.Char_uuid));
            Log.d(TAG, "Srvc uuid" + mService.getUuid().toString() +
                       "char uuid:" + mCharacteristic.getUuid().toString());

            if(RdWrClass.operation == GATT_READ) {
                status = mgattClient.mBluetoothGatt.readCharacteristic(mCharacteristic);
                if(status != true) {
                    Log.e(TAG, "Read Char failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Read Char failed!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    is_op_in_progress = false;
                }
            }
            else if(RdWrClass.operation == GATT_WRITE) {
                /* Check for write type */
                if(RdWrClass.Write_type != 0) {
                    mCharacteristic.setWriteType(RdWrClass.Write_type);
                    /* Check for length and set value */
                    if((RdWrClass.Value.length() >= (mtu_size -15)) &&
                        (RdWrClass.Write_type ==
                                BluetoothGattCharacteristic.WRITE_TYPE_SIGNED)) {
                        Log.e(TAG, "Write failed: Length cannot be more than" +
                                "ATT_MTU-15 for signed write");
                        PrintStr.setLength(0);
                        PrintStr.append("Write failed: Length cannot be more than" +
                                "ATT_MTU-15 for write!");
                        SocketServer.sendSocketData(PrintStr.toString());
                        is_op_in_progress = false;
                        return;
                    }
                    else if ((RdWrClass.Value.length() >= (mtu_size - 3)) &&
                            (RdWrClass.Write_type !=
                                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)) {
                        Log.e(TAG, "Write failed: Length cannot be more than" +
                                "ATT_MTU-3 for write");
                        PrintStr.setLength(0);
                        PrintStr.append("Write failed: Length cannot be more than" +
                                "ATT_MTU-3 for write!");
                        SocketServer.sendSocketData(PrintStr.toString());
                        is_op_in_progress = false;
                        return;
                    }
                } else {
                    mCharacteristic.setWriteType(
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    if(RdWrClass.Value.length() >= (mtu_size - 3)) {
                        Log.e(TAG, "Write failed: Length cannot be more than" +
                                "ATT_MTU-3 for write");
                        PrintStr.setLength(0);
                        PrintStr.append("Write failed: Length cannot be more than" +
                                "ATT_MTU-3 for write!");
                        SocketServer.sendSocketData(PrintStr.toString());
                        is_op_in_progress = false;
                        return;
                    }
                }
                Log.e(TAG, "using default format");
                mCharacteristic.setValue((RdWrClass.Value).getBytes());
                mgattClient.mBluetoothGatt.writeCharacteristic(
                             mCharacteristic);
            } else {
                is_op_in_progress = false;
                Log.e(TAG, "invalid operation");
                PrintStr.setLength(0);
                PrintStr.append("Invalid Operation!");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        private void processGattRegisterNotifications(ReadWriteOp RdWrClass){
            BluetoothGattCharacteristic mCharacteristic;
            BluetoothGattService mService;
            BluetoothGattDescriptor mDescriptor;
            boolean status = true;

            Log.d(TAG, "Service UUID:"+RdWrClass.Srvc_uuid);

            if(mServiceUUID.contains(UUID.fromString(RdWrClass.Srvc_uuid))) {
                if (!mCharUUID.contains(UUID.fromString(RdWrClass.Char_uuid))) {
                    Log.e(TAG, "Characteristic not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    return;
                }
                mService = mgattClient.mBluetoothGatt.getService(UUID.fromString(
                                   RdWrClass.Srvc_uuid));
                mCharacteristic = mService.getCharacteristic(UUID.fromString(
                                       RdWrClass.Char_uuid));
                mDescriptor = mCharacteristic.getDescriptor(
                                          CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR);
                if (mDescriptor == null) {
                    Log.e(TAG, "Descriptor not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    return;
                }
             } else {
                Log.e(TAG, "Service is not found");
                PrintStr.setLength(0);
                PrintStr.append("Service not found!");
                SocketServer.sendSocketData(PrintStr.toString());
                return;
            }

            mgattClient.mBluetoothGatt.setCharacteristicNotification(mCharacteristic, true);
            if(RdWrClass.operation == OP_NOTIFICATIONS) {
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else if(RdWrClass.operation == OP_INDICATIONS) {
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            } else {
                mDescriptor.setValue(ENABLE_NOTIFICATION_INDICATION_VALUE);
            }
            mgattClient.mBluetoothGatt.writeDescriptor(mDescriptor);
            Log.d(TAG, "Registering notifications");
        }

        private void processGattDeregisterNotifications(ReadWriteOp RdWrClass){
            BluetoothGattCharacteristic mCharacteristic;
            BluetoothGattService mService;
            BluetoothGattDescriptor mDescriptor;
            boolean status = true;

           if(mServiceUUID.contains(UUID.fromString(RdWrClass.Srvc_uuid)))  {
                if (!mCharUUID.contains(UUID.fromString(RdWrClass.Char_uuid))) {
                    Log.e(TAG, "Characteristic not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    return;
                }
                mService = mgattClient.mBluetoothGatt.getService(UUID.fromString(
                                   RdWrClass.Srvc_uuid));
                mCharacteristic = mService.getCharacteristic(UUID.fromString(
                                       RdWrClass.Char_uuid));
                mDescriptor = mCharacteristic.getDescriptor(
                                          CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR);
                if (mDescriptor == null) {
                    Log.e(TAG, "Descriptor not found");
                    PrintStr.setLength(0);
                    PrintStr.append(" Descriptor not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    return;
                }
            } else {
                Log.e(TAG, "Service is not found");
                PrintStr.setLength(0);
                PrintStr.append("Service not found!");
                SocketServer.sendSocketData(PrintStr.toString());
                return;
            }
            mgattClient.mBluetoothGatt.setCharacteristicNotification(mCharacteristic, false);
            mDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mgattClient.mBluetoothGatt.writeDescriptor(mDescriptor);
            Log.d(TAG, "Deregistering notifications");
        }

        private void processGattStartReliableWrite(ReadWriteOp RdWrReliableClass){
            BluetoothGattCharacteristic mCharacteristic;
            BluetoothGattService mService;
            boolean status = true;

            if(is_op_in_progress) {
                Log.d(TAG, "Operation in progress");
                PrintStr.setLength(0);
                PrintStr.append("Operation in Progress, please wait until it is finished!!");
                SocketServer.sendSocketData(PrintStr.toString());
                return;
            }
            is_op_in_progress = true;


            if(mServiceUUID.contains(UUID.fromString(RdWrReliableClass.Srvc_uuid)))  {
                Log.d(TAG, "mService is not null");
                if (!mCharUUID.contains(UUID.fromString(RdWrReliableClass.Char_uuid))) {
                    Log.e(TAG, "Characteristic not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    is_op_in_progress = false;
                    return;
                }
            } else {
                Log.e(TAG, "Service is not found");
                PrintStr.setLength(0);
                PrintStr.append("Service not found!");
                SocketServer.sendSocketData(PrintStr.toString());
                is_op_in_progress = false;
                return;
            }

            mService = mgattClient.mBluetoothGatt.getService(UUID.fromString(
                                   RdWrReliableClass.Srvc_uuid));
            mCharacteristic = mService.getCharacteristic(UUID.fromString(
                                       RdWrReliableClass.Char_uuid));
            if(mgattClient.mBluetoothGatt.beginReliableWrite()) {
                reliable_write = true;
                Log.i(TAG, "beginReliableWrite successful!");
            } else {
                Log.e(TAG, "beginReliableWrite failed");
                PrintStr.setLength(0);
                PrintStr.append("ReliableWrite failed!");
                SocketServer.sendSocketData(PrintStr.toString());
                is_op_in_progress = false;
                return;
            }

            mCharacteristic.setWriteType(
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            total_length = RdWrReliableClass.Value.length();
            if(total_length >= (mtu_size - 5)) {
                reliable_write_no_more_data = false;
                offset_value = RdWrReliableClass.Value.substring(
                        length_offset, (mtu_size - 5));
                length_offset +=  (mtu_size - 5);
                /* set value according to format type */
                if(RdWrReliableClass.Format_type == GATT_FORMAT_STRING) {
                    mCharacteristic.setValue(new String(offset_value));
                }
                else if(RdWrReliableClass.Format_type == GATT_FORMAT_INT){
                    mCharacteristic.setValue(offset_value.getBytes());
                }
                else{
                    Log.e(TAG, "using default format");
                    mCharacteristic.setValue(offset_value.getBytes());
                }
            } else {
                length_offset = total_length;
                reliable_write_no_more_data = true;
                offset_value = String.valueOf(RdWrReliableClass.Value);
                Log.d(TAG, "leng_offset"+length_offset+"offset_value"+offset_value.toString());
                /* set value according to format type */
                if(RdWrReliableClass.Format_type == GATT_FORMAT_STRING) {
                    mCharacteristic.setValue(new String(offset_value));
                }
                else if(RdWrReliableClass.Format_type == GATT_FORMAT_INT){
                    mCharacteristic.setValue(offset_value.getBytes());
                }
                else{
                    Log.e(TAG, "using default format");
                    mCharacteristic.setValue(offset_value.getBytes());
                }
            }
            mgattClient.mBluetoothGatt.writeCharacteristic(
                                        mCharacteristic);
        }

        private void processGattAbortReliableWrite() {
            mgattClient.mBluetoothGatt.abortReliableWrite();
            reliable_write = false;
            reliable_write_no_more_data = false;
            length_offset = 0;
            is_op_in_progress = false;
        }
/*
        private void processGattLeCoCRead(BluetoothSocket socket)
        {
            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

            // Cancel the accept thread because we only want to connect to one device
            if (mSecureAcceptThread != null) {
                mSecureAcceptThread.cancel();
                mSecureAcceptThread = null;
            }
            if (mInsecureAcceptThread != null) {
                mInsecureAcceptThread.cancel();
                mInsecureAcceptThread = null;
            }

            // Start the thread to manage the connection and perform transmissions
            mConnectedThread = new ConnectedThread(socket);
            mConnectedThread.start();
        }
*/
        private void processGattLeCocConnect(LecocConnect LecocConnClass) {
            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

            // Cancel the accept thread because we only want to connect to one device
            if (mSecureAcceptThread != null) {
                mSecureAcceptThread.cancel();
                mSecureAcceptThread = null;
            }
            if (mInsecureAcceptThread != null) {
                mInsecureAcceptThread.cancel();
                mInsecureAcceptThread = null;
            }
            mConnectedThread = new ConnectedThread(LecocConnClass);
            Thread t1 = new Thread(mConnectedThread);
            t1.start();
        }

        private void processGattLeCocOffloadConnect(LecocOffloadConnect LecocConnClass) {
            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

            // Cancel the accept thread because we only want to connect to one device
            if (mSecureAcceptThread != null) {
                mSecureAcceptThread.cancel();
                mSecureAcceptThread = null;
            }
            if (mInsecureAcceptThread != null) {
                mInsecureAcceptThread.cancel();
                mInsecureAcceptThread = null;
            }
            mConnectedThread = new ConnectedThread(LecocConnClass);
            Thread t1 = new Thread(mConnectedThread);
            t1.start();
        }

        private void processGattLeCocWrite (int ChunkSize) {
            Log.d(TAG, "processGattLeCocWrite ");
            if (mOffloadedSocket == true) {
                PrintStr.setLength(0);
                PrintStr.append("LECOC write is not possible with offloaded socket");
                SocketServer.sendSocketData(PrintStr.toString());
                return ;
            }
            mtxOperationRunnable = new TxOperationRunnable(ChunkSize);
			Thread t1 = new Thread(mtxOperationRunnable);
			t1.start();
		}
        private void processGattLeCocListen (boolean SecureFlag) {
            if (SecureFlag && mSecureAcceptThread == null) {
                mSecureAcceptThread = new AcceptThread(true);
                mSecureAcceptThread.start();
            }
            if (!SecureFlag && mInsecureAcceptThread == null) {
                mInsecureAcceptThread = new AcceptThread(false);
                mInsecureAcceptThread.start();
            }
            /*
               RxOperationRunnable mRxOperationRunnable = new RxOperationRunnable(SecureFlag);
               Thread t1 = new Thread(mRxOperationRunnable);
               t1.start();*/

        }

        private void processGattLeCocOffloadListen(LecocOffloadListen LecocOffloadListenObj) {
            Log.d(TAG, "processGattLeCocOffloadListen  ");
            if ( (LecocOffloadListenObj.mEncryption || LecocOffloadListenObj.mAuthentication) && mSecureAcceptThread == null) {
                Log.d(TAG, "processGattLeCocOffloadListen Secure ");
                mSecureAcceptThread = new AcceptThread(LecocOffloadListenObj);
                mSecureAcceptThread.start();
            }

            if (!(LecocOffloadListenObj.mEncryption || LecocOffloadListenObj.mAuthentication) && mInsecureAcceptThread == null) {
                Log.d(TAG, "processGattLeCocOffloadListen InSecure ");
                mInsecureAcceptThread = new AcceptThread(LecocOffloadListenObj);
                mInsecureAcceptThread.start();
            }
        }

        private void processGattLeCocClose (boolean SecureFlag) {
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            if(mtxOperationRunnable !=null) {
               mtxOperationRunnable = null;
              }
            if (SecureFlag &&  mSecureAcceptThread != null) {
                mSecureAcceptThread.cancel();
                mSecureAcceptThread = null;
            }
            if (!SecureFlag && mInsecureAcceptThread != null) {
                mInsecureAcceptThread.cancel();
                mInsecureAcceptThread = null;
            }

            mOffloadedSocket = false;
        }

        private void processGattExecuteWrite(){
            /*execute write*/
            if(mgattClient.mBluetoothGatt.executeReliableWrite()) {
                Log.i(TAG, "Execute Write Successful!");
                PrintStr.setLength(0);
                PrintStr.append("Execute Write Successful!");
                SocketServer.sendSocketData(PrintStr.toString());
                length_offset = 0;
                reliable_write = false;
                reliable_write_no_more_data = false;
                offset_value = null;
            } else {
                Log.e(TAG, "Execute Write Failed!");
                PrintStr.setLength(0);
                PrintStr.append("Execute Write failed!");
                SocketServer.sendSocketData(PrintStr.toString());
                length_offset = 0;
                reliable_write = false;
                reliable_write_no_more_data = false;
                offset_value = null;
            }
        }

        private void processGattReadWriteDescReq(ReadWriteOp RdWrClass) {
            BluetoothGattCharacteristic mCharacteristic;
            BluetoothGattService mService;
            BluetoothGattDescriptor mDescriptor;
            boolean status = true;

            if(is_op_in_progress) {
                Log.d(TAG, "Operation in progress");
                PrintStr.setLength(0);
                PrintStr.append("Operation in Progress, please wait until it is finished!!");
                SocketServer.sendSocketData(PrintStr.toString());
                return;
            }
            is_op_in_progress = true;

           if(mServiceUUID.contains(UUID.fromString(RdWrClass.Srvc_uuid)))  {
                if (!mCharUUID.contains(UUID.fromString(RdWrClass.Char_uuid))) {
                    Log.e(TAG, "Characteristic not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Characteristic not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    is_op_in_progress = false;
                    return;
                }
               if (!mDescUUID.contains(UUID.fromString(RdWrClass.Desc_uuid))) {
                    Log.e(TAG, "Descriptor not found");
                    PrintStr.setLength(0);
                    PrintStr.append("Descriptor not found!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    is_op_in_progress = false;
                    return;
                }
            } else {
                PrintStr.setLength(0);
                PrintStr.append("Service not found!");
                SocketServer.sendSocketData(PrintStr.toString());
                Log.e(TAG, "Service is not found");
                is_op_in_progress = false;
                return;
            }

            mService = mgattClient.mBluetoothGatt.getService(UUID.fromString(
                                    RdWrClass.Srvc_uuid));
            mCharacteristic = mService.getCharacteristic(UUID.fromString(
                                       RdWrClass.Char_uuid));
            mDescriptor = mCharacteristic.getDescriptor(UUID.fromString(
                                        RdWrClass.Desc_uuid));

            if(RdWrClass.operation == GATT_READ) {
                status = mgattClient.mBluetoothGatt.readDescriptor(mDescriptor);
                if(status != true) {
                    Log.e(TAG, "Read Desc failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Read Desc failed!");
                    SocketServer.sendSocketData(PrintStr.toString());
                    is_op_in_progress = false;
                }

                return;
            }

            if(RdWrClass.operation == GATT_WRITE) {
                mDescriptor.setValue((RdWrClass.Value).getBytes());
                mgattClient.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else {
                Log.e(TAG, "invalid operation");
                PrintStr.setLength(0);
                PrintStr.append("Invalid Operation!");
                SocketServer.sendSocketData(PrintStr.toString());
                is_op_in_progress = false;
            }
        }
    }
}
