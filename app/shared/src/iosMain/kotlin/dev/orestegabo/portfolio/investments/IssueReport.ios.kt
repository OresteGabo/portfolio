package dev.orestegabo.portfolio.investments

import platform.UIKit.UIDevice

internal actual fun issueReportPlatform(): String = UIDevice.currentDevice.let { "${it.systemName} ${it.systemVersion}" }
