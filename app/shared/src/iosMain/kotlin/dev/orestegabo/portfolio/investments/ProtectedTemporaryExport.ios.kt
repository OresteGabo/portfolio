@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.*

/**
 * App-owned staging for user-requested PDF/backup export. Platform Data Protection covers the
 * directory, final file and Foundation's atomic temporary file. No cryptographic format is added.
 * The destination chosen by the user/recipient remains outside this staging area's ownership.
 */
internal suspend fun <T> withProtectedTemporaryExport(
    fileName: String,
    bytes: ByteArray,
    export: suspend (NSURL) -> T,
): T {
    var ownedDirectory: String? = null
    try {
        val url = withContext(Dispatchers.Default) {
            val files = NSFileManager.defaultManager
            val root = NSTemporaryDirectory() + "inzira-exports"
            val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete)
            check(files.createDirectoryAtPath(root, true, protection, null)) { "Unable to prepare the document." }
            check(files.setAttributes(protection, root, null)) { "Unable to prepare the document." }
            val rootUrl = NSURL.fileURLWithPath(root, true)
            check(rootUrl.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) { "Unable to prepare the document." }
            removeExpiredExports(root)
            val directory = "$root/${NSUUID().UUIDString}"
            check(files.createDirectoryAtPath(directory, false, protection, null)) { "Unable to prepare the document." }
            ownedDirectory = directory
            val name = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(240)
                .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "investment.pdf"
            val destination = NSURL.fileURLWithPath("$directory/$name")
            check(bytes.toData().writeToURL(destination, NSDataWritingAtomic or NSDataWritingFileProtectionComplete, null)) {
                "Unable to prepare the document."
            }
            destination
        }
        return export(url)
    } finally {
        // Also runs if cancellation wins delivery of the newly created URL back to the caller.
        ownedDirectory?.let { directory ->
            withContext(NonCancellable + Dispatchers.Default) {
                NSFileManager.defaultManager.removeItemAtPath(directory, null)
            }
        }
    }
}

/** Only UUID directories in our own staging root are eligible; never sweep the system temp root. */
private fun removeExpiredExports(root: String) {
    val files = NSFileManager.defaultManager
    val expiry = NSDate().timeIntervalSince1970 - 24 * 60 * 60
    for (name in files.contentsOfDirectoryAtPath(root, null).orEmpty().filterIsInstance<String>()) {
        if (!name.matches(Regex("[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}"))) continue
        val path = "$root/$name"
        val modified = files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate ?: continue
        if (modified.timeIntervalSince1970 < expiry) files.removeItemAtPath(path, null)
    }
}
