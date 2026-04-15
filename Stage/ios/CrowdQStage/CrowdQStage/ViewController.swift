//
//  ViewController.swift
//  CrowdQ Stage
//
//  Created by Patrick Miller on 1/15/26.
//

import UIKit
import CoreBluetooth
import Exchange

enum Direction : Int {
    case none = 0
    case left = 1
    case right = 2
    case up = 3
    case down = 4
    case inward = 5
    case outward = 6
}

typealias Reading = (ax: Int16, ay: Int16, az: Int16)
struct Telemetry {
    let sensor: UInt8
    let reading: Reading
}

extension Telemetry {
    init?(_ data: Data) {
        // Validation: 1 byte + (3 * 2 bytes) = 7 bytes
        guard data.count == 19 else { return nil }
        self = data.withUnsafeBytes { buffer in
            return Telemetry(
                sensor: buffer.load(fromByteOffset: 0, as: UInt8.self),
                reading:
                    Reading(
                        buffer.loadUnaligned(fromByteOffset: 1,  as: Int16.self),
                        buffer.loadUnaligned(fromByteOffset: 3,  as: Int16.self),
                        buffer.loadUnaligned(fromByteOffset: 5,  as: Int16.self)
                    )
                
            )
        }
    }
}

var main : ViewController? = nil
let deviceUUID = UIDevice.current.identifierForVendor?.uuidString ?? "00000000-0000-0000-0000-000000000000"
let phonesUUID = CBUUID(string: "EEEE")  // Has to be a 16-bit UUID

class ViewController: UIViewController {
    var bleClient : BLEManager?
    let deviceUUID = UIDevice.current.identifierForVendor?.uuidString ?? "00000000-0000-0000-0000-000000000000"
    let sensorUUID = CBUUID(string: "BBBB")
    let pager = PagerViewController(
        transitionStyle: .scroll,
        navigationOrientation: .horizontal
    )
    
    let page1 = Page1()
    let page2 = SettingsViewController()
    let page3 = ButtonGridViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        
        bleClient = BLEManager(uuid: phonesUUID, sensorUUID: sensorUUID, callback: telemetry)

        print(deviceUUID)
        page1.main = self
        page2.main = self
        page3.main = self
        
        // Don't allow app to timeout and sleep on the idle timer...
        UIApplication.shared.isIdleTimerDisabled = true
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification,
                                               object: nil,
                                               queue: .main) { _ in
            print("App is coming back to the foreground!",UIApplication.shared.isIdleTimerDisabled)
        }
        
        // See if we already have an email saved
        let email = UserDefaults.standard.string(forKey: "email") ?? ""
        if email.count != 0 {
            page1.emailTextField.text = email
        }
        
        pager.pages.append(page1)
        pager.pages.append(page2)
        
        page3.view.backgroundColor = .systemRed
        pager.pages.append(page3)
    }
    
    private var didPresent = false
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        
        // Just once
        guard !didPresent else { return }
        didPresent = true
        
        pager.modalPresentationStyle = .fullScreen
        present(pager, animated: true)
    }

    var lastDirection : Direction = .none
    func telemetry(_ message : Data) {

        let threshold = Int(page2.gravityScale/2.0 * 512.0) // Reference 2G
        if let telemetry = Telemetry(message) {
            //print("tel", threshold, telemetry)
            // Unpack the ax,ay,az values and trigger if over the current threshold
            // Maybe don't send the same one twice in a row?
            // Maybe pick the component with the maximum magnitude?
            var heading = lastDirection
            let reading = telemetry.reading
            let ax = reading.ax
            let ay = reading.ay
            let az = reading.az
            
            let axMag = abs(ax)
            let ayMag = abs(ay)
            let azMag = abs(az)
            
            if axMag > threshold && axMag > ayMag && axMag > azMag {
                //print("left/right",threshold,axMag,ayMag,azMag )
                heading = ( ax < 0 ) ? .left : .right
            } else if ayMag > threshold && ayMag > axMag && ayMag > azMag {
                //print("up/down",threshold,axMag,ayMag,azMag )
                heading = ( ay < 0 ) ? .up : .down
            } else if azMag > threshold && azMag > axMag && azMag > ayMag {
                //print("inout",threshold,axMag,ayMag,azMag )
                heading = ( az < 0 ) ? .inward : .outward
            }
            
            if heading != lastDirection {
                lastDirection = heading
                let command = String(format:"%02d", telemetry.sensor) + ".\(heading)"
                print(command)
                //bleClient?.updateOutboundBroadcast(with: Data(command.utf8))
                bleClient?.enqueue(.command,Int(telemetry.sensor),".\(heading)")
            }
        }
    }
}

