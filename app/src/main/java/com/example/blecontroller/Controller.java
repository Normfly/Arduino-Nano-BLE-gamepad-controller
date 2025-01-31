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
import androidx.appcompat.app.AppCompatActivity;

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
    private TextView textView;  // Add a TextView reference
    private Button bStartStop;
    private boolean bStop = true;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
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
                        setupCharacteristicNotification(gatt, characteristic); // Enable notifications
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
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        // Force the activity to display in landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Get the TextView reference
        textView = findViewById(R.id.textView);

        // Get the Start/Stop button reference
        bStartStop = findViewById(R.id.bStartStop);

        // Set up buttons to handle continuous press
        setButtonTouchListener(R.id.bUp, 'U');
        setButtonTouchListener(R.id.bForward, 'F');
        setButtonTouchListener(R.id.bBack, 'B');
        setButtonTouchListener(R.id.bDown, 'D');
        setButtonTouchListener(R.id.bLeft, 'L');
        setButtonTouchListener(R.id.bRight, 'R');
        setButtonTouchListener(R.id.bStartStop, 'S');
        setButtonTouchListener(R.id.bCW, 'C');
        setButtonTouchListener(R.id.bCCW, 'W');

        // Get the Bluetooth device address from the Intent
        Intent intent = getIntent();
        String deviceAddress = intent.getStringExtra("DEVICE_ADDRESS");
        if (deviceAddress != null) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (device != null) {
                bluetoothGatt = device.connectGatt(this, false, gattCallback);
            } else {
                Log.e(TAG, "Failed to get BluetoothDevice from address");
            }
        } else {
            Log.e(TAG, "No device address passed to Controller");
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // Method to set touch listener for each button
    private void setButtonTouchListener(int buttonId, final char character) {
        Button button = findViewById(buttonId);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startSendingCharacter(character);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (character == 'S') {
                            if (bStop) {
                                bStartStop.setText("Start");
                            } else {
                                bStartStop.setText("Stop");
                            };
                            bStop = !bStop;
                            startSendingCharacter(character);
                        };
                    case MotionEvent.ACTION_CANCEL:
                        stopSendingCharacter();
                        return true;
                }
                return false;
            }
        });
    }

    // Start sending the character continuously
    private void startSendingCharacter(final char character) {
        if (!isSending) {
            isSending = true;
            currentCharacter = character;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (isSending && currentCharacter == character) {
                        writeSingleCharacter(character);
                        handler.postDelayed(this, 100); // Adjust the delay as needed
                    }
                }
            });
        }
    }

    // Stop sending the character
    private void stopSendingCharacter() {
        isSending = false;
        handler.removeCallbacksAndMessages(null);
    }

    // Method to write a single character to the BLE characteristic
    private void writeSingleCharacter(char character) {
        if (characteristic != null) {
            // Set the value of the characteristic to the character
            characteristic.setValue(String.valueOf(character));
            // Write the characteristic
            boolean success = bluetoothGatt.writeCharacteristic(characteristic);
            if (success) {
                Log.d(TAG, "Successfully wrote character to characteristic");
            } else {
                Log.e(TAG, "Failed to write character to characteristic");
            }
        } else {
            Log.e(TAG, "Characteristic is null.");
        }
    }

    // Method to set up characteristic notification
    private void setupCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }
    }

    // Method to process received data
    private void processReceivedData(BluetoothGattCharacteristic characteristic) {
        String receivedString = characteristic.getStringValue(0);
        Log.d(TAG, "Received data: " + receivedString);
        // Handle the received string as needed, for example, update the UI or send a notification
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(receivedString);  // Update the TextView with the received string
            }
        });
    }
}