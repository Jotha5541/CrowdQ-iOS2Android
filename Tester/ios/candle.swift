import CoreBluetooth

class BluetoothAdvertiser : NSObject, CBPeripheralManagerDelegate {
    private let uuid = CBUUID(string: "ABCD")
    private var peripheralManager: CBPeripheralManager!

    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    var sequence = 2000
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print(peripheral.state)
        switch peripheral.state {
        case .poweredOn:
            print("✅ Bluetooth is ON. Starting advertisement...")
            sequence += 1
            let advertisementData: [String: Any] = [
                CBAdvertisementDataServiceUUIDsKey: [uuid],
                CBAdvertisementDataLocalNameKey: "candleOn"
            ]
            peripheralManager.startAdvertising(advertisementData)
        case .unauthorized:
            print("❌ Error: Bluetooth Permissions denied.")
        case .poweredOff:
            print("❌ Error: Bluetooth is turned off.")
        default:
            print("State updated: \(peripheral.state.rawValue)")
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        print("did start")
        if let error = error {
            print("Advertising failed: \(error.localizedDescription)")
        } else {
            print("🚀 Successfully advertising",uuid)
        }
    }
}



let control = BluetoothAdvertiser()

RunLoop.current.run()
