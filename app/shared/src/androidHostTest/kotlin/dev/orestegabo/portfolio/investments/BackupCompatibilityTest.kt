package dev.orestegabo.portfolio.investments

import kotlin.test.*

class BackupCompatibilityTest {
    @Test fun androidSevenKdfMatchesPlatformProviderIncludingUnicode() {
        val salt=ByteArray(16) { it.toByte() }
        assertContentEquals(backupKey("SyntheticPässword2026",salt),backupCompatibilityKey("SyntheticPässword2026",salt))
    }
}
