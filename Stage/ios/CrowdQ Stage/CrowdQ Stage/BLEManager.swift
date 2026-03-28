//
//  BLEManager.swift
//  CrowdQ Stage
//
//  Created by Patrick Miller on 1/17/26.
//

//  The first 3 binary bytes (4 Base64 chars) are tSSSSS (4 bits of tag, 20 bits of sequence)
// For show, the next binary bytes are two bytes of packet number and two bytes of count followed by
//

import Foundation
import CoreBluetooth
import Exchange

public enum MessageType : Int {
    case show = 0
    case command = 1
    case sensor = 2
    //case unused3 = 3
}

class BLEManager : NSObject, CBCentralManagerDelegate, CBPeripheralManagerDelegate {
    public var listening = false
    let sensorUUID : CBUUID
    let broadcastUUID : CBUUID
    let callback : (Data)->Void
    var sequenceNumber = 0
    let serialQueue = OperationQueue()

    var centralManager: CBCentralManager!
    var peripheralManager: CBPeripheralManager!
    init(uuid : CBUUID, sensorUUID : CBUUID, callback:  @escaping (Data)->Void ) {
        broadcastUUID = uuid
        self.sensorUUID = sensorUUID
        self.callback = callback
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
        serialQueue.maxConcurrentOperationCount = 1
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("BLE powered on")
            // Must use 'nil' to see Service Data for short UUID devices on iOS
            centralManager.scanForPeripherals(withServices: nil,
                                            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if peripheral.state == .poweredOn {
            print("Peripheral Ready")
        } else if peripheral.state == .poweredOff {
            print("Peripheral now off")
        }
    }
    
    var lastName : String = ""
    var count = 0
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        // The service on BBBB is for wands, capos, etc...   We may not care unless in demo mode
        if listening,
           let serviceData = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID],
           serviceData == [sensorUUID],
           let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String,
           name != lastName
        {
            // Update the saved name to eliminate duplicates here
            lastName = name
            print(count,name)
            count += 1
            guard let decodedData = Data(base64Encoded: name) else {return}
            callback(decodedData)
        }
    }
    
    var sequence : Int = 0
    func enqueue(_ tag : CrowdQExchangeTag, _ arg : Int, _ payload : String) {
        serialQueue.addOperation {
            let packet = CrowdQExchange(sequence: self.sequence,tag: tag,argument: arg,payload: payload)
            self.sequence += 1
            print(packet)
            print("enqueue",tag,arg,packet.pack())
            
            //let shortUUID = CBUUID(string: "EEEE")
            let adData: [String: Any] = [
                CBAdvertisementDataServiceUUIDsKey: [self.broadcastUUID],
                CBAdvertisementDataLocalNameKey: packet.pack(),
            ]
            print("send",adData)
            self.peripheralManager.stopAdvertising()
            self.peripheralManager.startAdvertising(adData)
        }
    }
}
/*
import Foundation
import CoreBluetooth

class BLEManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    // UPDATED UUIDS
    let wandServiceDataUUID = CBUUID(string: "BBBB")
    let hubServiceUUID       = CBUUID(string: "c4a3b2d1-e0f1-4a2b-8c9d-0e1f2a3b4c5d")
    let hubCharUUID          = CBUUID(string: "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
    
    var centralManager: CBCentralManager!
    var hubPeripheral: CBPeripheral?
    var writeChar: CBCharacteristic?
    var telemetry : ((Data) -> Void)? = nil
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    // ... Boilerplate for didConnect, discoverServices, and discoverCharacteristics ...
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("BLE powered on")
            // Must use 'nil' to see Service Data for 0xBBBB on iOS
            centralManager.scanForPeripherals(withServices: nil,
                                            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        
        // The service on BBBB is for wands, capos, etc...   We may not care unless in demo mode
        if let telemetry = self.telemetry,
           let serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data],
           let wandRawData = serviceData[wandServiceDataUUID] {
            telemetry(wandRawData)
        }

        // --- 2. FILTER FOR PI (GATT SERVER) ---
        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID],
           serviceUUIDs.contains(hubServiceUUID) {
            if hubPeripheral == nil {
                print("Discovered Hub...")
                hubPeripheral = peripheral
                central.connect(peripheral, options: nil)
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connected to Hub!")
        peripheral.delegate = self // CRITICAL: Don't forget this!
        peripheral.discoverServices([hubServiceUUID])
    }
    
    // MARK: - GATT Interaction
    func sendJSON(_ dict: [String: Any]) {
        print("send",dict)
        guard let p = hubPeripheral, let c = writeChar else { return }
        print(p)
        print(c)
        if let data = try? JSONSerialization.data(withJSONObject: dict) {
            print("as",data)
            // Write with Response ensures the Pi acknowledges the packet
            p.writeValue(data, for: c, type: .withResponse)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if characteristic.uuid == hubCharUUID, let data = characteristic.value {
            if let jsonAck = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                print("Received ACK from Pi: \(jsonAck)")
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        print("search services...")
        for service in services where service.uuid == hubServiceUUID {
            print("Service found, discovering characteristics...")
            peripheral.discoverCharacteristics([hubCharUUID], for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        
        for characteristic in characteristics where characteristic.uuid == hubCharUUID {
            print("Characteristic found! Ready to communicate.")
            self.writeChar = characteristic
            
            // Optional: If you want the Pi to send you JSON ACKs, subscribe here:
            peripheral.setNotifyValue(true, for: characteristic)
        }
    }

}
/*
class BLEManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    
    // UUIDs
    let broadcasterUUID = CBUUID(string: "c4a3b2d1-e0f1-4a2b-8c9d-0e1f2a3b4c5d")
    let gattServiceUUID = CBUUID(string: "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e") // Your Pi
    let gattCharUUID    = CBUUID(string: "87654321-4321-8765-4321-876543210987")

    var centralManager: CBCentralManager!
    var gattPeripheral: CBPeripheral?
    var writeChar: CBCharacteristic?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    // MARK: - Scanning
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            // Scan for BOTH types. 
            // Broadcaster must include its UUID in the 'Service UUIDs' field of its advert.
            central.scanForPeripherals(withServices: [broadcasterUUID, gattServiceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        
        // 1. Handle Broadcaster (No connection)
        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID],
           serviceUUIDs.contains(broadcasterUUID) {
            
            // Check for custom data in Manufacturer Data or Service Data
            if let customData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data {
                print("Broadcaster Data: \(customData.hexEncodedString())")
            }
            return // Don't connect to this one
        }

        // 2. Handle GATT Server (Connect)
        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID],
           serviceUUIDs.contains(gattServiceUUID) {
            print("Found GATT Server. Connecting...")
            self.gattPeripheral = peripheral
            central.connect(peripheral, options: nil)
        }
    }

    // MARK: - GATT Communication
    func sendJSON(_ dict: [String: Any]) {
        guard let p = gattPeripheral, let c = writeChar else { return }
        if let data = try? JSONSerialization.data(withJSONObject: dict) {
            // Write and wait for ACK (Response)
            p.writeValue(data, for: c, type: .withResponse)
        }
    }

    // This is where you get the JSON ACK back
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if characteristic.uuid == gattCharUUID, let data = characteristic.value {
            if let jsonAck = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                print("Received ACK from Pi: \(jsonAck)")
            }
        }
    }
}
*/
*/
