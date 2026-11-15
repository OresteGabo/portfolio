package dev.orestegabo.portfolio.network

/** Account-bound data access; request adapters can check origin without owning a session. */
interface OriginBoundDataApi : PortfolioDataApi {
    val origin: String
}

/** Session capability owned by the application's session/lifecycle coordinator.
 * Snapshots are opaque to callers and can be restored only by a compatible client at the same origin.
 * [hasSession] reports whether restorable session credentials are present, not server authentication.
 * Closing releases the client; the owner supplies a replacement before further use.
 */
interface PortfolioSessionApi : OriginBoundDataApi {
    suspend fun hasSession(): Boolean
    suspend fun sessionSnapshot(): String
    suspend fun restoreSession(snapshot: String)
    suspend fun clearSession()
    fun close()
}
