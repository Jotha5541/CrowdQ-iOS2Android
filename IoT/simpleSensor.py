import asyncio
import aioble
import bluetooth
import struct
import math
import machine
LED = machine.Pin("LED")
LED.off()

# Constants
ADXL345_ADDRESS = 0x53 # address for accelerometer 
ADXL345_POWER_CTL = 0x2D # address for power control
ADXL345_DATA_FORMAT = 0x31 # configure data format
ADXL345_DATAX0 = 0x32 # where the x-axis data starts

class Accelerometer:
    # SD0 is pulled to ground, so we use this address
    ADXL345_ADDR = 0x53
    POWER_CTL    = 0x2D
    DATA_FORMAT  = 0x31
    BW_RATE      = 0x2C
    FIFO_CTL     = 0x38

    def __init__(self):
        global ERROR_TAP
        
        # Get I2C0 (pins 1 and 2)
        from machine import I2C,Pin
        self.i2c = i2c = I2C(0, scl=Pin(1), sda=Pin(0))

        try:
            # Set bit 3 to 1 to enable measurement mode
            self.i2c.writeto_mem(ADXL345_ADDRESS, ADXL345_POWER_CTL, bytearray([0x08]))
            # Set data format to 2g resolution (0x0B for 12G)
            self.i2c.writeto_mem(ADXL345_ADDRESS, ADXL345_DATA_FORMAT, bytearray([0x00]))
        except OSError as e:
            ERROR_TAP = 5
            raise RuntimeError("I2C setup problem") from e
        return

    def raw(self):
        try:
            return self.i2c.readfrom_mem(ADXL345_ADDRESS, ADXL345_DATAX0, 6)
        except OSError as e:
            ERROR_TAP = 6
            raise RuntimeError("I2C read error") from e

    def read(self):
        data = self.raw()
        x, y, z = struct.unpack('<3h', data)
        return x, y, z

try:
    A = Accelerometer()
except:
    A = None
print("Accelerometer",A)

# 1. Define UUIDs (Generate your own for custom services)
_SERVICE_UUID = bluetooth.UUID("12345678-1234-5678-BBBB-567812345678")
_CHAR_UUID = bluetooth.UUID("12345678-1234-5678-BBBB-567812345679")

# 2. Register GATT Server
my_service = aioble.Service(_SERVICE_UUID)
# 'notify=True' allows us to push data without the client asking
my_characteristic = aioble.Characteristic(my_service, _CHAR_UUID, read=True, notify=True)
aioble.register_services(my_service)

async def peripheral_task():
    while True:
        print("Advertising...")
        async with await aioble.advertise(
            250_000, # 250ms interval
            name="Telemetry",
            services=[_SERVICE_UUID],
        ) as connection:
            print("Connection from", connection.device)
            LED.on()
            n = 0
            sensorID = 3  # We'll read from a config here later
            while connection.is_connected():
                # On a pico without an accelerometer, we'll just create a fake sine-wave of left right
                if A is None:
                    # Generate 'x' value (sine wave)
                    ax = int(511.0 * math.sin(n / 100))
                    ay = 0
                    az = 0
                    n = (n + 10) % 628
                else:
                    ax,ay,az = A.read()
                    
                # 3. Pack the 7-byte data
                # 'b' = signed char (1 byte), 'h' = signed short (2 bytes)
                # Structure: [SensorID, 0, 1, 0, 2, HighByte, LowByte]
                # Note: 'x' is 2 bytes, so we use '>h' for Big Endian
                payload = struct.pack(">bhhh", sensorID, ax,ay,az)
                
                # 4. Write and Notify
                my_characteristic.write(payload)
                my_characteristic.notify(connection)
                
                print(f"Sent: {list(payload)} ({ax},{ay},{az})")
                await asyncio.sleep_ms(25)
            
            print("Disconnected")
            LED.off()

async def main():
    await peripheral_task()

asyncio.run(main())
