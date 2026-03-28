//
//  Settings.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 2/1/26.
//
import UIKit

class Settings: UIViewController {
    let showLabel : UILabel = {
        let label = UILabel()
        label.text = "-- no show loaded --"
        label.font = UIFont.systemFont(ofSize: 35)
        label.backgroundColor = .secondarySystemBackground
        label.textColor = UIColor.label
        label.textAlignment = .center
        return label
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
        tv.text = "waiting for a show...\n"
        return tv
    }()

    override func viewDidLoad() {
        
        super.viewDidLoad()
        
        let background = UIImageView()
        background.image = UIImage(named:"lighted-stage")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)
        
        let stackView = UIStackView(arrangedSubviews: [showLabel,textView])
        stackView.axis = .vertical
        stackView.spacing = 16
        stackView.alignment = .fill
        stackView.distribution = .fill
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        
        print("subviews", stackView.arrangedSubviews)
        let safeArea = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            
            // Centering the stack
            stackView.centerXAnchor.constraint(equalTo: safeArea.centerXAnchor),
            stackView.centerYAnchor.constraint(equalTo: safeArea.centerYAnchor),
            
            // Setting a width so it doesn't hug the edges or collapse
            stackView.leadingAnchor.constraint(equalTo: safeArea.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: safeArea.trailingAnchor, constant: -20),
            
            // Give it a specific height
            textView.heightAnchor.constraint(equalToConstant: 150)
        ])
    }
    
    func add(_ text : String) {
        let df = DateFormatter()
        df.dateFormat = "HH:mm:ss"   // HH for 24h clock

        let date = Date()
        let timeString = df.string(from: date)
        let info = "\n\(timeString) - \(text)"
        print("***** \(info)")
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
            self.textView.text += info
            let range = NSMakeRange(self.textView.text?.count ?? 0 - 1, 1)
            self.textView.scrollRangeToVisible(range)
        }
    }
}
