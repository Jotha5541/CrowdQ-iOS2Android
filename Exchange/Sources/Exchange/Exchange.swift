// The Swift Programming Language
// https://docs.swift.org/swift-book
import Foundation

// Restricted from 0-15 to keep within 4 bit field
public enum CrowdQExchangeTag : Int {
    case load = 0
    case restart = 1
    case command = 2
    case show = 3
    case showdata = 4
}

func decodeFourChar(_ xxxx : String) -> Int32? {
    // The cluster of 4 base64 chars yeilds 3 bytes.  Prepend a zero
    if let data = Data(base64Encoded:xxxx) {
        let int32Value : Int32? = (Data([UInt8(0)]) + data).withUnsafeBytes { rawBufferPointer in
            // Ensure the data has at least enough bytes for an Int32 (note that
            // data is in big endian order
            guard rawBufferPointer.count >= MemoryLayout<Int32>.size else {
                return nil
            }
            return Int32(bigEndian:rawBufferPointer.load(as: Int32.self))
        }
        return int32Value
    } else {
        return nil
    }
}

public struct CrowdQExchange {
    public let sequence : Int
    public let tag : CrowdQExchangeTag
    public let argument : Int
    public let payload : String

    public init?(_ packet : String) {
        guard packet.count >= 8 else { return nil }
        // break AAAA BBBB xxxxxx
        let startArgument = packet.index(packet.startIndex,offsetBy: 4)
        let startData = packet.index(packet.startIndex,offsetBy: 8)
        let sequenceTag64 = String(packet[packet.startIndex..<startArgument])
        let argument64 = String(packet[startArgument..<startData])
        
        if let sequenceTag = decodeFourChar(sequenceTag64),
           let argument = decodeFourChar(argument64) {
            sequence = Int(sequenceTag >> 4)
            if let tag = CrowdQExchangeTag(rawValue: Int(sequenceTag & 0xF)) {
                self.tag = tag
            } else {
                return nil
            }
            self.argument = Int(argument)
        } else {
            return nil
        }
        payload = String(packet[startData...])
    }

    public init(sequence : Int, tag : CrowdQExchangeTag, argument : Int, payload : String) {
        self.sequence = sequence
        self.tag = tag
        self.argument = argument
        self.payload = payload
    }

    func byteArray(from value: Int32) -> [UInt8]  {
        var bytes = withUnsafeBytes(of: value.bigEndian, Array.init)
        bytes.removeFirst() // Big endian, network byte order
        return bytes
    }

    public func pack() -> String {
        // We build a 20 bit sequence value + the 4 bit tag => 3 bytes
        let block1 : Int32 = Int32(sequence << 4 | tag.rawValue)
        var bytes = byteArray(from: block1) + byteArray(from: Int32(argument))
        let prefix = Data(bytes).base64EncodedString()
        return prefix + payload
    }
}
    
