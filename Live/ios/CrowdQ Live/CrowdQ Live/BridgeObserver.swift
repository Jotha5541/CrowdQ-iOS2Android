import UIKit
import Foundation
import CoreBluetooth
import Exchange

class BridgeObserver: NSObject, CBCentralManagerDelegate {
    var main : ViewController?
    private var restoredPeripheral: CBPeripheral?
    
    // Make sure we fully handle a packet before doing another
    // since we may get out of order packets from relays
    let serialQueue = OperationQueue()
    
    //let outputUUID    = CBUUID(string: "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")
    //let bridgeUUID = CBUUID(string: "f1e2d3c4-b5a6-4f7e-8d9c-0a1b2c3d4e5f")
    //let phonesUUID = CBUUID(string: "c4a3b2d1-e0f1-4a2b-8c9d-0e1f2a3b4c5d")
    //let broadcastUUID = CBUUID(string: "DDDD")
    let phonesUUID = CBUUID(string: "EEEE")
    
    var centralManager: CBCentralManager!
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
        serialQueue.maxConcurrentOperationCount = 1
    }
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("Observer started. Scanning for Stage or Relay...")
            // allowDuplicates: true is required to see the changing data in real-time
            centralManager.scanForPeripherals(withServices: [phonesUUID],
                                              options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }
    
    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String : Any]) {
        print("BluetoothManager: willRestoreState called")
    }

    func decodeFourChar(_ xxxx : String) -> Int32 {
        // The cluster of 4 base64 chars yeilds 3 bytes.  Prepend a zero
        if let data = Data(base64Encoded:xxxx) {
             let int32Value = (Data([UInt8(0)]) + data).withUnsafeBytes { rawBufferPointer in
                // Ensure the data has at least enough bytes for an Int32
                guard rawBufferPointer.count >= MemoryLayout<Int32>.size else {
                    fatalError("Data is too short to load an Int32")
                }
                return rawBufferPointer.load(as: Int32.self)
            }
            return int32Value
        } else {
            return 0
        }
    }
    
    var lastSequence : Int = -1
    var currentShowName : String?
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        
        // Filter for our Stage UUID from demo mode device
        if let uuids = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            if uuids.contains(phonesUUID),
                      let payload = advertisementData[CBAdvertisementDataLocalNameKey] as? String,
                      let exchange = CrowdQExchange(payload)
            {
                serialQueue.addOperation {
                    if exchange.sequence > self.lastSequence {
                        print(exchange)

                        self.lastSequence = exchange.sequence
                        switch exchange.tag {
                        case .load:
                            if self.currentShowName != exchange.payload {
                                self.main?.loadNewShow(exchange.payload)
                                self.currentShowName = exchange.payload
                            }
                        case .restart:
                            break
                        case .command:
                            self.main?.executeCommand(exchange.payload, exchange.argument)
                        case .show:
                            break
                        case .showdata:
                            break
                        }
                    }
                }
                
            }
        }
    }

    
    private func handleIncomingTelemetry(_ data: Data, from deviceID: UUID) {
        // This is where you convert bytes back into your accelerometer/sensor values
        let hexString = data.map { String(format: "%02x", $0) }.joined()
        print("Received from Bridge [\(deviceID.uuidString.prefix(4))]: \(hexString)")
        
        // Example: If you sent 3-axis data as Int16s
        // let ax = data.subdata(in: 0..<2).withUnsafeBytes { $0.load(as: Int16.self) }
    }
}
