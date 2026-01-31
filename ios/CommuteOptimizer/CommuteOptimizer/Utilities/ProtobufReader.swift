import Foundation

/// Manual protobuf decoder for GTFS-Realtime feeds
/// Avoids SwiftProtobuf dependency, matches webapp/Android implementations
class ProtobufReader {
    private let data: Data
    private var position: Int = 0

    init(data: Data) {
        self.data = data
    }

    var hasMore: Bool {
        position < data.count
    }

    func readVarint() -> UInt64 {
        var result: UInt64 = 0
        var shift: UInt64 = 0

        while position < data.count {
            let byte = data[position]
            position += 1
            result |= UInt64(byte & 0x7F) << shift
            if (byte & 0x80) == 0 { break }
            shift += 7
        }
        return result
    }

    func readString(length: Int) -> String {
        let endIndex = min(position + length, data.count)
        let bytes = data[position..<endIndex]
        position = endIndex
        return String(data: Data(bytes), encoding: .utf8) ?? ""
    }

    func readBytes(length: Int) -> Data {
        let endIndex = min(position + length, data.count)
        let bytes = Data(data[position..<endIndex])
        position = endIndex
        return bytes
    }

    func skip(wireType: Int) {
        switch wireType {
        case 0: _ = readVarint()           // Varint
        case 1: position += 8              // 64-bit
        case 2:                            // Length-delimited
            let len = Int(readVarint())
            position += len
        case 5: position += 4              // 32-bit
        default: break
        }
    }
}
