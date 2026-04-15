//
//  SettingsViewController.swift
//  CrowdQ Stage
//
//  Created by Patrick Miller on 1/21/26.
//


import UIKit
import LuxCGIWithJSON
import BluedogI
import CoreBluetooth
import Exchange

class ContinuousControlViewController: UIViewController {

    // Label to display the continuous value
    private let valueLabel: UILabel = {
        let label = UILabel()
        label.font = .boldSystemFont(ofSize: 24)
        label.text = "1.00"
        label.widthAnchor.constraint(equalToConstant: 45).isActive = true
        return label
    }()

    private let statusSwitch: UISwitch = {
        let sw = UISwitch()
        sw.isOn = false
        return sw
    }()

    private let rangeSlider: UISlider = {
        let slider = UISlider()
        slider.minimumValue = 0
        slider.maximumValue = 2
        slider.value = 1.0
        slider.isContinuous = true // Ensures events fire while sliding
        return slider
    }()

    override func viewDidLoad() {
        print("loading settings")
        super.viewDidLoad()
        setupLayout()
    }

    private func setupLayout() {

        
        // Adding the label to the stack for feedback
        let stackView = UIStackView(arrangedSubviews: [statusSwitch, rangeSlider, valueLabel])
        stackView.axis = .horizontal
        stackView.alignment = .center
        stackView.spacing = 15
        stackView.translatesAutoresizingMaskIntoConstraints = false
        
        view.addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),
            stackView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20)
        ])

        statusSwitch.addTarget(self, action: #selector(switchToggled), for: .valueChanged)
        rangeSlider.addTarget(self, action: #selector(sliderMoved), for: .valueChanged)
    }

    @objc private func switchToggled(_ sender: UISwitch) {
        // Disable the slider and dim the label when off
        rangeSlider.isEnabled = sender.isOn
        valueLabel.alpha = sender.isOn ? 1.0 : 0.5
    }

    @objc private func sliderMoved(_ sender: UISlider) {
        // Update the label with 2 decimal places
        valueLabel.text = String(format: "%.2f", sender.value)
    }
}


class SettingsViewController: UIViewController {
    var alert : UIAlertController? = nil
    var main : ViewController?
    var bluedog : BlueDogMessage?

    public let mode : UILabel = {
        let label = UILabel()
        label.text = "Demo mode"
        label.font = .boldSystemFont(ofSize: 24)
        label.textColor = .label
        label.backgroundColor = .systemBackground
        return label
    }()
    
    // Three-way selection (Radio Button style)
    private let hubSegmentedControl: UISegmentedControl = {
        let items = ["None", "microHub", "Hub"]
        let sc = UISegmentedControl(items: items)
        sc.selectedSegmentIndex = 0
        sc.backgroundColor = .secondarySystemBackground
        sc.selectedSegmentTintColor = .systemBlue

        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 24, weight: .medium),
            .foregroundColor: UIColor.secondaryLabel
            // Optional: change text color too
        ]
        sc.setTitleTextAttributes(attributes, for: .normal)
        let selectedAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 24, weight: .medium),
            .foregroundColor: UIColor.white
        ]
        sc.setTitleTextAttributes(selectedAttributes, for: .selected)
        return sc
    }()

    private let sensorSwitch: UISwitch = {
        let sw = UISwitch()
        sw.isOn = false
        return sw
    }()
    
    let rangeSlider: UISlider = {
            let slider = UISlider()
            slider.minimumValue = 0
            slider.maximumValue = 2
            slider.value = 1.0
            slider.isContinuous = true // Ensures events fire while sliding
            return slider
        }()
    
    let sliderLabel : UILabel = {
        let label = UILabel()
        label.text = "1.0"
        label.textAlignment = .center
        return label
    }()

    private let optionsButton: UIButton = {
        var config = UIButton.Configuration.filled()
        config.title = "Select show to go live"
        let button = UIButton(configuration: config)
        button.showsMenuAsPrimaryAction = true
        return button
    }()

    private let information: UITextField = {
        let tx = UITextField()
        tx.text = "Select a show"
        tx.font = .systemFont(ofSize: 24)
        tx.backgroundColor = .systemBackground
        tx.textColor = .label
        return tx
    }()
    
    var timer: Timer?
    var showTitle: String?
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        setupLayout()
        setupMenu(["free/demo.json"], overwrite: false) // There's a race to get setup

        hubSegmentedControl.addTarget(self, action: #selector(hubChanged(_:)), for: .valueChanged)
        sensorSwitch.addTarget(self, action: #selector(sensorSwitchChanged(_:)), for: .valueChanged)
        rangeSlider.addTarget(self, action: #selector(sliderChanged(_:)), for: .valueChanged)
        
        // We want to periodically send a "show" command since people will be adding phones
        // to the performance
        timer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: true) { [weak self] timer in
            if let self = self, let showTitle = self.showTitle {
                let name = String(showTitle.dropLast(5))
                self.main?.bleClient?.enqueue(.load,0,name)
            }
        }
    }

    private func setupLayout() {
        // We want this image in the background
        let background = UIImageView()
        background.image = UIImage(named:"on-stage")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)
        
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        
        let sensors : UIStackView = {
            let row = UIStackView()
            row.axis = .horizontal
            row.alignment = .fill
            row.spacing = 15
                        
            row.addArrangedSubview(sensorSwitch)
            row.addArrangedSubview(sliderLabel)
            row.addArrangedSubview(rangeSlider)

            return row
        }()
        
        let stackView = UIStackView(arrangedSubviews: [
            mode,
            UILabel(),
            createLabel(text: "Hub Type"), hubSegmentedControl,
            UILabel(),
            createLabel(text: "Sensors"), sensors,
            UILabel(),
            createLabel(text: "Show configuration"), optionsButton,
            UILabel(),
            createLabel(text: "Information"), information
        ])
        
        stackView.axis = .vertical
        stackView.spacing = 20
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            // ScrollView fills the Safe Area
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            
            // StackView anchors to ScrollView's Content Guide
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 120),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -20),
            
            // Critical: Ensure the StackView width matches the ScrollView width (minus padding)
            stackView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -40)
        ])
    }
    
    var active = false
    private func setupMenu(_ choices : [String], overwrite : Bool) {
        print("setup",choices,active,overwrite)
        guard overwrite || !active else { return }
        active = true  // Still a small race
        let menuActions = choices.map { title in
            UIAction(title: title) { action in
                self.showTitle = title
                DispatchQueue.main.async {
                    self.information.text = "loading " + title
                }
                self.optionsButton.setTitle(title, for: .normal)
                print("Selected: \(title)")
                getBlueDog(title) { data, bluedog, status in
                    if let show = bluedog {
                        DispatchQueue.main.async {
                            self.information.text = "loaded " + title
                        }
                        self.main?.page3.newShow(data, show)
                    } else {
                        DispatchQueue.main.async {
                            self.information.text = "Could not load \(title) with code \(status)"
                        }
                    }
                }
            }
        }
        optionsButton.menu = UIMenu(title: "Choices", children: menuActions)
    }

    private func createLabel(text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .boldSystemFont(ofSize: 24)
        label.textColor = .white
        return label
    }

    func onVerified() {
        print("SVC verifed")
        DispatchQueue.main.async {
            self.mode.text = "Free show mode"
            self.information.text = "Searching available shows"
        }
        
        // Start downloading the list of free shows
        restAPI("https://luxcedia.icu/cgi-bin/freeshows",[
            "id" : main?.page1.emailTextField.text ?? "",
            "uuid" : deviceUUID,
        ]) { error, data, status in
            if error == nil, let data = data, status == 200 {
                print("got data",data)
                do {
                    let shows = try JSONDecoder().decode([String].self, from: data)
                    DispatchQueue.main.async {
                        self.information.text = "Found \(shows.count) available shows"
                        self.setupMenu(shows, overwrite: true)
                    }
                    print(shows)
                } catch {
                    print(String(data: data, encoding: .utf8))
                    print("JSON Error: \(error)")
                }

            } else {
                print("Bad fetch",status)
            }
        }
        
    }
    
    func popup(_ message : String) {
        DispatchQueue.main.async {
            // Pop down the old message
            self.alert?.dismiss(animated: false)
            
            self.alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            self.alert?.addAction(UIAlertAction(title: "Dismiss", style: .default, handler: nil))
            
            if let alert = self.alert {
                self.present(alert, animated: true, completion: nil)
            }
        }
    }
    
    @objc private func hubChanged(_ sender: UISegmentedControl) {
        let selectedIndex = sender.selectedSegmentIndex
        let selectedTitle = sender.titleForSegment(at: selectedIndex)
        
        print("Selected Segment Index: \(selectedIndex), Title: \(selectedTitle ?? "")")
        
        // Perform your logic here (e.g., updating other UI elements)
        if selectedIndex != 0 {
            popup("hubs are disabled in this early version")
            DispatchQueue.main.async {
                self.sensorSwitch.isOn = false
            }
        }
    }
    
    @objc private func sensorSwitchChanged(_ sender: UISwitch) {
        if sender.isOn {
            // We can only enable sensors when we have no external hub
            let selectedIndex = hubSegmentedControl.selectedSegmentIndex
            if selectedIndex == 0 {
                print("Sensors on",selectedIndex)
                main?.bleClient?.listening = true
                DispatchQueue.main.async {
                    self.information.text = "Listening for basic sensors"
                }
            } else {
                popup("the selected hub is handling sensors")
                main?.bleClient?.listening = false
                DispatchQueue.main.async {
                    sender.isOn = false
                }
            }
        } else {
            print("Sensors Disabled")
            main?.bleClient?.listening = false
        }
    }
    
    public var gravityScale : Float = 1.0
    @objc private func sliderChanged(_ sender: UISlider) {
        gravityScale = sender.value
        let value = String(format: "%.2f", sender.value)
        DispatchQueue.main.async {
            self.sliderLabel.text = value + "G"
        }
    }
}
