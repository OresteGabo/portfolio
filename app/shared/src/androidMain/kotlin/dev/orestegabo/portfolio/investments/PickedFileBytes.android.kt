package dev.orestegabo.portfolio.investments

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Caller owns stream lifetime and dispatcher. Read failures never become empty selections. */
internal suspend fun readPickedFileBytes(input: InputStream, maximum: Int, limitMessage: String): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= maximum) { limitMessage }
        output.write(buffer, 0, count)
    }
    require(output.size() > 0) { "The selected PDF is empty." }
    return output.toByteArray()
}
