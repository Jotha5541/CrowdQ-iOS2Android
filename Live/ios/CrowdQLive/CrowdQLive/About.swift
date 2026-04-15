//
//  About.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 2/1/26.
//
import UIKit

class About: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        
        let background = UIImageView()
        background.image = UIImage(named:"lighted-stage")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)

        let textView = UITextView()
        textView.isScrollEnabled = true
        textView.font = UIFont.systemFont(ofSize: 20)
        textView.layer.borderColor = UIColor.lightGray.cgColor
        textView.layer.borderWidth = 1.0
        textView.translatesAutoresizingMaskIntoConstraints = false
        textView.backgroundColor = .secondarySystemBackground
        textView.textColor = UIColor.label
        textView.text = """
            About CrowdQ™
            
            CrowdQ turns your phone into part of the show. 
            
            Lights flash, visuals pulse, sounds hit, and your phone vibrates in sync with the artist live on stage -- triggered by their moves, gestures and beat.
            """
        
        // Add this back in once location is enabled
        //CrowdQ uses Luxcedia's patent pending micro-location capability to deliver effects taylored to your position in the venue (location-capable shows require external hardware.

        textView.isOpaque = false
        view.addSubview(textView)
        
        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            
            // Anchor to the top safe area
            textView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            
            // Anchor to the leading (left) safe area
            textView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 10),
            
            // Anchor to the trailing (right) safe area
            textView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -10),
            
            // Give it a specific height or anchor it to the bottom
            textView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: 10),
        ])
    }
}

