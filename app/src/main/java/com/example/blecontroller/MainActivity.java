package com.example.blecontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.blecontroller.BluetoothManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLEController";
    private static final int REQUEST_PERMISSIONS = 1;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-6789-4321-6789abcdef01");
    private SwipeRefreshLayout swipeRefreshLayout;

    private List<String> deviceNameList = new ArrayList<>(); // List to store device names
    private List<BluetoothDevice> bluetoothDeviceList = new ArrayList<>(); // List to store BluetoothDevice objects
    private ArrayAdapter<String> deviceListAdapter; // Adapter for ListView
    private ListView deviceListView; // ListView to display device names

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.");
            return;
        }

        // Initialize SwipeRefreshLayout and ListView
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        deviceListView = findViewById(R.id.device_list_view);
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNameList);
        deviceListView.setAdapter(deviceListAdapter);

        // Set an item click listener on the ListView
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            connectToDevice(bluetoothDeviceList.get(position));
            deviceListView.setEnabled(false); // Disable the ListView
        });

        // Set the refresh listener on the SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this::refreshDeviceList);

        // Request necessary permissions
        requestPermissions();
    }

    private void refreshDeviceList() {
        // Clear the current device lists
        deviceNameList.clear();
        bluetoothDeviceList.clear();
        deviceListAdapter.notifyDataSetChanged();

        // Start a new BLE scan
        startBleScan();

        // Stop the refreshing animation
        swipeRefreshLayout.setRefreshing(false);
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS);
        } else {
            // Permissions already granted, proceed with your logic
            startBleScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // Permissions granted, proceed with your logic
                startBleScan();
            } else {
                Log.e(TAG, "Permission denied.");
            }
        }
    }

    private void startBleScan() {
        // Enable Bluetooth if it's not already enabled
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Requesting user to enable Bluetooth.");
            bluetoothAdapter.enable();
        }

        // Get BluetoothLeScanner
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Failed to get BluetoothLeScanner.");
            return;
        }

        Log.d(TAG, "Starting BLE scan.");
        bluetoothLeScanner.startScan(leScanCallback);
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            if (deviceName != null && !deviceNameList.contains(deviceName)) {
                deviceNameList.add(deviceName);
                bluetoothDeviceList.add(device); // Store the BluetoothDevice object
                deviceListAdapter.notifyDataSetChanged(); // Update the ListView
                Log.d(TAG, "Found device: " + deviceName);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
        }
    };

    // Method to connect to a Bluetooth device
    private void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "BluetoothDevice is null.");
            return;
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        Log.d(TAG, "Connecting to device: " + device.getName());
    }

    // GATT callback methods
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        BluetoothManager.getInstance().setBluetoothGatt(gatt);
                        BluetoothManager.getInstance().setCharacteristic(characteristic);
                        startControllerActivity(gatt.getDevice()); // Pass the BluetoothDevice here
                    } else {
                        Log.e(TAG, "Characteristic not found.");
                    }
                } else {
                    Log.e(TAG, "Service not found.");
                }
            } else {
                Log.e(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    final String value = characteristic.getStringValue(0);
                    runOnUiThread(() -> Log.d(TAG, "Characteristic value: " + value));
                }
            } else {
                Log.e(TAG, "Characteristic read failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic written successfully");
            } else {
                Log.e(TAG, "Characteristic write failed with status: " + status);
            }
        }
    };

    // After successful connection, start ControllerActivity
    private void startControllerActivity(BluetoothDevice device) {
        Intent intent = new Intent(this, Controller.class);
        intent.putExtra("DEVICE_ADDRESS", device.getAddress());
        startActivity(intent);
    }
}