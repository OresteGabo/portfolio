@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.test.runTest
import platform.Foundation.*
import kotlin.test.*

class IosPdfReceiverTest {
    @Test fun nativeFileHandoffReadsBoundedBytesAndNeverDeletesTheOriginalOrLoadsRemoteUrls() = runTest {
        val manager = NSFileManager.defaultManager
        val directory = NSTemporaryDirectory() + "struct-share-test-" + NSUUID().UUIDString
        manager.createDirectoryAtPath(directory, true, null, null)
        val original = NSURL.fileURLWithPath("$directory/shared proof.pdf")
        val bytes = "%PDF-1.7 synthetic receipt\n%%EOF".encodeToByteArray()
        try {
            assertTrue(bytes.toData().writeToURL(original, true))
            assertContentEquals(bytes, readIosSharedPdf(original))
            assertTrue(manager.fileExistsAtPath(original.path!!))
            val inbox = PdfImportInbox()
            assertTrue(inbox.offer(readIosSharedPdf(original), "ios-open-in"))
            assertContentEquals(bytes, inbox.pending!!.bytes)
            assertFailsWith<IllegalArgumentException> { readIosSharedPdf(NSURL.URLWithString("https://example.invalid/private.pdf")!!) }
            assertTrue(ByteArray(MAX_PDF_BYTES + 1).toData().writeToURL(original, true))
            assertFailsWith<IllegalArgumentException> { readIosSharedPdf(original) }
            assertTrue(manager.fileExistsAtPath(original.path!!))
            // The system-owned copy inside this app's Inbox is cleaned after receipt.
            val inboxDirectory = NSHomeDirectory() + "/Documents/Inbox"
            manager.createDirectoryAtPath(inboxDirectory, true, null, null)
            val copy = NSURL.fileURLWithPath("$inboxDirectory/${NSUUID().UUIDString}.pdf")
            assertTrue(bytes.toData().writeToURL(copy, true))
            assertContentEquals(bytes, readIosSharedPdf(copy))
            assertFalse(manager.fileExistsAtPath(copy.path!!))
            val outside = NSURL.fileURLWithPath(NSHomeDirectory() + "/Documents/${NSUUID().UUIDString}.pdf")
            try {
                assertTrue(bytes.toData().writeToURL(outside, true))
                val traversal = NSURL.fileURLWithPath("$inboxDirectory/../" + outside.lastPathComponent)
                assertContentEquals(bytes, readIosSharedPdf(traversal))
                assertTrue(manager.fileExistsAtPath(outside.path!!))
            } finally { manager.removeItemAtURL(outside, null) }
        } finally { manager.removeItemAtPath(directory, null) }
    }
}
