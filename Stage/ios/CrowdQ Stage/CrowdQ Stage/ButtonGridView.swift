import UIKit
import BluedogI
import Exchange

class RadioButton: UIButton {
    
    override var isSelected: Bool {
        didSet {
            backgroundColor = isSelected ? .systemBlue : .systemGray6
            setTitleColor(isSelected ? .white : .label, for: .normal)
        }
    }

    init(number: Int) {
        let radius : CGFloat = 40.0
        
        super.init(frame: .zero)
        setTitle("\(number)", for: .normal)
        titleLabel?.font = .systemFont(ofSize: radius/2.0, weight: .bold)
        layer.cornerRadius = radius/2.0
        clipsToBounds = true
        
        // Initial state
        isSelected = false
        
        // Set constraints for a perfect circle
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            widthAnchor.constraint(equalToConstant: radius),
            heightAnchor.constraint(equalToConstant: radius)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}


class ButtonGridViewController: UIViewController {
    let background = UIImageView()

    // We want to lock to landscape
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        return .landscape
    }
    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation {
        return .landscapeLeft
    }
    override var shouldAutorotate: Bool {
        return true
    }
    
    var main : ViewController?
    
    let scrollView = UIScrollView()
    let stackView = UIStackView()
    
    func newShow(_ raw : Data?, _ show : BlueDogMessage) {
        let immediates : [String] = show.immediateActions.map { def in
            return def.name
        }
        let sensors : [String] = show.sensorActions.map { def in
            return def.name
        } 
        DispatchQueue.main.async {
            self.setupDynamicButtonGrid(actions: immediates, toggles: sensors)
            self.background.image = UIImage(named:"live-show-active")
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupScrollView()
        setupDynamicButtonGrid(actions: [], toggles: [])
    }
    
    override func viewIsAppearing(_ animated: Bool) {
        super.viewIsAppearing(animated)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.landscape()
        }
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        
        // Am I still up?
        guard isViewLoaded && view.window != nil else { return }
        
        if let pageViewController = self.parent as? UIPageViewController {
            // Only proceed if THIS view controller is the one currently on screen
            guard pageViewController.viewControllers?.first == self else {
                return
            }
        }
        landscape()
    }
    
    func landscape() {
        print("set landscape")
        if let windowScene = view.window?.windowScene {
            windowScene.requestGeometryUpdate(
                .iOS(interfaceOrientations: .landscape)
            )
        }
        
        // Force the system to re-read the 'supportedInterfaceOrientations' property above
        self.setNeedsUpdateOfSupportedInterfaceOrientations()
    }

    private func setupScrollView() {
        
        // We want this image in the background
        background.image = UIImage(named:"lighted-stage")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)
        
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        stackView.translatesAutoresizingMaskIntoConstraints = false
        
        view.addSubview(scrollView)
        scrollView.addSubview(stackView)
        
        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            
            // Pin ScrollView to safe area
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            
            // Pin StackView to ScrollView Content Guide
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 20),
            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -20),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
            
            // Critical: Match StackView width to ScrollView Frame to prevent horizontal scrolling
            stackView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -40)
        ])
    }
    var maxWidth : CGFloat = 0.0
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        let safeArea = view.safeAreaLayoutGuide.layoutFrame
        let safeWidth = safeArea.width
        let safeHeight = safeArea.height
        maxWidth = max(safeWidth,safeHeight)
    }
    
    var radios : [RadioButton] = []
    var toggleState : [String:Int] = [:]
    func setupDynamicButtonGrid(actions: [String], toggles: [String]) {
        stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
        stackView.axis = .vertical
        stackView.spacing = 15

        // Force layout to get accurate width for calculations
        self.view.layoutIfNeeded()
        
        // Set up the row of radio buttons
        let (radioRow,radioLead) = createHorizontalStack()
        stackView.addArrangedSubview(radioRow)
        
        // We have to keep track of the toggles so we can recolor the buttons
        for toggle in toggles {
            toggleState[toggle] = -1
        }

        radios = []
        var currentLineWidth: CGFloat = 0
        for i in 0...20 {
            let button = RadioButton(number: i)
            button.addTarget(self, action: #selector(radioTapped(_:)), for: .touchUpInside)
            radios.append(button)
            
            let buttonWidth = button.intrinsicContentSize.width + 32 // Add padding
            currentLineWidth += buttonWidth
            if currentLineWidth > maxWidth { break }
            if i == 0 { button.isSelected = true }
            radioRow.addArrangedSubview(button)
        }

        let trailingRadio = UIView()
        trailingRadio.translatesAutoresizingMaskIntoConstraints = false
        radioRow.addArrangedSubview(trailingRadio)
        radioLead.widthAnchor.constraint(equalTo: trailingRadio.widthAnchor).isActive = true
        
        currentLineWidth = 0.0
        var (currentRowStack,leadingSpacer) = createHorizontalStack()
        stackView.addArrangedSubview(currentRowStack)
        print("stay under",maxWidth,stackView.frame.width,stackView.frame.height)
        for name in actions+toggles {
            if name.starts(with: "_") { continue } // Ignore private names
            print("look at button",name,"at",currentLineWidth)
            let button = createStyledButton(title: name)
            let buttonWidth = button.intrinsicContentSize.width + 32 // Add padding
            
            if currentLineWidth + buttonWidth > maxWidth && currentLineWidth > 0 {
                let trailingSpacer = UIView()
                trailingSpacer.translatesAutoresizingMaskIntoConstraints = false
                currentRowStack.addArrangedSubview(trailingSpacer)

                // Link their widths so they stay perfectly equal, forcing buttons to dead center
                leadingSpacer.widthAnchor.constraint(equalTo: trailingSpacer.widthAnchor).isActive = true
                
                (currentRowStack,leadingSpacer) = createHorizontalStack()
                stackView.addArrangedSubview(currentRowStack)
                currentLineWidth = 0
            }
            
            currentRowStack.addArrangedSubview(button)
            currentLineWidth += buttonWidth + currentRowStack.spacing
        }
        let trailingSpacer = UIView()
        trailingSpacer.translatesAutoresizingMaskIntoConstraints = false
        currentRowStack.addArrangedSubview(trailingSpacer)

        // Link their widths so they stay perfectly equal, forcing buttons to dead center
        leadingSpacer.widthAnchor.constraint(equalTo: trailingSpacer.widthAnchor).isActive = true
    }

    private func createHorizontalStack() -> (UIStackView, UIView) {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 12
        stack.alignment = .center
        stack.distribution = .fill

        let leadingSpacer = UIView()
        leadingSpacer.translatesAutoresizingMaskIntoConstraints = false
        stack.addArrangedSubview(leadingSpacer)
        
        return (stack, leadingSpacer)
    }

    private func createStyledButton(title: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(" " + title + " ", for: .normal)
        button.layer.cornerRadius = 10
        button.backgroundColor = .systemBlue
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = .boldSystemFont(ofSize: 28)
        // Add action
        if let _ = toggleState[title] {
            print("\(title) is a toggle")
            button.addTarget(self, action: #selector(toggleTapped(_:)), for: .touchDown)

        } else {
            print("\(title) is action")
            button.addTarget(self, action: #selector(buttonTapped(_:)), for: .touchDown)
        }
        return button
    }

    @objc func buttonTapped(_ sender: UIButton) {
        // Quickly fade out and back in
        sender.alpha = 0.3
        
        UIView.animate(withDuration: 0.4, delay: 0, options: .allowUserInteraction, animations: {
            sender.alpha = 1.0
        }, completion: nil)
        
        if let label = sender.title(for: .normal) {
            let action = label.trimmingCharacters(in: .whitespacesAndNewlines)
            print("Selected: \(action)")
            main?.bleClient?.enqueue(.command,dollarArgument,action)
        }
    }

    @objc func toggleTapped(_ sender: UIButton) {
        let oldBG = sender.backgroundColor
        let oldText = sender.titleColor(for: .normal)
        
        sender.backgroundColor = oldText
        sender.setTitleColor(oldBG, for: .normal)
        
        // Update the border color too, so it stays visible
        sender.layer.borderColor = oldBG?.cgColor
        
        if let label = sender.title(for: .normal) {
            let action = label.trimmingCharacters(in: .whitespacesAndNewlines)
            // We are either on and we want to be off or we are off and want to be on
            let state = toggleState[action] ?? -1
            if state == -1 {
                print("ON: \(action)",dollarArgument)
                toggleState[action] = dollarArgument
                main?.bleClient?.enqueue(.command,dollarArgument,action)
            } else {
                print("OFF: \(action)")
                toggleState[action] = -1
                main?.bleClient?.enqueue(.command,state,"_off")
            }
        }
    }

    func onVerified() {
        print("BGV verifed")
    }
 
    var dollarArgument : Int = 0
    @objc func radioTapped(_ sender: RadioButton) {
        // Deselect all and select the sender
        radios.forEach { $0.isSelected = false }
        sender.isSelected = true
        dollarArgument = Int(sender.currentTitle ?? "0") ?? 0
        print("Selected:",dollarArgument)
    }
}
