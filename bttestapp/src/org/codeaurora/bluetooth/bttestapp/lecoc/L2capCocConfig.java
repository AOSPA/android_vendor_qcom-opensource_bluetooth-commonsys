/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuration class for L2CAP CoC parameters.
 * Allows runtime configuration of buffer size, credits, header size, and PSM values.
 */
public class L2capCocConfig {
    private static final String PREFS_NAME = "L2capCocConfig";
    private static final String KEY_BUFFER_SIZE = "buffer_size";
    private static final String KEY_MAX_CREDITS = "max_credits";
    private static final String KEY_HEADER_SIZE = "header_size";
    private static final String KEY_PSM = "psm";
    
    // Default values (original static values)
    public static final int DEFAULT_BUFFER_SIZE = 1048576;
    public static final int DEFAULT_MAX_CREDITS = 10;
    public static final int DEFAULT_HEADER_SIZE = 4;
    public static final int DEFAULT_PSM = 0x0080;
    
    // Configuration parameters
    private int bufferSize;
    private int maxCredits;
    private int headerSize;
    private int psm;
    
    // Singleton instance
    private static L2capCocConfig instance;
    private SharedPreferences prefs;
    
    /**
     * Private constructor for singleton pattern
     */
    private L2capCocConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadConfiguration();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized L2capCocConfig getInstance(Context context) {
        if (instance == null) {
            instance = new L2capCocConfig(context);
        }
        return instance;
    }
    
    /**
     * Load configuration from SharedPreferences
     */
    private void loadConfiguration() {
        bufferSize = prefs.getInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
        maxCredits = prefs.getInt(KEY_MAX_CREDITS, DEFAULT_MAX_CREDITS);
        headerSize = prefs.getInt(KEY_HEADER_SIZE, DEFAULT_HEADER_SIZE);
        psm = prefs.getInt(KEY_PSM, DEFAULT_PSM);
    }
    
    /**
     * Save configuration to SharedPreferences
     */
    private void saveConfiguration() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_BUFFER_SIZE, bufferSize);
        editor.putInt(KEY_MAX_CREDITS, maxCredits);
        editor.putInt(KEY_HEADER_SIZE, headerSize);
        editor.putInt(KEY_PSM, psm);
        editor.apply();
    }
    
    // Getters
    public int getBufferSize() {
        return bufferSize;
    }
    
    public int getMaxCredits() {
        return maxCredits;
    }
    
    public int getHeaderSize() {
        return headerSize;
    }
    
    public int getPsm() {
        return psm;
    }
    
    // Setters with validation
    public boolean setBufferSize(int bufferSize) {
        if (bufferSize < 64 || bufferSize > 1048576) {
            return false; // Invalid range
        }
        this.bufferSize = bufferSize;
        saveConfiguration();
        return true;
    }
    
    public boolean setMaxCredits(int maxCredits) {
        if (maxCredits < 1 || maxCredits > 1000) {
            return false; // Invalid range - increased for large file transfers
        }
        this.maxCredits = maxCredits;
        saveConfiguration();
        return true;
    }
    
    public boolean setHeaderSize(int headerSize) {
        if (headerSize < 2 || headerSize > 16) {
            return false; // Invalid range
        }
        this.headerSize = headerSize;
        saveConfiguration();
        return true;
    }
    
    public boolean setPsm(int psm) {
        if (psm < 1 || psm > 32767) {
            return false; // Invalid range
        }
        this.psm = psm;
        saveConfiguration();
        return true;
    }
    
    /**
     * Reset all values to defaults
     */
    public void resetToDefaults() {
        bufferSize = DEFAULT_BUFFER_SIZE;
        maxCredits = DEFAULT_MAX_CREDITS;
        headerSize = DEFAULT_HEADER_SIZE;
        psm = DEFAULT_PSM;
        saveConfiguration();
    }
    
    /**
     * Validate current configuration
     */
    public boolean isValid() {
        return bufferSize >= 64 && bufferSize <= 1048576 &&
               maxCredits >= 1 && maxCredits <= 1000 &&
               headerSize >= 2 && headerSize <= 16 &&
               psm >= 1 && psm <= 32767;
    }
    
    /**
     * Get configuration summary as string
     */
    public String getConfigSummary() {
        return String.format("Buffer: %d, Credits: %d, Header: %d, PSM: 0x%04X",
                bufferSize, maxCredits, headerSize, psm);
    }
    
    /**
     * Get validation ranges as string
     */
    public static String getValidationRanges() {
        return "Buffer Size: 64-1048576 bytes\n" +
               "Max Credits: 1-1000\n" +
               "Header Size: 2-16 bytes\n" +
               "PSM: 1-32767";
    }
}
