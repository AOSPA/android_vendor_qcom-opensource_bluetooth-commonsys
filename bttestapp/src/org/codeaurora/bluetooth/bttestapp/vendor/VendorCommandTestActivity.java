/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.  
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.vendor;

import org.codeaurora.bluetooth.bttestapp.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Set;

/**
 * VendorCommandTestActivity provides a UI to test Bluetooth vendor specific commands
 */
public class VendorCommandTestActivity extends Activity {

    private static final String TAG = "VendorCommandTest";
    private static final boolean DBG = true;

    private BluetoothVendorCommands mVendorCommands;
    private TextView mStatusText;
    private EditText mOpcodeEdit;
    private EditText mParametersEdit;
    private Button mSendCustomButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.d(TAG, "onCreate");
        
        setContentView(R.layout.activity_vendor_test);
        
        // Initialize vendor commands using singleton
        mVendorCommands = BluetoothVendorCommands.getInstance(this);
        
        initializeViews();
        setupClickListeners();
        
        // Register vendor callback
        boolean registered = mVendorCommands.registerBtVendorCb();
        updateStatus("Vendor Command Test Activity initialized. Callback registered: " + registered);
    }

    private void initializeViews() {
        mStatusText = findViewById(R.id.status_text);
        mOpcodeEdit = findViewById(R.id.opcode_edit);
        mParametersEdit = findViewById(R.id.parameters_edit);
        mSendCustomButton = findViewById(R.id.send_custom_button);
    }

    private void setupClickListeners() {
        mSendCustomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCustomCommand();
            }
        });

    }

    private void sendCustomCommand() {
        try {
            String opcodeStr = mOpcodeEdit.getText().toString().trim();
            String parametersStr = mParametersEdit.getText().toString().trim();
            
            if (opcodeStr.isEmpty()) {
                showToast("Please enter opcode");
                return;
            }
            
            int opcode = Integer.parseInt(opcodeStr, 16);
            byte[] parameters = parseHexParameters(parametersStr);
            
            boolean result = mVendorCommands.sendCustomVendorCommand(opcode, parameters);
            updateStatus("Custom command sent - Opcode: 0x" + opcodeStr + 
                        ", Result: " + result);
            
        } catch (NumberFormatException e) {
            showToast("Invalid opcode format. Use hex format (e.g., FC01)");
            Log.e(TAG, "Invalid opcode format", e);
        } catch (Exception e) {
            showToast("Error sending custom command: " + e.getMessage());
            Log.e(TAG, "Error sending custom command", e);
        }
    }

    
    private void resetController() {
        boolean result = mVendorCommands.resetController();
        updateStatus("Reset controller command sent. Result: " + result);
    }

    private byte[] parseHexParameters(String hexStr) {
        if (hexStr.isEmpty()) {
            return new byte[0];
        }
        
        // Remove spaces and convert to uppercase
        hexStr = hexStr.replaceAll("\\s+", "").toUpperCase();
        
        // Ensure even length
        if (hexStr.length() % 2 != 0) {
            hexStr = "0" + hexStr;
        }
        
        byte[] result = new byte[hexStr.length() / 2];
        for (int i = 0; i < hexStr.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hexStr.substring(i, i + 2), 16);
        }
        
        return result;
    }

    private void updateStatus(String message) {
        if (DBG) Log.d(TAG, message);
        if (mStatusText != null) {
            mStatusText.setText(message);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DBG) Log.d(TAG, "onDestroy");
        
        // Unregister vendor callback
        if (mVendorCommands != null) {
            boolean unregistered = mVendorCommands.unRegisterBtVendorCb();
            if (DBG) Log.d(TAG, "Vendor callback unregistered: " + unregistered);
        }
    }
}
