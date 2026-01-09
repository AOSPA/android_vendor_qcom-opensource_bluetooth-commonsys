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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for L2CAP CoC operations providing common functionality
 * for both server and client implementations.
 */
public abstract class L2capCocBase {
    private static final String TAG = "L2capCocBase";
    private static final boolean DBG = true;
    
    // Configuration
    protected final L2capCocConfig config;
    
    // Common components
    protected final L2capCocCallback callback;
    
    // Socket and streams
    protected BluetoothSocket socket;
    protected InputStream inputStream;
    protected OutputStream outputStream;
    protected BluetoothDevice connectedDevice;
    
    // Threading
    protected ExecutorService executorService;
    protected final AtomicBoolean isConnected = new AtomicBoolean(false);
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // Flow control
    protected final AtomicInteger availableCredits;
    protected final Object creditsLock = new Object();
    
    /**
     * Constructor for L2CAP CoC base class
     * 
     * @param callback Callback interface for events
     * @param context Context for configuration access
     */
    protected L2capCocBase(L2capCocCallback callback, Context context) {
        this.callback = callback;
        this.config = L2capCocConfig.getInstance(context);
        this.availableCredits = new AtomicInteger(config.getMaxCredits());
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Check if connected to a remote device
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected.get() && socket != null && socket.isConnected();
    }
    
    /**
     * Get the connected device
     * 
     * @return The connected BluetoothDevice, or null if not connected
     */
    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }
    
    /**
     * Get available credits for flow control
     * 
     * @return Number of available credits
     */
    public int getAvailableCredits() {
        return availableCredits.get();
    }
    
    /**
     * Send data to the connected device
     * 
     * @param data The data to send
     * @return true if data was sent successfully, false otherwise
     */
    public boolean sendData(byte[] data) {
        if (!isConnected() || outputStream == null) {
            if (DBG) Log.w(TAG, "Cannot send data - not connected");
            return false;
        }
        
        if (data == null || data.length == 0) {
            if (DBG) Log.w(TAG, "Cannot send empty data");
            return false;
        }
        
        if (data.length > config.getBufferSize() - config.getHeaderSize()) {
            if (DBG) Log.w(TAG, "Data too large: " + data.length + " bytes");
            return false;
        }
        
        // Check credits before sending
        synchronized (creditsLock) {
            if (availableCredits.get() <= 0) {
                if (DBG) Log.w(TAG, "No credits available for sending data");
                return false;
            }
            availableCredits.decrementAndGet();
        }
        
        try {
            // Send data length first (4 bytes, big-endian)
            byte[] lengthBytes = intToBytes(data.length);
            outputStream.write(lengthBytes);
            
            // Send actual data
            outputStream.write(data);
            outputStream.flush();
            
            if (DBG) Log.d(TAG, "Sent " + data.length + " bytes, credits remaining: " + availableCredits.get());
            
            // Notify callback about credit change
            if (callback != null) {
                callback.onCreditsChanged(availableCredits.get(), config.getMaxCredits());
            }
            
            return true;
            
        } catch (IOException e) {
            if (DBG) Log.e(TAG, "Failed to send data", e);
            
            // Restore credit on failure
            synchronized (creditsLock) {
                availableCredits.incrementAndGet();
            }
            
            // Disconnect on I/O error
            disconnect();
            return false;
        }
    }
    
    /**
     * Set the socket and update connection state
     * 
     * @param socket The BluetoothSocket to set
     */
    protected void setSocket(BluetoothSocket socket) {
        this.socket = socket;
        if (socket != null) {
            isConnected.set(true);
            isRunning.set(true);
            try {
                connectedDevice = socket.getRemoteDevice();
            } catch (Exception e) {
                if (DBG) Log.w(TAG, "Could not get remote device", e);
            }
        }
    }
    
    /**
     * Setup input and output streams for the socket
     * 
     * @throws IOException if stream setup fails
     */
    protected void setupStreams() throws IOException {
        if (socket != null) {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            if (DBG) Log.d(TAG, "I/O streams setup successfully");
        }
    }
    
    /**
     * Start data reading loop in background thread
     */
    protected void startDataReading() {
        ensureExecutorService();
        executorService.execute(this::readDataLoop);
    }
    
    /**
     * Read data from the connected device (runs in background thread)
     */
    private void readDataLoop() {
        if (DBG) Log.d(TAG, "Starting data read loop");
        
        while (isRunning.get() && isConnected.get()) {
            try {
                // Read data length first (header bytes)
                byte[] lengthBytes = new byte[config.getHeaderSize()];
                int bytesRead = 0;
                while (bytesRead < config.getHeaderSize()) {
                    int read = inputStream.read(lengthBytes, bytesRead, config.getHeaderSize() - bytesRead);
                    if (read == -1) {
                        throw new IOException("End of stream reached");
                    }
                    bytesRead += read;
                }
                
                int dataLength = bytesToInt(lengthBytes);
                
                if (dataLength <= 0 || dataLength > config.getBufferSize() - config.getHeaderSize()) {
                    if (DBG) Log.w(TAG, "Invalid data length: " + dataLength);
                    continue;
                }
                
                // Read actual data
                byte[] data = new byte[dataLength];
                bytesRead = 0;
                while (bytesRead < dataLength) {
                    int read = inputStream.read(data, bytesRead, dataLength - bytesRead);
                    if (read == -1) {
                        throw new IOException("End of stream reached");
                    }
                    bytesRead += read;
                }
                
                if (DBG) Log.d(TAG, "Received " + dataLength + " bytes");
                
                // Notify callback
                if (callback != null) {
                    callback.onDataReceived(data);
                }
                
                // Grant credit back (simple flow control)
                synchronized (creditsLock) {
                    if (availableCredits.get() < config.getMaxCredits()) {
                        availableCredits.incrementAndGet();
                        if (callback != null) {
                            callback.onCreditsChanged(availableCredits.get(), config.getMaxCredits());
                        }
                    }
                }
                
            } catch (IOException e) {
                if (isRunning.get() && isConnected.get()) {
                    if (DBG) Log.e(TAG, "Error reading data", e);
                    if (callback != null) {
                        callback.onStatusChanged("Failed to read data: " + e.getMessage());
                    }
                }
                break;
            }
        }
        
        // Disconnect when read loop exits
        disconnect();
    }
    
    /**
     * Disconnect from the remote device
     */
    public void disconnect() {
        if (DBG) Log.d(TAG, "Disconnecting");
        
        isConnected.set(false);
        isRunning.set(false);
        
        if (callback != null) {
            callback.onConnectionStateChanged(false);
        }
        
        closeStreams();
        closeSocket();
        
        connectedDevice = null;
        
        if (DBG) Log.d(TAG, "Disconnected");
    }
    
    /**
     * Close I/O streams
     */
    protected void closeStreams() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                if (DBG) Log.w(TAG, "Error closing input stream", e);
            }
            inputStream = null;
        }
        
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                if (DBG) Log.w(TAG, "Error closing output stream", e);
            }
            outputStream = null;
        }
    }
    
    /**
     * Close the socket
     */
    protected void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                if (DBG) Log.w(TAG, "Error closing socket", e);
            }
            socket = null;
        }
    }
    
    /**
     * Cleanup resources
     */
    protected void cleanup() {
        disconnect();
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            // Don't wait for termination, just mark it as shut down
            // The ensureExecutorService() method will create a new one if needed
        }
    }
    
    /**
     * Convert integer to byte array (big-endian)
     */
    protected byte[] intToBytes(int value) {
        return new byte[] {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }
    
    /**
     * Convert byte array to integer (big-endian)
     */
    protected int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }
    
    /**
     * Reset credits to maximum value
     */
    protected void resetCredits() {
        synchronized (creditsLock) {
            availableCredits.set(config.getMaxCredits());
            if (callback != null) {
                callback.onCreditsChanged(availableCredits.get(), config.getMaxCredits());
            }
        }
    }
    
    /**
     * Ensure executor service is available and not shut down
     */
    protected synchronized void ensureExecutorService() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            if (DBG) Log.d(TAG, "Creating new executor service");
            try {
                executorService = Executors.newCachedThreadPool();
            } catch (Exception e) {
                if (DBG) Log.e(TAG, "Failed to create executor service", e);
                // Create a simple single-thread executor as fallback
                executorService = Executors.newSingleThreadExecutor();
            }
        }
    }
}
