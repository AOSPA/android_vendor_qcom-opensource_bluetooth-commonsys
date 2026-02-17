/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.codeaurora.bluetooth.bttestapp.R;

/**
 * Configuration activity for L2CAP CoC parameters.
 * Allows users to modify buffer size, credits, header size, and PSM values.
 */
public class L2capCocConfigActivity extends Activity {
    private static final String TAG = "L2capCocConfigActivity";
    
    // UI Components
    private EditText etBufferSize, etMaxCredits, etHeaderSize, etPsm;
    private TextView tvValidationRanges, tvCurrentConfig;
    private Button btnSave, btnReset, btnCancel;
    
    // Configuration instance
    private L2capCocConfig config;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_l2cap_coc_config);
        
        config = L2capCocConfig.getInstance(this);
        
        initializeUI();
        loadCurrentConfiguration();
    }
    
    /**
     * Initialize UI components and set up event listeners
     */
    private void initializeUI() {
        // Input fields
        etBufferSize = findViewById(R.id.etBufferSize);
        etMaxCredits = findViewById(R.id.etMaxCredits);
        etHeaderSize = findViewById(R.id.etHeaderSize);
        etPsm = findViewById(R.id.etPsm);
        
        // Information displays
        tvValidationRanges = findViewById(R.id.tvValidationRanges);
        tvCurrentConfig = findViewById(R.id.tvCurrentConfig);
        
        // Buttons
        btnSave = findViewById(R.id.btnSave);
        btnReset = findViewById(R.id.btnReset);
        btnCancel = findViewById(R.id.btnCancel);
        
        // Set validation ranges text
        tvValidationRanges.setText(L2capCocConfig.getValidationRanges());
        
        // Set up button listeners
        btnSave.setOnClickListener(v -> saveConfiguration());
        btnReset.setOnClickListener(v -> resetToDefaults());
        btnCancel.setOnClickListener(v -> finish());
    }
    
    /**
     * Load current configuration values into UI
     */
    private void loadCurrentConfiguration() {
        etBufferSize.setText(String.valueOf(config.getBufferSize()));
        etMaxCredits.setText(String.valueOf(config.getMaxCredits()));
        etHeaderSize.setText(String.valueOf(config.getHeaderSize()));
        etPsm.setText(String.valueOf(config.getPsm()));
        
        updateCurrentConfigDisplay();
    }
    
    /**
     * Update the current configuration display
     */
    private void updateCurrentConfigDisplay() {
        tvCurrentConfig.setText("Current: " + config.getConfigSummary());
    }
    
    /**
     * Save configuration with validation
     */
    private void saveConfiguration() {
        try {
            // Get values from UI
            String bufferSizeStr = etBufferSize.getText().toString().trim();
            String maxCreditsStr = etMaxCredits.getText().toString().trim();
            String headerSizeStr = etHeaderSize.getText().toString().trim();
            String psmStr = etPsm.getText().toString().trim();
            
            // Validate input
            if (TextUtils.isEmpty(bufferSizeStr) || TextUtils.isEmpty(maxCreditsStr) ||
                TextUtils.isEmpty(headerSizeStr) || TextUtils.isEmpty(psmStr)) {
                showError("All fields are required");
                return;
            }
            
            // Parse values
            int bufferSize = Integer.parseInt(bufferSizeStr);
            int maxCredits = Integer.parseInt(maxCreditsStr);
            int headerSize = Integer.parseInt(headerSizeStr);
            int psm = Integer.parseInt(psmStr);
            
            // Validate and set each value
            boolean allValid = true;
            StringBuilder errorMsg = new StringBuilder();
            
            if (!config.setBufferSize(bufferSize)) {
                allValid = false;
                errorMsg.append("Buffer Size must be 64-1048576 bytes\n");
            }
            
            if (!config.setMaxCredits(maxCredits)) {
                allValid = false;
                errorMsg.append("Max Credits must be 1-1000\n");
            }
            
            if (!config.setHeaderSize(headerSize)) {
                allValid = false;
                errorMsg.append("Header Size must be 2-16 bytes\n");
            }
            
            if (!config.setPsm(psm)) {
                allValid = false;
                errorMsg.append("PSM must be 1-32767\n");
            }
            
            if (!allValid) {
                showError("Validation failed:\n" + errorMsg.toString());
                // Reload current values to revert invalid changes
                loadCurrentConfiguration();
                return;
            }
            
            // Success
            updateCurrentConfigDisplay();
            Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show();
            
            // Set result and finish
            setResult(RESULT_OK);
            finish();
            
        } catch (NumberFormatException e) {
            showError("Invalid number format. Please enter valid integers.");
        } catch (Exception e) {
            showError("Error saving configuration: " + e.getMessage());
        }
    }
    
    /**
     * Reset all values to defaults
     */
    private void resetToDefaults() {
        config.resetToDefaults();
        loadCurrentConfiguration();
        Toast.makeText(this, "Configuration reset to defaults", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onBackPressed() {
        // Just finish without saving
        super.onBackPressed();
    }
}
