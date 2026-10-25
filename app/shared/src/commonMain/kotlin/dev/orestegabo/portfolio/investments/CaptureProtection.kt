package dev.orestegabo.portfolio.investments

/** Platform masking fallback while the capture shield is unavailable or capture is active. */
expect val captureProtectionRequiresMaskedNumbers: Boolean
expect val captureProtectionDescription: String

/** Android's inactive-window privacy remains enabled even after foreground capture is permitted. */
fun protectCaptureWindow(protectionEnabled: Boolean, inBackground: Boolean) = protectionEnabled || inBackground
