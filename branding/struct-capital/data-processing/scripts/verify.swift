// Check every production PNG using native macOS image decoding.
import Foundation
import ImageIO
import CoreGraphics
let base = URL(fileURLWithPath: CommandLine.arguments[1])
let items = try JSONSerialization.jsonObject(with: Data(contentsOf: base.appendingPathComponent("manifest.json"))) as! [[String: Any]]
var count = 0
for item in items {
    for export in item["png"] as! [[String: Any]] {
        let path = export["path"] as! String
        let source = CGImageSourceCreateWithURL(base.appendingPathComponent(path) as CFURL, nil)!
        let image = CGImageSourceCreateImageAtIndex(source, 0, nil)!
        precondition(image.width == export["width"] as! Int && image.height == export["height"] as! Int, path)
        let w = image.width, h = image.height
        var pixels = [UInt8](repeating: 0, count: w*h*4)
        pixels.withUnsafeMutableBytes { buffer in
            let context = CGContext(data: buffer.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w*4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            context.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        let edges = (0..<w).map { $0 } + (0..<w).map { (h-1)*w+$0 } + (0..<h).map { $0*w } + (0..<h).map { $0*w+w-1 }
        precondition(edges.allSatisfy { pixels[$0*4+3] == 0 }, "Clipped or opaque edge: \(path)")
        precondition(stride(from: 3, to: pixels.count, by: 4).contains { pixels[$0] > 0 }, "Empty image: \(path)")
        count += 1
    }
}
print("Verified \(count) PNGs: expected dimensions, visible artwork, transparent canvases, and clear edges.")
