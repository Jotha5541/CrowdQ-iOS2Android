//
//  FlashController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/26/26.
//
import UIKit
import AVKit
import AVFoundation

class FlashController {
    let semaphore = DispatchSemaphore(value:1)
    var workQueue = OperationQueue()
    let device : AVCaptureDevice?
    
    init() {
        workQueue.maxConcurrentOperationCount = 1
        device = AVCaptureDevice.default(for: .video)
    }
    
    func addFlash(_ intensity : Double, _ duration : Int) {
        workQueue.addOperation {
            self.doFlash(intensity,duration)
        }
    }
    
    private func doFlash(_ intensity : Double, _ duration : Int) {
        print("SET FLASH TO",intensity,"for",duration,"ms")
        guard let device = self.device, device.hasTorch else {
            print("No torch available")
            return
        }
        
        do {
            try device.lockForConfiguration()
            if intensity > 0 {
                try device.setTorchModeOn(level: min(1.0,Float(intensity)/100.0))
            } else {
                device.torchMode = .off
            }
            device.unlockForConfiguration()
        } catch {
            print("Torch could not be used: \(error)")
        }
        Thread.sleep(forTimeInterval: Double(duration) / 1000.0)
        
        do {
            try device.lockForConfiguration()
            device.torchMode = .off
            device.unlockForConfiguration()
        } catch {
            print("Torch could not be used: \(error)")
        }
    }
}
