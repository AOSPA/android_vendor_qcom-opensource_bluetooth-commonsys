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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.os.Message;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class AdvertiserService extends Service {

    private static String TAG = "ADV SERVICE";
    private Map<Integer, AdvertiserEntity> advertisements;
    private static final int MAX_ADVERTISEMENTS = 16;
    private ArrayList<Integer> removedAdvertisements;
    private static int INVALID_INDEX = -1;
    private AdvServiceCallback mAdvServiceCb;

    public class LocalBinder extends Binder {
        AdvertiserService getService() {
            return AdvertiserService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"onBind");
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

    public AdvertiserService() {
        Log.d(TAG,"AdvertiserService");
    }

    @Override
    public void onCreate() {
        Log.d(TAG,"onCreate");
        // max size of ArrayList is limited to 16 using MAX_ADVERTISEMENTS
        advertisements = new HashMap<Integer, AdvertiserEntity>();
        removedAdvertisements = new ArrayList<Integer>();
        mAdvServiceCb = new AdvServiceCallback();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        for(Integer id : advertisements.keySet()) {
            AdvertiserEntity advInstance = advertisements.get(id);
            if(advInstance.getAdv_status() == AdvertiserEntity.ADV_STARTED) {
                advInstance.StopAdvertisement();
            }
        }
        advertisements.clear();
        removedAdvertisements.clear();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG,"onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG,"onTaskRemoved");
        super.onTaskRemoved(rootIntent);
    }

    public int getAdvertisingIndex() {
        int i;
        int temp_index = 0;

        if (advertisements.size() == 0) {
            temp_index = 0;
        }

        if (advertisements.size() > 0) {
            if(removedAdvertisements.size() > 0) {
                temp_index = removedAdvertisements.remove(0);
            } else {
                temp_index = advertisements.size();
            }
        }
        Log.d(TAG," temp index is " + temp_index);
        return temp_index;
    }

    public int startAdvertising(Adv adv_info){
        boolean status = false;
        int index = getAdvertisingIndex();

        //since array index starts a 0, size is used for next index
        if(index >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"Max advertisements limit reached, abort");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return MAX_ADVERTISEMENTS;
        }
        AdvertiserEntity advInstance = new AdvertiserEntity(adv_info,index, mAdvServiceCb);
        advertisements.put(index,advInstance);
        status = advInstance.StartAdvertisement();
        Log.d(TAG,"new Adv instance is created, total advs : " + advertisements.size());
        return index;
    }

    public int getAdvInfoArrayIndex(int Adv_id) {
        for(Integer id : advertisements.keySet()) {
            AdvertiserEntity advEnt = advertisements.get(id);
            if(advEnt.getAdv_id() == Adv_id) {
                Log.d(TAG, "found index to be deleted,i="+id);
                return id;
            }
        }
        return INVALID_INDEX;
    }

    public boolean stopAdvertising(int Adv_id) {
        boolean status = false;
        Log.d(TAG,"Adv id"+ Adv_id + "is deleted");
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"stopAdvertising invalid adv_id");
            return status;
        }

        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.StopAdvertisement();
        }
        else {
            Log.e(TAG, "Adv Index not found");
        }
        return status;
    }

    public boolean enableAdvSet(EnableAdv enadv_info) {
        boolean status = false;
        int Adv_id = enadv_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"EnableAdvertisingSet invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.EnableAdvertisingSet(enadv_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean setAdverData(AdvDataInfo advdata_info) {
        boolean status = false;
        int Adv_id = advdata_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"setAdvertisingData invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.SetAdvertData(advdata_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean setScanData(AdvDataInfo scandata_info) {
        boolean status = false;
        int Adv_id = scandata_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"setScanData invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.SetScanRspData(scandata_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean setAdvParam(SetAdvParam setadvparam_info) {
        boolean status = false;
        int Adv_id = setadvparam_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"setAdvParam invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.SetAdvertParam(setadvparam_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean setPeriAdvParam(SetPerAdvParam setperadvparam_info) {
        boolean status = false;
        int Adv_id = setperadvparam_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"SetPerioAdvParam invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.SetPerioAdvParam(setperadvparam_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean setPeriAdvData(SetPerAdvData setperadvdata_info) {
        boolean status = false;
        int Adv_id = setperadvdata_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"SetPerioAdvData invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.SetPerioAdvData(setperadvdata_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean EnablePeriAdv(EnablePerAdv enperadv_info) {
        boolean status = false;
        int Adv_id = enperadv_info.AdvId;
        if(Adv_id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"EnablePeriAdv invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.EnablePerioAdv(enperadv_info);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public boolean getOwnaddset(int Adv_Id) {
        boolean status = false;
        if(Adv_Id >= MAX_ADVERTISEMENTS){
            Log.e(TAG,"Getownaddset invalid adv_id");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Max advertisements limit reached!");
            SocketServer.sendSocketData(PrintStr.toString());
            return status;
        }
        int adv_index = getAdvInfoArrayIndex(Adv_Id);
        if(INVALID_INDEX != adv_index){
            AdvertiserEntity advInstance = advertisements.get(adv_index);
            status = advInstance.Getownaddrset(Adv_Id);
        }
        else {
            Log.e(TAG, "Adv Index not found");
            StringBuilder PrintStr = new StringBuilder();
            PrintStr.setLength(0);
            PrintStr.append("Adv Index not found!");
            SocketServer.sendSocketData(PrintStr.toString());

        }
        return status;
    }

    public void adapterChangedEvent(int state) {
        if (state == BluetoothAdapter.STATE_ON) {
            Log.i(TAG, "BT Adapter is on");
        } else if (state == BluetoothAdapter.STATE_OFF) {
            Log.i(TAG, "BT Adapter is off");
            advertisements.clear();
            removedAdvertisements.clear();
        }
    }

    public class AdvServiceCallback {
        Message msg;
        public void onAdvStarted(int adv_id){
            Log.d(TAG,"Advertising Started on adv_id : " + adv_id);
            msg = BleAppService.msghandler.obtainMessage(
                      BleAppService.MSG_MA_ADV_STARTED, Integer.toString(adv_id));
            BleAppService.msghandler.sendMessage(msg);
        }
        public void onAdvStopped(int adv_id){
            Log.d(TAG,"Advertising Stopped on adv_id : " + adv_id +
                    "Deleting the adv instance");
            int adv_index = getAdvInfoArrayIndex(adv_id);
            if(INVALID_INDEX != adv_index) {
                advertisements.remove(adv_index);
                Log.d(TAG,"Total adv instances after deleting: " + advertisements.size());
                removedAdvertisements.add(adv_index);
                Log.d(TAG,"removedAdvertisements  size is : " + removedAdvertisements.size() );
                msg = BleAppService.msghandler.obtainMessage(
                          BleAppService.MSG_MA_ADV_STOPPED, Integer.toString(adv_index));
                BleAppService.msghandler.sendMessage(msg);
            }
            else {
                Log.e(TAG, "Adv Index not found");
            }
        }
        public void onAdvEnabled(int adv_id, boolean enable){
            Log.d(TAG,"onAdvEnabled on adv_id : " + adv_id +
                    "adv instance");
            int adv_index = getAdvInfoArrayIndex(adv_id);
            if(enable != true){
                if(INVALID_INDEX != adv_index) {
                    advertisements.remove(adv_index);
                    Log.d(TAG,"Total adv instances after deleting: " + advertisements.size());
                    removedAdvertisements.add(adv_index);

                    msg = BleAppService.msghandler.obtainMessage(
                            BleAppService.MSG_MA_BLE_ADV_ENABLED_EVENT, Integer.toString(adv_index));
                    BleAppService.msghandler.sendMessage(msg);
                }
                else {
                    Log.e(TAG, "Adv Index not found");
                }
            }else{
                    msg = BleAppService.msghandler.obtainMessage(
                            BleAppService.MSG_MA_BLE_ADV_ENABLED_EVENT, Integer.toString(adv_index));
                    BleAppService.msghandler.sendMessage(msg);
            }
        }
    }
}
