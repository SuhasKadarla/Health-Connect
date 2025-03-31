package com.example.bluetoothwaveformapp;

import static androidx.core.content.ContextCompat.registerReceiver;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.Toolbar;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.airbnb.lottie.LottieAnimationView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final int REQUEST_PERMISSIONS = 2;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothAdapter bluetoothAdapter;
    private TextView statusText, batteryStatusText;
    private Button scanButton, recordButton, playbackButton, pdfButton, disconnectButton;
    private SeekBar frequencySeekBar;
    private TextView frequencyValue, servicesText;


    private LineChart lineChart;
    private LineData lineData;
    private LineDataSet lineDataSet;
    private ArrayList<Entry> waveformEntries = new ArrayList<>();
    private boolean isRecording = false;
    private List<Float> recordedWaveforms = new ArrayList<>();
    private ArrayList<String> scannedDevices = new ArrayList<>();  // Persistent list of devices
    private HashMap<String, BluetoothDevice> deviceMap = new HashMap<>(); // To store device by address
    private HashMap<String, String> deviceStatusMap = new HashMap<>();

    private ArrayAdapter<String> adapter;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;
    private BluetoothGattService targetService;
    private BluetoothGattCharacteristic targetCharacteristic;
    private BluetoothGattCharacteristic charVolumeUp;
    private BluetoothGattCharacteristic charVolumeDown;
    private BluetoothGattCharacteristic charSeekBar;
    private BluetoothGattCharacteristic charSetFrequency;
    private static final int MAX_DATA_POINTS = 500; // Set a limit for the number of points

    private TextView recordingStatusText;
    private ImageView recordingIcon;

    private LottieAnimationView loadingAnimation;

    //led
    private Button btnOn, btnOff;

    private static final String LED_SERVICE_UUID = "00001523-1212-efde-1523-785feabcd123";
    private static final String LED_CHAR_UUID = "00001525-1212-efde-1523-785feabcd123";
    private BluetoothGattCharacteristic ledCharacteristic;

    private final Map<UUID, Consumer<byte[]>> characteristicHandlers = new HashMap<>();

    // Initialize the mapping in your constructor or initialization block
    {
        /*characteristicHandlers.put(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"), this::processBatteryLevelData);*/
        characteristicHandlers.put(UUID.fromString("00002B90-0000-1000-8000-00805F9B34FB"), this::processChargingStatusData);

        characteristicHandlers.put(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"), this::processBatteryLevelData);
    }
    private int currentVolumeLevel = 0;

    private Fragment homeFragment, chartFragment, accountFragment, activeFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        batteryStatusText = findViewById(R.id.batteryStatusText);
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        recordingStatusText = findViewById(R.id.recordingStatusText);
        // Start the chart update timer
        startChartUpdateTimer();

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Set up refresh listener
        // Perform your refresh action here
        swipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.home) {
                // Navigate to Home (optional logic here)
                return true;
            } else if (item.getItemId() == R.id.chart) {
                // Open ChartActivity when "Chart" button is clicked
                Intent intent = new Intent(MainActivity.this, ChartActivity.class);

                // Convert waveform data to a string or array
                int[] waveformArray = new int[waveformPoints.size()];
                for (int i = 0; i < waveformPoints.size(); i++) {
                    waveformArray[i] = waveformPoints.get(i);
                }

                // Pass data
                intent.putExtra("waveform_data", waveformArray);
                startActivity(intent);

                return true;
            }
            else if (item.getItemId() == R.id.account) {
                // Open AccountActivity when "Account" button is clicked
                Intent intent = new Intent(MainActivity.this, AccountActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });



        // Set click listeners

        //volume buttons

        ImageButton volumePlusButton = findViewById(R.id.volumePlusButton);
        ImageButton volumeMinusButton = findViewById(R.id.volumeMinusButton);
        ImageButton volumeEqualsButton = findViewById(R.id.volumeEqualsButton);

        SeekBar volumeSeekBar = findViewById(R.id.volumeSeekBar);
        TextView volumeText = findViewById(R.id.volumeText);


        volumePlusButton.setOnClickListener(v -> increaseVolume(volumeSeekBar, volumeText));
        volumeMinusButton.setOnClickListener(v -> decreaseVolume(volumeSeekBar, volumeText));
        volumeEqualsButton.setOnClickListener(v -> sendSetFrequencyCommand());



        // SeekBar Volume Control (Independent from Buttons)

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    volumeText.setText("Volume: " + progress);
                    sendSeekBarVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.getThumb().setAlpha(180);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.getThumb().setAlpha(255);
            }
        });












        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN},
                        1);
            }
        }

        statusText = findViewById(R.id.statusText);
        batteryStatusText = findViewById(R.id.batteryStatusText);
        scanButton = findViewById(R.id.scanButton);
        recordButton = findViewById(R.id.recordButton);
        playbackButton = findViewById(R.id.playbackButton);
        pdfButton = findViewById(R.id.pdfButton);
        lineChart = findViewById(R.id.lineChart);
        disconnectButton = findViewById(R.id.disconnectButton);



        frequencySeekBar = findViewById(R.id.frequencySeekBar);
        frequencyValue = findViewById(R.id.frequencyValue);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);  // This completely hides the title
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        lineDataSet = new LineDataSet(new ArrayList<>(), "Waveform");
        lineData = new LineData(lineDataSet);
        lineChart.setData(lineData);

        ImageButton settingsButton = findViewById(R.id.buttonSettings);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChartSettingsActivity.class);
            startActivity(intent);
        });


        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth not supported.");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        // Button Listeners
        scanButton.setOnClickListener(v -> startScanningForDevices());
        recordButton.setOnClickListener(v -> toggleRecording());
        playbackButton.setOnClickListener(v -> playbackRecording());
        pdfButton.setOnClickListener(v -> generatePDFReport());
        disconnectButton.setOnClickListener(v -> disconnectDevice());

        // Battery Monitoring
        startBatteryMonitoring();
        navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);

        // Permissions Request
        requestPermissions();

        frequencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int frequency = progress; // Assuming the frequency range is 0-100 Hz
                frequencyValue.setText("Frequency: " + frequency + " Hz");

                // Send the frequency to the connected device
                sendFrequencyToDevice(frequency);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        applyChartSettings();  // Apply settings when activity resumes
    }

    private void applyChartSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences("ChartPrefs", MODE_PRIVATE);
        float lineWidth = sharedPreferences.getFloat("lineWidth", 2f);
        boolean showGridLines = sharedPreferences.getBoolean("showGridLines", true);
        boolean enableZoom = sharedPreferences.getBoolean("enableZoom", true);

        lineDataSet.setLineWidth(lineWidth);
        lineChart.getXAxis().setDrawGridLines(showGridLines);
        lineChart.getAxisLeft().setDrawGridLines(showGridLines);
        lineChart.getAxisRight().setDrawGridLines(showGridLines);

        // Apply zoom settings correctly
        lineChart.setScaleEnabled(enableZoom);
        lineChart.setPinchZoom(enableZoom);
        lineChart.setDragEnabled(enableZoom);

        lineChart.invalidate(); // Refresh the chart

        // Reset Zoom Button
        Button resetZoomButton = findViewById(R.id.buttonResetZoom);
        resetZoomButton.setOnClickListener(v -> resetZoom());
    }

    private void resetZoom() {
        lineChart.fitScreen(); // Reset zoom and panning to the original state

        // Reapply Enable Zoom setting from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("ChartPrefs", MODE_PRIVATE);
        boolean enableZoom = sharedPreferences.getBoolean("enableZoom", true);

        lineChart.setScaleEnabled(enableZoom);
        lineChart.setPinchZoom(enableZoom);
        lineChart.setDragEnabled(enableZoom);

        lineChart.invalidate(); // Refresh the chart
    }


    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        List<String> toRequest = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(perm);
            }
        }
        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanningForDevices();
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void startScanningForDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION},
                        1);
                return;
            }
        }

        // Start scanning if permissions are granted
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show();
            return;
        }


        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                int rssi = result.getRssi();
                updateRssiDisplay(rssi);  // Ensure the display is updated in the UI thread


                if (deviceName != null && !scannedDevices.contains(deviceName)) {
                    scannedDevices.add(deviceName);
                    deviceMap.put(deviceAddress, device);
                    updateNavigationMenu(deviceName, deviceAddress);  // Add device to Navigation Drawer
                }

                // On item click, connect to the selected device
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e("BLE Scan", "Scan failed with error code: " + errorCode);
            }
        });
    }
    private void updateNavigationMenu(String deviceName, String deviceAddress) {
        MenuItem newItem = navigationView.getMenu().add(deviceName + " (" + deviceAddress + ")");
        newItem.setOnMenuItemClickListener(item -> {
            BluetoothDevice selectedDevice = deviceMap.get(deviceAddress);
            connectToDevice(selectedDevice);
            return true;
        });
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        statusText.setText("Connecting to " + device.getName());
        bluetoothGatt.readRemoteRssi();

        disconnectButton.setEnabled(true);


    }

    @SuppressLint("MissingPermission")
    private void disconnectDevice() {
        if (bluetoothGatt != null) {
            // Disconnect the device
            bluetoothGatt.disconnect();

            // Close the GATT connection to free up resources
            bluetoothGatt.close();
            bluetoothGatt = null;

            // Update UI
            statusText.setText("Disconnected");
            Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show();

            // Disable buttons after disconnecting
            recordButton.setEnabled(false);
            disconnectButton.setEnabled(false);
        } else {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            // Check if we are in the main thread
            if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                // If not, post the update to the main thread
                runOnUiThread(() -> {
                    TextView statusTextView = findViewById(R.id.statusText);

                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        // Successfully connected
                        statusTextView.setText("Connected to " + gatt.getDevice().getName());
                        Toast.makeText(MainActivity.this, "Connection successful!", Toast.LENGTH_SHORT).show();

                        // Enable the record button after successful connection
                        recordButton.setEnabled(true);
                        disconnectButton.setEnabled(true);
                        recordButton.setText("Start Recording");  // Show "Start Recording"
                        displayDeviceDetails(gatt);  // Call to display device details


                        // Optional: You can update any other UI elements (e.g., device info, battery status, etc.)
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        // Disconnected from the device
                        statusTextView.setText("Disconnected from " + gatt.getDevice().getName());
                        Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();

                        // Disable the record button after disconnection
                        recordButton.setEnabled(false);
                        disconnectButton.setEnabled(false);
                        recordButton.setText("Start Recording");  // Reset button text to default
                    }
                });
            } else {
                // Directly update if already on the main thread
                TextView statusTextView = findViewById(R.id.statusText);

                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    // Successfully connected
                    statusTextView.setText("Connected to " + gatt.getDevice().getName());
                    Toast.makeText(MainActivity.this, "Connection successful!", Toast.LENGTH_SHORT).show();

                    // Enable the record button after successful connection
                    recordButton.setEnabled(true);
                    recordButton.setText("Start Recording");  // Show "Start Recording"
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    // Disconnected from the device
                    statusTextView.setText("Disconnected from " + gatt.getDevice().getName());
                    Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();

                    // Disable the record button after disconnection
                    recordButton.setEnabled(false);
                    recordButton.setText("Start Recording");  // Reset button text to default
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            // Display the RSSI value
            runOnUiThread(() -> {
                TextView rssiText = findViewById(R.id.rssiText);  // Assuming you have a TextView for RSSI
                rssiText.setText("RSSI: " + rssi + " dBm");
            });
        }

        //led

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                assignCharacteristics();
                Log.d("Bluetooth", "Services discovered successfully.");

                BluetoothGattService batteryService = gatt.getService(UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB"));
                if (batteryService != null) {
                    BluetoothGattCharacteristic batteryLevelChar = batteryService.getCharacteristic(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"));
                    BluetoothGattCharacteristic chargingStatusChar = batteryService.getCharacteristic(UUID.fromString("00002B90-0000-1000-8000-00805F9B34FB"));

                    if (batteryLevelChar != null && chargingStatusChar != null) {
                        enableNotificationsWithDelay(gatt, batteryLevelChar, 0); // Enable immediately
                        enableNotificationsWithDelay(gatt, chargingStatusChar, 500); // Enable after 500ms delay
                    }
                }

                // Display supported services on UI
                runOnUiThread(() -> {
                    StringBuilder servicesInfo = new StringBuilder("Supported Services:\n");
                    for (BluetoothGattService discoveredService : gatt.getServices()) {
                        servicesInfo.append(discoveredService.getUuid().toString()).append("\n");
                    }
                    TextView servicesText = findViewById(R.id.servicesText);
                    servicesText.setText(servicesInfo.toString());
                });

                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d("Bluetooth", "Discovered Service: " + service.getUuid());

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d("Bluetooth", " ├─ Characteristic: " + characteristic.getUuid());

                        // Check for writable characteristic
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                            targetService = service;
                            targetCharacteristic = characteristic;
                            Log.d("Bluetooth", " │  └─ Found Writable Characteristic: " + characteristic.getUuid());
                        }

                        // Enable notifications for characteristics supporting it
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            enableNotifications(gatt, characteristic);

                        }

                        // Log descriptors
                        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                            Log.d("Bluetooth", " │     ├─ Descriptor: " + descriptor.getUuid());
                        }
                    }
                }

                // Try reading battery level (for devices that support reading)

                // Retrieve RSSI after service discovery
                gatt.readRemoteRssi();
            } else {
                Log.e("Bluetooth", "Service discovery failed with status: " + status);
            }
        }


        private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            boolean success = gatt.setCharacteristicNotification(characteristic, true);
            Log.d("Bluetooth", "setCharacteristicNotification for " + characteristic.getUuid() + ": " + success);

            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                if (descriptor.getUuid().equals(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    boolean writeSuccess = gatt.writeDescriptor(descriptor);
                    Log.d("Bluetooth", "writeDescriptor for " + characteristic.getUuid() + ": " + writeSuccess);
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                UUID characteristicUUID = characteristic.getUuid();
                byte[] data = characteristic.getValue();

                if (data != null) {
                    Consumer<byte[]> handler = characteristicHandlers.get(characteristicUUID);
                    if (handler != null) {
                        handler.accept(data); // Call the appropriate method
                    }
                }
            }
        }
        private void displayDeviceDetails(BluetoothGatt gatt) {
            // Start discovering services
            gatt.discoverServices();
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID characteristicUUID = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            Log.d("Bluetooth", "Received data: " + Arrays.toString(data));

            if (data != null) {
                Consumer<byte[]> handler = characteristicHandlers.get(characteristicUUID);
                if (handler != null) {
                    handler.accept(data); // Call the appropriate method
                }
            }
        }

        private void enableNotificationsWithDelay(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, long delay) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                enableNotifications(gatt, characteristic);
            }, delay);
        }


    };


    private void processBatteryLevelData(byte[] data) {
        int batteryLevel = data[0] & 0xFF; // Convert byte to unsigned int
        updateBatteryLevelUI(batteryLevel);
    }

    private void processChargingStatusData(byte[] data) {
        int chargingStatus = data[0] & 0xFF; // 0 for charging, 1 for not charging
        updateChargingStatusUI(chargingStatus);
    }

    private void updateBatteryLevelUI(int batteryLevel) {
        runOnUiThread(() -> {
            TextView batteryLevelText = findViewById(R.id.batteryLevelText);
            batteryLevelText.setText("Bluetooth Battery Level: " + batteryLevel + "%");
        });
    }

    private void updateChargingStatusUI(int chargingStatus) {
        runOnUiThread(() -> {
            TextView chargingStatusText = findViewById(R.id.chargingStatusText);
            String status = (chargingStatus == 0) ? "Charging" : "Not Charging";
            chargingStatusText.setText("Charging Status: " + status);
        });
    }


    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Toast.makeText(this, item.getTitle() + " selected", Toast.LENGTH_SHORT).show();
        drawerLayout.closeDrawers();
        return true;
    }

    private void toggleRecording() {
        isRecording = !isRecording;

        if (isRecording) {
            recordedWaveforms.clear(); // Clear previous recordings
            recordingStatusText.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } else {
            recordingStatusText.setVisibility(View.GONE);
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        }

        recordButton.setText(isRecording ? "Stop Recording" : "Start Recording");
    }

    private void playbackRecording() {
        if (recordedWaveforms.isEmpty()) {
            Toast.makeText(this, "No recording to playback", Toast.LENGTH_SHORT).show();
            return;
        }

        showPlaybackPopup();
    }

    private void showPlaybackPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View popupView = inflater.inflate(R.layout.popup_playback, null);
        builder.setView(popupView);
        AlertDialog dialog = builder.create();
        dialog.show();

        LineChart playbackChart = popupView.findViewById(R.id.playbackChart);
        Button startButton = popupView.findViewById(R.id.startPlaybackButton);
        Button stopButton = popupView.findViewById(R.id.stopPlaybackButton);
        Button pauseButton = popupView.findViewById(R.id.pausePlaybackButton);
        Button closeButton = popupView.findViewById(R.id.closePopupButton);

        final boolean[] isPaused = {false};
        final int[] index = {0};
        Handler playbackHandler = new Handler();

        List<Entry> entries = new ArrayList<>();
        LineDataSet dataSet = new LineDataSet(entries, "Recorded Waveform");
        dataSet.setColor(Color.BLUE);
        dataSet.setDrawCircles(true);
        dataSet.setDrawValues(true);
        LineData lineData = new LineData(dataSet);
        playbackChart.setData(lineData);
        playbackChart.invalidate();

        Runnable playbackRunnable = new Runnable() {
            @Override
            public void run() {
                if (index[0] < recordedWaveforms.size() && !isPaused[0]) {
                    entries.add(new Entry(index[0], recordedWaveforms.get(index[0])));
                    dataSet.notifyDataSetChanged();
                    lineData.notifyDataChanged();
                    playbackChart.notifyDataSetChanged();
                    playbackChart.invalidate();
                    index[0]++;
                    playbackHandler.postDelayed(this, 150); // Slower update to reduce UI lag
                } else {
                    playbackHandler.removeCallbacks(this);
                }
            }
        };

        startButton.setOnClickListener(v -> {
            index[0] = 0;
            entries.clear();
            playbackChart.invalidate();
            playbackHandler.post(playbackRunnable);
        });

        stopButton.setOnClickListener(v -> {
            playbackHandler.removeCallbacks(playbackRunnable);
            index[0] = 0;
        });

        pauseButton.setOnClickListener(v -> {
            isPaused[0] = !isPaused[0];
            if (!isPaused[0]) playbackHandler.post(playbackRunnable);
        });

        closeButton.setOnClickListener(v -> {
            playbackHandler.removeCallbacksAndMessages(null); // ✅ Fix: Remove all callbacks
            dialog.dismiss();
        });
    }


    private void startBatteryMonitoring() {
        BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                batteryStatusText.setText("Battery: " + level + "%");
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void generatePDFReport() {
        try {
            Uri pdfUri = null; // Store the URI for sharing

            // Generate a unique filename with a timestamp
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "waveform_report_" + timeStamp + ".pdf";

            // For Android 10 (API level 29) and later
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                // Insert the new file into MediaStore
                pdfUri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

                if (pdfUri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(pdfUri);
                    PdfWriter writer = new PdfWriter(outputStream);
                    PdfDocument pdf = new PdfDocument(writer);
                    Document document = new Document(pdf);


                    // Add metadata
                    document.add(new Paragraph("Bluetooth Waveform Monitoring Report"));
                    document.add(new Paragraph("Generated on: " + java.time.LocalDateTime.now()));
                    document.add(new Paragraph("Battery Status: " + getBatteryLevel() + "%"));

                    // Add user-defined notes
                    document.add(new Paragraph("Notes: User can add custom notes here."));

                    // Add waveform chart as an image
                    Bitmap chartBitmap = lineChart.getChartBitmap();
                    if (chartBitmap != null) {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                        ImageData imageData = ImageDataFactory.create(stream.toByteArray());
                        com.itextpdf.layout.element.Image chartImage = new com.itextpdf.layout.element.Image(imageData);
                        document.add(chartImage);
                    }

                    // Add waveform data
                    for (Float waveform : recordedWaveforms) {
                        document.add(new Paragraph("Waveform Data: " + waveform));
                    }

                    document.close();
                    Toast.makeText(this, "Report generated and saved to Downloads " + fileName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Failed to create the report", Toast.LENGTH_SHORT).show();
                }
            } else {
                // For older versions of Android (before Android 10)
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);

                PdfWriter writer = new PdfWriter(file);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);

                // Add metadata
                document.add(new Paragraph("Bluetooth Waveform Monitoring Report"));
                document.add(new Paragraph("Generated on: " + java.time.LocalDateTime.now()));
                document.add(new Paragraph("Battery Status: " + getBatteryLevel() + "%"));

                // Add user-defined notes
                document.add(new Paragraph("Notes: User can add custom notes here."));

                // Add waveform chart as an image
                Bitmap chartBitmap = lineChart.getChartBitmap();
                if (chartBitmap != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    ImageData imageData = ImageDataFactory.create(stream.toByteArray());
                    com.itextpdf.layout.element.Image chartImage = new com.itextpdf.layout.element.Image(imageData);
                    document.add(chartImage);
                }

                // Add waveform data
                for (Float waveform : recordedWaveforms) {
                    document.add(new Paragraph("Waveform Data: " + waveform));
                }

                document.close();
                pdfUri = Uri.fromFile(file); // Get URI for sharing
                Toast.makeText(this, "Report generated and saved to Downloads " + fileName, Toast.LENGTH_LONG).show();
            }

            // Share the generated PDF
            if (pdfUri != null) {
                sharePDF(pdfUri);
            }

        } catch (Exception e) {
            Log.e("PDFError", e.getMessage());
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void sharePDF(Uri pdfUri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Allow access to other apps

        startActivity(Intent.createChooser(shareIntent, "Share Report via"));
    }




    private int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void sendFrequencyToDevice(int frequency) {
        if (bluetoothGatt != null && targetService != null && targetCharacteristic != null) {
            targetCharacteristic.setValue(String.valueOf(frequency).getBytes());
            boolean success = bluetoothGatt.writeCharacteristic(targetCharacteristic);
            if (success) {
                Log.d("Bluetooth", "Frequency sent: " + frequency + " Hz");
            } else {
                Log.e("Bluetooth", "Failed to send frequency");
            }
        } else {
            Toast.makeText(this, "No writable characteristic found or device not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateChartRunnable = new Runnable() {
        @Override
        public void run() {
            if (!waveformPoints.isEmpty()) {
                updateWaveformGraph(waveformPoints);
            }
            handler.postDelayed(this, 100); // Update every 100ms
        }
    };

    private void startChartUpdateTimer() {
        handler.post(updateChartRunnable);
    }

    // Declare this at the class level
    private List<Integer> waveformPoints = new ArrayList<>();

    private void processReceivedData(byte[] data) {
        if (data == null || data.length < 2) return;

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int battery = ((buffer.get(12) & 0xFF) | ((buffer.get(13) & 0xFF) << 8));
        int lungActivity = ((buffer.get(14) & 0xFF) | ((buffer.get(15) & 0xFF) << 8));
        int heartRate = buffer.get(15) & 0xFF;



        Log.d("BLE", "Battery: " + battery + "%, Lung Activity: " + lungActivity + ", Heart Rate: " + heartRate);

        // Extract waveform samples
        List<Integer> newWaveformPoints = new ArrayList<>();
        for (int i = 4; i < data.length; i += 2) {
            newWaveformPoints.add((int) buffer.getShort(i)); // Convert to signed 16-bit integers
        }

        // Add new waveform points, keeping only the most recent MAX_DATA_POINTS
        waveformPoints.addAll(newWaveformPoints);

        if (waveformPoints.size() > MAX_DATA_POINTS) {
            waveformPoints = waveformPoints.subList(waveformPoints.size() - MAX_DATA_POINTS, waveformPoints.size());
        }

        if (isRecording) {
            for (int point : newWaveformPoints) {
                recordedWaveforms.add((float) point);
            }
        }

        // Merge the new waveform data with the existing one (keeping MAX_DATA_POINTS)


        // Update UI on the main thread
        runOnUiThread(() -> {
            /*devicebattery(battery);*/
            updateLungActivityDisplay(lungActivity);
            updateWaveformGraph(waveformPoints);  // Pass the updated waveformPoints
            updateHeartRateDisplay(heartRate); // Display correct heart rate

        });
    }



    private void updateHeartRateDisplay(int heartRate) {
        TextView heartRateTextView = findViewById(R.id.heartRateTextView);
        heartRateTextView.setText("Heart Rate: " + heartRate + " bpm");
    }


    private void updateWaveformGraph(List<Integer> waveformPoints) {
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < waveformPoints.size(); i++) {
            entries.add(new Entry(i, waveformPoints.get(i)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Waveform");
        dataSet.setColor(Color.RED);
        dataSet.setDrawCircles(true); // Hide circles for smoother graph
        dataSet.setDrawValues(true); // Hide values for performance

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        // Make the graph auto-scroll with incoming data
        lineChart.setVisibleXRangeMaximum(200);  // Show only last 200 points
        lineChart.moveViewToX(lineData.getEntryCount());

        lineChart.invalidate(); // Refresh chart
    }


    /*private void devicebattery(int battery) {
        TextView heartRateTextView = findViewById(R.id.batteryLevelText);
        heartRateTextView.setText("Battery level: " + battery + " %");
    }*/

    private void updateLungActivityDisplay(int lungActivity) {
        TextView lungActivityTextView = findViewById(R.id.lungActivityTextView);
        lungActivityTextView.setText("Lung Activity: " + lungActivity);
    }


    private void updateRssiDisplay(final int rssi) {
        runOnUiThread(() -> {
            TextView rssiTextView = findViewById(R.id.rssiText); // Ensure you have the TextView with this ID in your layout
            rssiTextView.setText("RSSI: " + rssi); // Update the TextView with the RSSI value
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateChartRunnable);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        } else if (id == R.id.action_permissions) {
            openAppPermissions();
            return true;
        }else if (id == R.id.menu_reset) {
            showResetConfirmationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        String versionName = getAppVersionName();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About")
                .setMessage("This is your Health Connect App.\nVersion " + versionName)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String getAppVersionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "Unknown"; // Fallback in case of an error
        }
    }

    private void openAppPermissions() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }


    private void refreshContent() {
        // Simulate loading data (e.g., refreshing data from the server)
        new Handler().postDelayed(() -> {
            // Stop the refreshing animation
            swipeRefreshLayout.setRefreshing(false);

            // Update UI or data
            Toast.makeText(this, "Content refreshed!", Toast.LENGTH_SHORT).show();
        }, 2000); // Simulate a 2-second refresh delay
    }

    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset App")
                .setMessage("Are you sure you want to reset the app to its default state? This will erase all data.")
                .setPositiveButton("Reset", (dialog, which) -> reset())
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void reset(){
        waveformPoints.clear();
        recordedWaveforms.clear();
        lineChart.clear();
        lineChart.invalidate();

        servicesText = findViewById(R.id.servicesText);
        servicesText.setText("Supported Services:\n");
        TextView rssiText = findViewById(R.id.rssiText);
        rssiText.setText("RSSI: N/A");
        TextView batteryLevelText = findViewById(R.id.batteryLevelText);
        batteryLevelText.setText("Battery Level: N/A");
        TextView heartRateTextView = findViewById(R.id.heartRateTextView);
        heartRateTextView.setText("Heart Rate: -- bpm");
        TextView lungActivityTextView = findViewById(R.id.lungActivityTextView);
        lungActivityTextView.setText("Lung Activity: --");
        TextView batteryStatusText = findViewById(R.id.batteryStatusText);
        batteryStatusText.setText("Battery: --%");
        disconnectDevice();

    }

    //led


    //volume button


    // Method for SeekBar Volume (Independent from buttons)
    // Method to send volume command for buttons
    private void sendVolumeCommand(int command, BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt != null && characteristic != null) {
            byte[] commandBytes = new byte[1];
            commandBytes[0] = (byte) command;

            characteristic.setValue(commandBytes);
            boolean success = bluetoothGatt.writeCharacteristic(characteristic);

            if (success) {
            } else {
            }
        } else {
            Toast.makeText(this, "Device not connected or characteristic not found", Toast.LENGTH_SHORT).show();
        }
    }

    // Use these for button clicks
    public void increaseVolume(SeekBar volumeSeekBar, TextView volumeText) {
        if (currentVolumeLevel < 100) {
            currentVolumeLevel += 20;
            sendVolumeCommand(0, charVolumeUp); // Command for Volume Up
            volumeSeekBar.setProgress(currentVolumeLevel); // Update SeekBar
            volumeText.setText("Volume: " + currentVolumeLevel);
        }
    }

    // Volume Down Button (Decrements by 20)
    public void decreaseVolume(SeekBar volumeSeekBar, TextView volumeText) {
        if (currentVolumeLevel > 0) {
            currentVolumeLevel -= 20;
            sendVolumeCommand(1, charVolumeDown); // Command for Volume Down
            volumeSeekBar.setProgress(currentVolumeLevel); // Update SeekBar
            volumeText.setText("Volume: " + currentVolumeLevel);
        }
    }

    private void sendSeekBarVolume(int volume) {
        if (bluetoothGatt != null && charSeekBar != null) {
            byte[] volumeBytes = new byte[1];
            volumeBytes[0] = (byte) volume; // Send the volume level to the SeekBar characteristic

            charSeekBar.setValue(volumeBytes);
            boolean success = bluetoothGatt.writeCharacteristic(charSeekBar);

            if (success) {
            }
        } else {
        }
    }

    private void sendSetFrequencyCommand() {
        if (bluetoothGatt != null && charSetFrequency != null) {
            byte[] commandBytes = new byte[1];
            commandBytes[0] = (byte) 2; // 2 = Set Frequency

            charSetFrequency.setValue(commandBytes);
            boolean success = bluetoothGatt.writeCharacteristic(charSetFrequency);

            if (success) {

            } else {

            }
        } else {
            Toast.makeText(this, "Device not connected or Set Frequency characteristic not found", Toast.LENGTH_SHORT).show();
        }
    }


    // Assign Bluetooth characteristics after discovering services
    private void assignCharacteristics() {
        if (bluetoothGatt == null) return;

        for (BluetoothGattService service : bluetoothGatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                String uuid = characteristic.getUuid().toString();

                switch (uuid) {
                    case "0000a101-1212-efde-1523-785feabcd123":
                        charVolumeUp = characteristic;
                        break;
                    case "0000a102-1212-efde-1523-785feabcd123":
                        charVolumeDown = characteristic;
                        break;
                    case "0000a103-1212-efde-1523-785feabcd123":
                        charSeekBar = characteristic;
                        break;
                    case "0000a104-1212-efde-1523-785feabcd123":
                        charSetFrequency = characteristic;
                        break;
                }
            }
        }
    }

}