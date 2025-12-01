/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.vendor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Executors;

/**
 * BluetoothVendorCommands class provides functionality to send vendor specific
 * Bluetooth commands to the Bluetooth stack. This class uses reflection to access
 * vendor-specific APIs that may not be part of the public Android Bluetooth API.
 * 
  */
public class BluetoothVendorCommands {

    private static final String TAG = "BluetoothVendorCommands";
    private static final boolean DBG = true;

    // Singleton instance
    private static BluetoothVendorCommands sInstance;
    private static final Object sLock = new Object();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter mBtAdapter; // Additional reference for vendor callbacks
    private Context mContext;
    private BtVendorCb mBVCb;
    private BluetoothAdapter.BluetoothHciVendorSpecificCallback mVSCallback;

    // Common vendor command opcodes (these may vary by vendor)
    public static final int VENDOR_CMD_RESET = 0xFC02;

  private final class BtVendorCb implements BluetoothAdapter.BluetoothHciVendorSpecificCallback {

    @Override
    public void onCommandStatus(int ocf, int status) {
      Log.d(TAG," onCommandStatus "+ocf);

    }

    @Override
    public void onCommandComplete(int ocf, byte[] returnParameters) {
      Log.d(TAG," onCommandComplete "+ocf);
    }

    @Override
    public void onEvent(int code, byte[] data) {
      Log.d(TAG," onEvent "+code);
    }

  }


    /**
     * Private constructor for BluetoothVendorCommands (Singleton pattern)
     * @param context Application context
     */
    private BluetoothVendorCommands(Context context) {
        mContext = context.getApplicationContext(); // Use application context to avoid memory leaks
        
        // Use BluetoothManager for modern Android versions
        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
            mBtAdapter = mBluetoothAdapter; // Initialize both references
        } else {
            // Fallback to deprecated method for older versions
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBtAdapter = mBluetoothAdapter;
        }
        
        // Initialize callback
        mBVCb = new BtVendorCb();
        mVSCallback = mBVCb;
        
        if (DBG) Log.d(TAG, "BluetoothVendorCommands singleton initialized");
    }

    /**
     * Get singleton instance of BluetoothVendorCommands
     * @param context Application context
     * @return BluetoothVendorCommands singleton instance
     */
    public static BluetoothVendorCommands getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new BluetoothVendorCommands(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Get singleton instance (if already initialized)
     * @return BluetoothVendorCommands singleton instance or null if not initialized
     */
    public static BluetoothVendorCommands getInstance() {
        return sInstance;
    }

    /**
     * Check if Bluetooth adapter is available and enabled
     * @return true if Bluetooth is available and enabled, false otherwise
     */
    public boolean isBluetoothReady() {
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null");
            return false;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            return false;
        }
        return true;
    }


  boolean registerBtVendorCb() {
    byte[] eventcodes = new byte[0];
    Log.i(TAG, "registerBtVendorCb");
    if (mBtAdapter == null) {
        Log.w(TAG, "BluetoothAdapter is NOT available.");
        return false;
    }
    String hex = "";
    for (byte i : eventcodes) {
        hex += String.format("%02X", i);
    }
    Log.d(TAG, "Event Codes to UnMask "+hex);
    mBVCb = new BtVendorCb();
    try {
      mBtAdapter.registerBluetoothHciVendorSpecificCallback(byteArrayToSet(eventcodes), Executors.newSingleThreadExecutor(), mVSCallback);
    } catch (NullPointerException | IllegalArgumentException e) {
      Log.e(TAG, "registerBtVendorCb: ", e);
    }
    return true;
  }

  boolean unRegisterBtVendorCb() {
    Log.i(TAG, "unRegisterBtVendorCb");
    if (mBtAdapter == null) {
        Log.w(TAG, "BluetoothAdapter is NOT available.");
        return false;
    }
    if (mBVCb == null) {
        Log.w(TAG, "unRegisterBtVendorCb is NOT available.");
        return false;
    }
    // Unregister the callback (if any)
    try {
      mBtAdapter.unregisterBluetoothHciVendorSpecificCallback(mBVCb);
    } catch (NullPointerException | IllegalArgumentException e) {
      Log.e(TAG, "unRegisterBtVendorCb: ", e);
    }
    return true;
  }


    /**
     * Send a vendor specific command using available Android APIs
     * Note: Direct HCI vendor commands are not available through public Android APIs.
     * This method provides vendor-specific functionality through available methods.
     * @param opcode The vendor command opcode (for logging/identification)
     * @param parameters Command parameters (for logging/identification)
     * @return true if command was sent successfully, false otherwise
     */
    public boolean sendVendorCommand(int opcode, byte[] parameters) {
        if (!isBluetoothReady()) {
            Log.e(TAG, "sendVendorCommand Bluetooth not ready");
            return false;
        }

        try {
            if (DBG) {
                Log.d(TAG, "sendVendorCommand - Opcode: 0x" + 
                      Integer.toHexString(opcode) + 
                      ", Parameters: " + Arrays.toString(parameters));
            }
            mBtAdapter.sendBluetoothHciVendorSpecificCommand(opcode, parameters);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "sendVendorCommand Failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send vendor command to reset the Bluetooth controller
     * @return true if command was sent successfully
     */
    public boolean resetController() {
        if (DBG) Log.d(TAG, "Resetting Bluetooth controller");
        return sendVendorCommand(VENDOR_CMD_RESET, new byte[0]);
    }

    /**
     * Utility method to convert byte array to Set
     * @param eventcodes byte array of event codes
     * @return Set of Integers
     */
    private Set<Integer> byteArrayToSet(byte[] eventcodes) {
        Set<Integer> eventSet = new HashSet<>();
        for (byte code : eventcodes) {
            eventSet.add((int) code & 0xFF);
        }
        return eventSet;
    }

    /**
     * Send a custom vendor command with custom opcode and parameters
     * @param opcode Custom HCI command opcode
     * @param parameters Custom command parameters
     * @return true if command was sent successfully
     */
    public boolean sendCustomVendorCommand(int opcode, byte[] parameters) {
        if (DBG) {
            Log.d(TAG, "Sending custom vendor command - Opcode: 0x" + 
                  Integer.toHexString(opcode));
        }
        return sendVendorCommand(opcode, parameters);
    }

}
