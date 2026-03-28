import Foundation
import CoreBluetooth

class PicoListener: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    var centralManager: CBCentralManager!
    var picoPeripheral: CBPeripheral?
    
    // Must match the UUIDs you set in the MicroPython code
    let serviceUUID = CBUUID(string: "12345678-1234-5678-BBBB-567812345678")
    let charUUID    = CBUUID(string: "12345678-1234-5678-BBBB-567812345679")

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    // 1. Start scanning once Bluetooth is on
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("Scanning for Pico...")
            centralManager.scanForPeripherals(withServices: [serviceUUID], options: nil)
        }
    }

    // 2. Found the Pico
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi: NSNumber) {
        print("Found Pico! Connecting...")
        picoPeripheral = peripheral
        picoPeripheral?.delegate = self
        centralManager.stopScan()
        centralManager.connect(peripheral, options: nil)
    }

    // 3. Connected! Now find the service
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connected to \(peripheral.name ?? "Unknown")")
        peripheral.discoverServices([serviceUUID])
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services {
            peripheral.discoverCharacteristics([charUUID], for: service)
        }
    }

    // 4. Found characteristic, now SUBSCRIBE to notifications
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        for characteristic in characteristics where characteristic.uuid == charUUID {
            print("Subscribing to telemetry...")
            peripheral.setNotifyValue(true, for: characteristic)
        }
    }

    // 5. DATA RECEIVED
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        
        // Data is 7 bytes: [3, 0, 1, 0, 2, HighByte, LowByte]
        if data.count >= 7 {
            let bytes = [UInt8](data)
            
            // Extract the 16-bit signed integer (Big Endian)
            // Reconstruct x from indices 5 and 6
            let ax = Int16(bitPattern: (UInt16(bytes[1]) << 8) | UInt16(bytes[2]))
            let ay = Int16(bitPattern: (UInt16(bytes[3]) << 8) | UInt16(bytes[4]))
            let az = Int16(bitPattern: (UInt16(bytes[5]) << 8) | UInt16(bytes[6]))
            
            print("Received Telemetry - Sensor: \(bytes[0]), (\(ax),\(ay),\(az))")
        }
    }
}

// Keep the script running
let listener = PicoListener()
RunLoop.main.run()
