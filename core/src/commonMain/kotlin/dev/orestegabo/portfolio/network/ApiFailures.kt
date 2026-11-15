package dev.orestegabo.portfolio.network

/** A successful HTTP response could not be decoded as the existing JSON object contract.
 * The original decoder cause is retained for local diagnosis, never as user-facing text/log data.
 * A failed response decode does not establish whether a preceding write completed.
 */
class ApiResponseException(cause: Throwable) : Exception(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "The server response could not be read. Refresh before trying again."
    }
}

/** Transport timeout, separate from cancellation requested by the operation's owner.
 * Retains the platform cause without exposing its URL/body. Does not authorize automatic replay.
 */
class ApiTimeoutException(cause: Throwable) : Exception(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "The request timed out. Refresh before trying again."
    }
}
