//
//  ImagePopupViewController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/26/26.
//

import UIKit

class ImagePopupViewController: UIViewController {

    var imageToShow: UIImage?
    // Make displayDurationMilliseconds a constant set via initializer
    let displayDurationMilliseconds: Int
    
    private let imageView: UIImageView = {
        let view = UIImageView()
        view.contentMode = .scaleAspectFit
        view.translatesAutoresizingMaskIntoConstraints = false
        view.backgroundColor = .black.withAlphaComponent(0.7)
        return view
    }()

    // This is the new required initializer to pass the duration and the image
    init(image: UIImage, durationMs: Int) {
        self.imageToShow = image
        self.displayDurationMilliseconds = durationMs
        super.init(nibName: nil, bundle: nil)
    }
    
    // Required initializer for UIKit if you were using Storyboards/Nibs
    required init?(coder: NSCoder) {
        // Handle this case if necessary, or simply fatalError if you are only using code
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.backgroundColor = .clear
        
        if let imageToShow = imageToShow {
            imageView.image = imageToShow
            view.addSubview(imageView)
            
            NSLayoutConstraint.activate([
                // Center it in the middle of the screen
                imageView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
                imageView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
                
                // Set the specific size you want (500x500)
                imageView.widthAnchor.constraint(equalToConstant: imageToShow.size.width),
                imageView.heightAnchor.constraint(equalToConstant: imageToShow.size.height)
            ])
            
            scheduleSelfDismiss()
        }
    }
    
    private func scheduleSelfDismiss() {
        // Convert milliseconds integer to a TimeInterval (Double in seconds)
        let durationInSeconds = Double(displayDurationMilliseconds) / 1000.0
        
        DispatchQueue.main.asyncAfter(deadline: .now() + durationInSeconds) {
            self.dismiss(animated: true, completion: nil)
        }
    }
}
