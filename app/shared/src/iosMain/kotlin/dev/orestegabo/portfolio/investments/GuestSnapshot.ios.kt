@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.cinterop.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

private var applicationGuestRepository: GuestPortfolioRepository? = null
@Composable actual fun rememberGuestPortfolioRepository(): GuestPortfolioRepository = remember {
    applicationGuestRepository ?: LocalGuestPortfolioRepository(IosGuestSnapshotStorage(), documentStorage = IosGuestDocumentStorage()).also { applicationGuestRepository = it }
}

/** iOS owns file encryption/key lifecycle: Complete Data Protection, atomic replacement, no backup.
 * No credentials, unprotected plaintext staging, custom cryptography or migratable Keychain key. */
class IosGuestSnapshotStorage(private val storeName: String = "personal", private val maxBytes: Int = GUEST_STORE_MAX_BYTES) : GuestSnapshotStorage {
    init { require(storeName.matches(Regex("[A-Za-z0-9-]{1,100}"))) }
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
    private fun missing(error: NSError?) = error != null && error.domain == NSCocoaErrorDomain &&
        error.code in listOf(NSFileNoSuchFileError, NSFileReadNoSuchFileError)
    override suspend fun read(): ByteArray? = withContext(Dispatchers.Default) { memScoped {
        val path = requireNotNull(url().path)
        val failure = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
        val attrs = files.attributesOfItemAtPath(path, failure.ptr)
        if (attrs == null) {
            if (missing(failure.value)) return@memScoped null
            error("Guest storage is protected or unreadable. Data was not changed.")
        }
        val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: error("Guest storage is unavailable.")
        require(size in 1L..maxBytes.toLong()) { "Invalid guest storage size." }
        val data = NSData.dataWithContentsOfURL(url()) ?: error("Guest storage is protected or unreadable. Unlock the device and retry.")
        require(data.length.toLong() == size)
        data.toBytes()
    } }
    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.Default) {
        require(bytes.size in 1..maxBytes)
        protectDirectory()
        // Foundation applies protection to the temporary file as well as the atomic replacement.
        check(bytes.toData().writeToURL(url(), NSDataWritingAtomic or NSDataWritingFileProtectionComplete, null)) { "Could not save the local portfolio. Existing data was not replaced." }
    }
    suspend fun delete(): Unit = withContext(Dispatchers.Default) { memScoped {
        val failure = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
        check(files.removeItemAtURL(url(), failure.ptr) || missing(failure.value)) { "Could not delete the local PDF. Cleanup will be retried." }
    } }

}

class IosGuestDocumentStorage : GuestDocumentStorage {
    private fun file(id: String): IosGuestSnapshotStorage {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        return IosGuestSnapshotStorage("pdf-$id", MAX_PDF_BYTES)
    }
    override suspend fun read(id: String) = file(id).read()
    override suspend fun write(id: String, bytes: ByteArray) = file(id).write(bytes)
    override suspend fun delete(id: String) = file(id).delete()
}
@Composable actual fun rememberGuestInvestmentEngine(): InvestmentEngine = remember { IosInvestmentEngine() }
