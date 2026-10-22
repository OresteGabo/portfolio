@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package dev.orestegabo.portfolio.investments
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlin.native.Platform
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
actual fun createPlatformHttpEngine(): HttpClientEngine = Darwin.create {
    configureSession {
        HTTPCookieStorage = null
        HTTPShouldSetCookies = false
        URLCache = null
        requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
    }
}
actual val allowsDevelopmentHttp: Boolean get() = Platform.isDebugBinary
