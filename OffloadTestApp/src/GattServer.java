/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.
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
package org.codeaurora.bluetooth.offload_testapp;

import android.util.Log;

import android.content.Context;

import android.os.SystemProperties;
import android.os.Message;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.*;
import java.util.List;
import java.util.UUID;
import java.lang.*;

import libcore.io.IoUtils;

import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import android.bluetooth.BluetoothDevice;

import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothProfile;

public class GattServer{
    public GattServerMessageHandler mGattServerHandler = null;
    public BleGattServer mgattServer;
    public List<BluetoothDevice> connectedDevices;
    public HashMap<String,BluetoothGattService> Service_List;
    public Map<BluetoothGattCharacteristic,String> mMap_char;
    public Map<BluetoothDevice, List<BluetoothGattCharacteristic>>mMap_indicate;
    public Map<BluetoothDevice, List<BluetoothGattCharacteristic>>mMap_notify;
    public List<BluetoothGattService> services;
    public BluetoothDevice PrepWriteDevice;
    private Context mcontext = null;

    public static final int MSG_START_BLE_ADD_SERVICE = 0;
    public static final int MSG_START_BLE_REMOVE_SERVICE = 1;
    public static final int MSG_START_BLE_CLEAR_SERVICES = 2;
    public static final int MSG_START_BLE_GET_SERVICES = 3;
    public static final int MSG_START_BLE_PHY_UPDATE = 4;
    public static final int MSG_START_BLE_READ_PHY = 5;
    public static final int MSG_START_BLE_DISCONNECT = 6;
    public static final int MSG_START_BLE_REGISTER = 7;
    public static final int MSG_START_BLE_DEREGISTER = 8;
    public static final int MSG_GS_ACTION_MAX_VALUE = MSG_START_BLE_DEREGISTER;

    public static int LOG_LEVEL = 3;
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static final String base_uuid = "0000-1000-8000-00805f9b34fb";
    public static int mtu_size = 23;
    StringBuilder PrintStr = new StringBuilder();

    public GattServer(Context mcontext) {
        this.mcontext = mcontext;
        /* Initialize classes */
        mgattServer = new BleGattServer(mcontext);
        connectedDevices = MainActivity.mBluetoothManager.getConnectedDevices(
                            BluetoothProfile.GATT_SERVER);
        mMap_indicate = new HashMap<>();
        mMap_notify = new HashMap<>();
        mMap_char = new HashMap<>();
        /* Start Message handler */
        HandlerThread thread = new HandlerThread("GattServerHandler");
        thread.start();
        Looper looper = thread.getLooper();
        mGattServerHandler = new GattServerMessageHandler(mcontext, looper);
        Service_List = new HashMap<String,BluetoothGattService>();
    }

    /* Connection Class */
    public class BleGattServer {
        private static final String TAG = "BleGattServer";
        private BluetoothGattServer mBluetoothGattserver = null;
        private Context context;
        private int GATT_SUCCESS = 0x00;
        private int GATT_FAILURE = 0x101;
        Message msg;

        public BleGattServer(Context context) {
            this.context = context;
        }

        private final BluetoothGattServerCallback mGattServerCallbacks =
                                                      new BluetoothGattServerCallback() {
            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                if ((status == GATT_SUCCESS)) {
                    Log.d(TAG, "onServiceAdded() - handle=" + service.getInstanceId()
                                      + " uuid=" + service.getUuid() + " status=" + status);
                    PrintStr.setLength(0);
                    PrintStr.append("service Added/Modified with UUID :");
                    PrintStr.append(service.getUuid().toString());
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "AddService failed");
                    PrintStr.setLength(0);
                    PrintStr.append("AddService failed with status: " + status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                Log.d(TAG, "onConnectionStateChange() got connection event");
                if (newState == BluetoothProfile.STATE_CONNECTED &&
                        !connectedDevices.contains(device)) {
                    mGattServerHandler.processConnectReq(device);
                    PrintStr.setLength(0);
                    PrintStr.append("GattServer: Device Connected - ");
                    PrintStr.append(device.getAddress());
                    SocketServer.sendSocketData(PrintStr.toString());
                    connectedDevices.add(device);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED &&
                        connectedDevices.contains(device)) {
                    PrintStr.setLength(0);
                    PrintStr.append("GattServer: Device Disonnected - ");
                    PrintStr.append(device.getAddress());
                    SocketServer.sendSocketData(PrintStr.toString());
                    connectedDevices.remove(device);
                    if(mMap_notify.containsKey(device))
                       mMap_notify.remove(device);
                    else if(mMap_indicate.containsKey(device))
                       mMap_indicate.remove(device);
                }
            }

            @Override
            public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
                if ((status == GATT_SUCCESS)) {
                    Log.i(TAG, "on Phy updated:"
                         + " tx phy " + txPhy + " rx phy " + rxPhy +" status " + status);
                    /* Print Phy values along with bdAddress*/
                    PrintStr.setLength(0);
                    PrintStr.append("Phy Update done, BDAddress:");
                    PrintStr.append(device.getAddress());
                    PrintStr.append(" Tx Phy :");
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
            public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
                if (status == GATT_SUCCESS) {
                    Log.i(TAG, "on Read Phy: Tx Phy-"+txPhy+"Rx Phy:"+rxPhy);
                    PrintStr.setLength(0);
                    PrintStr.append("Phy Read, BDAddress:");
                    PrintStr.append(device.getAddress());
                    PrintStr.append(" Tx Phy :");
                    PrintStr.append(txPhy);
                    PrintStr.append(" Rx Phy :");
                    PrintStr.append(rxPhy);
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    Log.i(TAG, "Read Phy failed");
                    PrintStr.setLength(0);
                    PrintStr.append("Read Phy failed with status: " + status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                       int offset, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "sendResponse() - device: " + device.getAddress());
                if(offset + mtu_size < characteristic.getValue().length) {
                   Log.d(TAG, "offset index" + offset);
                   Log.d(TAG,"data length = " +
                              new String(Arrays.copyOfRange(
                              characteristic.getValue(),offset,offset+mtu_size-1)).length());
                   Log.d(TAG,"data values is = " +
                              new String(Arrays.copyOfRange(
                                       characteristic.getValue(),offset,offset+mtu_size-1)));
                   mgattServer.mBluetoothGattserver.sendResponse(device, requestId, GATT_SUCCESS,
                      0, Arrays.copyOfRange(characteristic.getValue(),offset,offset+mtu_size-1));
                }
                else {
                    Log.d(TAG, "offset val in else" + offset);
                    mgattServer.mBluetoothGattserver.sendResponse(device, requestId, GATT_SUCCESS,
                    0, Arrays.copyOfRange(characteristic.getValue(),offset,
                    characteristic.getValue().length));
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                         BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                         boolean responseNeeded, int offset, byte[] value) {
                Log.d(TAG, "onCharacteristicWriteRequest from device " + device.getName());
                if (preparedWrite) {
                    if (PrepWriteDevice == null ||
                        device.getAddress().equalsIgnoreCase(PrepWriteDevice.getAddress())) {
                        if (responseNeeded)
                            mgattServer.mBluetoothGattserver.sendResponse(device, requestId,
                                                                 GATT_SUCCESS, 0, value);
                        PrepWriteDevice = device;
                        if (mMap_char.containsKey(characteristic)){
                            String new_value = (String)mMap_char.get(
                                 characteristic)+ new String(value);
                            mMap_char.replace(characteristic,new_value);
                        } else {
                            mMap_char.put(characteristic , new String(value));
                        }
                    } else {
                          if (responseNeeded)
                            mgattServer.mBluetoothGattserver.sendResponse(device, requestId,
                                                                 GATT_FAILURE, 0, value);
                    }
                    return;
                }
                characteristic.setValue(value);
                if (responseNeeded) {
                    mgattServer.mBluetoothGattserver.sendResponse(device, requestId,
                                                                 GATT_SUCCESS, 0, value);
                }
                if ((characteristic.getProperties() &
                                          BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    for(BluetoothDevice devs:mMap_notify.keySet()) {
                        List<BluetoothGattCharacteristic> lList_notify = mMap_notify.get(devs);
                        if(lList_notify.contains(characteristic)) {
                           Log.d(TAG, "notify_sendresponse " + devs.getAddress());
                           mgattServer.mBluetoothGattserver.notifyCharacteristicChanged(
                                          devs, characteristic, false);
                        }
                    }
                }
                if ((characteristic.getProperties() &
                                         BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    for(BluetoothDevice devs:mMap_indicate.keySet()) {
                        List<BluetoothGattCharacteristic> lList_indicate = mMap_indicate.get(devs);
                        if(lList_indicate.contains(characteristic)) {
                           Log.d(TAG, "indicate_sendresponse " + devs.getAddress());
                           mgattServer.mBluetoothGattserver.notifyCharacteristicChanged(
                                          devs, characteristic, true);
                        }
                    }
                }
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                Log.d(TAG, "onExecuteWrite from device " + device.getAddress());
                if ( device.getAddress().equalsIgnoreCase(PrepWriteDevice.getAddress())) {
                    mgattServer.mBluetoothGattserver.sendResponse(device, requestId,
                                                                        GATT_SUCCESS,0,null);
                    for (BluetoothGattCharacteristic characteristic : mMap_char.keySet()) {
                        characteristic.setValue(((String)mMap_char.get(
                                 characteristic)).getBytes());
                        if ((characteristic.getProperties() &
                                        BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            for(BluetoothDevice devs:mMap_notify.keySet()) {
                                List<BluetoothGattCharacteristic> lList_notify =
                                                                     mMap_notify.get(devs);
                                if(lList_notify.contains(characteristic)) {
                                    Log.d(TAG, "notify_sendresponse " + devs.getAddress());
                                    mgattServer.mBluetoothGattserver.
                                    notifyCharacteristicChanged(
                                    devs, characteristic, false);
                                }
                            }
                        }
                        if ((characteristic.getProperties() &
                                         BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                            for(BluetoothDevice devs:mMap_indicate.keySet()) {
                                List<BluetoothGattCharacteristic> lList_indicate =
                                                                      mMap_indicate.get(devs);
                                if(lList_indicate.contains(characteristic)) {
                                    Log.d(TAG, "indicate_sendresponse " + devs.getAddress());
                                    mgattServer.mBluetoothGattserver.
                                    notifyCharacteristicChanged(
                                    devs, characteristic, true);
                                }
                            }
                        }
                    }
                    PrepWriteDevice = null;
                    mMap_char.clear();
                } else {
                    mgattServer.mBluetoothGattserver.sendResponse(device, requestId, GATT_FAILURE,
                                                                      0,null);
                }
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId,
                                                int offset, BluetoothGattDescriptor descriptor) {
                Log.d(TAG, "onDescriptorReadRequest from device " + device.getAddress());
                mgattServer.mBluetoothGattserver.sendResponse(device, requestId, GATT_SUCCESS,
                                                                      0, descriptor.getValue());
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                        BluetoothGattDescriptor descriptor,boolean preparedWrite,
                        boolean responseNeeded, int offset, byte[] value) {
               Log.d(TAG, "onDescriptorWriteRequest" + device.getAddress());
               descriptor.setValue(value);
               if(responseNeeded) {
                   mgattServer.mBluetoothGattserver.sendResponse(device, requestId,
                                                                GATT_SUCCESS, 0, value);
               }
               if (descriptor.getUuid().toString().
                                            equalsIgnoreCase(CLIENT_CHARACTERISTIC_CONFIG)) {
                   List<BluetoothGattCharacteristic> lList_notify = mMap_notify.get(device);
                   List<BluetoothGattCharacteristic> lList_indicate = mMap_indicate.get(device);
                   if((value[0] == 0x01) || (value[0] == 0x03)){
                        Log.d(TAG, "onDescriptorWriteRequest, adding dev to list" );
                       if (lList_notify != null) {
                           lList_notify.add(descriptor.getCharacteristic());
                       } else {
                           lList_notify = new ArrayList<BluetoothGattCharacteristic>();
                           lList_notify.add(descriptor.getCharacteristic());
                           mMap_notify.put(device, lList_notify);
                       }
                       if(mMap_indicate.containsKey(device))
                           mMap_indicate.remove(device);
                   } else if(value[0] == 0x02) {
                       if (lList_indicate != null) {
                           lList_indicate.add(descriptor.getCharacteristic());
                       } else {
                           lList_indicate = new ArrayList<BluetoothGattCharacteristic>();
                           lList_indicate.add(descriptor.getCharacteristic());
                           mMap_indicate.put(device, lList_indicate);
                       }
                       if (mMap_notify.containsKey(device))
                           mMap_notify.remove(device);
                   } else {
                       if(mMap_notify.containsKey(device) || mMap_indicate.containsKey(device)) {
                           if(mMap_notify.containsKey(device)) {
                               lList_notify.remove(descriptor.getCharacteristic());
                               if(lList_notify.size() == 0)
                                   mMap_notify.remove(device);
                           } else if(mMap_indicate.containsKey(device)) {
                               lList_indicate.remove(descriptor.getCharacteristic());
                               if(lList_indicate.size() == 0)
                                   mMap_indicate.remove(device);
                           }
                       }
                   }
                }
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                Log.d(TAG, "onMtuChanged" + device.getAddress());
                mtu_size = mtu;
                PrintStr.setLength(0);
                PrintStr.append("MTU updated to :");
                PrintStr.append(mtu);
                PrintStr.append(" BDAddress:");
                PrintStr.append(device.getAddress());
                SocketServer.sendSocketData(PrintStr.toString());
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                if(status == GATT_SUCCESS) {
                    Log.d(TAG, "OnNotificationsent" + device.getAddress());
                    PrintStr.setLength(0);
                    PrintStr.append("NotificationSent for BDAddress:");
                    PrintStr.append(device.getAddress());
                    SocketServer.sendSocketData(PrintStr.toString());
                } else {
                    PrintStr.setLength(0);
                    PrintStr.append("Notificationsent failed with status: " + status);
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            }
        };
    }

    public class GattServerMessageHandler extends Handler {
        Context mMsgContext;
        private static final String TAG = "GattServerMessageHandler";
        public GattServerMessageHandler(Context contxt, Looper looper) {
            super(looper);
            mMsgContext = contxt;
            if (GattClient.LOG_LEVEL >= 2)
                Log.d(TAG, "GattServerMessageHandler");
        }

        @Override
        public void handleMessage(Message msg) {
            if (GattClient.LOG_LEVEL >= 2)
                Log.d(TAG, "Handler(): msg = " + msg.what);
            AddServices AddServ;
            switch (msg.what) {
                case MSG_START_BLE_ADD_SERVICE:
                    AddServ = (AddServices) msg.obj;
                    processGattAddServiceReq(AddServ);
                    break;
                case MSG_START_BLE_REMOVE_SERVICE:
                    String uuid= (String)msg.obj;
                    processGattRemoveServiceReq(uuid);
                    PrintStr.setLength(0);
                    PrintStr.append("service removed sucessfully");
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_START_BLE_CLEAR_SERVICES:
                    processGattClearServiceReq();
                    PrintStr.setLength(0);
                    PrintStr.append("All services sucessfully cleared");
                    SocketServer.sendSocketData(PrintStr.toString());
                    break;
                case MSG_START_BLE_GET_SERVICES:
                    processGattGetServiceReq();
                    break;
                case MSG_START_BLE_PHY_UPDATE:
                    PhyUpdate phyUpdate = (PhyUpdate) msg.obj;
                    processPhyUpdateReq(phyUpdate);
                    break;
                case MSG_START_BLE_READ_PHY:
                    String mdeviceAddr = (String) msg.obj;
                    processReadPhyReq(mdeviceAddr);
                    break;
                case MSG_START_BLE_REGISTER:
                    startServer();
                    SocketServer.sendSocketData("Server registered!");
                    break;
                case MSG_START_BLE_DEREGISTER:
                    stopServer();
                    SocketServer.sendSocketData("Server deregistered!");
                    break;
                case MSG_START_BLE_DISCONNECT:
                    String bdAddr = (String) msg.obj;
                    processDisconnectReq(bdAddr);
                    break;
            }
        }

        private BluetoothGattService createService(UUID srvcUUID, UUID charuuid,
                                 List<Integer> props, List<Integer>perms, byte[] value){

            if(LOG_LEVEL >=2)
                Log.d(TAG, "srvcUUID to be added is:" + srvcUUID);
            BluetoothGattService srvc = mgattServer.mBluetoothGattserver.getService(srvcUUID);
            if(srvc == null) {
                srvc = new BluetoothGattService(srvcUUID,
                                                 BluetoothGattService.SERVICE_TYPE_PRIMARY);
            }
            int prop_ored = 0;
            for(int x:props){
                prop_ored = prop_ored | x;
            }
            int perm_ored = 0;
            for(int x:perms){
                perm_ored = perm_ored | x;
            }
            BluetoothGattCharacteristic charAdd;

            charAdd = new BluetoothGattCharacteristic(charuuid, prop_ored, perm_ored);
            if(value != null)
                charAdd.setValue(value);

            if((prop_ored & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (prop_ored & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0){
                BluetoothGattDescriptor desc =  new BluetoothGattDescriptor
                        (UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG),
                                perm_ored);

                desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                charAdd.addDescriptor(desc);
            }

            srvc.addCharacteristic(charAdd);

            if(srvc != null){
                return srvc;
            }
            return null;
        }

        private void processGattAddServiceReq(AddServices AddServ) {

            BluetoothGattService lService = createService(AddServ.lserviceUUID,
                              AddServ.lcharUUID, AddServ.lProps,AddServ.lPerms, AddServ.lvalue);
            if(lService != null) {
                if(!Service_List.containsKey(AddServ.lserviceUUID.toString().toUpperCase())){
                    Log.d(TAG, AddServ.lserviceUUID.toString());
                    Service_List.put(AddServ.lserviceUUID.toString().toUpperCase(),lService);
                    mgattServer.mBluetoothGattserver.addService(lService);
                } else {
                    mgattServer.mBluetoothGattserver.removeService(lService);
                    Service_List.remove(AddServ.lserviceUUID.toString().toUpperCase());
                    mgattServer.mBluetoothGattserver.addService(lService);
                    Service_List.put(AddServ.lserviceUUID.toString().toUpperCase(),lService);
                }
            } else {
                PrintStr.setLength(0);
                PrintStr.append("service was not Added/Modified");
                SocketServer.sendSocketData(PrintStr.toString());
          }
        }

        private void processGattRemoveServiceReq(String srvc_uuid) {

          if(Service_List.containsKey(srvc_uuid.toUpperCase())) {
            mgattServer.mBluetoothGattserver.removeService(
                                          Service_List.get(srvc_uuid.toUpperCase()));
            Service_List.remove(srvc_uuid.toUpperCase());
            Log.d(TAG, "Service Removed");
           } else {
            Log.d(TAG, "Service Not Found");
           }
         }

        private void processPhyUpdateReq(PhyUpdate phyUpdate) {
            Log.i(TAG, "Phy Update");
            String bdAddr = phyUpdate.remoteAddress.toUpperCase();
            if (BleAppService.bleAdapter.checkBluetoothAddress(bdAddr)) {
                BluetoothDevice mdevice = getRemoteDevice(bdAddr);
                if (mdevice != null) {
                    mgattServer.mBluetoothGattserver.setPreferredPhy(mdevice,
                          phyUpdate.txPhy, phyUpdate.rxPhy, phyUpdate.phyOpt);
                } else {
                    PrintStr.setLength(0);
                    PrintStr.append("Device not in connected list");
                    PrintStr.append(bdAddr);
                    PrintStr.append("  ");
                    SocketServer.sendSocketData(PrintStr.toString());
                }
            } else {
                PrintStr.setLength(0);
                PrintStr.append("Improper Device Address for set phy:");
                PrintStr.append(bdAddr);
                PrintStr.append("  ");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        private void processGattClearServiceReq() {
            Log.d(TAG, "Clearing all the services");
            mgattServer.mBluetoothGattserver.clearServices();
            Service_List.clear();
        }

        private void processGattGetServiceReq() {
            Log.d(TAG, "Listing all the services");
            PrintStr.setLength(0);
            PrintStr.append("Services UUIDS :");
            SocketServer.sendSocketData(PrintStr.toString());
            PrintStr.setLength(0);
            services = mgattServer.mBluetoothGattserver.getServices();
            for (int i = 0; i < services.size(); i++) {
                Log.d(TAG, services.get(i).getUuid().toString());
                PrintStr.append(services.get(i).getUuid().toString());
                PrintStr.append("  ");
            }
            SocketServer.sendSocketData(PrintStr.toString());
        }

        private void processReadPhyReq(String bdAddr) {
            Log.i(TAG, "Read Phy");
            BluetoothDevice mdevice = getRemoteDevice(bdAddr);
            if (mdevice != null) {
                mgattServer.mBluetoothGattserver.readPhy(mdevice);
            } else {
                PrintStr.setLength(0);
                PrintStr.append("Device not in connected list");
                PrintStr.append(bdAddr);
                PrintStr.append("  ");
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        private void processConnectReq(BluetoothDevice mdevice) {
            mgattServer.mBluetoothGattserver.connect(mdevice,false);
        }

        public void processDisconnectReq(String bdAddr) {
            if (mgattServer.mBluetoothGattserver != null) {
                BluetoothDevice remoteDevice = getRemoteDevice(bdAddr);
                if (remoteDevice != null) {
                    mgattServer.mBluetoothGattserver.cancelConnection(remoteDevice);
                }
            }
        }

        public void startServer() {
            mgattServer.mBluetoothGattserver  = MainActivity.mBluetoothManager.openGattServer(mcontext,
                    mgattServer.mGattServerCallbacks);
        }

        public void stopServer() {
            if (mgattServer.mBluetoothGattserver != null) {
                mgattServer.mBluetoothGattserver.close();
                mgattServer.mBluetoothGattserver = null;
            }
        }

        private BluetoothDevice getRemoteDevice(String address) {
            BluetoothDevice mdevice = null;
            Log.i(TAG,"address: " + address);
            for (int i = 0; i < connectedDevices.size(); i++)  {
                 Log.i(TAG,connectedDevices.get(i).getAddress());
                 if (connectedDevices.get(i).getAddress().equals(address)) {
                     Log.i(TAG, "Found match");
                     mdevice = connectedDevices.get(i);
                     break;
                 }
            }
            return mdevice;
        }
    }
}
