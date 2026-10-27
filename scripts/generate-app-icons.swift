// Export native launcher sizes from the checked-in Struct Capital masters.
// Run from the repository root: swift scripts/generate-app-icons.swift
import AppKit
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
func render(source: String, size: Int, destination: String) {
    let input = CGImageSourceCreateWithURL(root.appendingPathComponent(source) as CFURL, nil)!
    let image = CGImageSourceCreateImageAtIndex(input, 0, nil)!
    let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.interpolationQuality = .high
    context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    let output = CGImageDestinationCreateWithURL(root.appendingPathComponent(destination) as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(output, context.makeImage()!, nil)
    precondition(CGImageDestinationFinalize(output))
}
let dark = "branding/struct-capital/struct-capital-icon-1024.png"
let light = "branding/struct-capital/struct-capital-icon-light-1024.png"
let ios = "app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/"
render(source: light, size: 1024, destination: ios + "app-icon-1024.png")
render(source: dark, size: 1024, destination: ios + "app-icon-dark-1024.png")
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    for name in ["ic_launcher", "ic_launcher_round"] {
        render(source: dark, size: size, destination: "app/androidApp/src/main/res/mipmap-\(density)/\(name).png")
    }
}
print("Rendered Struct Capital launcher icons for Android and iOS (light/dark)")
