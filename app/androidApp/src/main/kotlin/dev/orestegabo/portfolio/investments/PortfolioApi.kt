package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI

class ApiException(val status: Int, message: String) : Exception(message)

/** A single in-memory cookie jar per app session. Passwords and holdings never go to disk. */
class PortfolioApi(val origin: String) {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    init {
        val uri = URI(origin)
        require(uri.scheme == "https" || (BuildConfigAccess.debug && uri.scheme == "http")) {
            "Use an HTTPS server URL. HTTP is available in debug builds only."
        }
        require(!uri.host.isNullOrEmpty() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty().trim('/').isEmpty()) {
            "Enter the server origin only, for example https://bonds.orestegabo.dev"
        }
    }

    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val raw = bytes(path, method, body).toString(Charsets.UTF_8)
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    suspend fun bytes(path: String, method: String = "GET", body: JSONObject? = null): ByteArray = withContext(Dispatchers.IO) {
        require(path.startsWith("/api/bonds/") && !path.contains("..")) { "Invalid API path." }
        val uri = URI(origin + path)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            // Never forward private cookies to a redirect target.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json, application/pdf")
            connection.setRequestProperty("Origin", origin)
            cookies.get(uri, emptyMap()).forEach { (key, values) -> connection.setRequestProperty(key, values.joinToString("; ")) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields.filterKeys { it != null })
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val content = stream?.use {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = it.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= 24 * 1024 * 1024) { "Response is too large." }
                    buffer.write(chunk, 0, count)
                }
                buffer.toByteArray()
            } ?: byteArrayOf()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(content.toString(Charsets.UTF_8)).optString("error") }.getOrNull()
                throw ApiException(status, message?.takeIf { it.isNotBlank() } ?: "Server returned HTTP $status. Please try again.")
            }
            content
        } finally { connection.disconnect() }
    }

    fun clearSession() { cookies.cookieStore.removeAll() }
}

private object BuildConfigAccess { val debug = dev.orestegabo.portfolio.BuildConfig.DEBUG }
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()
fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
