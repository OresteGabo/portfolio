@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

private var applicationGuestRepository: GuestPortfolioRepository? = null
@Composable actual fun rememberGuestPortfolioRepository(): GuestPortfolioRepository = remember {
    applicationGuestRepository ?: LocalGuestPortfolioRepository(IosGuestSnapshotStorage()).also { applicationGuestRepository = it }
}

/** iOS owns file encryption/key lifecycle: Complete Data Protection, atomic replacement, no backup.
 * No credentials, unprotected plaintext staging, custom cryptography or migratable Keychain key. */
class IosGuestSnapshotStorage(private val storeName: String = "personal") : GuestSnapshotStorage {
    init { require(storeName.matches(Regex("[a-z0-9-]{1,60}"))) }
    private val files get() = NSFileManager.defaultManager
    private fun directory(): NSURL {
        val base = requireNotNull(files.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL)
        return requireNotNull(base.URLByAppendingPathComponent("GuestPortfolios", true))
    }
    private fun url() = requireNotNull(directory().URLByAppendingPathComponent("$storeName.json", false))
    private fun protectDirectory() {
        val dir = directory()
        check(files.createDirectoryAtURL(dir, true, mapOf(NSFileProtectionKey to NSFileProtectionComplete), null)) { "Guest storage is unavailable." }
        check(files.setAttributes(mapOf(NSFileProtectionKey to NSFileProtectionComplete), requireNotNull(dir.path), null)) { "Guest storage protection is unavailable." }
        check(dir.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) { "Could not exclude guest storage from backup." }
    }
    override suspend fun read(): ByteArray? = withContext(Dispatchers.Default) {
        val path = requireNotNull(url().path)
        if (!files.fileExistsAtPath(path)) return@withContext null
        val attrs = requireNotNull(files.attributesOfItemAtPath(path, null)) { "Guest storage is unavailable." }
        val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: error("Guest storage is unavailable.")
        require(size in 1L..GUEST_STORE_MAX_BYTES.toLong()) { "Invalid guest storage size." }
        val data = NSData.dataWithContentsOfURL(url()) ?: error("Guest storage is protected or unreadable. Unlock the device and retry.")
        require(data.length.toLong() == size)
        data.toBytes()
    }
    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.Default) {
        require(bytes.size in 1..GUEST_STORE_MAX_BYTES)
        protectDirectory()
        // Foundation applies protection to the temporary file as well as the atomic replacement.
        check(bytes.toData().writeToURL(url(), NSDataWritingAtomic or NSDataWritingFileProtectionComplete, null)) { "Could not save the local portfolio. Existing data was not replaced." }
    }
}
