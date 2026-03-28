import CoreBluetooth

class BluetoothAdvertiser : NSObject, CBPeripheralManagerDelegate {
    //let bluetoothQueue = DispatchQueue(label: "icu.luxcedia.bluetooth.queue", qos: .userInitiated)

    private var peripheralManager: CBPeripheralManager!

    override init() {
        super.init()
        rssiQueue.maxConcurrentOperationCount = 1
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    var sequence = 2000
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print(peripheral.state)
        switch peripheral.state {
        case .poweredOn:
            print("✅ Bluetooth is ON. Starting advertisement...")
            startTimer()
        case .unauthorized:
            print("❌ Error: Bluetooth Permissions denied.")
        case .poweredOff:
            print("❌ Error: Bluetooth is turned off.")
        default:
            print("State updated: \(peripheral.state.rawValue)")
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            print("Advertising failed: \(error.localizedDescription)")
        }
    }

    var timer: Timer?
    var n = 0
    var count = 0
    let sensor = 3
    var rssiQueue = OperationQueue()
    func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] timer in
            guard let self = self else { return }
            self.rssiQueue.addOperation {
                let x = Int(1023.0*sin(Double(self.n)/100))
                self.n = (self.n+10)%628

                let ax = x
                let ay = 1
                let az = 2

                let telemetry : [UInt8] = [
                  3,
                  UInt8(truncatingIfNeeded: ax), UInt8(truncatingIfNeeded: ax >> 8), 
                  UInt8(truncatingIfNeeded: ay), UInt8(truncatingIfNeeded: ay >> 8), 
                  UInt8(truncatingIfNeeded: az), UInt8(truncatingIfNeeded: az >> 8), 
                ]
                let name = Data(telemetry).base64EncodedString()
                
                let uuid = CBUUID(string: "BBBB")
                let advertisementData: [String: Any] = [
                  CBAdvertisementDataServiceUUIDsKey: [uuid],
                  CBAdvertisementDataLocalNameKey: name,
                ]
                print(self.count,name)
                self.count += 1
                
                self.peripheralManager.stopAdvertising()
                self.peripheralManager.startAdvertising(advertisementData)
            }
        }
    }
}



let control = BluetoothAdvertiser()
RunLoop.current.run()
