package dev.orestegabo.portfolio.network
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
internal actual fun createPlatformHttpEngine(): HttpClientEngine = CIO.create()
internal actual fun createPublicQuoteHttpEngine(): HttpClientEngine = createPlatformHttpEngine()
actual val allowsDevelopmentHttp: Boolean = false
