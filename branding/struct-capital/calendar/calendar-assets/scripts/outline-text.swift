// Emit portable SVG outlines for illustration labels; no runtime fonts required.
import Foundation
import CoreText
import CoreGraphics
let labels = ["Bond Subscription", "Opens", "Subscription", "Window Closes", "Dividend", "Payment", "Notification", "Dividend Payment", "Coupon Paid", "Mar 27,", "Mar 19,", "2023"] + (1...31).map(String.init)
let font = CTFontCreateWithName("ArialMT" as CFString, 1000, nil)
var result: [String: [String: Any]] = [:]
func n(_ x: CGFloat) -> String { String(format: "%.3f", Double(x)) }
for text in labels {
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font]))
    var data = ""
    for run in CTLineGetGlyphRuns(line) as! [CTRun] {
        let count = CTRunGetGlyphCount(run)
        var glyphs = [CGGlyph](repeating: 0, count: count)
        var positions = [CGPoint](repeating: .zero, count: count)
        CTRunGetGlyphs(run, CFRange(), &glyphs)
        CTRunGetPositions(run, CFRange(), &positions)
        for i in 0..<count {
            guard let path = CTFontCreatePathForGlyph(font, glyphs[i], nil) else { continue }
            let pos = positions[i]
            func point(_ p: CGPoint) -> String { "\(n(p.x + pos.x)) \(n(-p.y - pos.y))" }
            path.applyWithBlock { element in
                let e = element.pointee
                switch e.type {
                case .moveToPoint: data += "M\(point(e.points[0]))"
                case .addLineToPoint: data += "L\(point(e.points[0]))"
                case .addQuadCurveToPoint: data += "Q\(point(e.points[0])) \(point(e.points[1]))"
                case .addCurveToPoint: data += "C\(point(e.points[0])) \(point(e.points[1])) \(point(e.points[2]))"
                case .closeSubpath: data += "Z"
                @unknown default: break
                }
            }
        }
    }
    result[text] = ["path": data, "width": CTLineGetTypographicBounds(line, nil, nil, nil)]
}
let output = try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys])
FileHandle.standardOutput.write(output)
