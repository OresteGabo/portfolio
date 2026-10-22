package dev.orestegabo.portfolio.investments
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
actual fun createPlatformHttpEngine(): HttpClientEngine = CIO.create()
actual val allowsDevelopmentHttp: Boolean = false
