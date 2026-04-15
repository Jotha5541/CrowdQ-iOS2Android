//
//  BuzzController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/26/26.
//

import UIKit

class BuzzController {
    let semaphore = DispatchSemaphore(value:1)
    var workQueue = OperationQueue()
    
    init() {
        workQueue.maxConcurrentOperationCount = 1
    }
    
    func addBuzz(_ pattern : Int, _ duration : Int) {
        workQueue.addOperation {
            self.doBuzz(pattern,duration)
        }
    }
    
    private func doBuzz(_ pattern : Int, _ duration : Int) {
        var generator : UIImpactFeedbackGenerator? = nil
        switch pattern {
        case 0: // No haptic
            break
        case 1: //
            generator = UIImpactFeedbackGenerator(style: .light)
        case 2: //
            generator = UIImpactFeedbackGenerator(style: .medium)
        case 3: //
            generator = UIImpactFeedbackGenerator(style: .heavy)
        case 4: //
            generator = UIImpactFeedbackGenerator(style: .soft)
        case 5: //
            generator = UIImpactFeedbackGenerator(style: .rigid)
        default:
            generator = UIImpactFeedbackGenerator(style: .heavy)
        }
        if let generator = generator {
            generator.prepare()
            generator.impactOccurred()
        }
        Thread.sleep(forTimeInterval: Double(duration) / 1000.0)
    }
}
