// Render the web project's checked-in SVG brand mark into native launcher sizes.
// Run from the repository root: swift scripts/generate-app-icons.swift
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

final class BrandSVG: NSObject, XMLParserDelegate {
    var rect: [String: String] = [:]
    var mark: [String: String] = [:]
    var dot: [String: String] = [:]
    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String]) {
        if name == "rect" { rect = attributes }
        if name == "path" { mark = attributes }
        if name == "circle" { dot = attributes }
    }
}
func color(_ hex: String) -> CGColor {
    let value = UInt32(hex.dropFirst(), radix: 16)!
    return CGColor(colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, components: [CGFloat((value >> 16) & 255) / 255, CGFloat((value >> 8) & 255) / 255, CGFloat(value & 255) / 255, 1])!
}
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let svg = BrandSVG()
let parser = XMLParser(contentsOf: root.appendingPathComponent("branding/gabo-icon.svg"))!
parser.delegate = svg
precondition(parser.parse())
let tokens = svg.mark["d"]!.split(separator: " ").map(String.init)
let path = CGMutablePath()
var command: Character = "M"
var x: CGFloat = 0, y: CGFloat = 0
var i = 0
// The source brand path uses absolute M/H/L commands only.
while i < tokens.count {
    var token = tokens[i]
    if let first = token.first, first.isLetter { command = first; token.removeFirst() }
    if token.isEmpty { i += 1; continue }
    let value = CGFloat(Double(token)!)
    switch command {
    case "M", "L":
        x = value; i += 1; y = CGFloat(Double(tokens[i])!)
        if command == "M" { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
    case "H": x = value; path.addLine(to: CGPoint(x: x, y: y))
    default: fatalError("Unsupported SVG brand command")
    }
    i += 1
}
func render(size: Int, destination: String) {
    let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.scaleBy(x: CGFloat(size) / 100, y: CGFloat(size) / 100)
    context.translateBy(x: 0, y: 100)
    context.scaleBy(x: 1, y: -1)
    context.setFillColor(color(svg.rect["fill"]!))
    context.fill(CGRect(x: 0, y: 0, width: 100, height: 100))
    context.setStrokeColor(color(svg.mark["stroke"]!))
    context.setLineWidth(CGFloat(Double(svg.mark["stroke-width"]!)!))
    context.setLineJoin(.round); context.setLineCap(.round)
    context.addPath(path); context.strokePath()
    let radius = CGFloat(Double(svg.dot["r"]!)!)
    context.setFillColor(color(svg.dot["fill"]!))
    context.fillEllipse(in: CGRect(x: CGFloat(Double(svg.dot["cx"]!)!) - radius, y: CGFloat(Double(svg.dot["cy"]!)!) - radius, width: radius * 2, height: radius * 2))
    let url = root.appendingPathComponent(destination)
    let output = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(output, context.makeImage()!, nil)
    precondition(CGImageDestinationFinalize(output))
}
render(size: 1024, destination: "app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png")
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    for name in ["ic_launcher", "ic_launcher_round"] {
        render(size: size, destination: "app/androidApp/src/main/res/mipmap-\(density)/\(name).png")
    }
}
print("Rendered native launcher icons from branding/gabo-icon.svg")
