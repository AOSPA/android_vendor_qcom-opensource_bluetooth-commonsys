/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * L2CAP CoC (Connection-oriented Channel) Server implementation.
 * This class handles server-side L2CAP CoC operations including:
 * - Creating L2CAP server socket
 * - Accepting incoming connections
 * - Managing data read/write operations
 * - Handling credits and flow control
 */
public class L2capCocServer extends L2capCocBase {
    private static final String TAG = "L2capCocServer";
    private static final boolean DBG = true;
    
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private int actualPsm = -1;
    
    // BLE Advertising
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private AdvertiseCallback advertiseCallback;
    private static final UUID L2CAP_COC_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    
    /**
     * Constructor for L2CAP CoC Server
     * 
     * @param callback Callback interface for server events
     */
    public L2capCocServer(L2capCocCallback callback) {
        super(callback);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }
    
    /**
     * Start the L2CAP CoC server
     * 
     * @return true if server started successfully, false otherwise
     */
    public boolean startServer() {
        try {
            start();
            return true;
        } catch (IOException e) {
            if (DBG) Log.e(TAG, "Failed to start server", e);
            if (callback != null) {
                callback.onStatusChanged("Failed to start server: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Stop the L2CAP CoC server
     */
    public void stopServer() {
        stop();
        if (callback != null) {
            callback.onStatusChanged("Server stopped");
        }
    }
    
    /**
     * Check if server is running
     * 
     * @return true if server is running, false otherwise
     */
    public boolean isServerRunning() {
        return isRunning();
    }
    
    /**
     * Send a test message to the connected client
     * @param message The message to send
     * @return true if message was sent successfully
     */
    public boolean sendTestMessage(String message) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send message - not connected");
            if (callback != null) {
                callback.onStatusChanged("Cannot send - not connected");
            }
            return false;
        }
        
        String testData = "SERVER_MSG: " + message;
        return sendData(testData.getBytes());
    }
    
    /**
     * Start the L2CAP CoC server (internal method)
     * 
     * @throws IOException if server socket creation fails
     */
    private void start() throws IOException {
        if (isRunning.get()) {
            if (DBG) Log.w(TAG, "Server already running");
            return;
        }
        
        if (DBG) Log.d(TAG, "Starting L2CAP CoC server on PSM: " + PSM);
        
        try {
            // Create insecure L2CAP server socket (no authentication/encryption)
            // Note: Android API auto-assigns PSM, cannot specify custom PSM
            serverSocket = bluetoothAdapter.listenUsingInsecureL2capChannel();
            
            if (serverSocket == null) {
                throw new IOException("Failed to create L2CAP server socket");
            }
            
            // Get the actual PSM assigned by the system
            try {
                actualPsm = serverSocket.getPsm();
                if (DBG) Log.d(TAG, "L2CAP server listening on actual PSM: " + actualPsm);
                if (callback != null) {
                    callback.onStatusChanged("Server started on PSM: " + actualPsm);
                }
            } catch (Exception e) {
                if (DBG) Log.w(TAG, "Could not get PSM from server socket", e);
                if (callback != null) {
                    callback.onStatusChanged("Server started (PSM unknown)");
                }
            }
            
            isRunning.set(true);
            
            // Start BLE advertising with PSM information
            startAdvertising();
            
            // Start accepting connections in background thread
            ensureExecutorService();
            executorService.execute(this::acceptConnections);
            
            if (DBG) Log.d(TAG, "L2CAP CoC server started successfully");
            
        } catch (IOException e) {
            if (DBG) Log.e(TAG, "Failed to start server", e);
            stop();
            throw e;
        } catch (SecurityException e) {
            if (DBG) Log.e(TAG, "Security exception starting server", e);
            stop();
            throw new IOException("Security exception: " + e.getMessage());
        }
    }
    
    /**
     * Stop the L2CAP CoC server
     */
    public void stop() {
        if (DBG) Log.d(TAG, "Stopping L2CAP CoC server");
        
        isRunning.set(false);
        actualPsm = -1; // Reset PSM when server stops
        
        // Stop BLE advertising
        stopAdvertising();
        
        // Close server socket
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                if (DBG) Log.w(TAG, "Error closing server socket", e);
            }
            serverSocket = null;
        }
        
        // Cleanup base resources
        cleanup();
        
        if (DBG) Log.d(TAG, "L2CAP CoC server stopped");
    }
    
    /**
     * Check if server is running
     * 
     * @return true if server is running, false otherwise
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Accept incoming connections (runs in background thread)
     */
    private void acceptConnections() {
        if (DBG) Log.d(TAG, "Waiting for incoming connections...");
        
        while (isRunning.get()) {
            try {
                // Accept incoming connection
                socket = serverSocket.accept();
                
                if (socket != null) {
                    connectedDevice = socket.getRemoteDevice();
                    
                    if (DBG) Log.d(TAG, "Client connected: " + connectedDevice.getAddress());
                    
                    // Setup I/O streams
                    setupStreams();
                    
                    // Update connection state
                    isConnected.set(true);
                    if (callback != null) {
                        callback.onConnectionStateChanged(true);
                    }
                    
                    // Reset credits for new connection
                    resetCredits();
                    
                    // Start data reading thread
                    startDataReading();
                    
                    // Server handles one connection at a time
                    // Break the loop to stop accepting new connections
                    break;
                }
                
            } catch (IOException e) {
                if (isRunning.get()) {
                    if (DBG) Log.e(TAG, "Error accepting connection", e);
                    if (callback != null) {
                        callback.onStatusChanged("Failed to accept connection: " + e.getMessage());
                    }
                }
                break;
            } catch (SecurityException e) {
                if (isRunning.get()) {
                    if (DBG) Log.e(TAG, "Security exception accepting connection", e);
                    if (callback != null) {
                        callback.onStatusChanged("Security exception: " + e.getMessage());
                    }
                }
                break;
            }
        }
        
        if (DBG) Log.d(TAG, "Connection acceptance loop ended");
    }
    
    /**
     * Restart the server to accept new connections after current client disconnects
     */
    public void restartForNewConnections() {
        if (!isRunning.get()) {
            if (DBG) Log.w(TAG, "Cannot restart - server not running");
            return;
        }
        
        if (isConnected.get()) {
            if (DBG) Log.w(TAG, "Cannot restart - client still connected");
            return;
        }
        
        // Start accepting connections again
        ensureExecutorService();
        executorService.execute(this::acceptConnections);
        
        if (DBG) Log.d(TAG, "Server restarted for new connections");
    }
    
    @Override
    public void disconnect() {
        super.disconnect();
        
        // After disconnection, restart accepting connections if server is still running
        // Use a simpler approach without delayed execution to avoid executor issues
        if (isRunning.get()) {
            // Schedule restart without delay to avoid executor lifecycle issues
            try {
                ensureExecutorService();
                executorService.execute(() -> {
                    if (isRunning.get() && !isConnected.get()) {
                        restartForNewConnections();
                    }
                });
            } catch (Exception e) {
                if (DBG) Log.w(TAG, "Failed to schedule restart: " + e.getMessage());
                // If executor submission fails, just log it - don't crash
            }
        }
    }
    
    /**
     * Get the actual PSM assigned by the system
     * 
     * @return The PSM value, or -1 if not available
     */
    public int getActualPsm() {
        return actualPsm;
    }
    
    /**
     * Get server status information
     * 
     * @return String containing server status
     */
    public String getServerStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Server Status: ");
        
        if (isRunning.get()) {
            status.append("Running");
            if (actualPsm != -1) {
                status.append(" (PSM: ").append(actualPsm).append(")");
            }
            if (isConnected.get()) {
                status.append(", Connected to: ").append(connectedDevice.getAddress());
                status.append(", Credits: ").append(getAvailableCredits());
            } else {
                status.append(", Waiting for connections");
            }
        } else {
            status.append("Stopped");
        }
        
        return status.toString();
    }
    
    /**
     * Start BLE advertising with PSM information
     */
    private void startAdvertising() {
        if (actualPsm == -1) {
            if (DBG) Log.w(TAG, "Cannot advertise - PSM not available");
            return;
        }
        
        try {
            bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            if (bluetoothLeAdvertiser == null) {
                if (DBG) Log.w(TAG, "BLE advertising not supported");
                return;
            }
            
            // Create advertise settings
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .setTimeout(0) // Advertise indefinitely
                    .build();
            
            // Create PSM data (2 bytes for PSM value)
            ByteBuffer psmBuffer = ByteBuffer.allocate(2);
            psmBuffer.putShort((short) actualPsm);
            byte[] psmData = psmBuffer.array();
            
            // Create advertise data with service UUID and PSM
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(true)
                    .addServiceUuid(new ParcelUuid(L2CAP_COC_SERVICE_UUID))
                    .addServiceData(new ParcelUuid(L2CAP_COC_SERVICE_UUID), psmData)
                    .build();
            
            // Create advertise callback
            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    if (DBG) Log.d(TAG, "BLE advertising started successfully with PSM: " + actualPsm);
                    if (callback != null) {
                        callback.onStatusChanged("Advertising L2CAP service on PSM: " + actualPsm);
                    }
                }
                
                @Override
                public void onStartFailure(int errorCode) {
                    if (DBG) Log.e(TAG, "BLE advertising failed with error: " + errorCode);
                    if (callback != null) {
                        callback.onStatusChanged("Advertising failed: " + errorCode);
                    }
                }
            };
            
            // Start advertising
            bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
            
        } catch (SecurityException e) {
            if (DBG) Log.e(TAG, "Security exception starting advertising", e);
            if (callback != null) {
                callback.onStatusChanged("Advertising permission denied");
            }
        } catch (Exception e) {
            if (DBG) Log.e(TAG, "Error starting advertising", e);
            if (callback != null) {
                callback.onStatusChanged("Advertising error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stop BLE advertising
     */
    private void stopAdvertising() {
        try {
            if (bluetoothLeAdvertiser != null && advertiseCallback != null) {
                bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
                if (DBG) Log.d(TAG, "BLE advertising stopped");
            }
        } catch (SecurityException e) {
            if (DBG) Log.e(TAG, "Security exception stopping advertising", e);
        } catch (Exception e) {
            if (DBG) Log.e(TAG, "Error stopping advertising", e);
        } finally {
            bluetoothLeAdvertiser = null;
            advertiseCallback = null;
        }
    }
}
