/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * L2CAP CoC Client implementation that extends the base functionality.
 * Handles client-side connection establishment and data exchange.
 */
public class L2capCocClient extends L2capCocBase {
    private static final String TAG = "L2capCocClient";
    
    private BluetoothDevice targetDevice;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    
    /**
     * Constructor for L2CAP CoC Client
     * @param callback Callback interface for client events
     * @param context Context for configuration access
     */
    public L2capCocClient(L2capCocCallback callback, Context context) {
        super(callback, context);
    }
    
    /**
     * Connect to a remote device using L2CAP CoC
     * @param device The target Bluetooth device
     * @return true if connection attempt started successfully
     */
    public boolean connectToDevice(BluetoothDevice device) {
        return connectToDevice(device, config.getPsm());
    }
    
    /**
     * Connect to a remote device using L2CAP CoC with custom PSM
     * @param device The target Bluetooth device
     * @param psm The PSM (Protocol Service Multiplexer) to connect to
     * @return true if connection attempt started successfully
     */
    public boolean connectToDevice(BluetoothDevice device, int psm) {
        if (device == null) {
            Log.e(TAG, "Target device is null");
            return false;
        }
        
        if (isConnecting.get() || isConnected()) {
            Log.w(TAG, "Already connecting or connected");
            return false;
        }
        
        this.targetDevice = device;
        isConnecting.set(true);
        
        // Start connection in background thread
        ensureExecutorService();
        executorService.execute(() -> performConnection(psm));
        return true;
    }
    
    /**
     * Performs the actual L2CAP CoC connection
     */
    private void performConnection() {
        performConnection(config.getPsm());
    }
    
    /**
     * Performs the actual L2CAP CoC connection with custom PSM
     * @param psm The PSM to connect to
     */
    private void performConnection(int psm) {
        try {
            Log.d(TAG, "Attempting to connect to device: " + targetDevice.getAddress() + " on PSM: " + psm);
            callback.onStatusChanged("Connecting to " + targetDevice.getAddress() + " (PSM: " + psm + ")...");
            
            // Create insecure L2CAP CoC channel (no authentication/encryption)
            BluetoothSocket socket = targetDevice.createInsecureL2capChannel(psm);
            if (socket == null) {
                throw new IOException("Failed to create insecure L2CAP channel for PSM: " + psm);
            }
            
            // Connect to the remote device
            socket.connect();
            
            // Connection successful
            setSocket(socket);
            setupStreams();
            isConnecting.set(false);
            
            Log.i(TAG, "Successfully connected to " + targetDevice.getAddress() + " on PSM: " + psm);
            callback.onStatusChanged("Connected to " + targetDevice.getAddress() + " (PSM: " + psm + ")");
            callback.onConnectionStateChanged(true);
            
            // Reset credits for new connection
            resetCredits();
            
            // Start data reading loop
            startDataReading();
            
        } catch (IOException e) {
            Log.e(TAG, "Connection failed: " + e.getMessage(), e);
            isConnecting.set(false);
            callback.onStatusChanged("Connection failed: " + e.getMessage());
            callback.onConnectionStateChanged(false);
            cleanup();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during connection: " + e.getMessage(), e);
            isConnecting.set(false);
            callback.onStatusChanged("Permission denied: " + e.getMessage());
            callback.onConnectionStateChanged(false);
            cleanup();
        }
    }
    
    /**
     * Disconnect from the remote device
     */
    public void disconnect() {
        Log.d(TAG, "Disconnecting client...");
        
        isConnecting.set(false);
        
        if (isConnected()) {
            callback.onStatusChanged("Disconnecting...");
        }
        
        // Call parent disconnect method directly to avoid recursion
        super.disconnect();
        
        // Clean up client-specific resources
        targetDevice = null;
        
        callback.onStatusChanged("Disconnected");
        callback.onConnectionStateChanged(false);
        
        Log.i(TAG, "Client disconnected");
    }
    
    /**
     * Get the target device
     * @return The target Bluetooth device
     */
    public BluetoothDevice getTargetDevice() {
        return targetDevice;
    }
    
    /**
     * Check if client is currently attempting to connect
     * @return true if connecting
     */
    public boolean isConnecting() {
        return isConnecting.get();
    }
    
    /**
     * Get client status information
     * @return Status string
     */
    public String getClientStatus() {
        if (isConnecting()) {
            return "Connecting to " + (targetDevice != null ? targetDevice.getAddress() : "unknown");
        } else if (isConnected()) {
            return "Connected to " + (targetDevice != null ? targetDevice.getAddress() : "unknown");
        } else {
            return "Disconnected";
        }
    }
    
    /**
     * Send a test message to the connected device
     * @param message The message to send
     * @return true if message was sent successfully
     */
    public boolean sendTestMessage(String message) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send message - not connected");
            callback.onStatusChanged("Cannot send - not connected");
            return false;
        }
        
        String testData = "CLIENT_MSG: " + message;
        return sendData(testData.getBytes());
    }
    
    @Override
    protected void cleanup() {
        isConnecting.set(false);
        targetDevice = null;
        
        // Don't call super.cleanup() as it calls disconnect() which would cause recursion
        // Instead, directly clean up resources without calling disconnect again
        isConnected.set(false);
        isRunning.set(false);
        
        closeStreams();
        closeSocket();
        
        connectedDevice = null;
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
