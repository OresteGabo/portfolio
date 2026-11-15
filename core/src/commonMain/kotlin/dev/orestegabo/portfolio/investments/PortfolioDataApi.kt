package dev.orestegabo.portfolio.investments

/** Data-only API capability. Features cannot restore/clear sessions or close the authenticated client. */
interface PortfolioDataApi {
    suspend fun request(path: String, method: String = "GET", body: JsonRecord? = null): JsonRecord
    suspend fun bytes(path: String, method: String = "GET", body: JsonRecord? = null): ByteArray
}
