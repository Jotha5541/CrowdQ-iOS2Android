//
//  ViewController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/15/26.
//

import UIKit
import AVKit
import BluedogI





class ViewController: UIViewController {
    let observer = BridgeObserver()
    let primary = Primary()
    let settings = Settings()
    let about = About()
    let pager = PagerViewController(
        transitionStyle: .scroll,
        navigationOrientation: .horizontal
    )
    var show : BlueDogMessage?
    var interpreter : Bluedog?
    var alert : UIAlertController? = nil

    let soundPlayer = SoundController()
    let flashPlayer = FlashController()
    let imagePlayer = ImageController()
    let buzzPlayer = BuzzController()
    
    var quadrant : Int = 0
    
    override func viewDidLoad() {
        super.viewDidLoad()
        quadrant = Int.random(in: 0...3) // Location will override
        observer.main = self
        
        // Don't allow app to timeout and sleep on the idle timer...
        UIApplication.shared.isIdleTimerDisabled = true
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification,
                                               object: nil,
                                               queue: .main) { _ in
            print("App is coming back to the foreground!",UIApplication.shared.isIdleTimerDisabled)
        }

        soundPlayer.addSound(view: self, silence, 0)
        pager.pages.append(primary)
        pager.pages.append(settings)
        pager.pages.append(about)
        
        settings.add("startup")
    }
    
    private var didPresent = false
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        // Just once
        guard !didPresent else { return }
        didPresent = true
        
        pager.modalPresentationStyle = .fullScreen
        present(pager, animated: true)
    }
    
    func executeCommand(_ command : String, _ argument : Int) {
        interpreter?.interpret_command(command, dollar:argument, quadrant: self.quadrant)
    }
    
    func loadNewShow(_ jsonName : String) {
        print("Start loading show file",jsonName)
        self.settings.add("loading \(jsonName)")
        primary.toggleBouncing(isOn: false)
        getBlueDog(jsonName+".json") { raw,show,code in
            self.settings.add("found \(jsonName)")
            guard code == 200 else {
                self.settings.add("failed loading \(jsonName)")
                return
            }
            if let show = show {
                self.settings.add("loaded \(jsonName)")
                DispatchQueue.main.async {
                    print("Setting showLabel",jsonName)
                    self.settings.showLabel.text = jsonName
                    print(self.settings.showLabel)
                    print(self.settings.showLabel.text)
                }
                
                self.show = show
                self.interpreter = Bluedog(show,
                                           flasher: self.flasher,
                                           render: self.render,
                                           player: self.player,
                                           buzzer: self.buzzer,
                                           background: self.background,
                                           printer: self.printer,
                                           video: self.video,
                                           sync: self.sync)
                if self.interpreter == nil {
                    print("the show that was loaded was invalid")
                }
            }
        }
    }
    func flasher(_ intensity : Double, duration: Int)->Void {
        print("flasher",intensity,duration)
        flashPlayer.addFlash(intensity, duration)
    }
    func render (_ image : UIImage, duration: Int)->Void {
        imagePlayer.addImage(view: primary, image, duration)
    }
    func player(_ sound : Data, duration: Int)->Void {
        soundPlayer.addSound(view: self, sound, duration)
    }
    func buzzer(_ pattern : Int, duration: Int)->Void {
        buzzPlayer.addBuzz(pattern,duration)
    }
    func background(_ image : UIImage) -> Void {
        DispatchQueue.main.async {
            self.primary.background.image = image
        }
    }
    func printer(_ message : String) -> Void {
        DispatchQueue.main.async {
            // Pop down the old message
            self.alert?.dismiss(animated: false)
            
            self.alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            self.alert?.addAction(UIAlertAction(title: "Dismiss", style: .default, handler: nil))
            
            if let alert = self.alert {
                self.primary.present(alert, animated: true, completion: nil)
            }
        }
    }
    func video(_ path : String) -> Void {
        print("play",path)
        DispatchQueue.main.async {
            if let url = URL(string: path) {
                let player = AVPlayer(url: url)
                let playerVC = AVPlayerViewController()
                playerVC.player = player

                // Popup-style presentation
                playerVC.modalPresentationStyle = .pageSheet
                if let sheet = playerVC.sheetPresentationController {
                    sheet.detents = [.medium(), .large()]
                    sheet.prefersGrabberVisible = true
                }
                
                // Observe playback end
                NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemDidPlayToEndTime,
                    object: player.currentItem,
                    queue: .main
                ) { [weak playerVC] _ in
                    playerVC?.dismiss(animated: true)
                }

                self.primary.present(playerVC, animated: true) {
                    player.play()
                }
            }
        }
    }

    func sync() {
        // We want to wait for all queues to be empty
        // We add an operation to each that will only run when all prior jobs
        // are complete
        let group = DispatchGroup()

        // We have 4 pipelines
        group.enter()
        group.enter()
        group.enter()
        group.enter()

        // Wait until all four leave this group because then all
        // previous work is complete
        imagePlayer.workQueue.addOperation {
            print("image sync'd")
            group.leave()
        }
        
        soundPlayer.workQueue.addOperation {
            print("sound sync'd")
            group.leave()
        }
        
        flashPlayer.workQueue.addOperation {
            print("flash sync'd")
            group.leave()
        }
        
        buzzPlayer.workQueue.addOperation {
            print("buzz sync'd")
            group.leave()
        }
        
        let wait = DispatchSemaphore(value: 0)
        group.notify(queue: .main) {
            wait.signal()
        }
        wait.wait()
    }
    
    
}

