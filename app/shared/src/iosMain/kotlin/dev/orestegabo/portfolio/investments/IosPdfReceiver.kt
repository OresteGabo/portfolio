@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*

/** Open In uses a system-provided local URL; never fetch arbitrary remote URLs. */
object IosPdfReceiver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun receive(url: String) {
        val file = NSURL.URLWithString(url)
        if (file == null || !file.fileURL) { NativePdfInbox.reject("Could not open the selected PDF."); return }
        scope.launch {
            try { NativePdfInbox.offer(readIosSharedPdf(file), NSUUID().UUIDString, file.lastPathComponent ?: "Shared PDF") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { NativePdfInbox.reject(userErrorMessage(failure, "Could not open the selected PDF.")) }
        }
    }
}

internal suspend fun readIosSharedPdf(file: NSURL): ByteArray {
    require(file.fileURL) { "Could not open the selected PDF." }
    val access = file.startAccessingSecurityScopedResource()
    try {
        return withContext(Dispatchers.Default) {
            val stream = NSInputStream(uRL = file)
            stream.open()
            try {
                val chunks = mutableListOf<ByteArray>()
                var total = 0
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = buffer.usePinned { stream.read(it.addressOf(0).reinterpret(), maxLength = buffer.size.toULong()) }.toInt()
                    require(count >= 0) { "Could not read the selected PDF." }
                    if (count == 0) break
                    total += count
                    require(total <= MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
                    chunks += buffer.copyOf(count)
                }
                ByteArray(total).also { output ->
                    var offset = 0
                    chunks.forEach { it.copyInto(output, offset); offset += it.size }
                }
            } finally { stream.close() }
        }
    } finally {
        if (access) file.stopAccessingSecurityScopedResource()
        // Delete only the system's copy in our own Inbox, never the sender's original.
        val inbox = NSHomeDirectory() + "/Documents/Inbox/"
        val path = file.path.orEmpty()
        val leaf = path.removePrefix(inbox)
        if (path.startsWith(inbox) && leaf.isNotEmpty() && leaf !in setOf(".", "..") && '/' !in leaf) {
            NSFileManager.defaultManager.removeItemAtURL(file, error = null)
        }
    }
}
