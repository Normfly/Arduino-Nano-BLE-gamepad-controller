# Arduino-Nano-BLE-gamepad-controller

Here is a list of the single characters sent when holding a gamepad button down.
setButtonTouchListener(R.id.bUp, 'U');
setButtonTouchListener(R.id.bForward, 'F');
setButtonTouchListener(R.id.bDown, 'D');
setButtonTouchListener(R.id.bLeft, 'L');
setButtonTouchListener(R.id.bRight, 'R');
setButtonTouchListener(R.id.bBack, 'B');
setButtonTouchListener(R.id.bCW, 'C');
setButtonTouchListener(R.id.bCCW, 'W');


Here is the simple Arduino BLE rev.2 code
The broadcasted BLE name is Nano BLE. This is changeable below.

#include <ArduinoBLE.h>
#include <Arduino.h>
#include <Wire.h>
#include <Arduino_BMI270_BMM150.h>

// BLE Service and Characteristic
BLEService customService("12345678-1234-5678-1234-56789abcdef0"); // Custom service
BLEStringCharacteristic customCharacteristic("87654321-4321-6789-4321-6789abcdef01", BLERead | BLEWrite | BLENotify, 20);

unsigned long previousTime = 0;
float yaw = 0; // Initialize yaw as a global variable
float fZeroRoll = 0;
float fZeroPitch = 0;
bool bFirstRun = true;

// Define pin for motor control
int motorPin[4]; // PWM pin to control speed
int speed = 0;
int motorSpeed[4]; //0-255 PWM to motors
const float lowVoltageThreshold = 40;  // Low voltage cutout threshold in percent
float Kp = 1.0; // Proportional gain

void setup() {
  // Start the hardware serial port
  Serial.begin(9600);

  // Setup BLE
  if (!BLE.begin()) {
    Serial.println("Starting BLE failed!");
    while (1);
  }

  BLE.setLocalName("Nano BLE");
  BLE.setAdvertisedService(customService);
  customService.addCharacteristic(customCharacteristic);
  BLE.addService(customService);
  customCharacteristic.writeValue("Hello World!");

  BLE.advertise();
  Serial.println("Bluetooth device active, waiting for connections...");

  pinMode(A1, INPUT); // Voltage sense

  // Setup PWM pins
  motorPin[0] = 3; // Left front
  motorPin[1] = 5; // Left rear
  motorPin[2] = 6; // Right rear
  motorPin[3] = 9; // Right front
  for (int i = 0; i <= 3; i++) {
    pinMode(motorPin[i], OUTPUT);
  }

  // IMU setup
  Wire.begin();
  if (!IMU.begin()) {
    Serial.println("Failed to initialize IMU!");
    while (1);
  }
  Serial.println("IMU initialized!");
  previousTime = millis();
}

void setMotorSpeeds(int pitch, int roll) {
  speed = constrain(speed, 0, 255); // Constrain speed to valid range

  motorSpeed[0] = constrain(speed + (Kp * roll) + (Kp * pitch), 0, 255); // Left front
  motorSpeed[1] = constrain(speed + (Kp * roll) - (Kp * pitch), 0, 255); // Left rear
  motorSpeed[2] = constrain(speed - (Kp * roll) - (Kp * pitch), 0, 255); // Right rear
  motorSpeed[3] = constrain(speed - (Kp * roll) + (Kp * pitch), 0, 255); // Right front

  for (int i = 0; i <= 3; i++) {
    analogWrite(motorPin[i], motorSpeed[i]);
  }
}

void runCommand(String cCommand, int pitch, int roll) {
  // Process the received command
  if (cCommand == "F") {
    Serial.println("Forward");
  } else if (cCommand == "B") {
    Serial.println("Back");
  } else if (cCommand == "L") {
    Serial.println("Left");
  } else if (cCommand == "R") {
    Serial.println("Right");
  } else if (cCommand == "U") {
    speed = speed + 10;
    Serial.println("Up");
  } else if (cCommand == "D") {
    speed = speed - 10;
    Serial.println("Down");
  } else if (cCommand == "C") {
    Serial.println("Yaw Right");
  } else if (cCommand == "W") {
    Serial.println("Yaw Left");
  }
}

// Main loop
void loop() {
  // IMU stuff
  float ax, ay, az;
  float gx, gy, gz;

  // Read accelerometer data
  if (IMU.accelerationAvailable()) {
    IMU.readAcceleration(ax, ay, az);
  }

  // Read gyroscope data
  if (IMU.gyroscopeAvailable()) {
    IMU.readGyroscope(gx, gy, gz);
  }

  // Normalize accelerometer data
  float norm = sqrt(ax * ax + ay * ay + az * az);
  ax /= norm;
  ay /= norm;
  az /= norm;

  // Calculate roll and pitch (in radians)
  float roll = atan2(ay, az);
  float pitch = atan2(-ax, sqrt(ay * ay + az * az));

  // Get the current time and calculate the time difference
  unsigned long currentTime = millis();
  float deltaTime = (currentTime - previousTime) / 1000.0; // Convert to seconds
  previousTime = currentTime;

  // Integrate gyroscope data to get yaw
  yaw += gz * deltaTime; // Integrate over time to get yaw in radians

  // Convert roll, pitch, and yaw to degrees
  roll = roll * 180.0 / PI;
  pitch = pitch * 180.0 / PI;

  // Normalize yaw to be within 0-360 degrees
  if (yaw < 0) yaw += 360;
  if (yaw >= 360) yaw -= 360;

  // Initialize first time to zero out pitch/roll
  if (isnan(pitch) || isnan(roll) || pitch == -45 || roll == 45) {
    Serial.println("pitch/roll is not initialized yet");
    Serial.print(pitch);
    Serial.print(",");
    Serial.println(roll);
    // Commenting out the IMU.end() call
    // IMU.end();
    delay(100);
    if (!IMU.begin()) {
      Serial.println("Failed to reinitialize IMU!");
      while (1);
    }
    Serial.println("IMU reinitialized!");
  } else if (bFirstRun) {
    bFirstRun = false;
    fZeroPitch = pitch;
    fZeroRoll = roll;
  }

  pitch = pitch - fZeroPitch;
  roll = roll - fZeroRoll;

  Serial.print("Roll: ");
  Serial.print(roll);
  Serial.print(" Pitch: ");
  Serial.print(pitch);
  Serial.print(" Yaw: ");
  Serial.println(yaw);

  delay(10); // Delay to simulate 100Hz update rate

  // Motor stuff
  int sensorValue = analogRead(A1);
  float batteryVoltage = map(sensorValue, 0, 1023, 0, 100);
  Serial.println(batteryVoltage);

  // Check if the battery voltage is below the threshold
  if (batteryVoltage < lowVoltageThreshold) {
    Serial.println("Low voltage! Load disconnected.");
    Serial.print(batteryVoltage);
    Serial.print(" volts.");
  } else { // If low battery, stop BLE and motor control
    // Get characters from BLE
    BLEDevice central = BLE.central();

    if (central) {
      Serial.print("Connected to central: ");
      Serial.println(central.address());

      if (central.connected()) {
        if (customCharacteristic.written()) {
          Serial.print("Characteristic written: ");
          String cCommand = customCharacteristic.value();
          Serial.println(cCommand);

          runCommand(cCommand, pitch, roll); // Command motors
        }

        setMotorSpeeds(pitch, roll);

        // Send pitch and roll values back to the Android device
        String pitchRollData = "P:" + String(pitch, 2) + " R:" + String(roll, 2);
        customCharacteristic.writeValue(pitchRollData);
      }
    } else { // Disconnected from BLE
      speed = 0;
      setMotorSpeeds(0, 0);
      Serial.print("Disconnected from central: ");
      Serial.println(central.address());
    }
  }
}
