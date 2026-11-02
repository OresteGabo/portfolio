@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.test.runTest
import platform.Foundation.*
import kotlin.test.*

class GuestNativeStorageTest {
    @Test fun protectedNativeFilePreservesPortfolioAndFailsClosedOnCorruption() = runTest {
        val name = "test-guest-phase3-native"
        val files = NSFileManager.defaultManager
        val base = requireNotNull(files.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL)
        val dir = requireNotNull(base.URLByAppendingPathComponent("GuestPortfolios",true))
        val url = requireNotNull(dir.URLByAppendingPathComponent("$name.json",false))
        val path = requireNotNull(url.path)
        assertFalse(files.fileExistsAtPath(path),"Do not overwrite a retained native test fixture")
        try {
            val repository = LocalGuestPortfolioRepository(IosGuestSnapshotStorage(name))
            val id = repository.saveBond(guestBondFixture()).portfolio.purchases.single().text("id")
            repository.saveShare(guestShareFixture())
            val reopened = LocalGuestPortfolioRepository(IosGuestSnapshotStorage(name)).load()
            assertEquals(id,reopened.portfolio.purchases.single().text("id"))
            assertEquals(36615.60,reopened.portfolio.holdings.single().optDouble("netAmountPayable"))
            val protection = files.attributesOfItemAtPath(path,null)?.get(NSFileProtectionKey)
            if (protection == null) {
                // This simulator returned no protection-class attribute. It cannot prove device encryption.
                assertNotNull(NSProcessInfo.processInfo.environment["SIMULATOR_UDID"], "Physical devices must report the protection class")
                println("GuestStorage: simulator exposes no protection-class attribute; physical lock/encryption verification remains required")
            } else assertEquals(NSFileProtectionComplete, protection.toString())
            val resource = requireNotNull(dir.resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey),null))
            assertEquals(true,(resource[NSURLIsExcludedFromBackupKey] as? NSNumber)?.boolValue)
            val original = requireNotNull(NSData.dataWithContentsOfURL(url)).toBytes()
            assertTrue("{invalid".encodeToByteArray().toData().writeToURL(url,NSDataWritingAtomic or NSDataWritingFileProtectionComplete,null))
            assertFails { LocalGuestPortfolioRepository(IosGuestSnapshotStorage(name)).load() }
            assertFails { repository.saveShare(guestShareFixture()) }
            assertEquals("{invalid",requireNotNull(NSData.dataWithContentsOfURL(url)).toBytes().decodeToString())
            assertTrue(original.toData().writeToURL(url,NSDataWritingAtomic or NSDataWritingFileProtectionComplete,null))
            repository.deleteBond(id)
            assertTrue(LocalGuestPortfolioRepository(IosGuestSnapshotStorage(name)).load().portfolio.purchases.isEmpty())
        } finally { files.removeItemAtURL(url,null) }
    }
}
