//
//  SoundController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/26/26.
//
import Foundation
import AVKit

class SoundController {
    let semaphore = DispatchSemaphore(value:1)
    var workQueue = OperationQueue()
    var audioPlayer: AVAudioPlayer?
    
    init() {
        workQueue.maxConcurrentOperationCount = 1
        
        // The audio play works better if we play a sound to "warm it up"
        if let asset = NSDataAsset(name: "Silent") {
            do {
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
                
                audioPlayer = try AVAudioPlayer(data: asset.data)
                audioPlayer?.numberOfLoops = 0  // play once
                audioPlayer?.play()
            } catch {
                print("Error playing sound: \(error)")
            }
        }
    }
    
    func addSound(view: ViewController,_ sound : Data, _ duration : Int) {
        workQueue.addOperation {
            self.playSound(view: view,sound,duration)
        }
    }
    
    private func playSound(view: ViewController, _ sound : Data, _ duration : Int) {
        defer { semaphore.signal() }
        semaphore.wait()
        do {
            audioPlayer = try AVAudioPlayer(data: sound)
            print("Play on",audioPlayer)

            audioPlayer?.numberOfLoops = 0  // play once
            audioPlayer?.play()
        } catch {
            print("Error playing sound: \(error)")
        }
        Thread.sleep(forTimeInterval: Double(duration) / 1000.0)
    }
}
