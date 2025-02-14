package com.example.blecontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class Controller extends AppCompatActivity {
    private static final String TAG = "ControllerActivity";
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-6789-4321-6789abcdef01");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic characteristic;
    private BluetoothGatt bluetoothGatt;
    private Handler handler = new Handler();
    private boolean isSending = false;
    private char currentCharacter;
    private TextView textView;
    private Button bStartStop;
    private boolean bStop = true;
    private String deviceAddress;

    private Queue<Character> writeQueue = new LinkedList<>();
    private boolean isWriting = false;
    private int writeFailureCount = 0;
    private static final int MAX_WRITE_FAILURES = 3;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                runOnUiThread(() -> {
                    textView.setText("BLE disconnected");
                    attemptReconnect();
                });
                cleanup();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        BluetoothManager.getInstance().setBluetoothGatt(gatt);
                        BluetoothManager.getInstance().setCharacteristic(characteristic);
                        setupCharacteristicNotification(gatt, characteristic);
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
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            processReceivedData(characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful");
                writeFailureCount = 0; // Reset failure count on success
                processNextWrite();
            } else {
                Log.e(TAG, "Characteristic write failed, status: " + status);
                writeFailureCount++;
                if (writeFailureCount >= MAX_WRITE_FAILURES) {
                    attemptReconnect();
                } else {
                    processNextWrite();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        textView = findViewById(R.id.textView);
        bStartStop = findViewById(R.id.bStartStop);

        setButtonTouchListener(R.id.bUp, 'U');
        setButtonTouchListener(R.id.bForward, 'F');
        setButtonTouchListener(R.id.bBack, 'B');
        setButtonTouchListener(R.id.bDown, 'D');
        setButtonTouchListener(R.id.bLeft, 'L');
        setButtonTouchListener(R.id.bRight, 'R');
        setButtonTouchListener(R.id.bStartStop, 'S');
        setButtonTouchListener(R.id.bCW, 'C');
        setButtonTouchListener(R.id.bCCW, 'W');

        Intent intent = getIntent();
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS");
        if (deviceAddress != null) {
            connectToDevice(deviceAddress);
        } else {
            Log.e(TAG, "No device address passed to Controller");
        }
    }

    @Override
    public void onBackPressed() {
        cleanup();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void setButtonTouchListener(int buttonId, final char character) {
        Button button = findViewById(buttonId);
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startSendingCharacter(character);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (character == 'S') {
                        bStartStop.setText(bStop ? "Start" : "Stop");
                        bStop = !bStop;
                        startSendingCharacter(character);
                    }
                case MotionEvent.ACTION_CANCEL:
                    stopSendingCharacter();
                    return true;
            }
            return false;
        });
    }

    private void startSendingCharacter(final char character) {
        if (!isSending) {
            isSending = true;
            currentCharacter = character;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (isSending && currentCharacter == character) {
                        queueWriteCharacter(character);
                        handler.postDelayed(this, 100);
                    }
                }
            });
        }
    }

    private void stopSendingCharacter() {
        isSending = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void queueWriteCharacter(char character) {
        writeQueue.add(character);
        if (!isWriting) {
            processNextWrite();
        }
    }

    private void processNextWrite() {
        if (!writeQueue.isEmpty()) {
            char character = writeQueue.poll();
            writeSingleCharacter(character);
        } else {
            isWriting = false;
        }
    }

    private void writeSingleCharacter(char character) {
        if (characteristic != null && bluetoothGatt != null) {
            isWriting = true;
            characteristic.setValue(String.valueOf(character));
            boolean success = bluetoothGatt.writeCharacteristic(characteristic);
            if (success) {
                Log.d(TAG, "Successfully wrote character to characteristic");
            } else {
                Log.e(TAG, "Failed to write character to characteristic");
                processNextWrite();
            }
        } else {
            Log.e(TAG, "Characteristic or BluetoothGatt is null.");
            processNextWrite();
        }
    }

    private void setupCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }
    }

    private void processReceivedData(BluetoothGattCharacteristic characteristic) {
        String receivedString = characteristic.getStringValue(0);
        Log.d(TAG, "Received data: " + receivedString);
        runOnUiThread(() -> textView.setText(receivedString));
    }

    private void attemptReconnect() {
        runOnUiThread(() -> Toast.makeText(Controller.this, "Attempting to reconnect to BLE device...", Toast.LENGTH_SHORT).show());
        handler.postDelayed(() -> {
            if (deviceAddress != null) {
                Log.i(TAG, "Attempting to reconnect to BLE device...");
                connectToDevice(deviceAddress);
            }
        }, 5000);
    }

    private void connectToDevice(String address) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device != null) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        } else {
            Log.e(TAG, "Failed to get BluetoothDevice from address");
        }
    }

    private void cleanup() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isSending = false;
        handler.removeCallbacksAndMessages(null);
        writeQueue.clear();
        isWriting = false;
    }
}