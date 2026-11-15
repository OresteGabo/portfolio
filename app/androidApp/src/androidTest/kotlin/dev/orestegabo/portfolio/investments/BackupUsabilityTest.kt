package dev.orestegabo.portfolio.investments

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Real backup UI and encryption, synthetic memory storage, no account or device financial files. */
class BackupUsabilityTest {
    @Test fun validationCancelledExportAndDestructiveRestoreHavePredictableRecovery() {
        val ui = UsabilityTestDriver()
        val writes = AtomicInteger()
        val saves = AtomicInteger()
        val restores = AtomicInteger()
        val exportGate = CompletableDeferred<Unit>()
        val restoreGate = CompletableDeferred<Unit>()
        var archive: ByteArray? = null
        val disk = object : GuestSnapshotStorage {
            var snapshot: ByteArray? = null
            override suspend fun read() = snapshot?.copyOf()
            override suspend fun write(bytes: ByteArray) {
                writes.incrementAndGet()
                restoreGate.await()
                snapshot = bytes.copyOf()
            }
        }
        val files = object : GuestDocumentStorage {
            override suspend fun read(id: String): ByteArray? = null
            override suspend fun write(id: String, bytes: ByteArray) = Unit
            override suspend fun delete(id: String) = Unit
        }
        val localRepository = LocalGuestPortfolioRepository(disk, documentStorage = files)
        val repository = object : GuestPortfolioRepository by localRepository {
            override suspend fun restoreBackup(bytes: ByteArray): GuestPortfolio {
                restores.incrementAndGet()
                return localRepository.restoreBackup(bytes)
            }
        }
        val settings = object : ServerSettings {
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) = Unit
            override fun readSessionChoice() = "guest-personal-v1"
            override fun readScreenCaptureAllowed() = true
            override fun readLanguage() = "en"
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No import in backup fixture")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = Unit
            override suspend fun saveBackup(fileName: String, bytes: ByteArray): Boolean {
                if (saves.incrementAndGet() == 1) { exportGate.await(); return false }
                archive = bytes.copyOf()
                return true
            }
            override suspend fun pickBackup() = requireNotNull(archive).copyOf()
        }
        try {
            UsabilityPreferences().use {
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    lateinit var model: PortfolioModel
                    scene.onActivity { host ->
                        model = PortfolioModel(settings, guestRepository = repository, apiFactory = { error("Guest backup attempted HTTP") })
                        host.viewModelStore.put("backup-ux", model)
                        AppLanguage.initialize(settings)
                        host.setContent { CompositionLocalProvider(LocalImportEngine provides engine, LocalNativeActions provides actions) { InvestmentApp(model) } }
                    }
                    ui.waitUntil("guest ready") { model.guestPortfolio != null && !model.guestStorageLoading }
                    ui.click("Settings"); ui.scroll("Encrypted backup"); ui.click("Encrypted backup")
                    ui.scroll("Export encrypted backup"); ui.click("Export encrypted backup")
                    ui.await("Use a backup password between 12 and 256 characters.")
                    assertEquals(0, saves.get())
                    val password = "synthetic backup password"
                    ui.enter("Backup password", password)
                    ui.enter("Confirm backup password (export)", "different password")
                    ui.scroll("Export encrypted backup"); ui.click("Export encrypted backup")
                    ui.await("The backup passwords do not match."); assertEquals(0, saves.get())
                    ui.enter("Confirm backup password (export)", password)
                    ui.scroll("Export encrypted backup")
                    var button = ui.await("Export encrypted backup")
                    while (!button.isClickable && button.parent != null) button = button.parent
                    assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ui.waitUntil("one export in flight") { saves.get() == 1 }
                    scene.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    ui.await("Your private guest backup"); assertEquals(1, saves.get())
                    exportGate.complete(Unit)
                    ui.scroll("Backup not saved. You can try again.")
                    // Retry without re-entering passwords proves cancellation retained the form.
                    ui.scroll("Export encrypted backup"); ui.click("Export encrypted backup")
                    ui.scroll("Encrypted backup saved."); assertEquals(2, saves.get()); assertNotNull(archive)
                    ui.scroll("Export encrypted backup"); ui.click("Export encrypted backup")
                    ui.await("Use a backup password between 12 and 256 characters.")
                    assertEquals(2, saves.get()) // Successful export cleared the secret fields.
                    ui.enter("Backup password", password)
                    ui.scroll("Select backup to restore"); ui.click("Select backup to restore")
                    ui.await("Replace guest workspace?"); ui.click("Cancel")
                    assertEquals(0, writes.get())
                    ui.scroll("Select backup to restore"); ui.click("Select backup to restore")
                    ui.await("Replace guest workspace?"); ui.click("Replace and restore")
                    ui.waitUntil("restore writing") { writes.get() == 1 && model.guestStorageLoading }
                    ui.await("Working…")
                    var cancel = ui.await("Cancel")
                    while (cancel.parent != null && !cancel.isClickable) cancel = cancel.parent
                    assertFalse("Cannot suggest cancelling a restore already being written", cancel.isEnabled)
                    scene.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    ui.await("Replace guest workspace?"); assertEquals(1, writes.get())
                    restoreGate.complete(Unit)
                    ui.scroll("Backup restored.")
                    assertEquals(1, restores.get())
                    assertEquals(1, requireNotNull(model.guestPortfolio).revision)
                    assertFalse(ui.nodes().any { ui.matches(it, "Replace guest workspace?") })
                }
            }
        } finally {
            exportGate.complete(Unit); restoreGate.complete(Unit); archive?.fill(0)
        }
    }
}
