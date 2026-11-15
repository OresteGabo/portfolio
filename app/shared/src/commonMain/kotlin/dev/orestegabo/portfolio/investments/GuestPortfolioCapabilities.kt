package dev.orestegabo.portfolio.investments

/** Snapshot and original-document access, without workspace mutation operations.
 * Returned values retain the repository's existing detached-copy semantics.
 */
interface GuestPortfolioReader {
    suspend fun load(): GuestPortfolio
    suspend fun documentBytes(id: String): ByteArray
}

/** Review/import workflows can read evidence and atomically save an imported investment.
 * Snapshot and PDF persistence stay with one owner; no separate file/record writes are exposed.
 */
interface GuestInvestmentImporter : GuestPortfolioReader {
    suspend fun importPdf(bytes: ByteArray, fileName: String, kind: String, body: JsonRecord, targetId: String? = null): GuestPortfolio
}
