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

package org.codeaurora.bluetooth.offload_testapp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class ScannerService extends Service {
    //Log Details
    private static final String TAG = "ScannerService";
    public static int LOG_LEVEL = 6;

    //States of the service
    public static boolean mScanstatus = false; //Tracks if scan is in progress
    private Context mContext = null;

    //Actions
    public ScannerServiceMessageHandler mScannerHandler = null;
    private static final int MSG_START_BLE_SCAN = 0;
    private static final int MSG_STOP_BLE_SCAN = 1;
    private static final int MSG_SCAN_RESULT = 3;

    //Variables
    private ScanSettings mScanSettings;
    private ArrayList<ScanFilter> mScanFilters;
    private List<BluetoothDevice> mDeviceList;
    private BluetoothAdapter mBTAdapter = BleAppService.bleAdapter;
    private BluetoothLeScanner mBleScanner;
    StringBuilder PrintStr = new StringBuilder();

    public class LocalBinder extends Binder {
        ScannerService getService() {
            Log.d(TAG, "getService:scanner ");
            return ScannerService.this;
        }
    }

    //@Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

    //Service lifecycle methods
    @Override
    public void onCreate() {
        if(ScannerService.LOG_LEVEL >= 2)
            Log.d(TAG, "Scanner service is created.");

        mContext = this;
        //set default scan settings
        mScanSettings = null;
        mScanFilters = null;
        mBleScanner = mBTAdapter.getBluetoothLeScanner();
        mDeviceList = new ArrayList<BluetoothDevice>();
        HandlerThread thread = new HandlerThread("ScannerServiceHandler");
        thread.start();
        Looper looper = thread.getLooper();

        mScannerHandler = new ScannerServiceMessageHandler(this, looper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void startScan(ArrayList<ScanFilter> filters, ScanSettings settings) {
        mScanFilters = filters;
        mScanSettings = settings;
        if (mScanFilters != null) {
            Log.d(TAG, "Start Scan with filters");
        }
        Log.d(TAG, "Start Scan ");
        Message msg = mScannerHandler.obtainMessage(MSG_START_BLE_SCAN, null);
        mScannerHandler.sendMessage(msg);
    }

    public void set_scan_parameters(Scan scn) {
        ArrayList<ScanFilter> mfilter;
        ScanSettings settings;
        ScanParams params = new ScanParams(scn.DeviceName, scn.DeviceAddress,
                                            scn.ServiceUuid, scn.SvcMaskUuid,
                                            scn.ManufacturerId, scn.ManufacturerData,
                                            scn.ManufacturerMaskData, scn.ServiceDataUuid,
                                            scn.ServiceData, scn.SvcDataMask,
                                            scn.ScanMode, scn.CallbackType, scn.ResultType,
                                            scn.NumOfAdvMatches, scn.MatchMode,
                                            scn.ReportDelay, scn.ScanPhy, scn.legacy);
        if(ScannerService.LOG_LEVEL >= 2) {
            Log.d(TAG, "cbtpe:" + scn.CallbackType + "scan mode:" + scn.ScanMode +
                "noOfadvmatches:" + scn.NumOfAdvMatches + "match mode:" + scn.MatchMode +
                "result type:" + scn.ResultType + "report delay:" + scn.ReportDelay +
             "manu id:"+scn.ManufacturerId + "mandu data:"+scn.ManufacturerData +
             "manu data mask:" + scn.ManufacturerMaskData + "scan phy:" + scn.ScanPhy);
        }
        if(params == null) {
            Log.i(TAG, "params NULL");
        }

        if(scn.ScanPhy != BluetoothDevice.PHY_LE_1M && scn.legacy) {
            Log.e(TAG, "invalid combination, can't set LE Coded Phy for legacy scan");
            PrintStr.setLength(0);
            PrintStr.append("Invalid combination, can't set LE Coded Phy for legacy scan");
            SocketServer.sendSocketData(PrintStr.toString());
            return;
        }

        mfilter = params.parseScanFilter();
        if(mfilter == null) {
            Log.i(TAG, "mfilter NULL");
        }
        settings = params.getScanSettings();
        if(settings == null) {
            Log.i(TAG, "settings NULL");
        }

        startScan(mfilter, settings);
    }

    public void stopScan() {
        Log.d(TAG, "Stop Scan ");

        Message msg = mScannerHandler.obtainMessage(MSG_STOP_BLE_SCAN, null);
        mScannerHandler.sendMessage(msg);
    }

    private void resetScanParams() {
        Log.d(TAG, "resetScanParams ");
        mScanSettings = null;
        mScanFilters = null;
    }

    @Override
    public void onDestroy() {

        if(mScanstatus) {
            if(mBTAdapter.isEnabled())
                mBleScanner.stopScan(mScanCallback);
            mScanstatus = false;
        }
    }

    public ScanCallback mScanCallback = new ScanCallback(){
        @Override
        public void onScanResult(int CallbackType, ScanResult result){
            final BluetoothDevice bluetoothDevice = result.getDevice();
            ScanRecord scanRecord = result.getScanRecord();
            if(scanRecord == null) {
              Log.d(TAG,"scan record null");
              return;
            }
            final String devName = scanRecord.getDeviceName();
            final int rssi = result.getRssi();
            final ScanResult r = result;
            final int primaryphy = r.getPrimaryPhy();
            byte[] bytes = result.getScanRecord().getBytes();
            Log.d(TAG, "Device found, devName: "+devName);
            if(!mScanstatus)
                return;

            for(BluetoothDevice dev:mDeviceList){
                if(dev.getAddress().equals(bluetoothDevice.getAddress()))
                    return;
            }
            Log.d(TAG, "Device found with addr:" + bluetoothDevice.getAddress().toString());
            mDeviceList.add(bluetoothDevice);

            if(BleAppService.scan_called == BleAppService.SCAN_CALLED_FROM_MAIN_ACTIVITY){
                /* Display scannner queue */
                PrintStr.setLength(0);
                PrintStr.append("==================================================\n");
                PrintStr.append("Scan Results: \nDevice Name : ");
                PrintStr.append(devName);
                PrintStr.append("\t Device Address : ");
                PrintStr.append(bluetoothDevice.getAddress());
                PrintStr.append("\tRSSI : ");
                PrintStr.append(rssi);
                PrintStr.append("\tPrimary Phy :");
                PrintStr.append(r.getPrimaryPhy());
                PrintStr.append("\tSecondary Phy :");
                PrintStr.append(r.getSecondaryPhy());
                if(mScanSettings.getScanResultType() == ScanSettings.SCAN_RESULT_TYPE_FULL) {
                    if ((scanRecord.getManufacturerSpecificData() != null) && (scanRecord.getManufacturerSpecificData().size() > 0)) {
                        for (int i = 0; i < scanRecord.getManufacturerSpecificData().size(); i++) {
                            int manufacturerId = scanRecord.getManufacturerSpecificData().keyAt(i);
                            Log.d(TAG, "Manu data ");
                            byte[] manufacturerData =
                                    scanRecord.getManufacturerSpecificData().get(manufacturerId);
                            PrintStr.append("\tMaufactureSpecificData : ");
                            PrintStr.append(Integer.toHexString(manufacturerId));
                            PrintStr.append(Arrays.toString(manufacturerData));
                        }
                    }
                    if ((scanRecord.getServiceData() != null) && !scanRecord.getServiceData().isEmpty()) {
                        for (ParcelUuid parcelUuid : scanRecord.getServiceData().keySet()) {
                            byte[] serviceData = scanRecord.getServiceData().get(parcelUuid);
                            Log.d(TAG, "service data ");
                            PrintStr.append("\tServiceData : ");
                            PrintStr.append(parcelUuid.toString());
                            PrintStr.append(Arrays.toString(serviceData));
                        }
                    }
                }
                PrintStr.append("\tScanTimeStamp(ns) : ");
                PrintStr.append(result.getTimestampNanos());
                SocketServer.sendSocketData(PrintStr.toString());
            }

            Message msg = BleAppService.msghandler.obtainMessage(
                      BleAppService.MSG_MA_SCAN_DEV_FOUND, primaryphy, 0, bluetoothDevice);
            BleAppService.msghandler.sendMessage(msg);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {

            int batchResultSize = results == null ? 0 : results.size();
            if(ScannerService.LOG_LEVEL >= 2) {
                Log.d(TAG, "current time stamp is " + SystemClock.elapsedRealtimeNanos());
                Log.d(TAG, "onBatchScanResults - size " + batchResultSize);

                StringBuilder PrintStr = new StringBuilder();

                if (!results.isEmpty()) {
                    for(int i=0; i<results.size(); i++){
                        ScanResult scanRes = results.get(i);
                        ScanRecord scanRecord = scanRes.getScanRecord();
                        String deviceName = scanRecord.getDeviceName();
                        if(scanRecord == null) {
                          Log.d(TAG,"scan record null");
                          return;
                        }
                        PrintStr.setLength(0);
                        PrintStr.append("==================================================\n");
                        PrintStr.append("Scan Results: \nDevice Name : ");
                        PrintStr.append(deviceName);
                        PrintStr.append("\tDevice Address : ");
                        PrintStr.append(scanRes.getDevice().getAddress());
                        PrintStr.append("\tRSSI : ");
                        PrintStr.append(scanRes.getRssi());
                        if(mScanSettings.getScanResultType() ==
                                                            ScanSettings.SCAN_RESULT_TYPE_FULL) {
                            if (scanRecord.getManufacturerSpecificData().size() > 0) {
                                for (int j = 0; j < scanRecord.getManufacturerSpecificData().size();
                                                j++) {
                                    int manufacturerId =
                                            scanRecord.getManufacturerSpecificData().keyAt(j);
                                    Log.d(TAG, "batch Manu data ");
                                    byte[] manufacturerData =
                                        scanRecord.getManufacturerSpecificData().get(manufacturerId);
                                    PrintStr.append("\tMaufactureSpecificData : ");
                                    PrintStr.append(Integer.toHexString(manufacturerId));
                                    PrintStr.append(Arrays.toString(manufacturerData));
                                }
                            }
                            if (!scanRecord.getServiceData().isEmpty()) {
                                for (ParcelUuid parcelUuid : scanRecord.getServiceData().keySet()) {
                                    byte[] serviceData =
                                            scanRecord.getServiceData().get(parcelUuid);
                                    Log.d(TAG, "batch service data ");
                                    PrintStr.append("\tServiceData : ");
                                    PrintStr.append(parcelUuid.toString());
                                    PrintStr.append(Arrays.toString(serviceData));
                                }
                            }
                        }
                        PrintStr.append("\tScanTimeStamp(ns) : ");
                        PrintStr.append(scanRes.getTimestampNanos());
                        SocketServer.sendSocketData(PrintStr.toString());
                    }
                    Message msg = BleAppService.msghandler.obtainMessage(
                          BleAppService.MSG_MA_SCAN_DEV_FOUND, results.get(0).getDevice());
                    BleAppService.msghandler.sendMessage(msg);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if(ScannerService.LOG_LEVEL >= 2) {
                Log.d(TAG, "Failed to start scan " + errorCode);
            }
            StringBuilder PrintStr = new StringBuilder();

            PrintStr.setLength(0);
            PrintStr.append("Failed to start scanning with error: ");
            PrintStr.append(errorCode);
            SocketServer.sendSocketData(PrintStr.toString());
        }
    };

    public class ScannerServiceMessageHandler extends Handler {
        Context mMsgContext;
        private static final String TAG = "ScannerServiceMessageHandler";

        public ScannerServiceMessageHandler(Context contxt, Looper looper) {
            super(looper);
            mMsgContext = contxt;
            if(ScannerService.LOG_LEVEL >= 2)
                Log.d(TAG, "ScannerServiceMessageHandler");
        }

        @Override
        public void handleMessage(Message msg) {
            if (ScannerService.LOG_LEVEL >= 2)
                Log.d(TAG, "Handler(): msg = " + msg.what);
            int status;
            switch (msg.what) {
                case MSG_START_BLE_SCAN:
                    scanLeDevice(true);
                    break;
                case MSG_STOP_BLE_SCAN:
                    resetScanParams();
                    scanLeDevice(false);
                    break;
                case MSG_SCAN_RESULT:
                    //Need to decide if we can call directly
                    break;
                default:
            }
        }

        public synchronized boolean scanLeDevice(boolean action){
            Log.d(TAG, "scanLeDevice" + action);

            if(action && !mScanstatus){
                if(mScanFilters != null && mScanSettings !=null ){
                    Log.d(TAG, "set Scan with Settings and filters1");
                    mBleScanner.startScan(mScanFilters,
                      mScanSettings, mScanCallback);
                } else if (mScanFilters != null){
                    Log.d(TAG, "set Scan with Settings and filters");
                    mBleScanner.startScan(mScanFilters,
                      new ScanSettings.Builder().build(),  mScanCallback);
                } else { //do a regular scan
                    Log.d(TAG, "Do a regular scan");
                    mBleScanner.startScan(mScanCallback);
                }
                mScanstatus = true;

                if(ScannerService.LOG_LEVEL >= 3)
                    Log.d(TAG, "scan started");
                PrintStr.setLength(0);
                PrintStr.append("Scanning started!!");
                SocketServer.sendSocketData(PrintStr.toString());
            } else if(!action && mScanstatus){
                if(mBTAdapter.isEnabled()) {
                    mBleScanner.stopScan(mScanCallback);
                    mBleScanner.flushPendingScanResults(mScanCallback);
                }
                mDeviceList.clear();
                mScanstatus = false;
                if(ScannerService.LOG_LEVEL >= 3)
                    Log.d(TAG, "scan stopped");
                PrintStr.setLength(0);
                PrintStr.append("Scanning stopped!!");
                SocketServer.sendSocketData(PrintStr.toString());
            } else if (action && mScanstatus) {
                Log.d(TAG, "Scan in progress");
                PrintStr.setLength(0);
                PrintStr.append("Not Staring scan , Scan in progress!!!");
                SocketServer.sendSocketData(PrintStr.toString());
            }
            return true;
        }
    }
}
