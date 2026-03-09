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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.ParcelUuid;
import android.util.Log;
import android.os.Message;

import java.nio.charset.Charset;
import java.util.UUID;
import java.util.HashMap;

public class AdvertiserEntity {

    private String TAG = "ADV_ENTITY";
    public static final int ADV_STARTED = 0x00;
    public static final int ADV_STOPPED = 0x01;
    public static final int ADV_FAILED = 0x02;

    private byte[] manuData;
    private BluetoothAdapter mBTAdapter = BleAppService.bleAdapter;

    Adv adv_info;

    private BluetoothLeAdvertiser mAdvertiser;
    private AdvertiserService.AdvServiceCallback  mAdvServiceCb;
    //legacy
    private AdvertiseCallback mAdvCallback;
    private AdvertiseSettings mAdvSettings;
    private AdvertiseData mAdvData = null;

    //non-legacy
    private AdvertisingSetParameters mAdvParams;
    private AdvertisingSetCallback mAdvSetCallback;
    private PeriodicAdvertisingParameters mPeriodicAdvParams;
    private AdvertiseData mScanResponseData = null;
    private AdvertiseData mPeriodicData = null;

    public int adv_id = 0;
    public int adv_status = ADV_STOPPED;
    public HashMap<Integer, AdvertisingSet> mAdvSetIdMap= new HashMap<>();
    Message msg;

    class AdvSetCallback extends AdvertisingSetCallback {
        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet,
                                            int txPower, int status) {
            Log.d(TAG,"onAdvertisingSetStarted status : "+status);

            if (status == ADVERTISE_SUCCESS){
                adv_status = ADV_STARTED;
                Log.d(TAG,"onAdvertisingSetStarted, adv_id:" + getAdv_id());
                mAdvServiceCb.onAdvStarted(getAdv_id());
                mAdvSetIdMap.put(getAdv_id(), advertisingSet);
            } else {
                StringBuilder PrintStr = new StringBuilder();

                PrintStr.setLength(0);
                PrintStr.append("Failed to start advertising with error: ");
                PrintStr.append(status);
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            Log.d(TAG,"onAdvertisingSetStopped");
            adv_status = ADV_STOPPED;
            Log.d(TAG,"onAdvertisingSetStopped, adv_id:" + getAdv_id());
            mAdvServiceCb.onAdvStopped(getAdv_id());
        }

        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet,
                                         boolean enable, int status) {
            Log.d(TAG,"onAdvertisingEnabled");
            msg = BleAppService.msghandler.obtainMessage(
                    BleAppService.MSG_MA_BLE_ADV_ENABLED_EVENT, Integer.toString(getAdv_id()));
            BleAppService.msghandler.sendMessage(msg);
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("onAdvertisingEnabled enable : ");
            PrintStr.append(enable);
            PrintStr.append(" status: ");
            PrintStr.append(status);
            SocketServer.sendSocketData(PrintStr.toString());
        }

        @Override
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            Log.d(TAG,"onAdvertisingDataSet");
            msg = BleAppService.msghandler.obtainMessage(
                    BleAppService.MSG_MA_BLE_ADV_DATA_EVENT, Integer.toString(getAdv_id()));
            BleAppService.msghandler.sendMessage(msg);
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("onAdvertisingDataSet status: ");
            PrintStr.append(status);
            SocketServer.sendSocketData(PrintStr.toString());
        }

        @Override
        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
            Log.d(TAG,"onScanResponseDataSet");
            msg = BleAppService.msghandler.obtainMessage(
                    BleAppService.MSG_MA_BLE_SCAN_RESP_DATA_EVENT, Integer.toString(getAdv_id()));
            BleAppService.msghandler.sendMessage(msg);
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("onScanResponseDataSet status: ");
            PrintStr.append(status);
            SocketServer.sendSocketData(PrintStr.toString());

        }

        @Override
        public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet, int txPower,
                            int status) {
            Log.d(TAG,"onAdvertisingParametersUpdated");
            msg = BleAppService.msghandler.obtainMessage(
                    BleAppService.MSG_MA_BLE_ADV_PARAM_UPDATED_EVENT, Integer.toString(getAdv_id()));
            BleAppService.msghandler.sendMessage(msg);
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("onAdvertisingParametersUpdated txPower: ");
            PrintStr.append(txPower);
            PrintStr.append("  status: ");
            PrintStr.append(status);
            SocketServer.sendSocketData(PrintStr.toString());
        }

        @Override
        public void onPeriodicAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
                                                               int status) {
            Log.d(TAG,"onPeriodicAdvertisingParametersUpdated");
            if(status == ADVERTISE_SUCCESS){
                msg = BleAppService.msghandler.obtainMessage(
                        BleAppService.MSG_MA_BLE_PERIODIC_ADV_PARAM_UPDATED_EVENT, Integer.toString(getAdv_id()));
                BleAppService.msghandler.sendMessage(msg);
                StringBuilder PrintStr = new StringBuilder();
                PrintStr.setLength(0);
                PrintStr.append("onPeriodicAdvertisingParametersUpdated Success");
                PrintStr.append(status);
                SocketServer.sendSocketData(PrintStr.toString());
            }else {
                StringBuilder PrintStr = new StringBuilder();
                PrintStr.setLength(0);
                PrintStr.append("Failed to set periodic advertising parametes with error: ");
                PrintStr.append(status);
                SocketServer.sendSocketData(PrintStr.toString());
            }

        }

        @Override
        public void onPeriodicAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            Log.d(TAG,"onPeriodicAdvertisingParametersUpdated");
            if(status == ADVERTISE_SUCCESS){
                msg = BleAppService.msghandler.obtainMessage(
                        BleAppService.MSG_MA_BLE_PERIODIC_ADV_DATA_EVENT, Integer.toString(getAdv_id()));
                BleAppService.msghandler.sendMessage(msg);
            }else {
                StringBuilder PrintStr = new StringBuilder();
                PrintStr.setLength(0);
                PrintStr.append("Failed to set periodic advertising data with error: ");
                PrintStr.append(status);
                SocketServer.sendSocketData(PrintStr.toString());
            }

        }

        @Override
        public void onPeriodicAdvertisingEnabled(AdvertisingSet advertisingSet,
                                                     boolean enable, int status) {
            Log.d(TAG,"onPeriodicAdvertisingEnabled");
            if(status == ADVERTISE_SUCCESS){
                msg = BleAppService.msghandler.obtainMessage(
                        BleAppService.MSG_MA_BLE_PERIODIC_ADV_ENABLED_EVENT, Integer.toString(getAdv_id()));
                BleAppService.msghandler.sendMessage(msg);
                StringBuilder PrintStr = new StringBuilder();
                PrintStr.setLength(0);
                PrintStr.append("onPeriodicAdvertisingEnabled Success");
                PrintStr.append(status);
                SocketServer.sendSocketData(PrintStr.toString());
            }else {
                StringBuilder PrintStr = new StringBuilder();
                PrintStr.setLength(0);
                PrintStr.append("Failed to set periodic advertising data with error: ");
                PrintStr.append(status);
                SocketServer.sendSocketData(PrintStr.toString());
            }
        }
/*
 //! Since this API is hidden we can't push this particular change, hence commenting
        @Override
        public void onOwnAddressRead(AdvertisingSet advertisingSet, int addressType, String address) {
            Log.d(TAG,"onOwnAddressRead");
            msg = BleAppService.msghandler.obtainMessage(
                    BleAppService.MSG_MA_BLE_GET_OWN_ADDRESS_EVENT, address);
            BleAppService.msghandler.sendMessage(msg);
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("onOwnAddressRead addressType: ");
            PrintStr.append(addressType);
            SocketServer.sendSocketData(PrintStr.toString());
        }
*/
    };

    class AdvCallback extends AdvertiseCallback {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            adv_status = ADV_STARTED;
            Log.d(TAG,"onAdvStartSuccess, adv_id:" + getAdv_id());
            mAdvServiceCb.onAdvStarted(getAdv_id());
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "Advertisement failed, errorcode -> "+errorCode);
            adv_status = ADV_FAILED;
            super.onStartFailure(errorCode);

            StringBuilder PrintStr = new StringBuilder();

            PrintStr.setLength(0);
            PrintStr.append("Failed to start advertising with error: ");
            PrintStr.append(errorCode);
            SocketServer.sendSocketData(PrintStr.toString());
        }
    };


    public AdvertiserEntity(Adv adv_info, int adv_id,
                            AdvertiserService.AdvServiceCallback mAdvServiceCb) {
        setAdv_info(adv_info);
        setAdv_id(adv_id);
        this.mAdvServiceCb = mAdvServiceCb;
        // Advertiser
        mAdvertiser = mBTAdapter.getBluetoothLeAdvertiser();
        //Set ADV callback
        mAdvCallback = new AdvCallback();
        // Adv Set callback
        mAdvSetCallback = new AdvSetCallback();
    }

    public void setAdv_id(int adv_id) {
        this.adv_id = adv_id;
    }

    public int getAdv_id() {
        return this.adv_id;
    }

    public Adv getAdv_info() {
        return this.adv_info;
    }

    public void setAdv_info(Adv adv_info) {
        this.adv_info = adv_info;
    }

    public int getAdv_status() {
        return this.adv_status;
    }

    public boolean BuildAdvertisementParameters(){
        Log.d(TAG,"BuildAdvertisementParameters");
        try {
            if(adv_info.Legacy) {
                mAdvSettings = new AdvertiseSettings.Builder()
                        .setAdvertiseMode(adv_info.AdvertiseMode)
                        .setTxPowerLevel(adv_info.TxPower)
                        .setConnectable(adv_info.Connectable)
                        .setTimeout(adv_info.TimeOutLegacy)
                        .build();
                Log.d(TAG,"BuildAdvertisementParameters Legacy done");
                return true;
            } else {
                mAdvParams = new AdvertisingSetParameters.Builder()
                        .setConnectable(adv_info.Connectable)
                        .setScannable(adv_info.Scannable)
                        .setLegacyMode(adv_info.Legacy)
                        .setAnonymous(adv_info.Anonymous)
                        .setIncludeTxPower(adv_info.IncludePower)
                        .setPrimaryPhy(adv_info.PrimaryPhy)
                        .setSecondaryPhy(adv_info.SecondaryPhy)
                        .setInterval(adv_info.Interval)
                        .setTxPowerLevel(adv_info.TxPower)
                        .build();
                Log.d(TAG,"BuildAdvertisementParameters Ext done");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean BuildAdvertisementData(){
        Log.d(TAG,"BuildAdvertisementData");
        try {
            if(adv_info.Legacy){
                AdvertiseData.Builder legacyData = new AdvertiseData.Builder();
                legacyData.setIncludeDeviceName(true);
                legacyData.setIncludeTxPowerLevel(adv_info.IncludePower);
                if(adv_info.ServiceUuid[0] != null) {
                    ParcelUuid pUuid = new ParcelUuid(UUID.fromString(adv_info.ServiceUuid[0]));
                    legacyData.addServiceUuid(pUuid);
                    Log.d(TAG, "service uuid added");
                }
                if(adv_info.ManufacturerData[0] != null) {
                    String[] manufacturerData = adv_info.ManufacturerData[0].split(",");
                    if(manufacturerData!= null && (manufacturerData).length>0) {
                        manuData = new byte[manufacturerData.length];
                        for(int i=0; i< manuData.length; i++) {
                            manuData[i] = Byte.parseByte(manufacturerData[i],16);
                        }
                    }
                    if(manuData != null && manuData.length >0) {
                        for(int j=0; j< manuData.length; j++) {
                            Log.d(TAG, "manufacturerData::"+manuData[j]);
                        }
                    }
                    legacyData.addManufacturerData(adv_info.ManufacturerId[0],manuData);
                    Log.d(TAG, "manu data added");
                }
                mAdvData = legacyData.build();
                Log.d(TAG,"BuildAdvertisementData done");
                return true;
            } else {
                AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
                dataBuilder.setIncludeDeviceName(true);
                dataBuilder.setIncludeTxPowerLevel(adv_info.IncludePower);
                if(adv_info.ServiceUuid != null) {
                    Log.d(TAG,"Setting Service UUIDs");
                    for (int i = 0; i < adv_info.ServiceUuid.length; i++) {
                        if (adv_info.ServiceUuid[i] != null) {
                            Log.d(TAG, "Setting Service UUID: " + adv_info.ServiceUuid[i]);
                            ParcelUuid pUuid = new ParcelUuid(UUID.fromString(adv_info.ServiceUuid[i]));
                            dataBuilder.addServiceUuid(pUuid);
                        }
                        else {
                            break;
                        }
                    }
                }

                if (adv_info.ServiceDataUuid != null && adv_info.ServiceData != null) {
                    Log.d(TAG, "ServiceDataUuid length " + adv_info.ServiceDataUuid.length);
                    for (int j = 0; j < adv_info.ServiceDataUuid.length; j++) {
                        if (adv_info.ServiceDataUuid[j] != null && adv_info.ServiceData[j] != null) {
                            Log.d(TAG, "Setting Service data UUID: " + adv_info.ServiceDataUuid[j]);
                            ParcelUuid pServiceDataUuid = new ParcelUuid(UUID.fromString(adv_info.ServiceDataUuid[j]));
                            Log.d(TAG, "Setting Service data: " + adv_info.ServiceData[j]);
                            dataBuilder.addServiceData(pServiceDataUuid, adv_info.ServiceData[j].getBytes(Charset.forName("UTF-8")));
                        }
                        else {
                            break;
                        }
                    }
                }
                if (adv_info.ManufacturerId != null && adv_info.ManufacturerData != null) {
                    Log.d(TAG, "Setting Manufacturer data");
                    for (int i = 0; i < adv_info.ManufacturerData.length; i++) {
                        if (adv_info.ManufacturerId !=null &&adv_info.ManufacturerData[i] != null) {
                            Log.d(TAG, "Setting Manufacturer data: " + adv_info.ManufacturerData[i]);
                            dataBuilder.addManufacturerData(adv_info.ManufacturerId[i],
                            adv_info.ManufacturerData[i].getBytes(Charset.forName("UTF-8")));
                        }
                        else {
                            break;
                        }
                    }
                }
                mAdvData = dataBuilder.build();
                Log.d(TAG,"BuildAdvertisementData done");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG,"Exception : " + e.toString());
        }
        return false;
    }

    public boolean SetPeriodicAdvParams(){
        Log.d(TAG,"SetPeriodicAdvParams");
        try {
            if(adv_info.Periodic){
                mPeriodicAdvParams = new PeriodicAdvertisingParameters.Builder()
                        .setIncludeTxPower(adv_info.IncludePower)
                        .setInterval(adv_info.PerAdvInterval)
                        .build();
                Log.d(TAG,"Build Periodic AdvertisingParameters done");
                return true;
            } else {
                mPeriodicAdvParams = null;
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG,"Exception : " + e.toString());
        }
        return false;
    }

    public boolean SetPeriodicAdvData(){
        Log.d(TAG,"SetPeriodicAdvData");
        if(adv_info.Periodic){
            mPeriodicData = mAdvData;
        } else {
            mPeriodicData = null;
        }
        return true;
    }

    public boolean SetScanResponseData(){
        Log.d(TAG,"SetScanResponseData");
        if(adv_info.Scannable){
            mScanResponseData = mAdvData;
            if(!adv_info.Legacy){
                mAdvData = null;
            }
        } else {
            mScanResponseData = null;
        }
        return true;
    }

    public boolean StartAdvertisement(){
        Log.d(TAG,"StartAdvertisement");
        boolean status = false;

        if(mAdvertiser == null) {
            Log.e(TAG,"Advertiser is null, adv_id : "+ adv_id);
            return status;
        }

        status = BuildAdvertisementParameters();
        if(!status) {
            Log.e(TAG,"BuildAdvertisementParameters failed, adv_id : "+ adv_id);
            return status;
        }

        status =  BuildAdvertisementData();
        if(!status) {
            Log.e(TAG,"BuildAdvertisementData failed, adv_id : "+ adv_id);
            return status;
        }

        status = SetPeriodicAdvParams();
        if(!status) {
            Log.e(TAG,"SetPeriodicAdvParams failed, adv_id : "+ adv_id);
            return status;
        }

        status = SetPeriodicAdvData();
        if(!status) {
            Log.e(TAG,"SetPeriodicAdvData failed, adv_id : "+ adv_id);
            return status;
        }

       status = SetScanResponseData();
       if(!status) {
           Log.e(TAG,"SetScanResponseData failed, adv_id : "+ adv_id);
           return status;
        }
        try {
            if(adv_info.Legacy){
                mAdvertiser.startAdvertising(mAdvSettings,mAdvData,mAdvCallback);
            } else {
                Log.d(TAG, "Max Adv Events:"+adv_info.MaxAdvEvents);
                mAdvertiser.startAdvertisingSet(mAdvParams,mAdvData,
                                                mScanResponseData,mPeriodicAdvParams,
                                                mPeriodicData,0,adv_info.MaxAdvEvents,mAdvSetCallback);
            }
            return status;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
            return false;
        }
    }

    public boolean StopAdvertisement() {
        Log.d(TAG,"StopAdvertisement");
        try {
            if(adv_info.Legacy){
                mAdvertiser.stopAdvertising(mAdvCallback);
                adv_status = ADV_STOPPED;
                Log.d(TAG,"StopAdvertisement, adv_id:" + getAdv_id());
                mAdvServiceCb.onAdvStopped(getAdv_id());
                return true;
            } else {
                mAdvertiser.stopAdvertisingSet(mAdvSetCallback);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean EnableAdvertisingSet(EnableAdv enadv_info) {
        Log.d(TAG,"EnableAdvertisingSet");
        int adv_id = enadv_info.AdvId;
        try {
               if(mAdvSetIdMap.containsKey(adv_id))
                {
                  AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                  mAdvSet.enableAdvertising(enadv_info.Enableset, enadv_info.Duration, enadv_info.MaxAdvEvents);
                  return true;
                }
                return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean SetAdvertData(AdvDataInfo advdata_info) {
        Log.d(TAG,"SetAdvertiseData");
        int adv_id = advdata_info.AdvId;
        AdvertiseData data = null;
        try {
            if(mAdvSetIdMap.containsKey(adv_id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                Log.d("TAG", "mAdvSet: " + mAdvSet);
                if(mAdvSet != null){
                    data = new AdvertiseData.Builder()
                        .addServiceData(new ParcelUuid(UUID.fromString(advdata_info.ServiceUuid)), advdata_info.AdvData.getBytes(Charset.forName("UTF-8")))
                        .setIncludeTxPowerLevel(true)
                        .build();
                    mAdvSet.setAdvertisingData(data);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean SetScanRspData(AdvDataInfo scandata_info) {
        Log.d(TAG,"SetScanRspData");
        int adv_id = scandata_info.AdvId;
        AdvertiseData data = null;
        try {
            if(mAdvSetIdMap.containsKey(adv_id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                Log.d("TAG", "mAdvSet: " + mAdvSet);
                if(mAdvSet != null){
                    data = new AdvertiseData.Builder()
                        .addServiceData(new ParcelUuid(UUID.fromString(scandata_info.ServiceUuid)), scandata_info.AdvData.getBytes(Charset.forName("UTF-8")))
                        .setIncludeTxPowerLevel(true)
                        .build();
                    mAdvSet.setScanResponseData(data);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean SetAdvertParam(SetAdvParam setadvparam_info) {
        Log.d(TAG,"SetAdvertParam");
        int adv_id = setadvparam_info.AdvId;
        try {
            if(mAdvSetIdMap.containsKey(adv_id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                AdvertisingSetParameters mParams = new AdvertisingSetParameters.Builder()
                                                         .setConnectable(setadvparam_info.Connectable)
                                                         .setScannable(setadvparam_info.Scannable)
                                                         .setLegacyMode(setadvparam_info.Legacy)
                                                         .setAnonymous(setadvparam_info.Anonymous)
                                                         .setIncludeTxPower(setadvparam_info.IncludePower)
                                                         .setPrimaryPhy(setadvparam_info.PrimaryPhy)
                                                         .setSecondaryPhy(setadvparam_info.SecondaryPhy)
                                                         .setInterval(setadvparam_info.Interval)
                                                         .setTxPowerLevel(setadvparam_info.TxPower)
                                                         .build();
                mAdvSet.setAdvertisingParameters(mParams);
            return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean SetPerioAdvParam(SetPerAdvParam setperadvparam_info) {
        Log.d(TAG,"SetPerioAdvParam");
        int adv_id = setperadvparam_info.AdvId;
        try {
            if(mAdvSetIdMap.containsKey(adv_id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                PeriodicAdvertisingParameters mParams = new PeriodicAdvertisingParameters.Builder()
                                                            .setIncludeTxPower(adv_info.IncludePower)
                                                            .setInterval(setperadvparam_info.PerAdvInterval)
                                                            .build();
                mAdvSet.setPeriodicAdvertisingParameters(mParams);
            return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean SetPerioAdvData(SetPerAdvData setperadvdata_info) {
       Log.d(TAG,"SetPerioAdvData");
        int adv_id = setperadvdata_info.AdvId;
        AdvertiseData data = null;
        try {
            if(mAdvSetIdMap.containsKey(adv_id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                Log.d("TAG", "mAdvSet: " + mAdvSet);
                if(mAdvSet != null){
                    data = new AdvertiseData.Builder()
                        .addServiceData(new ParcelUuid(UUID.fromString(setperadvdata_info.ServiceUuid)), setperadvdata_info.PeriodicData.getBytes(Charset.forName("UTF-8")))
                        .setIncludeTxPowerLevel(true)
                        .build();
                    mAdvSet.setPeriodicAdvertisingData(data);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean EnablePerioAdv(EnablePerAdv enperadv_info) {
       Log.d(TAG,"EnablePerioAdv");
        int adv_id = enperadv_info.AdvId;
        try {
            if(mAdvSetIdMap.containsKey(adv_id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
                mAdvSet.setPeriodicAdvertisingEnabled(enperadv_info.Enableset);
            return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }

    public boolean Getownaddrset(int Adv_Id) {
       Log.d(TAG,"Getownaddrset");
        try {
            if(mAdvSetIdMap.containsKey(Adv_Id))
            {
                AdvertisingSet mAdvSet = mAdvSetIdMap.get(adv_id);
               // mAdvSet.getOwnAddress();  //! Since this API is hidden we can't push this particular change, hence commenting
            return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Exception : "+ e.toString());
        }
        return false;
    }
}

