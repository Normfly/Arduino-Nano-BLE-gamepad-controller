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

BLEService customService("12345678-1234-5678-1234-56789abcdef0"); // Custom service
BLEStringCharacteristic customCharacteristic("87654321-4321-6789-4321-6789abcdef01", BLERead | BLEWrite, 20);

void setup() {
  Serial.begin(9600);
  while (!Serial);

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
}

void loop() {
  BLEDevice central = BLE.central();

  if (central) {
    Serial.print("Connected to central: ");
    Serial.println(central.address());

    while (central.connected()) {
      if (customCharacteristic.written()) {
        Serial.print("Characteristic written: ");
        Serial.println(customCharacteristic.value());
      }
    }

    Serial.print("Disconnected from central: ");
    Serial.println(central.address());
  }
}
