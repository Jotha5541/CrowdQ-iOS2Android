//
//  ImageController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/26/26.
//

import UIKit

class ImageController {
    let semaphore = DispatchSemaphore(value:1)
    var workQueue = OperationQueue()
    
    init() {
        workQueue.maxConcurrentOperationCount = 1
    }

    func addImage(view: UIViewController,_ image : UIImage, _ duration : Int) {
        workQueue.addOperation {
            self.displayImage(view: view,image,duration)
        }
    }
    
    private func displayImage(view: UIViewController, _ image : UIImage, _ duration : Int) {
        defer { semaphore.signal() }
        semaphore.wait()
        print("popup",image,"for",duration)
        var popupVC : ImagePopupViewController? = nil
        DispatchQueue.main.async {
            popupVC = ImagePopupViewController(image: image, durationMs: duration)
            print(popupVC)
            if let pop = popupVC {
                pop.modalPresentationStyle = .overFullScreen
                pop.modalTransitionStyle = .crossDissolve
                view.present(pop, animated: true, completion: nil)
                //view.present(image, animated: false, completion: nil)
            }
        }
        Thread.sleep(forTimeInterval: Double(duration) / 1000.0)
        DispatchQueue.main.async {
            defer { self.semaphore.signal() }
            if let pop = popupVC {
                pop.dismiss(animated: false, completion: nil)
            }
        }
    }
}
