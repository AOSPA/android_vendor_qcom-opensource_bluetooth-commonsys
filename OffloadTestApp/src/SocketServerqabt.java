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
*/

package org.codeaurora.bluetooth.offload_testapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

class SocketServer {
    private static SocketServer INSTANCE = null;
    private static Semaphore mutex = new Semaphore(1);

    private Context mContext;
    private static final String TAG = "SocketServer";
    final String SOCKET_ADDRESS = "BtTestAppSocket";
    static int socSendBufferSize = 4096;
    int socRcvBufferSize = 4096;
    byte[] socRcvBuffer;
    int bytesRead;
    private static boolean socketOpen = false;
    private static boolean closeReceived = false;
    InputStream input;
    private static OutputStream output;
    LocalServerSocket server = null;
    LocalSocket client = null;
    localServerSocket localServer;
    communicationHandler commHandler;
    static InputParse parse;
    StringBuilder sendStr = new StringBuilder();

    static final int MAIN_MENU = 1;
    static final int THROUGHPUT_MENU = 2;
    static final int ADV_MENU = 3;
    static final int SCAN_MENU = 4;
    static final int GATT_CLIENT_MENU = 5;
    static final int INVALID_INPUT = 6;
    static final int SOC_CLOSE_ACK = 7;
    static final int NONE = 8;
    static final int GATT_SERVER_MENU = 9;
    static final int LE_COC_MENU = 10;

    static int mainMenuState = MAIN_MENU;
    static int processOutputState = MAIN_MENU;

    private SocketServer(Context mAppContext) {
        Log.d(TAG, "SocketServer()");
        mContext = mAppContext;
        socRcvBuffer = new byte[socRcvBufferSize];

        parse = new InputParse();
        localServer = new localServerSocket();
        localServer.start();
    }

    public static synchronized SocketServer getInstance(Context mAppContext) {
        if(INSTANCE == null) {
            INSTANCE = new SocketServer(mAppContext);
        }
        return INSTANCE;
    }

    private class localServerSocket extends Thread {
        public localServerSocket() {
            Log.d(TAG, "localServerSocket()");
            bytesRead = 0;

            try {
                server = new LocalServerSocket(SOCKET_ADDRESS);
                Log.d(TAG, "LocalSocketServer created");
            } catch (IOException e) {
                Log.e(TAG, "LocalSocketServer created failed !!!");
                e.printStackTrace();
            }

            LocalSocketAddress localSocketAddress;
            localSocketAddress = server.getLocalSocketAddress();
            String str = localSocketAddress.getName();
            Log.d(TAG, "LocalSocketAddress = " + str);
        }

        public void run() {
            Log.d(TAG, "localServerSocket run()");
            if (null != server) {
                try {
                    Log.d(TAG, "localSocketServer begins to accept()");
                    client = server.accept();
                } catch (IOException e) {
                    Log.e(TAG, "localSocketServer accept() failed !!!");
                    e.printStackTrace();
                }

                socketOpen = true;
                Log.d(TAG, "localSocket accepted");

                try {
                    input = client.getInputStream();
                    Log.d(TAG, "getInputStream");
                } catch (IOException e) {
                    Log.e(TAG, "getInputStream() failed !!!");
                    e.printStackTrace();
                }

                try {
                    output = client.getOutputStream();
                    Log.d(TAG, "getOutputStream");
                } catch (IOException e) {
                    Log.e(TAG, "getOutputStream() failed !!!");
                    e.printStackTrace();
                }

                commHandler = new communicationHandler();
                commHandler.start();
            } else {
                Log.d(TAG, "The LocalServerSocket is NULL");
            }
        }
    }

    private class communicationHandler extends Thread {

        public void run() {
            Log.d(TAG, "communicationHandler run()");

            // Display main menu and start receiving socket data
            mainMenuState = MAIN_MENU;
            processOutputState = MAIN_MENU;
            sendSocketData(processOutput());

            while (true) {
                try {
                    bytesRead = input.read(socRcvBuffer, 0, socRcvBufferSize);
                } catch (IOException e) {
                    Log.e(TAG, "There is an exception when reading socket");
                    e.printStackTrace();
                    closeSocketServer();
                    INSTANCE.showMessage("Socket closed, restart app");
                    break;
                }

                if(!socketOpen) {
                    break; // If socket is closed, stop the thread
                }

                if (bytesRead >= 0) {
                    String inputStr = new String(socRcvBuffer, 0, bytesRead);
                    Log.i(TAG, "Received: " + inputStr);
                    bytesRead = 0;
                    processInput(inputStr);
                } else {
                    closeSocketServer();
                    break;
                }

                if (processOutputState != NONE) {
                    sendSocketData(processOutput());
                }

                if (closeReceived) {
                    closeSocketServer();
                    break;
                }
            }

            Log.d(TAG, "communicationHandler Stopped");
        }

        private String processOutput() {
            sendStr.setLength(0);

            switch (processOutputState) {
                case MAIN_MENU:
                    sendStr.append("\n******************** Bt Test App ********************\n");
                    sendStr.append("                     Advertiser\n");
                    sendStr.append("                     Scanner\n");
                    sendStr.append("                     Throughput\n");
                    sendStr.append("                     LECOC\n");
                    sendStr.append("                     GattClient\n");
                    sendStr.append("                     HoldWakeLock\n");
                    sendStr.append("                     ReleaseWakeLock\n");
                    sendStr.append("                     GattServer\n");
                    sendStr.append("                     GetConnectedDevices\n");
                    sendStr.append("                     Pair   (Ex: Pair 11:22:33:44:55:66)\n");
                    sendStr.append("                     UnPair   (Ex: UnPair 11:22:33:44:55:66)\n");
                    sendStr.append("                     Disconnect  (Ex: Disconnect 11:22:33:44:55:66)\n");
                    sendStr.append("                     GetBondedDevices\n");
                    sendStr.append("                     Close\n");
                    sendStr.append("*****************************************************\n");
                    break;

                case ADV_MENU:
                    sendStr.append("\n******************** Bt Test App ********************\n");
                    sendStr.append("                     AdvStart      (Ex: AdvStart TxPower:1;Legacy:true;Periodic:false;PerAdvInterval:200;Connectable:true;\n");
                    sendStr.append("                                                 Scannable:true;Anonymous:false;IncludePower:true;PrimaryPhy:1;SecondaryPhy:1;\n");
                    sendStr.append("                                                 MaxExtAdvEvents:0;Interval:160;TimeOutLegacy:10000;AdvertiseMode:0;\n");
                    sendStr.append("                                                 ServiceUuid:0000FF01-0000-1000-8000-00805F9B34FB;ManufacturerId:32;ManufacturerData:1,1,1;\n");
                    sendStr.append("                                                 ServiceDataUuid:0000FF01-0000-1000-8000-00805F9B34FB;ServiceData:abcdabcd(only for ext adv)\n");
                    sendStr.append("                     EnableAdvSet  (Ex: EnableAdvSet AdvId:0;Enableset:true;Duration:0;MaxAdvEvents:0)\n");
                    sendStr.append("                     SetAdvData    (Ex: SetAdvData AdvId:0;AdvData:abcdabcd)\n");
                    sendStr.append("                     SetScanRespData  (Ex: SetScanRespData AdvId:0;ScanRespData:abcdabcd)\n");
                    sendStr.append("                     SetAdvParams  (Ex: SetAdvParams AdvId:0;TxPower:1;Legacy:true;Connectable:true;Scannable:true;Anonymous:false;)\n");
                    sendStr.append("                                                     Interval:160;IncludePower:false;PrimaryPhy:1;SecondaryPhy:1\n");
                    sendStr.append("                     SetPeriodicAdvParams  (Ex: SetPeriodicAdvParams AdvId:0;PerAdvInterval:200)\n");
                    sendStr.append("                     SetPeriodicAdvData  (Ex: SetPeriodicAdvData AdvId:0;PeriodicData:abcdabcd)\n");
                    sendStr.append("                     EnablePeriodicAdvSet  (Ex: EnablePeriodicAdvSet AdvId:0;Enableset:true)\n");
                    sendStr.append("                     GetOwnAddrSet  (Ex: GetOwnAddrSet 0)\n");
                    sendStr.append("                     AdvStop       (Ex: AdvStop 0)\n");
                    sendStr.append("                     Back\n");
                    sendStr.append("*****************************************************\n");
                    break;

                case SCAN_MENU:
                    sendStr.append("\n******************** Bt Test App ********************\n");
                    sendStr.append("                     ScanStart      (Ex: ScanStart DeviceName:Minato;DeviceAddress:73:B5:C0:E6:62:A4;ServiceUuid:0000180f-0000-1000-8000-00805f9b34fb;\n");
                    sendStr.append("                                                   SvcMaskUuid:ffffffff-ffff-ffff-ffff-ffffffffffff;ManufacturerId:158;ManufacturerData:1,1,1;ManuMaskData:f,f,f;\n");
                    sendStr.append("                                                   ServiceDataUuid:0000180f-0000-1000-8000-00805f9b34fb;ServiceData:12;SvcDataMask:ff;ScanMode:1;CallbackType:1;\n");
                    sendStr.append("                                                   ResultType:0;NumOfAdvMatches:3;MatchMode:1;ReportDelay:0;ScanPhy:255;Legacy:false)\n");
                    sendStr.append("                     ScanStop\n");
                    sendStr.append("                     Back\n");
                    sendStr.append("*****************************************************\n");
                    break;

                case THROUGHPUT_MENU:
                    sendStr.append("\n******************** Throughput Menu ********************\n");
                    sendStr.append("                     Connect        (Ex: Connect DeviceName:Minato;DeviceAddress:73:B5:C0:E6:62:A4;ServiceUuid:0000180f-0000-1000-8000-00805f9b34fb;\n");
                    sendStr.append("                                                 SvcMaskUuid:ffffffff-ffff-ffff-ffff-ffffffffffff;ManufacturerId:158;ManufacturerData:1,1,1;ManuMaskData:f,f,f;\n");
                    sendStr.append("                                                 ServiceDataUuid:0000180f-0000-1000-8000-00805f9b34fb;ServiceData:12;SvcDataMask:ff;ScanMode:1;CallbackType:1;\n");
                    sendStr.append("                                                 ResultType:0;NumOfAdvMatches:3;MatchMode:1;ReportDelay:0;ScanPhy:255;Legacy:false)\n");
                    sendStr.append("                     ConnectToBdaddr                (Ex: ConnectToBdaddr 11:22:33:44:55:66\n");
                    sendStr.append("                     CancelConnect\n");
                    sendStr.append("                     ConfigureMTU                   (Ex: ConfigureMTU 512)\n");
                    sendStr.append("                     ReadPhy                        (Ex: ReadPhy)\n");
                    sendStr.append("                     SetPhy         (Ex: SetPhy Tx_Phy:2;Rx_Phy:2;Phy_Opt:00)\n");
                    sendStr.append("                     Pair\n");
                    sendStr.append("                     ReqConnPriority                (Ex: ReqConnPriority 0/1/2)\n");
                    sendStr.append("                     UnPair\n");
                    sendStr.append("                     Tx             (Ex: Tx Packet_Size:244;Num_Packets:50;\n");
                    sendStr.append("                                            TxService:0000FF01-0000-1000-8000-00805F9B34FB;TxChar:0000FF04-0000-1000-8000-00805F9B34FB)\n");
                    sendStr.append("                     Rx             (Ex: Rx NotificationsTimeInSec:2;NotificationsTimeInMin:5;RxService:0000FF01-0000-1000-8000-00805F9B34FB;RxChar:0000FF03-0000-1000-8000-00805F9B34FB)\n");
                    sendStr.append("                     TxRx        (Ex: TxRx Num_Packets:5000)\n");
                    sendStr.append("                     Latency        (Ex: Latency LatencyService:0000FF01-0000-1000-8000-00805F9B34FB;LatencyChar:0000FF02-0000-1000-8000-00805F9B34FB)\n");
                    sendStr.append("                     Disconnect\n");
                    sendStr.append("                     Back\n");
                    sendStr.append("*********************************************************\n");
                    break;
                case LE_COC_MENU:
                    sendStr.append("\n******************** LE COC Menu ********************\n");
                    sendStr.append("                     LeCoC_Connect                  (Ex : LeCoC_Connect DeviceAddress:73:B5:C0:E6:62:A4;psm:1;secure_flag:true\n");
					sendStr.append("                     LeCoC_Write                    (Ex : LeCoC_Write Data:500\n");
                    sendStr.append("                     LeCoC_listen                   (Ex : LeCoC_listen secure_flag:true\n");
                    sendStr.append("                     LeCoC_Offload_Connect          (Ex : LeCoC_Offload_Connect DeviceAddress:73:B5:C0:E6:62:A4;psm:1\n");
                    sendStr.append("                                                       Encryption:0/1;Authentication:0/1;SockName:offload_socket;HubId:0xFBFBFBFBFBFBFB0A;\n");
                    sendStr.append("                                                       EndpointId:0xFAFAFAFAFAFAFA0A;MaxPacketSize:100\n");
                    sendStr.append("                     LeCoC_Offload_listen           (Ex: LeCoC_Offload_listen Encryption:0/1;Authentication:0/1\n");
                    sendStr.append("                                                        SockName:offload_socket;HubId:0xFBFBFBFBFBFBFB0A;EndpointId:0xFAFAFAFAFAFAFA0A;MaxPacketSize:100\n");
                    sendStr.append("                     LeCoC_Close                    (Ex : LeCoC_Close secure_flag:true\n");
                    sendStr.append("                     Back\n");
                    sendStr.append("*********************************************************\n");
                    break;
                case GATT_CLIENT_MENU:
                    sendStr.append("\n******************** Gatt Client Menu ********************\n");
                    sendStr.append("                     StartBREDRDiscovery            \n");
                    sendStr.append("                     Connect        (Ex:    Connect DeviceName:Minato;DeviceAddress:73:B5:C0:E6:62:A4;ServiceUuid:0000180f-0000-1000-8000-00805f9b34fb;\n");
                    sendStr.append("                                                    SvcMaskUuid:ffffffff-ffff-ffff-ffff-ffffffffffff;ManufacturerId:158;ManufacturerData:1,1,1;ManuMaskData:f,f,f;\n");
                    sendStr.append("                                                    ServiceDataUuid:0000180f-0000-1000-8000-00805f9b34fb;ServiceData:12;SvcDataMask:ff;ScanMode:1;CallbackType:1;\n");
                    sendStr.append("                                                    ResultType:0;NumOfAdvMatches:3;MatchMode:1;ReportDelay:0;ScanPhy:255;Legacy:false)\n");
                    sendStr.append("                     ConnectToBdaddr                (Ex: ConnectToBdaddr DeviceAddress:11:22:33:44:55:66;initPhy:1(1->1M, 2->2M, 4->LE Coded Phy);\n");
                    sendStr.append("                                                    autoConnect:false(true->initiates background connection , false->doesn't initiate background connection));\n");
                    sendStr.append("                     ReadRemoteRssi                (Ex: ReadRemoteRssi)\n");
                    sendStr.append("                     DiscoverServiceUuid              (Ex: DiscoverServiceUuid ServiceUuid:0000180f-0000-1000-8000-00805f9b34fb)\n");
                    sendStr.append("                     ReadCharUUid                  (Ex: ReadCharUUid ServiceUuid:0000180f-0000-1000-8000-00805f9b34fb;CharUuid:00002a01-0000-1000-8000-00805f9b34fb;StartHdl:0;EndHdl:12)\n");
                    sendStr.append("                     CancelConnect\n");
                    sendStr.append("                     ReadPhy\n");
                    sendStr.append("                     SetPhy                         (Ex: SetPhy Tx_Phy:2;Rx_Phy:2;Phy_Opt:00)\n");
                    sendStr.append("                     ConfigureMTU                   (Ex: ConfigureMTU 512)\n");
                    sendStr.append("                     ReqConnPriority                (Ex: ReqConnPriority 0/1/2)\n");
                    sendStr.append("                     DiscoverServices\n");
                    sendStr.append("                     RW_Char                        (Ex: RW_Char Operation:1(1->Write,2->Read);ServiceUuid:0000FF01-0000-1000-8000-00805F9B34FB;CharUuid:0000FF03-0000-1000-8000-00805F9B34FB;Value:10;WriteType:2;FormatType:1(1->string,2->int))\n");
                    sendStr.append("                     RW_Desc                        (Ex: RW_Desc Operation:2(1->Write,2->Read);ServiceUuid:0000FF03-0000-1000-8000-00805F9B34FB;CharUuid:0000FF03-0000-1000-8000-00805F9B34FB;DescUuid:00002902-0000-1000-8000-00805F9B34FB;Value:10)\n");
                    sendStr.append("                     RegNotifications               (Ex: RegNotifications ServiceUuid:0000FF03-0000-1000-8000-00805F9B34FB;CharUuid:0000FF03-0000-1000-8000-00805F9B34FB)\n");
                    sendStr.append("                     DeRegNotifications             (Ex: DeRegNotifications ServiceUuid:0000FF03-0000-1000-8000-00805F9B34FB;CharUuid:0000FF03-0000-1000-8000-00805F9B34FB)\n");
                    sendStr.append("                     ReliableWrite                  (Ex: ReliableWrite Operation:1;ServiceUuid:0000FF03-0000-1000-8000-00805F9B34FB;CharUuid:0000FF03-0000-1000-8000-00805F9B34FB;Value:10;WriteType:2;FormatType:1(1->string,2->int))\n");
                    sendStr.append("                     ExecAbortReliableWrite         (Ex: ExecAbortReliableWrite Operation:1(1-> execute, 0 -> abort))\n");
                    sendStr.append("                     Disconnect\n");
                    sendStr.append("                     Unregister\n");
                    sendStr.append("                     Back\n");
                    sendStr.append("**********************************************************\n");
                    break;
                case GATT_SERVER_MENU:
                    sendStr.append("\n******************** Gatt Server Menu ********************\n");
                    sendStr.append("                       Register\n");
                    sendStr.append("                       AddService                   (Ex: AddService ServiceUuid:0000FF01-0000-1000-8000-00805F9B34FB;CharUuid:00002a06-0000-1000-8000-00805f9b34fb;Properties:0x10,0x01;Permissions:0x01,0x10;Value:0x12)\n");
                    sendStr.append("                       RemoveService                (Ex: RemoveService ServiceUuid:0000FF01-0000-1000-8000-00805F9B34FB)\n");
                    sendStr.append("                       ClearServices\n");
                    sendStr.append("                       GetServices\n");
                    sendStr.append("                       SetPhy                       (Ex: SetPhy DeviceAddress:11:22:33:44:55:66;Tx_Phy:2;Rx_Phy:2;Phy_Opt:00)\n");
                    sendStr.append("                       ReadPhy                      (Ex: ReadPhy 11:22:33:44:55:66)\n");
                    sendStr.append("                       Disconnect                   (Ex: Disconnect 11:22:33:44:55:66)\n");
                    sendStr.append("                       Deregister\n");
                    sendStr.append("                       Back\n");
                    sendStr.append("**********************************************************\n");
                    break;

                case INVALID_INPUT:
                    sendStr.append("\nInvalid Input\n");
                    break;

                case SOC_CLOSE_ACK:
                    sendStr.append("A_Close");
                    break;

                default:
                    sendStr.append("\nError\n");
                    break;
            }

            return sendStr.toString();
        }

        private void processInput(String inputString) {
            String[] tmp;
            Message msg = null;

            switch (mainMenuState) {
                case MAIN_MENU:
                    tmp = inputString.split(" ", 2);
                    if (tmp.length == 1) {
                        if (inputString.equals("Advertiser")) {
                            mainMenuState = ADV_MENU;
                            processOutputState = ADV_MENU;
                        } else if (inputString.equals("Scanner")) {
                            mainMenuState = SCAN_MENU;
                            processOutputState = SCAN_MENU;
                        } else if (inputString.equals("Throughput")) {
                            mainMenuState = THROUGHPUT_MENU;
                            processOutputState = THROUGHPUT_MENU;
                        } else if (inputString.equals("GattClient")) {
                            mainMenuState = GATT_CLIENT_MENU;
                            processOutputState = GATT_CLIENT_MENU;
                        } else if (inputString.equals("LECOC")) {
                            mainMenuState = LE_COC_MENU;
                            processOutputState = LE_COC_MENU;
                        } else if (inputString.equals("HoldWakeLock")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = NONE;
                            MainActivity.wl.acquire();
                            MainActivity.wl_acquired = true;
                            Log.d(TAG,"Wakelock acquired");
                            sendStr.setLength(0);
                            sendStr.append("Wakelock acquired");
                            SocketServer.sendSocketData(sendStr.toString());
                        } else if (inputString.equals("ReleaseWakeLock")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = NONE;
                            MainActivity.wl.release();
                            MainActivity.wl_acquired = false;
                            Log.d(TAG,"Wakelock released");
                            sendStr.setLength(0);
                            sendStr.append("Wakelock released");
                            SocketServer.sendSocketData(sendStr.toString());
                        } else if (inputString.equals("GattServer")) {
                            mainMenuState = GATT_SERVER_MENU;
                            processOutputState = GATT_SERVER_MENU;
                        } else if (inputString.equals("GetConnectedDevices")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                         BleAppService.MSG_MA_GET_CONNECTED_DEVICES, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (inputString.equals("GetBondedDevices")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                         BleAppService.MSG_MA_GET_PAIRED_DEVICES, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (inputString.equals("Close")) {
                            closeReceived = true;
                            mainMenuState = MAIN_MENU;
                            processOutputState = SOC_CLOSE_ACK;
                        } else {
                            mainMenuState = MAIN_MENU;
                            processOutputState = INVALID_INPUT;
                        }
                    } else if (tmp.length == 2) {
                        if (tmp[0].equals("Pair")) {
                            if (BleAppService.bleAdapter.
                                    checkBluetoothAddress(tmp[1].toUpperCase())) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_START_BLE_PAIR,
                                        tmp[1].toUpperCase());
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("UnPair")) {
                            if (BleAppService.bleAdapter.
                                    checkBluetoothAddress(tmp[1].toUpperCase())) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_START_BLE_UNPAIR,
                                        tmp[1].toUpperCase());
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("Disconnect")) {
                            if (BleAppService.bleAdapter.
                                    checkBluetoothAddress(tmp[1].toUpperCase())) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_START_BLE_DISCONNECT,
                                        tmp[1].toUpperCase());
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else {
                        mainMenuState = MAIN_MENU;
                        processOutputState = INVALID_INPUT;
                        }
                    } else {
                        processOutputState = INVALID_INPUT;
                    }
                    break;

                case ADV_MENU:
                    tmp = inputString.split(" ", 2);
                    if (tmp.length == 2) {
                        if (tmp[0].equals("AdvStart")) {
                            Adv advParam = parse.AdvParse(tmp[1]);
                            if (advParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_START_BLE_ADV, advParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        }else if (tmp[0].equals("EnableAdvSet")) {
                            EnableAdv EnadvParam = parse.EnableAdvParse(tmp[1]);
                            if (EnadvParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_ENABLE_ADV, EnadvParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("SetAdvData")) {
                            AdvDataInfo advdata = parse.AdvDataInfoParse(tmp[1]);
                            if (advdata != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_SET_ADV_DATA, advdata);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("SetScanRespData")) {
                            AdvDataInfo scanrespdata = parse.AdvDataInfoParse(tmp[1]);
                            if (scanrespdata != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_SET_SCAN_RESP_DATA, scanrespdata);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("SetAdvParams")) {
                            SetAdvParam advparam = parse.SetAdvParamParse(tmp[1]);
                            if (advparam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_SET_ADV_PARAM, advparam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("SetPeriodicAdvParams")) {
                            SetPerAdvParam peradvparam = parse.SetPerAdvParamParse(tmp[1]);
                            if (peradvparam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_SET_PERIODIC_ADV_PARAM, peradvparam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("SetPeriodicAdvData")) {
                            SetPerAdvData peradvdata = parse.SetPerAdvDataParse(tmp[1]);
                            if (peradvdata != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_SET_PERIODIC_DATA, peradvdata);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("EnablePeriodicAdvSet")) {
                            EnablePerAdv enperadv = parse.EnablePerAdvParse(tmp[1]);
                            if (enperadv != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_MA_BLE_ENABLE_PERIODIC_ADV, enperadv);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("GetOwnAddrSet")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(BleAppService.MSG_MA_BLE_GET_OWN_ADDRESS,
                                    Integer.parseInt(tmp[1]));
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("AdvStop")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(BleAppService.MSG_MA_STOP_BLE_ADV,
                                    Integer.parseInt(tmp[1]));
                            BleAppService.msghandler.sendMessage(msg);
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else if (tmp.length == 1) {
                        if (tmp[0].equals("Back")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = MAIN_MENU;
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else {
                        processOutputState = INVALID_INPUT;
                    }
                    break;

                case SCAN_MENU:
                    tmp = inputString.split(" ", 2);
                    if (tmp.length == 2) {
                        if (tmp[0].equals("ScanStart")) {
                            Scan scanParam = parse.ScanParse(tmp[1]);
                            if (scanParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(BleAppService.MSG_MA_START_BLE_SCAN,
                                        scanParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else if (tmp.length == 1) {
                        if (tmp[0].equals("ScanStop")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(BleAppService.MSG_MA_STOP_BLE_SCAN, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("Back")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = MAIN_MENU;
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else {
                        processOutputState = INVALID_INPUT;
                    }
                    break;

                case THROUGHPUT_MENU:
                    tmp = inputString.split(" ", 2);
                    if (tmp.length == 2) {
                        if (tmp[0].equals("Connect")) {
                            Scan scanParam = parse.ScanParse(tmp[1]);
                            if (scanParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(BleAppService.MSG_SM_START_BLE_CONNECT,
                                        scanParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ConnectToBdaddr")) {
                            if (BleAppService.bleAdapter.checkBluetoothAddress(tmp[1].toUpperCase())) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_SM_BLE_CONNECT_TO_BDADDR,
                                        tmp[1].toUpperCase());
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ConfigureMTU")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_START_BLE_GATT_CONFIGURE_MTU_SIZE,
                                    Integer.parseInt(tmp[1]));
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("ReqConnPriority")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_START_REQ_CONN_PRIORITY,
                                    Integer.parseInt(tmp[1]));
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("SetPhy")) {
                            PhyUpdate phyUpdateParam = parse.PhyUpdateParse(tmp[1]);
                            if (phyUpdateParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_SM_START_BLE_PHY_UPDATE, phyUpdateParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("Tx")) {
                            DataTx dataTxParam = parse.DataTxParse(tmp[1]);
                            if (dataTxParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_SM_START_BLE_DATA_TX_TEST, dataTxParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("Rx")) {
                            DataRx dataRxParam = parse.DataRxParse(tmp[1]);
                            if (dataRxParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_SM_START_BLE_DATA_RX_TEST, dataRxParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("TxRx")) {
                            DataTx dataTxParam = parse.DataTxParse(tmp[1]);
                            if (dataTxParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_SM_START_BLE_TX_RX_TEST, dataTxParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("Latency")) {
                            LatencyTest latencyTestParam = parse.LatencyTestParse(tmp[1]);
                            if (latencyTestParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_SM_START_BLE_LATENCY_TEST, latencyTestParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else if (tmp.length == 1) {
                        if (tmp[0].equals("ReadPhy")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_START_BLE_READ_PHY, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("CancelConnect")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_BLE_GATT_CANCEL_CONNECT, null);
                            BleAppService.msghandler.sendMessage(msg);
                        }  else if (tmp[0].equals("Pair")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_START_BLE_PAIR, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("UnPair")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_START_BLE_UNPAIR, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("Disconnect")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_SM_START_BLE_GATT_DISC, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("Back")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = MAIN_MENU;
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else {
                        processOutputState = INVALID_INPUT;
                    }
                    break;
                case LE_COC_MENU:
                    tmp = inputString.split(" ", 2);
                    if (tmp.length == 2) {
                        if(tmp[0].equals("LeCoC_Connect")) {
                            LecocConnect lecocConnectParam = parse.LecocConnectParse(tmp[1]);
                            if (lecocConnectParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_COC_CONNECT, lecocConnectParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("LeCoC_Offload_Connect")) {
                            LecocOffloadConnect lecocOffloadConnectParam = parse.LecocOffloadConnectParse(tmp[1]);
                            if (lecocOffloadConnectParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_COC_OFFLOAD_CONNECT, lecocOffloadConnectParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("LeCoC_Write")) {
                            String[] val = tmp[1].split(":", 2);
                            if (val.length == 2) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_COC_WRITE, Integer.valueOf(val[1]));
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("LeCoC_Close")) {
                            String[] val1 = tmp[1].split(":", 2);
                            if (val1.length == 2) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_COC_CLOSE, Boolean.parseBoolean(val1[1]));
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("LeCoC_listen")) {
                            String[] val1 = tmp[1].split(":", 2);
                            if (val1.length == 2) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_LISTEN, Boolean.parseBoolean(val1[1]));
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("LeCoC_Offload_listen")) {
                            LecocOffloadListen lecocOffloadListenParam = parse.LecocOffloadListenParse(tmp[1]);
                            if (lecocOffloadListenParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_COC_OFFLOAD_LISTEN, lecocOffloadListenParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else {
                            processOutputState = INVALID_INPUT;
                        }

                    } else if (tmp.length == 1){
                        if (tmp[0].equals("Back")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = MAIN_MENU;
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    }else {
                        processOutputState = INVALID_INPUT;
                    }
                    break;
                case GATT_CLIENT_MENU:
                    tmp = inputString.split(" ", 2);
                    if (tmp.length == 2) {
                        if (tmp[0].equals("Connect")) {
                            Scan scanParam = parse.ScanParse(tmp[1]);
                            if (scanParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                       BleAppService.MSG_GC_START_BLE_CONNECT, scanParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ConnectToBdaddr")) {
                            Scan initParam = parse.ScanParse(tmp[1]);
                            if (initParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_CONNECT_TO_BDADDR,
                                        initParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ReadCharUUid")) {
                            ReadWriteOp readWriteCharOpParam = parse.ReadWriteOpParse(tmp[1]);
                            if (readWriteCharOpParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_READ_CHAR_UUID, readWriteCharOpParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("DiscoverServiceUuid")) {
                            ReadWriteOp readWriteCharOpParam = parse.ReadWriteOpParse(tmp[1]);
                            if (readWriteCharOpParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_DISC_SRVC_UUID, readWriteCharOpParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        }  else if (tmp[0].equals("SetPhy")) {
                            PhyUpdate phyUpdateParam = parse.PhyUpdateParse(tmp[1]);
                            if (phyUpdateParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_PHY_UPDATE, phyUpdateParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ConfigureMTU")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_GATT_CONFIGURE_MTU_SIZE,
                                    Integer.parseInt(tmp[1]));
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("ReqConnPriority")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_BLE_GATT_REQ_CONN_PRIORITY,
                                    Integer.parseInt(tmp[1]));
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("RW_Char")) {
                            ReadWriteOp readWriteCharOpParam = parse.ReadWriteOpParse(tmp[1]);
                            if (readWriteCharOpParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_GATT_WRITE_READ_CHAR, readWriteCharOpParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("RW_Desc")) {
                            ReadWriteOp readWriteDescOpParam = parse.ReadWriteOpParse(tmp[1]);
                            if (readWriteDescOpParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_GATT_WRITE_READ_DESC, readWriteDescOpParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("RegNotifications")) {
                            ReadWriteOp regNotifParam = parse.ReadWriteOpParse(tmp[1]);
                            if (regNotifParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_REGISTER_BLE_GATT_NOTIFICATIONS, regNotifParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("DeRegNotifications")) {
                            ReadWriteOp deregNotifParam = parse.ReadWriteOpParse(tmp[1]);
                            if (deregNotifParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_DEREGISTER_BLE_GATT_NOTIFICATIONS, deregNotifParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ReliableWrite")) {
                            ReadWriteOp reliableWriteParam = parse.ReadWriteOpParse(tmp[1]);
                            if (reliableWriteParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GC_START_BLE_GATT_RELIABLE_WRITE, reliableWriteParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ExecAbortReliableWrite")) {
                            ReadWriteOp execWrite = parse.ReadWriteOpParse(tmp[1]);
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_GATT_EXECUTE_ABORT_RELIABLE_WRITE, execWrite.operation);
                            BleAppService.msghandler.sendMessage(msg);
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else if (tmp.length == 1) {
                        if (tmp[0].equals("ReadPhy")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_READ_PHY, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("DiscoverServices")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_GATT_DISCOVER, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("StartBREDRDiscovery")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BREDR_DISC, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("ReadRemoteRssi")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_READ_REMOTE_RSSI, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("CancelConnect")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_GATT_CANCEL_CONNECT, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("Disconnect")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_GATT_DISC, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("Unregister")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GC_START_BLE_GATT_UNREG, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if (tmp[0].equals("Back")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = MAIN_MENU;
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else {
                        processOutputState = INVALID_INPUT;
                    }
                    break;
                case GATT_SERVER_MENU:
                    tmp = inputString.split(" ", 2);
                    if(tmp.length == 2) {
                        if (tmp[0].equals("AddService")) {
                            AddServices AddServiceParam = parse.AddServicesParse(tmp[1]);
                            if ( AddServiceParam != null) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GS_START_BLE_ADD_SERVICE,AddServiceParam);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if(tmp[0].equals("RemoveService")) {
                            String [] tmp2 = tmp[1].split(":");
                            if(tmp2.length == 2) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GS_START_BLE_REMOVE_SERVICE, tmp2[1]);
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("ReadPhy")) {
                            if (BleAppService.bleAdapter.
                                  checkBluetoothAddress(tmp[1].toUpperCase())) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GS_START_BLE_READ_PHY,
                                          tmp[1].toUpperCase());
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("SetPhy")) {
                            PhyUpdate phyUpdateParam = parse.PhyUpdateParse(tmp[1]);
                            if (phyUpdateParam != null ) {
                              processOutputState = NONE;
                              msg = BleAppService.msghandler.obtainMessage(
                                BleAppService.MSG_GS_START_BLE_PHY_UPDATE, phyUpdateParam);
                              BleAppService.msghandler.sendMessage(msg);
                            } else {
                              processOutputState = INVALID_INPUT;
                            }
                        } else if (tmp[0].equals("Disconnect")) {
                            if (BleAppService.bleAdapter.
                                    checkBluetoothAddress(tmp[1].toUpperCase())) {
                                processOutputState = NONE;
                                msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GS_START_BLE_DISCONNECT,
                                        tmp[1].toUpperCase());
                                BleAppService.msghandler.sendMessage(msg);
                            } else {
                                processOutputState = INVALID_INPUT;
                            }
                        }  else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else if(tmp.length == 1) {
                        if (tmp[0].equals("Back")) {
                            mainMenuState = MAIN_MENU;
                            processOutputState = MAIN_MENU;
                        } else if(tmp[0].equals("ClearServices")) {
                             processOutputState = NONE;
                             msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GS_START_BLE_CLEAR_SERVICES, null);
                             BleAppService.msghandler.sendMessage(msg);
                        } else if(tmp[0].equals("GetServices")) {
                             processOutputState = NONE;
                             msg = BleAppService.msghandler.obtainMessage(
                                        BleAppService.MSG_GS_START_BLE_GET_SERVICES, null);
                             BleAppService.msghandler.sendMessage(msg);
                        }  else if(tmp[0].equals("Register")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GS_START_BLE_REGISTER, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else if(tmp[0].equals("Deregister")) {
                            processOutputState = NONE;
                            msg = BleAppService.msghandler.obtainMessage(
                                    BleAppService.MSG_GS_START_BLE_DEREGISTER, null);
                            BleAppService.msghandler.sendMessage(msg);
                        } else {
                            processOutputState = INVALID_INPUT;
                        }
                    } else {
                     processOutputState = INVALID_INPUT;
                    }
                    break;
            }
        }
    }

    public void closeSocketServer() {
        Log.i(TAG, "closeSocketServer()");
        closeReceived = false;
        socketOpen = false;
        mainMenuState = MAIN_MENU;
        processOutputState = MAIN_MENU;

        if (client != null) {
            try {
                client.close();
                Log.i(TAG, "client socket closed");
            } catch (IOException e) {
                Log.e(TAG, "client socket close failed");
                e.printStackTrace();
            }
            client = null;
        }

        if (server != null) {
            try {
                server.close();
                Log.i(TAG, "server closed");
            } catch (IOException e) {
                Log.e(TAG, "server close failed");
                e.printStackTrace();
            }
            server = null;
        }
        INSTANCE = null;
    }

    private void showMessage(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    public static void sendSocketData(String data) {
        if (socketOpen) {
            try {
                mutex.acquire();
                try {
                    try {
                        if (data.getBytes().length <= socSendBufferSize) {
                            data = data + '\n';
                            output.write(data.getBytes(), 0, data.getBytes().length);
                            output.flush();
                            Log.i(TAG, "Sent: " + data);
                        }else{
                            Log.i(TAG, "Data length is more than " + socSendBufferSize + " , ignore packet");
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "There is an exception when writing to socket");
                        e.printStackTrace();
                        INSTANCE.closeSocketServer();
                        INSTANCE.showMessage("Socket closed, restart app");
                    }
                } finally {
                    mutex.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "There is an exception when acquiring mutex");
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Socket is not open, ignore packet");
        }
    }
}
