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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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
    
    // Extended Set Event Filter UI elements
    private Button mClearAllFiltersButton;
    private Spinner mInquiryConditionSpinner;
    private EditText mInquiryCodEdit;
    private EditText mInquiryCodMaskEdit;
    private EditText mInquiryBdAddrEdit;
    private Button mSendInquiryFilterButton;
    private Spinner mConnectionConditionSpinner;
    private Spinner mAutoAcceptSpinner;
    private EditText mConnectionCodEdit;
    private EditText mConnectionCodMaskEdit;
    private EditText mConnectionBdAddrEdit;
    private Button mSendConnectionFilterButton;

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
        
        // Extended Set Event Filter UI elements
        mClearAllFiltersButton = findViewById(R.id.clear_all_filters_button);
        mInquiryConditionSpinner = findViewById(R.id.inquiry_condition_spinner);
        mInquiryCodEdit = findViewById(R.id.inquiry_cod_edit);
        mInquiryCodMaskEdit = findViewById(R.id.inquiry_cod_mask_edit);
        mInquiryBdAddrEdit = findViewById(R.id.inquiry_bdaddr_edit);
        mSendInquiryFilterButton = findViewById(R.id.send_inquiry_filter_button);
        mConnectionConditionSpinner = findViewById(R.id.connection_condition_spinner);
        mAutoAcceptSpinner = findViewById(R.id.auto_accept_spinner);
        mConnectionCodEdit = findViewById(R.id.connection_cod_edit);
        mConnectionCodMaskEdit = findViewById(R.id.connection_cod_mask_edit);
        mConnectionBdAddrEdit = findViewById(R.id.connection_bdaddr_edit);
        mSendConnectionFilterButton = findViewById(R.id.send_connection_filter_button);
        
        setupSpinners();
    }

    private void setupSpinners() {
        // Setup Inquiry Condition Spinner
        String[] inquiryConditions = {
            "All Devices",
            "Class of Device",
            "BD_ADDR"
        };
        ArrayAdapter<String> inquiryAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, inquiryConditions);
        inquiryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mInquiryConditionSpinner.setAdapter(inquiryAdapter);
        
        // Setup Connection Condition Spinner
        String[] connectionConditions = {
            "All Devices",
            "Class of Device", 
            "BD_ADDR"
        };
        ArrayAdapter<String> connectionAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, connectionConditions);
        connectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mConnectionConditionSpinner.setAdapter(connectionAdapter);
        
        // Setup Auto Accept Spinner
        String[] autoAcceptOptions = {
            "Auto Accept Off",
            "Auto Accept On (Role Switch Disabled)",
            "Auto Accept On (Role Switch Enabled)",
            "Reject Connection" 
        };
        ArrayAdapter<String> autoAcceptAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, autoAcceptOptions);
        autoAcceptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAutoAcceptSpinner.setAdapter(autoAcceptAdapter);
        
        // Setup spinner listeners to show/hide relevant fields
        mInquiryConditionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateInquiryFieldsVisibility(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        mConnectionConditionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateConnectionFieldsVisibility(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupClickListeners() {
        mSendCustomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCustomCommand();
            }
        });
        
        mClearAllFiltersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendClearAllFilters();
            }
        });
        
        mSendInquiryFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendInquiryResultFilter();
            }
        });
        
        mSendConnectionFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendConnectionSetupFilter();
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

    private void updateInquiryFieldsVisibility(int conditionType) {
        switch (conditionType) {
            case 0: // All Devices
                mInquiryCodEdit.setVisibility(View.GONE);
                mInquiryCodMaskEdit.setVisibility(View.GONE);
                mInquiryBdAddrEdit.setVisibility(View.GONE);
                break;
            case 1: // Class of Device
                mInquiryCodEdit.setVisibility(View.VISIBLE);
                mInquiryCodMaskEdit.setVisibility(View.VISIBLE);
                mInquiryBdAddrEdit.setVisibility(View.GONE);
                break;
            case 2: // BD_ADDR
                mInquiryCodEdit.setVisibility(View.GONE);
                mInquiryCodMaskEdit.setVisibility(View.GONE);
                mInquiryBdAddrEdit.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateConnectionFieldsVisibility(int conditionType) {
        switch (conditionType) {
            case 0: // All Devices
                mConnectionCodEdit.setVisibility(View.GONE);
                mConnectionCodMaskEdit.setVisibility(View.GONE);
                mConnectionBdAddrEdit.setVisibility(View.GONE);
                break;
            case 1: // Class of Device
                mConnectionCodEdit.setVisibility(View.VISIBLE);
                mConnectionCodMaskEdit.setVisibility(View.VISIBLE);
                mConnectionBdAddrEdit.setVisibility(View.GONE);
                break;
            case 2: // BD_ADDR
                mConnectionCodEdit.setVisibility(View.GONE);
                mConnectionCodMaskEdit.setVisibility(View.GONE);
                mConnectionBdAddrEdit.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void sendClearAllFilters() {
        boolean result = mVendorCommands.sendExtendedSetEventFilterClearAll();
        updateStatus("Clear All Filters command sent. Result: " + result);
    }

    private void sendInquiryResultFilter() {
        try {
            int conditionType = mInquiryConditionSpinner.getSelectedItemPosition();
            byte[] classOfDevice = null;
            byte[] classOfDeviceMask = null;
            byte[] bdAddr = null;

            switch (conditionType) {
                case 0: // All Devices
                    conditionType = BluetoothVendorCommands.INQUIRY_FILTER_CONDITION_ALL_DEVICES;
                    break;
                case 1: // Class of Device
                    conditionType = BluetoothVendorCommands.INQUIRY_FILTER_CONDITION_CLASS_OF_DEVICE;
                    String codStr = mInquiryCodEdit.getText().toString().trim();
                    String codMaskStr = mInquiryCodMaskEdit.getText().toString().trim();
                    
                    if (codStr.isEmpty() || codMaskStr.isEmpty()) {
                        showToast("Please enter Class of Device and Mask");
                        return;
                    }
                    
                    classOfDevice = BluetoothVendorCommands.parseClassOfDevice(codStr);
                    classOfDeviceMask = BluetoothVendorCommands.parseClassOfDevice(codMaskStr);
                    
                    if (classOfDevice == null || classOfDeviceMask == null) {
                        showToast("Invalid Class of Device format. Use 6 hex digits (e.g., 200404)");
                        return;
                    }
                    break;
                case 2: // BD_ADDR
                    conditionType = BluetoothVendorCommands.INQUIRY_FILTER_CONDITION_BD_ADDR;
                    String bdAddrStr = mInquiryBdAddrEdit.getText().toString().trim();
                    
                    if (bdAddrStr.isEmpty()) {
                        showToast("Please enter BD_ADDR");
                        return;
                    }
                    
                    bdAddr = BluetoothVendorCommands.parseBdAddr(bdAddrStr);
                    if (bdAddr == null) {
                        showToast("Invalid BD_ADDR format. Use XX:XX:XX:XX:XX:XX");
                        return;
                    }
                    break;
            }

            boolean result = mVendorCommands.sendExtendedSetEventFilterInquiryResult(
                conditionType, classOfDevice, classOfDeviceMask, bdAddr);
            updateStatus("Inquiry Result Filter command sent. Condition: " + 
                        mInquiryConditionSpinner.getSelectedItem() + ", Result: " + result);
                        
        } catch (Exception e) {
            showToast("Error sending inquiry filter: " + e.getMessage());
            Log.e(TAG, "Error sending inquiry filter", e);
        }
    }

    private void sendConnectionSetupFilter() {
        try {
            int conditionType = mConnectionConditionSpinner.getSelectedItemPosition();
            int autoAcceptFlag = mAutoAcceptSpinner.getSelectedItemPosition();
            byte[] classOfDevice = null;
            byte[] classOfDeviceMask = null;
            byte[] bdAddr = null;

            switch (conditionType) {
                case 0: // All Devices
                    conditionType = BluetoothVendorCommands.CONNECTION_FILTER_CONDITION_ALL_DEVICES;
                    break;
                case 1: // Class of Device
                    conditionType = BluetoothVendorCommands.CONNECTION_FILTER_CONDITION_CLASS_OF_DEVICE;
                    String codStr = mConnectionCodEdit.getText().toString().trim();
                    String codMaskStr = mConnectionCodMaskEdit.getText().toString().trim();
                    
                    if (codStr.isEmpty() || codMaskStr.isEmpty()) {
                        showToast("Please enter Class of Device and Mask");
                        return;
                    }
                    
                    classOfDevice = BluetoothVendorCommands.parseClassOfDevice(codStr);
                    classOfDeviceMask = BluetoothVendorCommands.parseClassOfDevice(codMaskStr);
                    
                    if (classOfDevice == null || classOfDeviceMask == null) {
                        showToast("Invalid Class of Device format. Use 6 hex digits (e.g., 200404)");
                        return;
                    }
                    break;
                case 2: // BD_ADDR
                    conditionType = BluetoothVendorCommands.CONNECTION_FILTER_CONDITION_BD_ADDR;
                    String bdAddrStr = mConnectionBdAddrEdit.getText().toString().trim();
                    
                    if (bdAddrStr.isEmpty()) {
                        showToast("Please enter BD_ADDR");
                        return;
                    }
                    
                    bdAddr = BluetoothVendorCommands.parseBdAddr(bdAddrStr);
                    if (bdAddr == null) {
                        showToast("Invalid BD_ADDR format. Use XX:XX:XX:XX:XX:XX");
                        return;
                    }
                    break;
            }

            switch (autoAcceptFlag) {
                case 0: // Auto Accept Off
                    autoAcceptFlag = BluetoothVendorCommands.AUTO_ACCEPT_OFF;
                    break;
                case 1: // Auto Accept On (Role Switch Disabled)
                    autoAcceptFlag = BluetoothVendorCommands.AUTO_ACCEPT_ON_ROLE_SWITCH_DISABLED;
                    break;
                case 2: // Auto Accept On (Role Switch Enabled)
                    autoAcceptFlag = BluetoothVendorCommands.AUTO_ACCEPT_ON_ROLE_SWITCH_ENABLED;
                    break;
                case 3: // Reject Connection
                    autoAcceptFlag = BluetoothVendorCommands.AUTO_ACCEPT_REJECT_CONNECTION;
                    break;
            }

            boolean result = mVendorCommands.sendExtendedSetEventFilterConnectionSetup(
                conditionType, autoAcceptFlag, classOfDevice, classOfDeviceMask, bdAddr);
            updateStatus("Connection Setup Filter command sent. Condition: " + 
                        mConnectionConditionSpinner.getSelectedItem() + ", Auto Accept: " + 
                        mAutoAcceptSpinner.getSelectedItem() + ", Result: " + result);
                        
        } catch (Exception e) {
            showToast("Error sending connection filter: " + e.getMessage());
            Log.e(TAG, "Error sending connection filter", e);
        }
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
