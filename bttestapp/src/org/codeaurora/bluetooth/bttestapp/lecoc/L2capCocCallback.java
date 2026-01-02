/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

/**
 * Callback interface for L2CAP CoC (Connection-oriented Channel) operations.
 * This interface provides callbacks for connection state changes, data reception,
 * status updates, and credits handling for both server and client implementations.
 */
public interface L2capCocCallback {
    
    /**
     * Called when the connection state changes.
     * 
     * @param connected true if connected, false if disconnected
     */
    void onConnectionStateChanged(boolean connected);
    
    /**
     * Called when data is received from the remote device.
     * 
     * @param data The received data as byte array
     */
    void onDataReceived(byte[] data);
    
    /**
     * Called when status changes occur during L2CAP CoC operations.
     * 
     * @param status A descriptive status message
     */
    void onStatusChanged(String status);
    
    /**
     * Called when credits are updated for flow control.
     * 
     * @param localCredits The current number of local credits
     * @param remoteCredits The current number of remote credits
     */
    void onCreditsChanged(int localCredits, int remoteCredits);
}
