package com.example.blecontroller;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.content.pm.ActivityInfo;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;

public class Controller extends AppCompatActivity {
    private static final String TAG = "ControllerActivity";
    private BluetoothGattCharacteristic characteristic;
    private BluetoothGatt bluetoothGatt;
    private Handler handler = new Handler();
    private boolean isSending = false;
    private char currentCharacter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        // Force the activity to display in landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Get the BluetoothGatt and BluetoothGattCharacteristic from the Singleton
        bluetoothGatt = BluetoothManager.getInstance().getBluetoothGatt();
        characteristic = BluetoothManager.getInstance().getCharacteristic();

        // Set up buttons to handle continuous press
        setButtonTouchListener(R.id.bUp, 'U');
        setButtonTouchListener(R.id.bForward, 'F');
        setButtonTouchListener(R.id.bDown, 'D');
        setButtonTouchListener(R.id.bLeft, 'L');
        setButtonTouchListener(R.id.bRight, 'R');
        setButtonTouchListener(R.id.bBack, 'B');
        setButtonTouchListener(R.id.bCW, 'C');
        setButtonTouchListener(R.id.bCCW, 'W');
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
}