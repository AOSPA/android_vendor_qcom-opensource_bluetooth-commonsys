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
 * Bluetooth commands to the Bluetooth stack. This class uses reflection to
 * access vendor-specific APIs that may not be part of the public Android
 * Bluetooth API.
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

    // HCI VS Extended Set Event Filter command
    public static final int HCI_VS_BLUETOOTH_CMD_OPCODE = 0x3FF;
    public static final int HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE = 0x29;

    // Filter types for Extended Set Event Filter
    public static final int FILTER_TYPE_CLEAR_ALL = 0x00;
    public static final int FILTER_TYPE_INQUIRY_RESULT = 0x01;
    public static final int FILTER_TYPE_CONNECTION_SETUP = 0x02;

    // Inquiry Result Filter Condition Types
    public static final int INQUIRY_FILTER_CONDITION_ALL_DEVICES = 0x00;
    public static final int INQUIRY_FILTER_CONDITION_CLASS_OF_DEVICE = 0x01;
    public static final int INQUIRY_FILTER_CONDITION_BD_ADDR = 0x02;

    // Connection Setup Filter Condition Types
    public static final int CONNECTION_FILTER_CONDITION_ALL_DEVICES = 0x00;
    public static final int CONNECTION_FILTER_CONDITION_CLASS_OF_DEVICE = 0x01;
    public static final int CONNECTION_FILTER_CONDITION_BD_ADDR = 0x02;

    // Auto Accept Flag values
    public static final int AUTO_ACCEPT_OFF = 0x01;
    public static final int AUTO_ACCEPT_ON_ROLE_SWITCH_DISABLED = 0x02;
    public static final int AUTO_ACCEPT_ON_ROLE_SWITCH_ENABLED = 0x03;
    public static final int AUTO_ACCEPT_REJECT_CONNECTION = 0xFF;

  private final class BtVendorCb implements BluetoothAdapter.BluetoothHciVendorSpecificCallback {

    @Override
    public void onCommandStatus(int ocf, int status) {
      Log.d(TAG, " onCommandStatus " + ocf);
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
            // Open this no-op android command for test purpose
            int getVendorCapabilitiesOcf = 0x153;
            if (opcode < 0 ||
                (opcode >= 0x150 && opcode < 0x160 &&
                 opcode != getVendorCapabilitiesOcf) ||
                opcode > 0x3ff) {
              Log.w(
                  TAG,
                  "sendVendorCommand Opcode command value not in valid range ");
              return false;
            } else if (parameters == null || parameters.length > 255) {
              Log.w(TAG, "Parameters size is too big");
              return false;
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

    /**
     * Send HCI VS Extended Set Event Filter command to clear all filters
     * @return true if command was sent successfully
     */
    public boolean sendExtendedSetEventFilterClearAll() {
        if (DBG) Log.d(TAG, "Sending Extended Set Event Filter - Clear All");

        byte[] parameters = new byte[2];
        parameters[0] = (byte) HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[1] = (byte) FILTER_TYPE_CLEAR_ALL;

        return sendVendorCommand(HCI_VS_BLUETOOTH_CMD_OPCODE, parameters);
    }

    /**
     * Send HCI VS Extended Set Event Filter command for Inquiry Result filtering
     * @param conditionType Filter condition type (ALL_DEVICES, CLASS_OF_DEVICE, BD_ADDR)
     * @param classOfDevice Class of Device (3 bytes) - used when conditionType is CLASS_OF_DEVICE
     * @param classOfDeviceMask Class of Device Mask (3 bytes) - used when conditionType is CLASS_OF_DEVICE
     * @param bdAddr BD_ADDR (6 bytes) - used when conditionType is BD_ADDR
     * @return true if command was sent successfully
     */
    public boolean sendExtendedSetEventFilterInquiryResult(
        int conditionType, byte[] classOfDevice, byte[] classOfDeviceMask,
        byte[] bdAddr) {
      if (DBG)
        Log.d(
            TAG,
            "Sending Extended Set Event Filter - Inquiry Result, Condition: " +
                conditionType);

      byte[] parameters;
      int paramIndex = 0;

      switch (conditionType) {
      case INQUIRY_FILTER_CONDITION_ALL_DEVICES:
        parameters = new byte[3];
        parameters[paramIndex++] =
            (byte)HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[paramIndex++] = (byte)FILTER_TYPE_INQUIRY_RESULT;
        parameters[paramIndex++] = (byte)INQUIRY_FILTER_CONDITION_ALL_DEVICES;
        break;

      case INQUIRY_FILTER_CONDITION_CLASS_OF_DEVICE:
        parameters = new byte[9]; // 3 + 3 + 3 bytes
        parameters[paramIndex++] =
            (byte)HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[paramIndex++] = (byte)FILTER_TYPE_INQUIRY_RESULT;
        parameters[paramIndex++] =
            (byte)INQUIRY_FILTER_CONDITION_CLASS_OF_DEVICE;

        // Copy Class of Device (3 bytes)
        if (classOfDevice != null && classOfDevice.length >= 3) {
          System.arraycopy(classOfDevice, 0, parameters, paramIndex, 3);
        }
        paramIndex += 3;

        // Copy Class of Device Mask (3 bytes)
        if (classOfDeviceMask != null && classOfDeviceMask.length >= 3) {
          System.arraycopy(classOfDeviceMask, 0, parameters, paramIndex, 3);
        }
        break;

      case INQUIRY_FILTER_CONDITION_BD_ADDR:
        parameters = new byte[9]; // 3 + 6 bytes
        parameters[paramIndex++] =
            (byte)HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[paramIndex++] = (byte)FILTER_TYPE_INQUIRY_RESULT;
        parameters[paramIndex++] = (byte)INQUIRY_FILTER_CONDITION_BD_ADDR;

        // Copy BD_ADDR (6 bytes)
        if (bdAddr != null && bdAddr.length >= 6) {
          System.arraycopy(bdAddr, 0, parameters, paramIndex, 6);
        }
        break;

      default:
        Log.e(TAG, "Invalid inquiry filter condition type: " + conditionType);
        return false;
      }

      return sendVendorCommand(HCI_VS_BLUETOOTH_CMD_OPCODE, parameters);
    }

    /**
     * Send HCI VS Extended Set Event Filter command for Connection Setup filtering
     * @param conditionType Filter condition type (ALL_DEVICES, CLASS_OF_DEVICE, BD_ADDR)
     * @param autoAcceptFlag Auto accept flag
     * @param classOfDevice Class of Device (3 bytes) - used when conditionType is CLASS_OF_DEVICE
     * @param classOfDeviceMask Class of Device Mask (3 bytes) - used when conditionType is CLASS_OF_DEVICE
     * @param bdAddr BD_ADDR (6 bytes) - used when conditionType is BD_ADDR
     * @return true if command was sent successfully
     */
    public boolean sendExtendedSetEventFilterConnectionSetup(int conditionType, int autoAcceptFlag,
            byte[] classOfDevice, byte[] classOfDeviceMask, byte[] bdAddr) {
      if (DBG)
        Log.d(
            TAG,
            "Sending Extended Set Event Filter - Connection Setup, Condition: " +
                conditionType + ", Auto Accept: " + autoAcceptFlag);

      byte[] parameters;
      int paramIndex = 0;

      switch (conditionType) {
      case CONNECTION_FILTER_CONDITION_ALL_DEVICES:
        parameters = new byte[4];
        parameters[paramIndex++] =
            (byte)HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[paramIndex++] = (byte)FILTER_TYPE_CONNECTION_SETUP;
        parameters[paramIndex++] =
            (byte)CONNECTION_FILTER_CONDITION_ALL_DEVICES;
        parameters[paramIndex++] = (byte)autoAcceptFlag;
        break;

      case CONNECTION_FILTER_CONDITION_CLASS_OF_DEVICE:
        parameters = new byte[10]; // 4 + 3 + 3 bytes
        parameters[paramIndex++] =
            (byte)HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[paramIndex++] = (byte)FILTER_TYPE_CONNECTION_SETUP;
        parameters[paramIndex++] =
            (byte)CONNECTION_FILTER_CONDITION_CLASS_OF_DEVICE;
        parameters[paramIndex++] = (byte)autoAcceptFlag;

        // Copy Class of Device (3 bytes)
        if (classOfDevice != null && classOfDevice.length >= 3) {
          System.arraycopy(classOfDevice, 0, parameters, paramIndex, 3);
        }
        paramIndex += 3;

        // Copy Class of Device Mask (3 bytes)
        if (classOfDeviceMask != null && classOfDeviceMask.length >= 3) {
          System.arraycopy(classOfDeviceMask, 0, parameters, paramIndex, 3);
        }
        break;

      case CONNECTION_FILTER_CONDITION_BD_ADDR:
        // 1 ( SUB_OPCODE) + 1  (CONNECTION_SETUP) + 1  (CONDITION_BD_ADDR)
		// + 6 (BD_ADDR) + 1 (Auto Accept)
        parameters = new byte[10];
        parameters[paramIndex++] =
            (byte)HCI_VS_EXTENDED_SET_EVENT_FILTER_SUB_OPCODE;
        parameters[paramIndex++] = (byte)FILTER_TYPE_CONNECTION_SETUP;
        parameters[paramIndex++] = (byte)CONNECTION_FILTER_CONDITION_BD_ADDR;

        /* Copy BD_ADDR (6 bytes) - little endian (LSB first)
           11:22:33:44:55:66 -> 66:55:44:33:22:11 */
        if (bdAddr != null && bdAddr.length >= 6) {
          for (int i = 0; i < 6; i++) {
            parameters[paramIndex + i] = bdAddr[5 - i];
          }
          paramIndex += 6;
        }
        parameters[paramIndex] = (byte)autoAcceptFlag;
        break;

      default:
        Log.e(TAG,
              "Invalid connection filter condition type: " + conditionType);
        return false;
        }

        return sendVendorCommand(HCI_VS_BLUETOOTH_CMD_OPCODE, parameters);
    }

    /**
     * Utility method to parse BD_ADDR string to byte array
     * @param bdAddrStr BD_ADDR string in format "XX:XX:XX:XX:XX:XX"
     * @return byte array of BD_ADDR or null if invalid format
     */
    public static byte[] parseBdAddr(String bdAddrStr) {
        if (bdAddrStr == null || bdAddrStr.length() != 17) {
            return null;
        }

        try {
            String[] parts = bdAddrStr.split(":");
            if (parts.length != 6) {
                return null;
            }

            byte[] bdAddr = new byte[6];
            for (int i = 0; i < 6; i++) {
                bdAddr[i] = (byte) Integer.parseInt(parts[i], 16);
            }
            return bdAddr;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid BD_ADDR format: " + bdAddrStr, e);
            return null;
        }
    }

    /**
     * Utility method to parse Class of Device string to byte array
     * @param codStr Class of Device string in format "XXXXXX" (6 hex digits)
     * @return byte array of Class of Device or null if invalid format
     */
    public static byte[] parseClassOfDevice(String codStr) {
        if (codStr == null || codStr.length() != 6) {
            return null;
        }

        try {
            byte[] cod = new byte[3];
            for (int i = 0; i < 3; i++) {
                cod[i] = (byte) Integer.parseInt(codStr.substring(i * 2, i * 2 + 2), 16);
            }
            return cod;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid Class of Device format: " + codStr, e);
            return null;
        }
    }

}
