//
//  ViewController.swift
//  XYBlue
//
//  Created by Patrick Miller on 2/16/26.
//

import UIKit
import CoreBluetooth

let oneTimeMessage = """
Please carefully enter your row letter and seat number.  Once you do, you'll see other cooperating devices' RSSI signal strength values.  Once the first start time is reached (there's a red countdown), you'll see a green count up for that test
"""
enum State : String {
    case startUp = "Waiting on Bluetooth"
    case waitForOthers = "Waiting on other devices"
    case waitForProject = "Waiting for project"
    case waitForStart = "Waiting for start time"
    case recording = "Recording"
}

struct Project : Decodable {
    let name : String
    let start : String
    let duration : Int
}

struct Record : Encodable {
    let timestamp : Double
    let capture : String
    let source : String
    let rssi : Int
    
    init(_ timestamp : Double = 0.0, _ capture : String = "", _ source : String = "", _ rssi : Int = -127) {
        self.timestamp = timestamp
        self.capture = capture
        self.source = source
        self.rssi = rssi
    }
}

class ViewController: UIViewController {
    var alert : UIAlertController? = nil

    var background = UIImageView()
    var centralManager: CBCentralManager!
    var peripheralManager: CBPeripheralManager!
    let bluetoothQueue = DispatchQueue(label: "icu.luxcedia.bluetooth.queue", qos: .userInitiated)
    var rssiQueue = OperationQueue()
    var timer: Timer?
    var clockTimer: Timer?
    var isFetching : Bool = false
    var state : State = .startUp
    var startTime : Date? = nil
    var project : Project? = nil
    var projects : [Project] = []
    
    let serviceUUID = CBUUID(string: "ABCD")
    var service : CBService? = nil
    var row : String? = nil
    var seat : String? = nil
    
    var readings : [String:NSNumber] = [:]
    var buffer : [Record]? = nil
    
    let infoLabel: UILabel = {
        let label = UILabel()
        label.font = UIFont.systemFont(ofSize: 30)
        label.textAlignment = .center
        label.text = State.startUp.rawValue
        label.translatesAutoresizingMaskIntoConstraints = false
        label.backgroundColor = .secondarySystemBackground
        label.textColor = UIColor.label
        return label
    }()
    
    let clockLabel: UILabel = {
        let label = UILabel()
        label.font = UIFont.systemFont(ofSize: 30)
        label.textAlignment = .center
        label.text = ""
        label.translatesAutoresizingMaskIntoConstraints = false
        label.backgroundColor = .secondarySystemBackground
        label.textColor = UIColor.label
        return label
    }()
    
    let projectLabel: UILabel = {
        let label = UILabel()
        label.font = UIFont.systemFont(ofSize: 25)
        label.textAlignment = .center
        label.text = "no project selected"
        label.translatesAutoresizingMaskIntoConstraints = false
        label.backgroundColor = .secondarySystemBackground
        label.textColor = UIColor.label
        return label
    }()
    
    let rowSelect: UIButton = {
        var config = UIButton.Configuration.filled()
        config.title = " -- row -- "
        let button = UIButton(configuration: config)
        button.showsMenuAsPrimaryAction = true
        return button
    }()
    
    let seatSelect: UIButton = {
        var config = UIButton.Configuration.filled()
        config.title = " -- seat -- "
        let button = UIButton(configuration: config)
        button.showsMenuAsPrimaryAction = true
        return button
    }()
    
    var textView : UITextView = {
        let tv = UITextView()
        tv.font = UIFont.systemFont(ofSize: 20)
        tv.layer.borderColor = UIColor.lightGray.cgColor
        tv.layer.borderWidth = 1.0
        tv.translatesAutoresizingMaskIntoConstraints = false
        tv.font = .preferredFont(forTextStyle: .body)
        tv.isEditable = false // Makes it uneditable
        tv.isScrollEnabled = true // Enable this so we can see more than 150pt of text
        tv.isUserInteractionEnabled = true // Required for scrolling even if not editable
        tv.backgroundColor = .secondarySystemBackground
        tv.textColor = UIColor.label
        tv.isOpaque = true
        tv.text = ""
        return tv
    }()
    
    var reloadButton : UIButton = {
        var config = UIButton.Configuration.filled()
        config.title = "Reload projects"
        let button = UIButton(configuration: config)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 20)
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()
    
    @objc func reload() {
        print("reload")
        changeState(.waitForStart)
        Task {
            await fetchProjectData()
        }
    }
        
    override func viewDidLoad() {
        super.viewDidLoad()
        reloadButton.addTarget(self, action: #selector(reload), for: .touchUpInside)

        changeState(.startUp)
        
        // No uncommanded sleep
        UIApplication.shared.isIdleTimerDisabled = true
        
        // Want to fully process to avoid races
        rssiQueue.maxConcurrentOperationCount = 1
        
        // Load some project data
        Task {
            await fetchProjectData()
        }
        
        // Some instructions to keep the Apple guys happy
        // We give some one-time instructions... partially to help with App Store acceptance
        let flagged = UserDefaults.standard.bool(forKey: "flagged")
        if !flagged {
            UserDefaults.standard.set(true, forKey: "flagged")
            DispatchQueue.main.async {
                // Pop down the old message
                self.alert?.dismiss(animated: false)
                
                self.alert = UIAlertController(title: nil, message: oneTimeMessage, preferredStyle: .alert)
                self.alert?.addAction(UIAlertAction(title: "Dismiss", style: .default, handler: nil))
                
                if let alert = self.alert {
                    self.present(alert, animated: true, completion: nil)
                }
            }
        }
        
        clockTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            guard let self = self else { return }
            
            // We need a project and start-time to show anything here
            guard let startTime = startTime, let project = project else {
                return
            }
            let difference = Int(Date().timeIntervalSince(startTime))
            if difference < 0 {
                // We have to wait until we start... use red text
                changeState(.waitForStart)
                DispatchQueue.main.async {
                    self.clockLabel.textColor = .red
                    self.clockLabel.text = "\(difference)"
                }
            } else if difference > project.duration {
                print("stop",difference,"count",buffer?.count)
                
                if let records = self.buffer, let json = prepareJsonData(from: records),
                   let url = URL(string: "https://luxcedia.icu/cgi-bin/uploadjson"),
                   let row = row,
                   let seat = seat
                {
                    let boundary = "Boundary-\(UUID().uuidString)"
                    var request = URLRequest(url: url)
                    request.httpMethod = "POST"
                    request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
                    
                    // 3. Build the body
                    var body = Data()
                    let fileName = "\(project.name)_\(row)_\(seat).json"
                    
                    body.append("--\(boundary)\r\n".data(using: .utf8)!)
                    // This 'name="file"' must match the "file" key in your Python script
                    body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
                    body.append("Content-Type: application/json\r\n\r\n".data(using: .utf8)!)
                    body.append(json)
                    body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
                    
                    request.httpBody = body
                    
                    Task {
                        do {
                            let (data, response) = try await URLSession.shared.data(for: request)
                            
                            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                                let responseString = String(data: data, encoding: .utf8)
                                print("Server Response: \(responseString ?? "No response body")")
                            }
                        } catch {
                            print("Upload failed: \(error.localizedDescription)")
                        }
                    }
                    
                    rssiQueue.addOperation {
                        self.buffer = nil
                    }
                    
                }
                // Do close up here
                changeState(.waitForProject)
                DispatchQueue.main.async {
                    self.projectLabel.text = "Project is over"
                }
                loadNextProject()
            } else {
                print("working",difference)
                if self.buffer == nil { self.buffer = [] }
                changeState(.recording)
                DispatchQueue.main.async {
                    self.clockLabel.textColor = .green
                    self.clockLabel.text = "\(difference) of \(project.duration)"
                }
            }
        }
        
        centralManager = CBCentralManager(delegate: self, queue: bluetoothQueue)
        peripheralManager = CBPeripheralManager(delegate: self, queue: bluetoothQueue)

        background.image = UIImage(named:"earth-icon")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)
        
        let stackView = UIStackView(arrangedSubviews: [rowSelect,seatSelect,infoLabel,clockLabel,projectLabel,textView,reloadButton])
        stackView.axis = .vertical
        stackView.spacing = 16
        stackView.alignment = .fill
        stackView.distribution = .fill
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        
        let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ").map { String($0) }
        let menuActions = alphabet.map { title in
            UIAction(title: title) { action in
                self.rowSelect.setTitle(title, for: .normal)
                print("Row: \(title)")
                self.row = title
                self.updateBroadcast()
                UserDefaults.standard.set(title, forKey: "row")
            }
        }
        rowSelect.menu = UIMenu(title: "-- row --", children: menuActions)
        if let row = UserDefaults.standard.string(forKey: "row") {
            self.row = row
            let updatedActions = menuActions.map { action -> UIAction in
                if action.title == row {
                    action.state = .on
                } else {
                    action.state = .off
                }
                return action
            }
            rowSelect.menu = UIMenu(title: "-- row --", children: updatedActions)
            rowSelect.setTitle(row, for: .normal)
        }

        
        let numbers = (1...30).map { String($0) }
        let menuActions2 = numbers.map { title in
            UIAction(title: title) { action in
                self.seatSelect.setTitle(title, for: .normal)
                print("Seat: \(title)")
                self.seat = title
                self.updateBroadcast()
                UserDefaults.standard.set(title, forKey: "seat")
            }
        }
        seatSelect.menu = UIMenu(title: "-- seat --", children: menuActions2)
        if let seat = UserDefaults.standard.string(forKey: "seat") {
            self.seat = seat
            let updatedActions = menuActions2.map { action -> UIAction in
                if action.title == seat {
                    action.state = .on
                } else {
                    action.state = .off
                }
                return action
            }
            seatSelect.menu = UIMenu(title: "-- seat --", children: updatedActions)
            seatSelect.setTitle(seat, for: .normal)
        }
        
        let safeArea = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            
            stackView.centerXAnchor.constraint(equalTo: safeArea.centerXAnchor),
            stackView.centerYAnchor.constraint(equalTo: safeArea.centerYAnchor),
            
            // Setting a width so it doesn't hug the edges or collapse
            stackView.leadingAnchor.constraint(equalTo: safeArea.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: safeArea.trailingAnchor, constant: -20),
            
            // Give it a specific height
            textView.heightAnchor.constraint(equalToConstant: 150)
        ])
    }

    func updateBroadcast() {
        if let row = row, let seat = seat, service != nil {
            let payload = row + ":" + seat
            let advertisementData: [String: Any] = [
                CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
                CBAdvertisementDataLocalNameKey: payload
            ]
            print("advertising")
            peripheralManager.stopAdvertising()
            peripheralManager.startAdvertising(advertisementData)
        }
    }

    func prepareJsonData(from records: [Record]) -> Data? {
        let encoder = JSONEncoder()
                
        do {
            let jsonData = try encoder.encode(records)
            return jsonData
        } catch {
            print("Error encoding JSON: \(error)")
            return nil
        }
    }
    
    func changeState(_ state : State) {
        self.state = state
        DispatchQueue.main.async {
            self.infoLabel.text = state.rawValue
        }
    }
    
    func fetchProjectData() async {
        guard let url = URL(string: "https://luxcedia.icu/experiments/xyBlue.json") else {
            print("Invalid URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData // <--- This is the magic line
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        
        do {
            // Perform the asynchronous network call
            let (data, response) = try await URLSession.shared.data(for: request)

            // Verify we got a success code (200-299)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else {
                print("Server error")
                return
            }

            // Decode the JSON into our struct
            let projects = try JSONDecoder().decode([Project].self, from: data)
            self.projects = projects
            
            print("Successfully fetched: \(projects)")
            loadNextProject()
        } catch {
            print("Failed to fetch or decode: \(error.localizedDescription)")
        }
    }
    
    func loadNextProject() {
        while !projects.isEmpty {
            
            
            let project = projects.removeFirst()
            self.project = project
            print("look at",project)
            
            DispatchQueue.main.async {
                self.projectLabel.text = project.start
            }
            
            let formatter = DateFormatter()
            formatter.dateFormat = "d MMM yyyy h:mm a zzz"
            formatter.locale = Locale(identifier: "en_US_POSIX")
            guard let startTime = formatter.date(from: project.start) else { continue }
            print("compare",startTime,Date())
            if startTime > Date() {
                self.startTime = startTime
                changeState(.waitForStart)
                return
            }
            print("IGNORE",project.start)
        }
        
        changeState(.waitForProject)
        return
    }
}

extension ViewController: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            changeState(.waitForOthers)
            let service = CBMutableService(type: serviceUUID, primary: true)
            peripheralManager.add(service)
        case .poweredOff:
            print("Bluetooth is Off.")
        case .unauthorized:
            print("Bluetooth usage is not authorized.")
        default:
            print("Peripheral Manager state changed: \(peripheral.state.rawValue)")
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            print("Error adding service: \(error.localizedDescription)")
            return
        }
        
        print("Service added successfully: \(service.uuid)")
        self.service = service
        self.updateBroadcast()
    }
}

extension ViewController: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            // Scan only for devices providing your specific service
            let options: [String: Any] = [
                        // This is the key that "disables" the deduplication filter
                        CBCentralManagerScanOptionAllowDuplicatesKey: true
                    ]
            central.scanForPeripherals(withServices: [serviceUUID], options: options)
            print("Central: Scanning for \(serviceUUID)")
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        if let timestamp = advertisementData["kCBAdvDataTimestamp"] as? Double,
           let name = advertisementData["kCBAdvDataLocalName"] as? String,
           let signal = RSSI as? Int,
           let row = row,
           let seat = seat
        {
            switch state {
            case .waitForOthers:
                print(name,RSSI)
                changeState(.waitForProject)
            default:
                break
            }
            rssiQueue.addOperation {
                let record = Record(
                    Date().timeIntervalSince1970,
                    "\(row):\(seat)",
                    name,
                    signal
                )
                self.buffer?.append(record)
            }
            readings[name] = RSSI
            var text = ""
            for key in readings.keys.sorted() {
                text += "\(key) \(readings[key]!) "
            }
            DispatchQueue.main.async {
                self.textView.text = text
            }
        }
    }
}



