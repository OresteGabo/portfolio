@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import platform.Foundation.*
import kotlin.test.*

class ProtectedTemporaryExportTest {
    private val bytes = "%PDF-1.7 synthetic export\n%%EOF".encodeToByteArray()
    private val files get() = NSFileManager.defaultManager

    @Test fun stagingIsBoundedProtectedAndRemovedAfterUseWithoutChangingCallerBytes() = runTest {
        val original = bytes.copyOf()
        var directory: String? = null
        withProtectedTemporaryExport("../../outside.pdf", bytes) { url ->
            val path = assertNotNull(url.path)
            directory = url.URLByDeletingLastPathComponent?.path
            assertTrue(path.startsWith(NSTemporaryDirectory() + "inzira-exports/"))
            assertEquals(".._.._outside.pdf", url.lastPathComponent)
            assertContentEquals(original, assertNotNull(NSData.dataWithContentsOfURL(url)).toBytes())
            val root = assertNotNull(url.URLByDeletingLastPathComponent?.URLByDeletingLastPathComponent)
            val resource = assertNotNull(root.resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null))
            assertEquals(true, (resource[NSURLIsExcludedFromBackupKey] as? NSNumber)?.boolValue)
            val attrs = assertNotNull(files.attributesOfItemAtPath(path, null))
            val protection = attrs[NSFileProtectionKey]
            if (protection != null) assertEquals(NSFileProtectionComplete, protection.toString())
            else assertNotNull(NSProcessInfo.processInfo.environment["SIMULATOR_UDID"],
                "Physical devices must expose protection metadata; simulator lock enforcement is not claimed.")
        }
        assertFalse(files.fileExistsAtPath(assertNotNull(directory)))
        assertContentEquals(original, bytes)
    }

    @Test fun failureAndCancellationBothRemoveOwnedStaging() = runTest {
        var directory: String? = null
        val failure = IllegalStateException("Synthetic recipient failure")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            withProtectedTemporaryExport("proof.pdf", bytes) { url ->
                directory = url.URLByDeletingLastPathComponent?.path
                throw failure
            }
        })
        assertFalse(files.fileExistsAtPath(assertNotNull(directory)))
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            withProtectedTemporaryExport("proof.pdf", bytes) { url ->
                directory = url.URLByDeletingLastPathComponent?.path
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await(); job.cancelAndJoin()
        assertFalse(files.fileExistsAtPath(assertNotNull(directory)))
    }

    @Test fun legitimateShareAndBackupNamesKeepTheirExtensions() = runTest {
        for (name in listOf("x".repeat(120) + ".pdf", "Inzira-local-backup.scbackup")) {
            withProtectedTemporaryExport(name, bytes) { url -> assertEquals(name, url.lastPathComponent) }
        }
    }

    @Test fun staleCleanupRemovesOnlyOwnedExpiredDirectories() = runTest {
        var expired: String? = null
        var foreign: String? = null
        try {
            withProtectedTemporaryExport("proof.pdf", bytes) { url ->
                val root = assertNotNull(url.URLByDeletingLastPathComponent?.URLByDeletingLastPathComponent?.path)
                expired = "$root/${NSUUID().UUIDString}"
                foreign = "$root/keep-unrelated"
                for (path in listOf(assertNotNull(expired), assertNotNull(foreign))) {
                    assertTrue(files.createDirectoryAtPath(path, false, null, null))
                    assertTrue(files.setAttributes(mapOf<Any?, Any?>(NSFileModificationDate to NSDate.dateWithTimeIntervalSince1970(1.0)), path, null))
                }
            }
            withProtectedTemporaryExport("..", bytes) { url ->
                assertEquals("investment.pdf", url.lastPathComponent)
                assertFalse(files.fileExistsAtPath(assertNotNull(expired)))
                assertTrue(files.fileExistsAtPath(assertNotNull(foreign)))
            }
        } finally {
            expired?.let { files.removeItemAtPath(it, null) }
            foreign?.let { files.removeItemAtPath(it, null) }
        }
    }
}
