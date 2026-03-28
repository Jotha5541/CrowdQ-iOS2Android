import asyncio
import aioble
import bluetooth
import json
import machine
import network
import socket
import struct
import sys
import ubinascii
import utime
from micropython import const

ERROR_TAP = 1
MAC = ubinascii.hexlify(network.WLAN().config('mac'),':').replace(b':',b'').decode('utf8')
print("PICO running on MAC",MAC)

LED = machine.Pin('LED')
LED.off()

# We will build a config dict from the abreviated BLE LX: messages
# we detect from our config device.
short2long = {'p':'passwd', 's':'ssid', 'w':'wand', 'm':'mac','@':'class','u':'units'}
config = {}

class Pico:
    ssid = None
    passwd = None
    def __init__(self,**config):
        for key,value in config.items():
            setattr(self,key,value)
        return

    def wifi(self):
        global ERROR_TAP
        
        print('connect to',self.ssid,'with',self.passwd)
        WLAN = network.WLAN(network.STA_IF)
        WLAN.disconnect()
        WLAN.active(True)

        # Wait until connected
        assert isinstance(self.ssid,str)
        assert isinstance(self.passwd,str)
        WLAN.connect(self.ssid,self.passwd)
        while not WLAN.isconnected():
            LED.on()
            utime.sleep(.1)
            print("Waiting for connection...",self.ssid,self.passwd)
            status = WLAN.status()
            print('status',status)
            if status == network.STAT_GOT_IP:
                print('got ip')
                break
            elif status == network.STAT_IDLE:
                print('idle',self.ssid,self.passwd)
            elif status == network.STAT_WRONG_PASSWORD:
                ERROR_TAP = 2
                raise RuntimeError('password')
            elif status == network.STAT_NO_AP_FOUND:
                ERROR_TAP = 3
                raise RuntimeError('no such AP')
            elif status == network.STAT_CONNECT_FAIL or status < 0:
                ERROR_TAP = 4
                raise RuntimeError('connection failure')
            LED.off()
            utime.sleep(.4)
        LED.off()
        return

_ADV_TYPE_FLAGS = const(0x01)    
_ADV_TYPE_NAME = const(0x09)
ble = bluetooth.BLE()
class Beacon(Pico):
    async def work(self):
        ble.active(True)
        name = f'pico-{MAC}'.encode('utf-8')

        # Name header: length + type
        payload = bytearray()
        payload.extend(struct.pack("BB", len(name) + 1, _ADV_TYPE_NAME))

        BLE_FLAGS = 0x01
        BR_EDR = 0x06 # This is to use BLE vs the EDR mode
        BLE_NAME = 0x09
        payload = struct.pack("BBBBB",2,BLE_FLAGS,BR_EDR,len(name)+1,BLE_NAME) + name

        # Slow flash sending at 10 hz
        print(f"Advertising '{name}' at 10Hz...")
        while True:
            LED.toggle()
            for i in range(10):
                # Change a byte in the packet to prompt subscribers to report a new one
                ble.gap_advertise(160, adv_data=payload+bytearray([i]), connectable=False)
                await asyncio.sleep_ms(100)
        return
    
class Wand(Pico):
    def __init__(self,**config):
        super().__init__(**config)
        self.wifi()
        return

    async def work(self):
        A = Accelerometer()

        WLAN = network.WLAN(network.STA_IF)
        ip_str, subnet_str, gateway, dns = WLAN.ifconfig()
        print(WLAN.ifconfig())

        def ip_to_bytes(ip):
            return bytes(map(int, ip.split('.')))

        def bytes_to_ip(b):
            return '.'.join(str(x) for x in b)

        ip_bytes = ip_to_bytes(ip_str)
        subnet_bytes = ip_to_bytes(subnet_str)

        # broadcast = ip | (~netmask)
        broadcast_bytes = bytes(ip_bytes[i] | (~subnet_bytes[i] & 0xFF) for i in range(4))
        broadcast_addr = bytes_to_ip(broadcast_bytes)
        print("Broadcast address:", broadcast_addr)

        # Set up for UDP broadcast
        udp_port = 12345
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

        # Read accelerometer at 50 Hz (and allow async)
        wand = int(self.wand)
        while True:
            LED.toggle()
            for _ in range(50):
                assert 0 <= wand < 100
                s.sendto(b'%02d'%wand + A.raw(),(broadcast_addr,udp_port))
                await asyncio.sleep_ms(20)
        return

def send_ack():
    ble.active(True)

    # 1. Prepare the Payload
    # "ACK:abcdefghi"
    name = "ACK:"+MAC
    payload = bytearray()
    
    # Add Flags (General Discoverable Mode)
    payload.extend(struct.pack("BB", 2, _ADV_TYPE_FLAGS))
    payload.append(0x06)
    
    # Add Local Name
    payload.extend(struct.pack("BB", len(name) + 1, _ADV_TYPE_NAME))
    payload.extend(name.encode('utf-8'))

    # 2. Set Frequency (Twice a second = 500ms interval)
    # Units are 0.625ms: 500 / 0.625 = 800
    interval_us = 800 

    print("Starting ACK broadcast...")
    
    # 3. Start Advertising
    ble.gap_advertise(interval_us, adv_data=payload, connectable=False)

    # 4. Duration Logic (3 seconds)
    time.sleep(3)

    # 5. Stop Advertising
    ble.gap_advertise(None)
    print("ACK Broadcast finished.")
    
    ble.active(False)
    return

def send_ack():
    ble.active(True)

    # 1. Prepare the Payload
    # "ACK:abcdefghi"
    name = "ACK:"+MAC
    payload = bytearray()
    
    # Add Flags (General Discoverable Mode)
    payload.extend(struct.pack("BB", 2, _ADV_TYPE_FLAGS))
    payload.append(0x06)
    
    # Add Local Name
    payload.extend(struct.pack("BB", len(name) + 1, _ADV_TYPE_NAME))
    payload.extend(name.encode('utf-8'))

    # 2. Set Frequency (Twice a second = 500ms interval)
    # Units are 0.625ms: 500 / 0.625 = 800
    interval_us = 800 

    print("Starting ACK broadcast...")
    
    # 3. Start Advertising
    ble.gap_advertise(interval_us, adv_data=payload, connectable=False)

    # 4. Duration Logic (3 seconds)
    utime.sleep(3)

    # 5. Stop Advertising
    ble.gap_advertise(None)
    print("ACK Broadcast finished.")
    
    ble.active(False)
    return

async def config_task():
    print("Scanning for LuxConfig for",MAC)
    LED.on()
    # Scan for 5 seconds
    async with aioble.scan(duration_ms=5000, interval_us=30000, window_us=30000, active=True) as scanner:
        async for result in scanner:
            # Extract data from the advertisement
            addr = result.device.addr_hex()
            name = result.name() or "Unknown"
            if not name.startswith("LX:"): continue
            rssi = result.rssi
            
            # Store in our "background" database
            payload = name[3:]
            key = short2long.get(payload[:1])
            if key is not None:
                config[key] = payload[1:]
            
            # Optional: Short sleep to keep the main loop responsive
            await asyncio.sleep_ms(1)

    LED.off()
    return

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
        x, y, z = ustruct.unpack('<3h', data)
        return x, y, z

async def main():
    # Maybe we are getting new config info
    await config_task()
    
    # If we got a new config, we use it otherwise we use the saved one
    global config
    if config and config.get('mac') in (MAC,'*'):
        print("We have a new config",config)
        json.dump(config,open('config.json','w'))
        send_ack()
    else:
        try:
            f = open('config.json')
        except OSError:
            print("no config file and not running LuxConfig")
            raise
        config = json.load(f)
        print(config)

    # This is a universal main.py, so pick our class and run it
    operation = globals()[config.get('class')]
    assert isinstance(operation,type) and issubclass(operation,Pico)
    operator = operation(**config)
    
    # Now do the real work (which may or may not finish)
    await operator.work()

    while True:
        print("alive")
        await asyncio.sleep(5)  # Update the console every 5 seconds

try:
    asyncio.run(main())
except Exception as e:
    sys.print_exception(e)
    while True:
        for _ in range(ERROR_TAP):
            LED.on()
            utime.sleep(.1)
            LED.off()
            utime.sleep(.2)
        utime.sleep(2)
