/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.bluetooth.bttestapp.lecoc;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.codeaurora.bluetooth.bttestapp.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enhanced L2CAP CoC Activity with Foreground Service architecture.
 * Demonstrates BLE L2CAP Connection-oriented Channel functionality using a service.
 */
public class L2capCocActivity extends Activity implements BluetoothL2capService.ServiceCallback {
    private static final String TAG = "L2capCocActivity";
    private static final int FILE_SELECT_REQUEST_CODE = 1001;
    private static final int CONFIG_REQUEST_CODE = 1002;
    
    // UI Components
    private Button btnStartServer, btnStopServer, btnScanDevices, btnStopScan;
    private Button btnSendData, btnClearLogs, btnSelectFile, btnSendFile, btnConfiguration;
    private EditText etDataToSend, etPsmValue;
    private TextView tvServerStatus, tvClientStatus, tvLogs, tvCredits, tvNoDevices, tvSelectedFile;
    private ListView lvDevices;
    
    // File selection
    private Uri selectedFileUri;
    private String selectedFileName;
    
    // Service components
    private BluetoothL2capService l2capService;
    private boolean isServiceBound = false;
    
    // Bluetooth adapter
    private BluetoothAdapter bluetoothAdapter;
    
    // Device management
    private ArrayAdapter<String> deviceAdapter;
    private List<BluetoothDevice> discoveredDevices;
    private BluetoothDevice selectedDevice;
    
    // UI Handler for thread-safe updates
    private Handler uiHandler;
    
    // Service connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            BluetoothL2capService.L2capServiceBinder binder = (BluetoothL2capService.L2capServiceBinder) service;
            l2capService = binder.getService();
            isServiceBound = true;
            
            // Register for service callbacks
            l2capService.registerCallback(L2capCocActivity.this);
            
            // Update UI with current service state
            updateUIFromService();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            if (l2capService != null) {
                l2capService.unregisterCallback(L2capCocActivity.this);
            }
            l2capService = null;
            isServiceBound = false;
        }
    };
    
    // Device discovery receiver
    private final BroadcastReceiver deviceDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                    updateDeviceList();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                updateScanButtons(false);
                appendLog("Device discovery finished");
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_l2cap_coc);
        
        uiHandler = new Handler(Looper.getMainLooper());
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        initializeUI();
        setupDeviceDiscovery();
        loadPairedDevices();
        
        // Start and bind to service
        startAndBindService();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (!isServiceBound) {
            startAndBindService();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Don't unbind service to keep connections alive
    }
    
    /**
     * Start and bind to the L2CAP service
     */
    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, BluetoothL2capService.class);
        startService(serviceIntent); // Start as foreground service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * Update UI with current service state
     */
    private void updateUIFromService() {
        if (l2capService == null) return;
        
        // Update server status
        updateServerButtons(l2capService.isServerRunning());
        updateServerStatus();
        
        // Update client status
        updateClientStatus();
        
        // Update data controls
        updateDataControls(l2capService.hasActiveConnection());
        
        appendLog("Connected to L2CAP service");
    }
    
    /**
     * Initialize UI components and set up event listeners
     */
    private void initializeUI() {
        // Configuration button
        btnConfiguration = findViewById(R.id.btnConfiguration);

        // Server controls
        btnStartServer = findViewById(R.id.btnStartServer);
        btnStopServer = findViewById(R.id.btnStopServer);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        
        // Client controls
        btnScanDevices = findViewById(R.id.btnScanDevices);
        btnStopScan = findViewById(R.id.btnStopScan);
        tvClientStatus = findViewById(R.id.tvClientStatus);
        lvDevices = findViewById(R.id.lvDevices);
        etPsmValue = findViewById(R.id.etPsmValue);
        
        // Data exchange
        etDataToSend = findViewById(R.id.etDataToSend);
        btnSendData = findViewById(R.id.btnSendData);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnSendFile = findViewById(R.id.btnSendFile);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        
        // Logs and status
        tvLogs = findViewById(R.id.tvLogs);
        tvCredits = findViewById(R.id.tvCredits);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        tvNoDevices = findViewById(R.id.tvNoDevices);
        
        // Make logs scrollable
        tvLogs.setMovementMethod(new ScrollingMovementMethod());
        
        // Set up button listeners
        btnConfiguration.setOnClickListener(v -> openConfiguration());
        btnStartServer.setOnClickListener(v -> startServer());
        btnStopServer.setOnClickListener(v -> stopServer());
        btnScanDevices.setOnClickListener(v -> startDeviceDiscovery());
        btnStopScan.setOnClickListener(v -> stopDeviceDiscovery());
        btnSendData.setOnClickListener(v -> sendData());
        btnSelectFile.setOnClickListener(v -> selectFile());
        btnSendFile.setOnClickListener(v -> sendFileData());
        btnClearLogs.setOnClickListener(v -> clearLogs());
        
        // Initialize UI state
        updateServerButtons(false);
        updateScanButtons(false);
        updateDataControls(false);
        updateCreditsDisplay(0, 0);
        
        // Initialize status displays
        updateServerStatus();
        updateClientStatus();
    }
    
    /**
     * Set up device discovery components
     */
    private void setupDeviceDiscovery() {
        discoveredDevices = new ArrayList<>();

        deviceAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    textView.setTextColor(getResources().getColor(android.R.color.black));
                    textView.setTextSize(14);
                    textView.setPadding(16, 12, 16, 12);
                }
                return view;
            }
        };
        lvDevices.setAdapter(deviceAdapter);
        
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < discoveredDevices.size()) {
                    selectedDevice = discoveredDevices.get(position);
                    connectToSelectedDevice();
                }
            }
        });
        
        // Register for device discovery broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(deviceDiscoveryReceiver, filter);
    }
    
    /**
     * Load and display paired devices
     */
    private void loadPairedDevices() {
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                if (!discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                }
            }
            updateDeviceList();
            appendLog("Loaded " + pairedDevices.size() + " paired devices");
        } catch (SecurityException e) {
            appendLog("Permission denied accessing paired devices: " + e.getMessage());
        }
    }
    
    /**
     * Start the L2CAP CoC server via service
     */
    private void startServer() {
        if (l2capService != null) {
            if (l2capService.startServer()) {
                appendLog("Server start initiated");
            } else {
                appendLog("Failed to start server");
            }
        } else {
            appendLog("Service not connected");
        }
    }
    
    /**
     * Stop the L2CAP CoC server via service
     */
    private void stopServer() {
        if (l2capService != null) {
            l2capService.stopServer();
            appendLog("Server stopped");
        } else {
            appendLog("Service not connected");
        }
    }
    
    /**
     * Start device discovery
     */
    private void startDeviceDiscovery() {
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            
            discoveredDevices.clear();
            updateDeviceList();
            
            if (bluetoothAdapter.startDiscovery()) {
                updateScanButtons(true);
                appendLog("Device discovery started");
            } else {
                appendLog("Failed to start device discovery");
            }
        } catch (SecurityException e) {
            appendLog("Permission denied for device discovery: " + e.getMessage());
        }
    }
    
    /**
     * Stop device discovery
     */
    private void stopDeviceDiscovery() {
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            updateScanButtons(false);
            appendLog("Device discovery stopped");
        } catch (SecurityException e) {
            appendLog("Permission denied stopping discovery: " + e.getMessage());
        }
    }
    
    /**
     * Connect to the selected device via service
     */
    private void connectToSelectedDevice() {
        if (selectedDevice == null) {
            appendLog("No device selected");
            return;
        }
        
        if (l2capService == null) {
            appendLog("Service not connected");
            return;
        }
        
        // Get PSM value from EditText
        int psm = getPsmValue();
        if (psm == -1) {
            return; // Error already logged
        }
        
        if (l2capService.connectClient(selectedDevice, psm)) {
            appendLog("Connecting to " + selectedDevice.getAddress() + " on PSM: " + psm);
        } else {
            appendLog("Failed to initiate connection");
        }
    }
    
    /**
     * Get PSM value from EditText with validation
     * @return PSM value or -1 if invalid
     */
    private int getPsmValue() {
        String psmText = etPsmValue.getText().toString().trim();
        
        if (psmText.isEmpty()) {
            appendLog("PSM value is empty, using default: 128");
            return 128; // Default PSM
        }
        
        try {
            int psm = Integer.parseInt(psmText);
            
            // Validate PSM range (odd numbers from 1 to 32767 for dynamic allocation)
            if (psm < 1 || psm > 32767) {
                appendLog("Invalid PSM value: " + psm + ". Must be between 1 and 32767");
                Toast.makeText(this, "PSM must be between 1 and 32767", Toast.LENGTH_SHORT).show();
                return -1;
            }
            
            if (psm % 2 == 0) {
                appendLog("Warning: PSM " + psm + " is even. Odd PSMs are recommended for dynamic allocation");
            }
            
            return psm;
            
        } catch (NumberFormatException e) {
            appendLog("Invalid PSM format: " + psmText + ". Must be a number");
            Toast.makeText(this, "PSM must be a valid number", Toast.LENGTH_SHORT).show();
            return -1;
        }
    }
    
    /**
     * Send data through the active connection via service
     */
    private void sendData() {
        String data = etDataToSend.getText().toString().trim();
        if (data.isEmpty()) {
            Toast.makeText(this, "Enter data to send", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (l2capService == null) {
            appendLog("Service not connected");
            return;
        }
        
        if (l2capService.sendData(data)) {
            appendLog("Sent: " + data);
            etDataToSend.setText("");
        } else {
            appendLog("Failed to send data - no active connection");
        }
    }
    
    /**
     * Send file data through the active connection via service using streaming approach
     */
    private void sendFileData() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (l2capService == null) {
            appendLog("Service not connected");
            return;
        }
        
        // Stream file content in background thread to avoid OOM
        new Thread(() -> {
            try {
                sendFileInChunks(selectedFileUri);
            } catch (Exception e) {
                uiHandler.post(() -> appendLog("Error sending file: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Open configuration activity
     */
    private void openConfiguration() {
        Intent configIntent = new Intent(this, L2capCocConfigActivity.class);
        startActivityForResult(configIntent, CONFIG_REQUEST_CODE);
    }

    /**
     * Clear the logs display
     */
    private void clearLogs() {
        tvLogs.setText("");
    }
    
    /**
     * Update device list display and show/hide no devices message
     */
    private void updateDeviceList() {
        deviceAdapter.clear();
        for (BluetoothDevice device : discoveredDevices) {
            try {
                String deviceInfo = device.getName() + " (" + device.getAddress() + ")";
                if (device.getName() == null) {
                    deviceInfo = device.getAddress();
                }
                deviceAdapter.add(deviceInfo);
            } catch (SecurityException e) {
                deviceAdapter.add(device.getAddress());
            }
        }
        deviceAdapter.notifyDataSetChanged();
        
        // Show/hide no devices message
        if (discoveredDevices.isEmpty()) {
            lvDevices.setVisibility(View.GONE);
            tvNoDevices.setVisibility(View.VISIBLE);
        } else {
            lvDevices.setVisibility(View.VISIBLE);
            tvNoDevices.setVisibility(View.GONE);
        }
    }
    
    /**
     * Update server button states
     */
    private void updateServerButtons(boolean serverRunning) {
        btnStartServer.setEnabled(!serverRunning);
        btnStopServer.setEnabled(serverRunning);
    }
    
    /**
     * Update scan button states
     */
    private void updateScanButtons(boolean scanning) {
        btnScanDevices.setEnabled(!scanning);
        btnStopScan.setEnabled(scanning);
    }
    
    /**
     * Update data control states
     */
    private void updateDataControls(boolean connected) {
        btnSendData.setEnabled(connected);
        btnSendFile.setEnabled(connected && selectedFileUri != null);
    }
    
    /**
     * Update credits display
     */
    private void updateCreditsDisplay(int localCredits, int remoteCredits) {
        String creditsText = "Local: " + localCredits + " | Remote: " + remoteCredits;
        tvCredits.setText(creditsText);
    }
    
    /**
     * Update server status display
     */
    private void updateServerStatus() {
        if (l2capService != null) {
            tvServerStatus.setText(l2capService.getServerStatus());
        } else {
            tvServerStatus.setText("Server: Service not connected");
        }
    }
    
    /**
     * Update client status display
     */
    private void updateClientStatus() {
        if (l2capService != null) {
            tvClientStatus.setText(l2capService.getClientStatus());
        } else {
            tvClientStatus.setText("Client: Service not connected");
        }
    }
    
    /**
     * Append log message with timestamp
     */
    private void appendLog(String message) {
        uiHandler.post(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry = "[" + timestamp + "] " + message + "\n";
            tvLogs.append(logEntry);
            
            // Auto-scroll to bottom - check if layout is available
            if (tvLogs.getLayout() != null) {
                int scrollAmount = tvLogs.getLayout().getLineTop(tvLogs.getLineCount()) - tvLogs.getHeight();
                if (scrollAmount > 0) {
                    tvLogs.scrollTo(0, scrollAmount);
                }
            } else {
                // Fallback scrolling when layout is not ready
                tvLogs.post(() -> {
                    int scrollAmount = tvLogs.getLayout().getLineTop(tvLogs.getLineCount()) - tvLogs.getHeight();
                    if (scrollAmount > 0) {
                        tvLogs.scrollTo(0, scrollAmount);
                    }
                });
            }
        });
    }
    
    // ServiceCallback implementation
    
    @Override
    public void onConnectionStateChanged(boolean connected) {
        uiHandler.post(() -> {
            updateDataControls(connected);
            updateServerButtons(l2capService != null && l2capService.isServerRunning());
            if (connected) {
                appendLog("Connection established");
            } else {
                appendLog("Connection lost");
            }
        });
    }
    
    @Override
    public void onDataReceived(byte[] data) {
        String receivedData = new String(data);
        appendLog("Received: " + receivedData);
    }
    
    @Override
    public void onStatusChanged(String status) {
        uiHandler.post(() -> {
            // Update server status
            updateServerStatus();
            
            // Update client status
            updateClientStatus();
            
            appendLog("Status: " + status);
        });
    }
    
    @Override
    public void onCreditsChanged(int localCredits, int remoteCredits) {
        uiHandler.post(() -> updateCreditsDisplay(localCredits, remoteCredits));
    }
    
    // File selection methods
    
    /**
     * Open file picker to select a file
     */
    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_SELECT_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Read content from selected file URI (kept for backward compatibility with small files)
     */
    private String readFileContent(Uri uri) {
        StringBuilder content = new StringBuilder();
        
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            return content.toString();
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading file", e);
            return null;
        }
    }
    
    /**
     * Send file in chunks to avoid Out of Memory errors for large files (100MB+)
     * Uses streaming approach with configurable chunk size based on buffer configuration
     */
    private void sendFileInChunks(Uri uri) throws IOException {
        L2capCocConfig config = L2capCocConfig.getInstance(this);
        final int CHUNK_SIZE = Math.min(config.getBufferSize(), 1048576); // Use buffer size up to 1MB max
        final byte[] buffer = new byte[CHUNK_SIZE];

        long totalBytes = 0;
        long sentBytes = 0;
        int chunkCount = 0;

        uiHandler.post(() -> appendLog("Starting file transfer in chunks of " + CHUNK_SIZE + " bytes"));

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Cannot open input stream for file");
            }

            // Get file size for progress tracking (if available)
            try {
                totalBytes = inputStream.available();
                final long totalBytesFinal = totalBytes;
                uiHandler.post(() -> appendLog("File size: " + formatBytes(totalBytesFinal)));
            } catch (IOException e) {
                totalBytes = -1; // Unknown size
                uiHandler.post(() -> appendLog("File size: Unknown"));
            }

            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                chunkCount++;

                // Create chunk data (only the bytes actually read)
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                // Send chunk via service on UI thread
                final int currentChunk = chunkCount;
                final long currentSentBytes = sentBytes + bytesRead;
                final long finalTotalBytes = totalBytes;

                uiHandler.post(() -> {
                    if (l2capService != null && l2capService.sendData(chunkData)) {
                        String progressMsg = String.format("Sent chunk %d (%s)",
                            currentChunk, formatBytes(chunkData.length));

                        if (finalTotalBytes > 0) {
                            int progress = (int) ((currentSentBytes * 100) / finalTotalBytes);
                            progressMsg += String.format(" - Progress: %d%%", progress);
                        }

                        appendLog(progressMsg);
                    } else {
                        appendLog("Failed to send chunk " + currentChunk + " - connection lost");
                    }
                });

                sentBytes += bytesRead;

                // Small delay to prevent overwhelming the Bluetooth stack
                try {
                    Thread.sleep(10); // 10ms delay between chunks
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("File transfer interrupted", e);
                }
            }

            // Final status update
            final long finalSentBytes = sentBytes;
            final int finalChunkCount = chunkCount;
            uiHandler.post(() -> {
                appendLog(String.format("File transfer completed: %s in %d chunks",
                    formatBytes(finalSentBytes), finalChunkCount));
            });

        } catch (IOException e) {
            uiHandler.post(() -> appendLog("File transfer failed: " + e.getMessage()));
            throw e;
        }
    }

    /**
     * Format bytes into human readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Get file name from URI
     */
    private String getFileName(Uri uri) {
        String fileName = "Unknown";
        
        try {
            String path = uri.getPath();
            if (path != null) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash < path.length() - 1) {
                    fileName = path.substring(lastSlash + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
        }
        
        return fileName;
    }
    
    /**
     * Update file selection display
     */
    private void updateFileSelection() {
        if (selectedFileUri != null && selectedFileName != null) {
            tvSelectedFile.setText(selectedFileName);
            tvSelectedFile.setTextColor(getResources().getColor(android.R.color.black));
        } else {
            tvSelectedFile.setText("No file selected");
            tvSelectedFile.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
        
        // Update send file button state
        boolean connected = l2capService != null && l2capService.hasActiveConnection();
        btnSendFile.setEnabled(connected && selectedFileUri != null);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == FILE_SELECT_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                selectedFileUri = data.getData();
                selectedFileName = getFileName(selectedFileUri);
                
                updateFileSelection();
                appendLog("File selected: " + selectedFileName);
            }
        } else if (requestCode == CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            // Configuration was updated
            L2capCocConfig config = L2capCocConfig.getInstance(this);
            appendLog("Configuration updated: " + config.getConfigSummary());

            // Note: Configuration changes will take effect on next connection
            // as existing connections use the configuration from when they were created
            if (l2capService != null && (l2capService.isServerRunning() || l2capService.hasActiveConnection())) {
                appendLog("Note: Configuration changes will take effect on next connection");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unregister from service callbacks
        if (l2capService != null) {
            l2capService.unregisterCallback(this);
        }
        
        // Unbind from service (but don't stop it)
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        // Stop device discovery
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied stopping discovery in onDestroy", e);
        }
        
        // Unregister receiver
        try {
            unregisterReceiver(deviceDiscoveryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }
        
        Log.d(TAG, "Activity destroyed - L2CAP connections remain active in service");
    }
}
