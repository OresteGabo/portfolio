package dev.orestegabo.portfolio.investments
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
actual fun createPlatformHttpEngine(): HttpClientEngine = Android.create()
actual val allowsDevelopmentHttp: Boolean get() = PlatformBuild.debug
/** Set by the Android host using its build variant; production defaults to HTTPS-only. */
object PlatformBuild { var debug: Boolean = false }
