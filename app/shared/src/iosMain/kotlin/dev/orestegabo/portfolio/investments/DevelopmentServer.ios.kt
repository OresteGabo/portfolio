@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
package dev.orestegabo.portfolio.investments

import kotlin.native.Platform
import platform.Foundation.NSBundle

actual fun developmentApiOrigin(): String? = developmentOrigin(
    Platform.isDebugBinary,
    NSBundle.mainBundle.objectForInfoDictionaryKey("GaboDevelopmentApiOrigin") as? String,
)
