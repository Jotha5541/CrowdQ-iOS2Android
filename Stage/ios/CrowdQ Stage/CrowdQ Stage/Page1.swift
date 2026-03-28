//
//  Page1.swift
//  CrowdQ Stage
//
//  Created by Patrick Miller on 1/20/26.
//

import UIKit
import LuxCGIWithJSON


let oneTimeMessage =  """
    Welcome to CrowdQ Stage 

    * This is a one time message.
    * Without an email, you can access just the "demo" show
    * Provide and verify your email to access more free shows
    * Swipe left/right to navigate between app screens
"""

let blankInstructions = """
You currently have not entered an email.  This locks CrowdQ Stage into demo mode.Swipe left to expose the current show.

Enter your email and press verify.  This will generate a email with a link to follow to make sure your email is valid.  Check your SPAM folder.  It will be coming from noreply@luxcedia.icu

This will move your account to "free" status which allows a few more interesting shows.  You can later upgrade this to a paid account to allow you to generate your own shows.

You can unsubscribe/disconnect this device using the [unsubscribe] button below
"""

let askToVerifyEmail = """
    You may now use the [Verify] button to verify your email.
    
    If you previously verified on another device, you'll be good to go.
    
    Otherwise, we'll email you a link to follow get verified (check your SPAM). That link will also contain a link to unsubscribe (then or in the future).  You may also request removal under your right to be forgotten.
    
    See our privacy policy at https://luxcedia.icu/privacyCrowdQStage.html
    """

let emailIsVerified = """
    You now have access to all the free shows.
    Swipe left to see the show play board.
    
    """

class Page1 : ScrollingTextPage {
    var alert : UIAlertController? = nil
    let background = UIImageView()
    var main : ViewController?
    
    private let instructions: UITextView = {
        let tv = UITextView()
        tv.text = blankInstructions
        tv.font = UIFont.systemFont(ofSize: 25)
        tv.backgroundColor = .secondarySystemBackground
        tv.textColor = UIColor.label
        tv.isEditable = false        // This makes it read-only
        tv.isSelectable = true      // Allows user to copy text if needed
        tv.isScrollEnabled = false   // Allows the stack view to expand to fit text
        tv.textContainerInset = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        tv.textContainer.lineFragmentPadding = 0
        return tv
    }()
    
    
    let emailTextField: UITextField = {
        let tf = UITextField()
        tf.placeholder = "email for verification..."
        tf.font = UIFont.systemFont(ofSize: 30)
        tf.backgroundColor = .secondarySystemBackground
        tf.textColor = UIColor.label
        tf.borderStyle = .roundedRect
        tf.keyboardType = .emailAddress
        tf.returnKeyType = .done
        tf.autocapitalizationType = .none
        return tf
    }()
    
    private let statusButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("  verify  ", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 30, weight: .bold)
        button.layer.cornerRadius = 15
        button.layer.borderWidth = 2.0
        button.layer.borderColor = UIColor.white.cgColor // Note: .cgColor is required here
        button.backgroundColor = .systemBlue.withAlphaComponent(0.5)
        button.clipsToBounds = true
        return button
    }()
    
    private let unsubscribeButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("  unsubscribe  ", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 30, weight: .bold)
        button.layer.cornerRadius = 15
        button.layer.borderWidth = 2.0
        button.layer.borderColor = UIColor.white.cgColor // Note: .cgColor is required here
        button.backgroundColor = .systemBlue.withAlphaComponent(0.5)
        button.clipsToBounds = true
        return button
    }()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        emailTextField.delegate = self
        textCallback = newEmail
        unsubscribeButton.addTarget(self, action: #selector(unsubscribe), for: .touchUpInside)
        statusButton.addTarget(self, action: #selector(verify), for: .touchUpInside)
        
        instructions.textColor = (traitCollection.userInterfaceStyle == .dark) ? .white : .black
        
        let email = UserDefaults.standard.string(forKey: "email") ?? ""
        if email.count > 0 {
            DispatchQueue.main.async {
                self.emailTextField.text = email
            }
            verifyEmail(email)
        }
    }
    
    override func setupLayout() {
        super.setupLayout()
        
        let background = UIImageView()
        background.image = UIImage(named:"lighted-stage")
        background.contentMode = .scaleAspectFill
        background.clipsToBounds = true
        background.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(background)
        view.sendSubviewToBack(background)
        
        NSLayoutConstraint.activate([
            background.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            background.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            background.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor),
            background.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor),
            background.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            background.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }
    
    override func elementConstraints() -> [NSLayoutConstraint] {
        super.elementConstraints()
    }
    
    override func setupStackView() {

        stackView.addArrangedSubview(instructions)
        stackView.addArrangedSubview(emailTextField)
        stackView.addArrangedSubview(statusButton)
        stackView.addArrangedSubview(unsubscribeButton)

        stackView.alignment = .center
        let iPadWidthConstraint = stackView.widthAnchor.constraint(equalToConstant: 400)
        iPadWidthConstraint.priority = .defaultHigh // This prevents the error
        
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
    }
    
    func newEmail(_ textField: UITextField, _ email : String) {
        DispatchQueue.main.async {
            self.instructions.text = askToVerifyEmail
        }
        UserDefaults.standard.set(textField.text ?? "", forKey: "email")
    }
    
    func verifyEmail(_ email : String) {
        DispatchQueue.main.async {
            self.instructions.text = "verifying email"
            LuxCGIWithJSON.restAPI("https://luxcedia.icu/cgi-bin/verifyemail",[
                "id":self.emailTextField.text ?? "",
                "uuid": deviceUUID
            ]) { error, data, status in
                if error == nil, let data = data {
                    if let answer = String(data:data, encoding: .utf8)  {
                        DispatchQueue.main.async {
                            if status != 200 {
                                self.instructions.text = ("Problem with verify:\n\n" + answer)
                            } else {
                                self.instructions.text = emailIsVerified + "\n" + answer
                                self.main?.page2.onVerified()
                                self.main?.page3.onVerified()
                            }
                        }
                    }
                }
            }
        }
    }
    
    @objc func verify(sender: UIButton) {
        verifyEmail(emailTextField.text ?? "")
    }

    @objc func unsubscribe(sender: UIButton) {
        restAPI("https://luxcedia.icu/cgi-bin/unsubscribe",["id":emailTextField.text ?? ""]) { error, data, status in
            if error == nil, let data = data {
                if let answer = String(data:data, encoding: .utf8)  {
                    if status != 200 {
                        DispatchQueue.main.async {
                            self.instructions.text = ("Problem with unsubscribe:\n\n" + answer)
                        }
                        
                    } else {
                        DispatchQueue.main.async {
                            self.instructions.text = answer
                            self.emailTextField.text = nil
                            UserDefaults.standard.set("",forKey: "email")
                        }
                    }
                }
            }
        }
    }
}
