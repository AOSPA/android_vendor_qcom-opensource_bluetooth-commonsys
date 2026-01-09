/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.codeaurora.bluetooth.bttestapp.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Foreground Service for managing L2CAP CoC connections independently of Activity lifecycle.
 * Maintains persistent Bluetooth connections and provides UI updates through callbacks.
 */
public class BluetoothL2capService extends Service implements L2capCocCallback {
    private static final String TAG = "BluetoothL2capService";
    
    // Notification constants
    private static final String CHANNEL_ID = "bluetooth_l2cap_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Service actions
    public static final String ACTION_START_SERVER = "start_server";
    public static final String ACTION_STOP_SERVER = "stop_server";
    public static final String ACTION_CONNECT_CLIENT = "connect_client";
    public static final String ACTION_DISCONNECT_CLIENT = "disconnect_client";
    public static final String ACTION_SEND_DATA = "send_data";
    
    // Intent extras
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_PSM = "psm";
    public static final String EXTRA_DATA = "data";
    
    // L2CAP components
    private L2capCocServer server;
    private L2capCocClient client;
    
    // Service state
    private boolean isServiceRunning = false;
    private final List<ServiceCallback> callbacks = new CopyOnWriteArrayList<>();
    private Handler mainHandler;
    
    // Notification manager
    private NotificationManager notificationManager;
    
    /**
     * Interface for Activity callbacks
     */
    public interface ServiceCallback {
        void onConnectionStateChanged(boolean connected);
        void onDataReceived(byte[] data);
        void onStatusChanged(String status);
        void onCreditsChanged(int localCredits, int remoteCredits);
    }
    
    /**
     * Binder class for Activity communication
     */
    public class L2capServiceBinder extends Binder {
        public BluetoothL2capService getService() {
            return BluetoothL2capService.this;
        }
    }
    
    private final IBinder binder = new L2capServiceBinder();
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        // Create notification channel for Android O+
        createNotificationChannel();
        
        // Initialize L2CAP components
        server = new L2capCocServer(this, this);
        client = new L2capCocClient(this, this);
        
        isServiceRunning = true;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started with intent: " + (intent != null ? intent.getAction() : "null"));
        
        if (intent != null) {
            handleServiceAction(intent);
        }
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Return START_STICKY to restart service if killed by system
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return binder;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        
        isServiceRunning = false;
        
        // Clean up L2CAP connections
        if (server != null) {
            server.stopServer();
        }
        
        if (client != null) {
            client.disconnect();
        }
        
        // Clear callbacks
        callbacks.clear();
        
        super.onDestroy();
    }
    
    /**
     * Handle service actions from intents
     */
    private void handleServiceAction(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        
        switch (action) {
            case ACTION_START_SERVER:
                startServer();
                break;
                
            case ACTION_STOP_SERVER:
                stopServer();
                break;
                
            case ACTION_CONNECT_CLIENT:
                BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
                int psm = intent.getIntExtra(EXTRA_PSM, 128);
                connectClient(device, psm);
                break;
                
            case ACTION_DISCONNECT_CLIENT:
                disconnectClient();
                break;
                
            case ACTION_SEND_DATA:
                String data = intent.getStringExtra(EXTRA_DATA);
                sendData(data);
                break;
        }
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Bluetooth L2CAP Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Manages Bluetooth L2CAP CoC connections");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Create notification for foreground service
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, L2capCocActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        
        String contentText = getNotificationText();
        
        Notification.Builder builder = new Notification.Builder(this)
            .setContentTitle("Bluetooth L2CAP Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }
        
        return builder.build();
    }
    
    /**
     * Get notification text based on current state
     */
    private String getNotificationText() {
        List<String> states = new ArrayList<>();
        
        if (server != null && server.isServerRunning()) {
            if (server.isConnected()) {
                states.add("Server: Connected");
            } else {
                states.add("Server: Listening");
            }
        }
        
        if (client != null && client.isConnected()) {
            states.add("Client: Connected");
        } else if (client != null && client.isConnecting()) {
            states.add("Client: Connecting");
        }
        
        if (states.isEmpty()) {
            return "Ready";
        }
        
        return String.join(", ", states);
    }
    
    /**
     * Update notification with current state
     */
    private void updateNotification() {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification());
        }
    }
    
    // Public API methods for Activity interaction
    
    /**
     * Register callback for service events
     */
    public void registerCallback(ServiceCallback callback) {
        if (callback != null && !callbacks.contains(callback)) {
            callbacks.add(callback);
            Log.d(TAG, "Callback registered. Total callbacks: " + callbacks.size());
        }
    }
    
    /**
     * Unregister callback
     */
    public void unregisterCallback(ServiceCallback callback) {
        if (callback != null) {
            callbacks.remove(callback);
            Log.d(TAG, "Callback unregistered. Total callbacks: " + callbacks.size());
        }
    }
    
    /**
     * Start L2CAP CoC server
     */
    public boolean startServer() {
        if (server != null) {
            boolean result = server.startServer();
            updateNotification();
            return result;
        }
        return false;
    }
    
    /**
     * Stop L2CAP CoC server
     */
    public void stopServer() {
        if (server != null) {
            server.stopServer();
            updateNotification();
        }
    }
    
    /**
     * Connect client to device
     */
    public boolean connectClient(BluetoothDevice device, int psm) {
        if (client != null && device != null) {
            boolean result = client.connectToDevice(device, psm);
            updateNotification();
            return result;
        }
        return false;
    }
    
    /**
     * Disconnect client
     */
    public void disconnectClient() {
        if (client != null) {
            client.disconnect();
            updateNotification();
        }
    }
    
    /**
     * Send data through active connection
     */
    public boolean sendData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return false;
        }
        
        boolean sent = false;
        
        // Try server connection first
        if (server != null && server.isConnected()) {
            sent = server.sendTestMessage(data);
        }
        // Try client connection
        else if (client != null && client.isConnected()) {
            sent = client.sendTestMessage(data);
        }
        
        return sent;
    }

    /**
     * Send raw byte data through active connection
     */
    public boolean sendData(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }

        boolean sent = false;

        // Try server connection first
        if (server != null && server.isConnected()) {
            sent = server.sendData(data);
        }
        // Try client connection
        else if (client != null && client.isConnected()) {
            sent = client.sendData(data);
        }

        return sent;
    }
    
    /**
     * Get server status
     */
    public String getServerStatus() {
        if (server != null) {
            if (server.isServerRunning()) {
                return server.getServerStatus();
            } else {
                return "Server: Stopped";
            }
        }
        return "Server: Not initialized";
    }
    
    /**
     * Get client status
     */
    public String getClientStatus() {
        if (client != null) {
            return "Client: " + client.getClientStatus();
        }
        return "Client: Not initialized";
    }
    
    /**
     * Check if server is running
     */
    public boolean isServerRunning() {
        return server != null && server.isServerRunning();
    }
    
    /**
     * Check if client is connected
     */
    public boolean isClientConnected() {
        return client != null && client.isConnected();
    }
    
    /**
     * Check if any connection is active
     */
    public boolean hasActiveConnection() {
        return (server != null && server.isConnected()) || 
               (client != null && client.isConnected());
    }
    
    // L2capCocCallback implementation
    
    @Override
    public void onConnectionStateChanged(boolean connected) {
        Log.d(TAG, "Connection state changed: " + connected);
        
        // Update notification
        updateNotification();
        
        // Notify all registered callbacks
        mainHandler.post(() -> {
            for (ServiceCallback callback : callbacks) {
                try {
                    callback.onConnectionStateChanged(connected);
                } catch (Exception e) {
                    Log.e(TAG, "Error in callback", e);
                }
            }
        });
    }
    
    @Override
    public void onDataReceived(byte[] data) {
        Log.d(TAG, "Data received: " + data.length + " bytes");
        
        // Notify all registered callbacks
        mainHandler.post(() -> {
            for (ServiceCallback callback : callbacks) {
                try {
                    callback.onDataReceived(data);
                } catch (Exception e) {
                    Log.e(TAG, "Error in callback", e);
                }
            }
        });
    }
    
    @Override
    public void onStatusChanged(String status) {
        Log.d(TAG, "Status changed: " + status);
        
        // Update notification
        updateNotification();
        
        // Notify all registered callbacks
        mainHandler.post(() -> {
            for (ServiceCallback callback : callbacks) {
                try {
                    callback.onStatusChanged(status);
                } catch (Exception e) {
                    Log.e(TAG, "Error in callback", e);
                }
            }
        });
    }
    
    @Override
    public void onCreditsChanged(int localCredits, int remoteCredits) {
        Log.d(TAG, "Credits changed - Local: " + localCredits + ", Remote: " + remoteCredits);
        
        // Notify all registered callbacks
        mainHandler.post(() -> {
            for (ServiceCallback callback : callbacks) {
                try {
                    callback.onCreditsChanged(localCredits, remoteCredits);
                } catch (Exception e) {
                    Log.e(TAG, "Error in callback", e);
                }
            }
        });
    }
}
