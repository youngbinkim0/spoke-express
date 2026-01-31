import XCTest
@testable import CommuteOptimizer

final class ProtobufReaderTests: XCTestCase {

    func testReadVarint_SingleByte() {
        // Value 1 encoded as single byte
        let data = Data([0x01])
        let reader = ProtobufReader(data: data)
        XCTAssertEqual(reader.readVarint(), 1)
    }

    func testReadVarint_MultiByte() {
        // Value 300 = 0b100101100 encoded as [0xAC, 0x02]
        let data = Data([0xAC, 0x02])
        let reader = ProtobufReader(data: data)
        XCTAssertEqual(reader.readVarint(), 300)
    }

    func testReadString() {
        let testString = "hello"
        let data = Data(testString.utf8)
        let reader = ProtobufReader(data: data)
        XCTAssertEqual(reader.readString(length: 5), "hello")
    }

    func testHasMore() {
        let data = Data([0x01, 0x02])
        let reader = ProtobufReader(data: data)
        XCTAssertTrue(reader.hasMore)
        _ = reader.readVarint()
        XCTAssertTrue(reader.hasMore)
        _ = reader.readVarint()
        XCTAssertFalse(reader.hasMore)
    }

    func testReadBytes() {
        let data = Data([0x01, 0x02, 0x03, 0x04])
        let reader = ProtobufReader(data: data)
        let bytes = reader.readBytes(length: 2)
        XCTAssertEqual(bytes, Data([0x01, 0x02]))
        XCTAssertTrue(reader.hasMore)
    }
}
