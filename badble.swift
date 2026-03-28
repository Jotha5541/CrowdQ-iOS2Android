import Foundation
import CoreBluetooth

class CrowdQClient: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    
    let serviceUUID = CBUUID(string: "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    let rxUUID      = CBUUID(string: "9c858901-8a57-4791-81fe-4c455b099bc9")
    let txUUID      = CBUUID(string: "6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    private var centralManager: CBCentralManager!
    private var targetPeripheral: CBPeripheral?
    
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }
    
    @objc private func appWillEnterForeground() {
        reconnectIfNeeded()
    }
    
    private func reconnectIfNeeded() {
        guard let peripheral = targetPeripheral else {
            // not discovered yet
            centralManager.scanForPeripherals(withServices: [serviceUUID], options: nil)
            return
        }

        if peripheral.state == .disconnected {
            print("Reconnecting to Pi...")
            centralManager.connect(peripheral, options: nil)
        }
    }


    // MARK: - Public API
    
    func sendJSON(_ dictionary: [String: Any]) {
        guard let peripheral = targetPeripheral,
              let characteristic = writeCharacteristic,
              peripheral.state == .connected else {
            print("Not connected to Pi yet.")
            return
        }
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: dictionary, options: [])
            peripheral.writeValue(jsonData, for: characteristic, type: .withoutResponse)
            print("Sent JSON:", dictionary)
        } catch {
            print("Failed to serialize JSON:", error)
        }
    }

    // MARK: - Central Manager Delegates
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("Scanning for CrowdQ-Hub...")
            central.scanForPeripherals(withServices: [serviceUUID], options: nil)
        }
    }
    
    func centralManager(_ central: CBCentralManager,
                        willRestoreState dict: [String : Any]) {

        if let peripherals =
            dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {

            for peripheral in peripherals {
                targetPeripheral = peripheral
                peripheral.delegate = self
                central.connect(peripheral, options: nil)
            }
        }
    }
    
    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String : Any],
                        rssi RSSI: NSNumber) {

        print("Discovered Pi:", peripheral.name ?? "Unknown")
        targetPeripheral = peripheral
        central.stopScan()
        central.connect(peripheral, options: nil)
    }
    
    func centralManager(_ central: CBCentralManager,
                        didConnect peripheral: CBPeripheral) {

        print("Connected!")
        targetPeripheral = peripheral
        peripheral.delegate = self
        peripheral.discoverServices([serviceUUID])
    }
    
    func centralManager(_ central: CBCentralManager,
                        didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {

        print("Disconnected from Pi")
        writeCharacteristic = nil
        notifyCharacteristic = nil
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            // maybe disable button until we connect?
            print("try reconnect")
            self.centralManager.connect(peripheral, options: nil)
        }

    }

    // MARK: - Peripheral Delegates
    
    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverServices error: Error?) {

        guard let services = peripheral.services else { return }

        for service in services where service.uuid == serviceUUID {
            peripheral.discoverCharacteristics([rxUUID, txUUID], for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {

        guard let characteristics = service.characteristics else { return }

        for characteristic in characteristics {
            switch characteristic.uuid {
            case rxUUID:
                writeCharacteristic = characteristic
                print("RX ready (write)")
                
            case txUUID:
                notifyCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
                print("Subscribed to TX notify")
                
            default:
                break
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {

        guard error == nil else {
            print("Notify error:", error!)
            return
        }

        guard let data = characteristic.value else { return }

        // Only handle notifications from TX characteristic
        guard characteristic.uuid == txUUID else { return }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            print("Heartbeat:", json)
        }
    }
}
