//
//  Primary.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 2/1/26.
//
import UIKit

let oneTimeMessage = """
        This is a one-time message.
        
        
        When you start this app up, it will look for a local CrowdQ performance.  If there aren't any running yet, you'll see a bouncing red guitar while our app scans the bluetooth airwaves for a show server.  The guitar will be dismissed when the server is discovered.
        
        
        You can swipe left to see status and "about" pages
        """
class Primary: UIViewController {
    var alert : UIAlertController? = nil

    let image = UIImageView()
    var animator: UIDynamicAnimator?
    var iconView: UIImageView!
    var pushBehavior: UIPushBehavior?
    var background = UIImageView()

  
    override func viewDidLoad() {
        super.viewDidLoad()

        // We want this image in the background
        background.image = UIImage(named:"lighted-stage")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)
        
        view.addSubview(image)
        toggleBouncing(isOn: true)
        
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
        
        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            
            image.centerXAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerXAnchor),
            image.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),
        ])
    }
    
    func setupIcon() {
        iconView = UIImageView(frame: CGRect(x: 100, y: 100, width: 50, height: 50))
        //iconView.image = UIImage(named: "looking") // Your icon
        iconView.image = UIImage(named: "guitar_red") // Your icon
        iconView.contentMode = .scaleAspectFit
        view.addSubview(iconView)
        
        animator = UIDynamicAnimator(referenceView: self.view)
    }
    
    func toggleBouncing(isOn: Bool) {
        // Always stop physics and clear behaviors first
        animator?.removeAllBehaviors()
        
        if isOn {
            // Re-create and add the icon if it's being turned on
            setupIcon()
            
            let collision = UICollisionBehavior(items: [iconView])
            collision.translatesReferenceBoundsIntoBoundary = true
            animator?.addBehavior(collision)
            
            let bounciness = UIDynamicItemBehavior(items: [iconView])
            bounciness.elasticity = 1.0
            bounciness.friction = 0.0
            bounciness.resistance = 0.0
            animator?.addBehavior(bounciness)
            
            let push = UIPushBehavior(items: [iconView], mode: .instantaneous)
            push.pushDirection = CGVector(dx: 0.5, dy: 0.5)
            push.active = true
            animator?.addBehavior(push)
            
        } else {
            // If toggled off, remove the view from the screen
            DispatchQueue.main.async {
                self.iconView?.removeFromSuperview()
                self.iconView = nil // Clean up the reference
            }
        }
    }

}
