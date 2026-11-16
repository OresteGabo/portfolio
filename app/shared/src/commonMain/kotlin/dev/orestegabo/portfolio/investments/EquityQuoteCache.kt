package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

/** Only public price fields are persisted, never holdings, personal balances, sessions or arbitrary API fields. */
internal class EquityQuoteCache(private val settings: ServerSettings) {
    data class Entry(val quotes: JsonRecord, val receivedAt: Instant)

    fun read(account: String, now: Instant): Entry? = safely {
        val raw = settings.readCachedEquityQuotes(account) ?: return@safely null
        require(raw.length <= MAX_LENGTH)
        val root = JsonRecord(raw)
        require(root.optInt("version") == 1)
        val receivedAt = Instant.parse(root.text("receivedAt"))
        require(receivedAt <= now)
        val quotes = sanitize(root.getJSONObject("quotes"), now)
        quotes.put("source", quotes.getJSONObject("source").put("stale", true))
        Entry(quotes, receivedAt)
    }

    fun write(account: String, quotes: JsonRecord, now: Instant) {
        safely {
            val raw = JsonRecord().put("version", 1).put("receivedAt", now.toString())
                .put("quotes", sanitize(quotes, now)).toString()
            require(raw.length <= MAX_LENGTH)
            settings.writeCachedEquityQuotes(account, raw)
        }
    }

    companion object {
        private const val MAX_LENGTH = 128 * 1024

        /** A malformed or future-dated board must not replace a usable cached valuation. */
        fun sanitize(raw: JsonRecord, now: Instant): JsonRecord {
            val source = raw.getJSONObject("source")
            val publishedAt = source.text("publishedAt")
            val date = requireNotNull(calendarDate(publishedAt.take(10)))
            if (publishedAt.length != 10) require(Instant.parse(publishedAt) <= now)
            require(date <= bondSubscriptionDate(now) && source.text("status") != "error")
            val rows = raw.rows("quotes")
            require(rows.size in 1..200 && rows.size == raw.getJSONArray("quotes").length())
            val quotes = rows.map { row ->
                val ticker = row.text("ticker").trim().uppercase()
                val closing = row.getDouble("closing")
                require(ticker.matches(Regex("[A-Z][A-Z0-9]{1,9}")) && closing.isFinite() && closing > 0)
                JsonRecord().put("ticker", ticker).put("closing", closing).put("name", row.text("name").take(160))
            }
            require(quotes.map { it.text("ticker") }.distinct().size == quotes.size)
            return JsonRecord().put("source", JsonRecord().put("publishedAt", publishedAt)
                .put("stale", source.optBoolean("stale"))).put("quotes", JsonList(quotes))
        }
    }
}

private inline fun <T> safely(block: () -> T): T? = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null } // Optional public cache failure must never prevent opening the portfolio.
