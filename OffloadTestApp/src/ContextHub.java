/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 *
 */

package org.codeaurora.bluetooth.offload_testapp;

import android.hardware.contexthub.IContextHub;
import android.hardware.contexthub.IEndpointCallback;
import android.hardware.contexthub.EndpointId;
import android.hardware.contexthub.EndpointInfo;
import android.hardware.contexthub.Message;
import android.hardware.contexthub.MessageDeliveryStatus;
import android.hardware.contexthub.Reason;
import android.hardware.contexthub.Service;
import android.hardware.contexthub.ErrorCode;
import android.hardware.contexthub.HubInfo;
import android.hardware.contexthub.VendorHubInfo;
import android.hardware.contexthub.IEndpointCommunication;
import android.hardware.contexthub.ContextHubInfo;
import java.util.HashMap;
import android.util.Log;
import java.util.List;

import android.os.ServiceManager;
import android.os.IBinder;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

public class ContextHub {
    private static final String TAG = "ContextHub";
    public static IContextHub mIContextHubApp;
    public static IEndpointCommunication mIEndpointCommunication;
    public static int mNumContextHubSessions = 0;
    public static int mCurMsgSequenceNum = 0;
    public static long mLeCocHubId = 0xFBFBFBFBFBFBFB0AL;

    public static HashMap<Integer, EndpointInfo> sessionIdToInitrEndpointInfoMap = new HashMap<Integer, EndpointInfo>();
    private static Handler mMainThreadHandler;
    public ContextHub (Handler handler) {
        this.mMainThreadHandler = handler;
    }

    public static void contextHubCleanup() {
        if (mIEndpointCommunication != null) {
            try {
                mIEndpointCommunication.unregister();
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException during IEndpointCommunication unregistration: " + e);
            } catch (Exception e) {
                Log.e(TAG, "General error during IEndpointCommunication unregistration: " + e);
            } finally {
                try {
                    if (mIContextHubApp != null) {
                        IBinder binder = mIContextHubApp.asBinder();
                        if (binder != null) {
                            binder.unlinkToDeath(deathRecipient, 0);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error unlinking death recipient for IContextHubApp in onDestroy: " + e);
                } finally {
                    mIContextHubApp = null;
                    mIEndpointCommunication = null;
                }
            }
        } else if (mIContextHubApp != null) {
            try {
                IBinder binder = mIContextHubApp.asBinder();
                if (binder != null) {
                    binder.unlinkToDeath(deathRecipient, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error unlinking death recipient for IContextHubApp in onDestroy: " + e);
            } finally {
                mIContextHubApp = null;
            }
        }
    }

    public static void registerForContextHubHalService() {
        mIContextHubApp = null;
        mIEndpointCommunication = null;
        Log.e(TAG, "registerForContextHubHalService");
        boolean isDeclared = ServiceManager.isDeclared(IContextHub.DESCRIPTOR + "/default");
        Log.d(TAG, "IContextHub.DESCRIPTOR " + IContextHub.DESCRIPTOR
                    + " isDeclared " + isDeclared);
        if (!isDeclared) {
            Log.e(TAG, "IContextHub not declared");
        }

        IBinder binder = Binder.allowBlocking(
            ServiceManager.waitForDeclaredService(IContextHub.DESCRIPTOR + "/default"));

        if (binder == null) {
            Log.e(TAG, "Failed to obtain IBinder");
        }

        mIContextHubApp = IContextHub.Stub.asInterface(binder);
        if (binder == null) {
            Log.w(TAG, "mContextHubFinder null");
        }

        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register DeathRecipient for " + binder
                    + " or callback registration failed : " + e);
        }
        try{
            HubInfo hub_info = new HubInfo();
            hub_info.hubId = mLeCocHubId;
            VendorHubInfo vendor_hub = new VendorHubInfo();
            vendor_hub.name = new String("BT LECOC APP");
            vendor_hub.version = 1;
            //hub_info.setVendorHubInfo(vendor_hub);
            hub_info.hubDetails = HubInfo.HubDetails.vendorHubInfo(vendor_hub);
            mIEndpointCommunication = mIContextHubApp.registerEndpointHub(iEndpointCb, hub_info);
        } catch (RemoteException | IllegalStateException e) { // Catch both RemoteException (checked) and IllegalStateException (runtime)
            Log.e(TAG, "Error registering endpoint hub: " + e.getMessage()); // Log the specific error
            e.printStackTrace();
        }

        Log.d(TAG, "IContextHub binding successful");
    }
    /**
     * DeathReceipient handler to binder service disconnection.
     */
    static IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.d(TAG, "binderDied");
            mIContextHubApp = null;
            /*
             * Bind to vendor service again
             */
            //registerForContextHubHalService();
        }
    };
    private static IEndpointCallback iEndpointCb = new IEndpointCallback.Stub() {
        @Override
        public void onEndpointStarted(EndpointInfo[] endpointInfos) throws RemoteException{
            Log.d(TAG, "onEndpointStarted Callback received ");
        }

        @Override
        public void onEndpointStopped(EndpointId[] endpointIds, byte reason) {
            Log.d(TAG, "onEndpointStopped Callback received ");
        }

        @Override
        public void onMessageReceived(int sessionId, Message msg) throws RemoteException{
            Log.d(TAG, "onMessageReceived Callback received with sessionId " + sessionId);
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    SocketServer.sendSocketData("MessageReceived Callback received with sessionId  :: "+sessionId);
                    SocketServer.sendSocketData("flags:"+msg.flags+" sequenceNumber:"+msg.sequenceNumber+" type:"+msg.type);
                    for (byte byte_c : msg.content) {
                        SocketServer.sendSocketData("msg.content: " + byte_c);
                    }

                    //Send MessageDeliveryStatus
                    if (msg.flags == android.hardware.contexthub.Message.FLAG_REQUIRES_DELIVERY_STATUS) {
                        MessageDeliveryStatus status = new MessageDeliveryStatus ();
                        status.messageSequenceNumber = msg.sequenceNumber;
                        status.errorCode = ErrorCode.OK;
                        try {
                            mIEndpointCommunication.sendMessageDeliveryStatusToEndpoint(sessionId, status);
                            SocketServer.sendSocketData("MessageDeliveryStatus sent");

                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
        @Override
        public void onMessageDeliveryStatusReceived(int sessionId, MessageDeliveryStatus msgStatus) throws RemoteException{
            Log.d(TAG, "onMessageDeliveryStatusReceived Callback received with sessionId :" + sessionId);
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    SocketServer.sendSocketData("onMessageDeliveryStatusReceived Callback received with sessionId :: "+sessionId);
                    SocketServer.sendSocketData("messageSequenceNumber "+msgStatus.messageSequenceNumber+ " errorCode "+msgStatus.errorCode);
                }
            });
        }
        @Override
        public void onCloseEndpointSession(int sessionId, byte reason) throws RemoteException{
            Log.d(TAG, "onCloseEndpointSession Callback received with sessionId :" + sessionId);
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    SocketServer.sendSocketData("onCloseEndpointSession Callback received with sessionId :: "+sessionId);
                }
            });
        }
        @Override
        public void onEndpointSessionOpenRequest(int sessionId, EndpointId destination,
            EndpointId initiator, String serviceDescriptor) throws RemoteException{
            Log.d(TAG, "onEndpointSessionOpenRequest Callback received with sessionId :" + sessionId);
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    SocketServer.sendSocketData("onEndpointSessionOpenRequest Callback received with sessionId :: "+sessionId);
                }
            });
        }
        @Override
        public void onEndpointSessionOpenComplete(int sessionId) throws RemoteException{
            Log.d(TAG, "onEndpointSessionOpenComplete Callback received with sessionId :" + sessionId);
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    SocketServer.sendSocketData("onEndpointSessionOpenComplete Callback received with sessionId :: "+sessionId);
                }
            });
        }
        @Override
        public String getInterfaceHash() {
            return android.hardware.contexthub.IContextHub.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return android.hardware.contexthub.IContextHub.VERSION;
        }
    };

    public static void handleGetHubs() {
        Log.d(TAG,"Inside getHubs handling");
        SocketServer.sendSocketData("inside getHubs handling");
        if (ContextHub.mIContextHubApp != null) {
            try {
                final List<android.hardware.contexthub.HubInfo> apphalHubs = mIContextHubApp.getHubs();
                for (android.hardware.contexthub.HubInfo hubInfo : apphalHubs) {
                    Log.d(TAG,"Hub_ID: " + hubInfo.hubId);
                    SocketServer.sendSocketData("Hub_ID: " + hubInfo.hubId);
                    switch (hubInfo.hubDetails.getTag()) {
                        case android.hardware.contexthub.HubInfo.HubDetails.contextHubInfo:
                            android.hardware.contexthub.ContextHubInfo ctx_hub_info = hubInfo.hubDetails.getContextHubInfo();
                            Log.d(TAG,"contextHubInfo name :" + ctx_hub_info.name);
                            SocketServer.sendSocketData("contextHubInfo name :" + ctx_hub_info.name);
                        break;
                        case android.hardware.contexthub.HubInfo.HubDetails.vendorHubInfo:
                            android.hardware.contexthub.VendorHubInfo vendor_hub_info = hubInfo.hubDetails.getVendorHubInfo();
                            Log.d(TAG,"VendorHubInfo name :" + vendor_hub_info.name);
                            SocketServer.sendSocketData("VendorHubInfo name :" + vendor_hub_info.name);
                        break;
                        default:
                            Log.d(TAG,"Invalid Hub tag");
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            SocketServer.sendSocketData("IContextHubApp is not initialized or is null. Cannot get hubs.");
            Log.e(TAG, "IContextHubApp is null when trying to getHubs.");
        }
    }

    public static void handleGetEndpoints() {
        Log.d(TAG, "Inside getEndpoints handling");
        if (ContextHub.mIContextHubApp != null) {
            try {
                final List<EndpointInfo> endpointsList = mIContextHubApp.getEndpoints();
                for (EndpointInfo info : endpointsList) {
                    Log.d(TAG, "Hub_ID: " + info.id.hubId);
                    Log.d(TAG, "Endpoint_ID: " + info.id.id);
                    SocketServer.sendSocketData("Hub_ID: " + info.id.hubId + " Endpoint_ID: " + info.id.id);
                    for (Service svc : info.services) {
                        Log.d(TAG, "serviceDescriptor: " + svc.serviceDescriptor);
                        SocketServer.sendSocketData("serviceDescriptor: " + svc.serviceDescriptor);
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            SocketServer.sendSocketData("IContextHubApp is not initialized or is null. Cannot get endpoints.");
            Log.e(TAG, "IContextHubApp is null when trying to getEndpoints.");
        }
    }

    public static void handleOpenSession(String inputString) {
        String[] tmp = inputString.split(" ", 5);
        if(tmp.length == 5){
            Log.d(TAG,"Split into 6 portions");
            String splits[];
            for(int i=0;i<5;i++){
                Log.d(TAG,"tmp["+i+"] is :: "+tmp[i]);
            }
            if(tmp[0].equals("openSession")){
                EndpointInfo initr = new EndpointInfo();
                initr.id = new EndpointId();
                splits = tmp[1].split(":",2);
                initr.id.id = Long.parseLong(splits[1]);
                //splits = tmp[2].split(":",2);
                //initr.id.hubId = Long.parseLong(splits[1]);
                initr.id.hubId = mLeCocHubId;

                initr.type = android.hardware.contexthub.EndpointInfo.EndpointType.APP;
                initr.version = 1;
                String perm = new String ("All_Perms");
                initr.requiredPermissions = new String[1];
                initr.requiredPermissions[0] = perm;

                android.hardware.contexthub.Service svc = new android.hardware.contexthub.Service();
                svc.format = android.hardware.contexthub.Service.RpcFormat.CUSTOM;

                splits = tmp[4].split(":",2);
                String svc_desc = splits[1];
                //initr.name = new String();
                initr.name = svc_desc;

                svc.serviceDescriptor = svc_desc;
                svc.majorVersion = 1;
                svc.minorVersion = 0;
                initr.services = new Service[1];
                initr.services[0] = svc;

                EndpointId dest_id = new EndpointId();
                splits = tmp[2].split(":",2);
                dest_id.id = Long.parseLong(splits[1]);
                splits = tmp[3].split(":",2);
                dest_id.hubId = Long.parseLong(splits[1]);

                EndpointId initr_id = new EndpointId();
                initr_id.id = initr.id.id;
                initr_id.hubId = initr.id.hubId;
                try {
                    mIEndpointCommunication.registerEndpoint(initr);
                    SocketServer.sendSocketData("Registered Initiator Endpoint with:");
                    SocketServer.sendSocketData("Hub_ID: " + initr.id.hubId + " Endpoint_ID: " + initr.id.id);
                    SocketServer.sendSocketData("serviceDescriptor: " + svc.serviceDescriptor);
                    final int[] session_id_range  = mIEndpointCommunication.requestSessionIdRange(10);
                    int mSessionId = 0;
                    for (int session_id : session_id_range) {
                        Log.d(TAG,"session_id range: " + session_id);
                        mSessionId = session_id;
                    }
                    mSessionId = mSessionId - mNumContextHubSessions;
                    mIEndpointCommunication.openEndpointSession(mSessionId, dest_id, initr_id, svc_desc);
                    mNumContextHubSessions++;
                    SocketServer.sendSocketData("OpenSessionReq sent for Dest Endpoint with:");
                    SocketServer.sendSocketData("Hub_ID: " + dest_id.hubId + " Endpoint_ID: " + dest_id.id);
                    SocketServer.sendSocketData("serviceDescriptor: " + svc_desc);
                    sessionIdToInitrEndpointInfoMap.put(mSessionId, initr);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.d(TAG,"open_session tmp.length " + tmp.length);
        }
    }
    public static void handleCloseSession(String inputString) {
        String[] tmp = inputString.split(" ", 2);
        if(tmp.length == 2){
            Log.d(TAG,"Split into 2 portions");
            String splits[];
            for(int i=0;i<2;i++){
                Log.d(TAG,"tmp["+i+"] is :: "+tmp[i]);
            }
            if(tmp[0].equals("closeSession")){
                splits = tmp[1].split(":",2);
                int session_id = Integer.parseInt(splits[1]);
                //android.hardware.contexthub.Reason reason = android.hardware.contexthub.Reason.ENDPOINT_GONE;
                byte reason = 6;
                try {
                    mIEndpointCommunication.closeEndpointSession(session_id, reason);
                    SocketServer.sendSocketData("closeEndpointSession Completed for session_id:" + session_id);
                    mNumContextHubSessions--;
                    if (sessionIdToInitrEndpointInfoMap.get(session_id) != null) {
                        EndpointInfo info = sessionIdToInitrEndpointInfoMap.get(session_id);
                        mIEndpointCommunication.unregisterEndpoint(info);
                        SocketServer.sendSocketData("unregisterEndpoint Completed for session_id:" + session_id);
                        sessionIdToInitrEndpointInfoMap.remove(session_id);
                    } else {
                        Log.d(TAG,"Could not value in sessionIdToInitrEndpointInfoMap for " + session_id);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.d(TAG,"closeSession tmp.length " + tmp.length);
        }
    }
    public static void handleSendMessageToEndpoint(String inputString) {
        String[] tmp = inputString.split(" ", 5);
        if(tmp.length == 4 || tmp.length == 5){
            //Log.d(TAG,"Split into %d portions", tmp.length);
            String splits[];
            for(int i=0;i<tmp.length;i++){
                Log.d(TAG,"tmp["+i+"] is :: "+tmp[i]);
            }
            if(tmp[0].equals("sendMessageToEndpoint")){
                splits = tmp[1].split(":",2);
                int session_id = Integer.parseInt(splits[1]);

                android.hardware.contexthub.Message endpt_msg = new android.hardware.contexthub.Message();
                endpt_msg.flags = android.hardware.contexthub.Message.FLAG_REQUIRES_DELIVERY_STATUS;
                endpt_msg.sequenceNumber = mCurMsgSequenceNum++;
                splits = tmp[2].split(":",2);
                endpt_msg.type = Integer.parseInt(splits[1]);
                endpt_msg.permissions = new String[]{"PERMISSION1", "PERMISSION2"};
                splits = tmp[3].split(":",2);
                int msg_len = Integer.parseInt(splits[1]);
                endpt_msg.content = new byte[msg_len];
                if (msg_len > 0) {
                    splits = tmp[4].split(":",2);
                    int byte_len = splits[1].length()/2;
                    if (msg_len == byte_len) {
                        for (int i=0; i <byte_len; i++) {
                            String byte_c = splits[1].substring(i * 2, i * 2 + 2);
                            endpt_msg.content[i] = (byte)(Integer.parseInt(byte_c, 16) & 0xff);
                            Log.d(TAG,"Endpoint Msg_Byte: " + endpt_msg.content[i]);
                        }
                    } else {
                        Log.d(TAG,"Invalid msg with msg_len: " + msg_len + " actual byte len:" +byte_len) ;
                    }
                }

                try {
                    mIEndpointCommunication.sendMessageToEndpoint(session_id, endpt_msg);
                    SocketServer.sendSocketData("sendMessageToEndpoint sent SessionId:" + session_id);

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.d(TAG,"open_session tmp.length " + tmp.length);
        }
    }
}
