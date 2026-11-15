package dev.orestegabo.portfolio.investments

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PickedFileBytesTest {
    @Test fun validBytesAtTheLimitAreRetainedAndTheCallerStillOwnsTheStream() = runTest {
        var closed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() { closed = true; super.close() }
        }
        assertContentEquals(byteArrayOf(1, 2, 3), readPickedFileBytes(source, 3, "limit"))
        assertFalse(closed)
    }
    @Test fun emptyAndOversizedSelectionsThrowInsteadOfReturningSuccess() = runTest {
        assertEquals("The selected PDF is empty.", assertFailsWith<IllegalArgumentException> {
            readPickedFileBytes(ByteArrayInputStream(byteArrayOf()), 3, "limit")
        }.message)
        assertEquals("limit", assertFailsWith<IllegalArgumentException> {
            readPickedFileBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3, "limit")
        }.message)
    }
    @Test fun aReadFailureIsPropagatedRatherThanReportedAsCancellationOrEmptyBytes() = runTest {
        val failure = IOException("synthetic read failure")
        val source = object : InputStream() { override fun read(): Int = throw failure }
        assertSame(failure, assertFailsWith<IOException> { readPickedFileBytes(source, 3, "limit") })
    }
    @Test fun cancelledReadingStopsBeforeAnotherStreamRead() = runTest {
        var reads = 0
        val job = launch {
            val context = currentCoroutineContext()
            val source = object : InputStream() {
                override fun read(): Int = error("Use buffered reading")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    reads++
                    context[kotlinx.coroutines.Job]!!.cancel()
                    buffer[0] = 1
                    return 1
                }
            }
            assertFailsWith<CancellationException> { readPickedFileBytes(source, 3, "limit") }
        }
        job.join()
        assertEquals(1, reads)
    }
}
