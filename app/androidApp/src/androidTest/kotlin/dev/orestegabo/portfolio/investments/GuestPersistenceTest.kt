package dev.orestegabo.portfolio.investments

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore

/** Dedicated test stores only; never opens or removes a user's personal guest workspace. */
class GuestPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name: String) = File(context.noBackupFilesDir,"guest-portfolios/$name.scg")
    private fun cleanup(name: String) {
        listOf("", ".bak", ".new").forEach { File(file(name).path + it).delete() }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("struct.guest.snapshot.$name.v1") }
    }
    private fun bond() = JsonRecord().put("bondName","Synthetic durable bond").put("currency","RWF").put("status","active")
        .put("purchaseDate","2026-07-15").put("settlementDate","2026-07-16").put("maturityDate","2033-07-15")
        .put("faceValue",2200000).put("pricePercent",100).put("feesPaid",0).put("accruedInterestPaid",0)
        .put("couponRate",.115).put("withholdingTaxRate",.05).put("couponFrequency",2).put("scheduleConfidence","confirmed")
        .put("couponDates",JsonList(listOf("2027-01-15","2033-07-15")))
    private fun shares() = JsonRecord().put("securityName","Synthetic durable shares").put("ticker","FIXTURE").put("currency","RWF")
        .put("status","active").put("tradeDate","2026-07-15").put("settlementDate","2026-07-16").put("shares",400)
        .put("pricePerShare",90).put("brokerageCommission",615.60).put("rseTransactionFee",0).put("csdTransactionLevy",0).put("cmaTransactionFee",0)
    private fun repo(name: String): LocalGuestPortfolioRepository {
        var sequence = 0
        return LocalGuestPortfolioRepository(AndroidGuestSnapshotStorage(context,name), newId = { "fixture-${++sequence}" }, today = { LocalDate(2027,2,1) })
    }
    @Test fun encryptionTamperingKeyLossAndNativeCrud() = runBlocking {
        val name = "test-guest-phase3-crypto"
        assertFalse("Do not overwrite a retained test fixture",file(name).exists())
        try {
            val repository = repo(name)
            repository.saveBond(bond()); repository.saveShare(shares()); repository.setReceipt("guest:bond:fixture-1","2027-01-15",true)
            val encrypted = file(name).readBytes()
            assertFalse(encrypted.toString(Charsets.UTF_8).contains("Synthetic durable"))
            assertFalse(encrypted.toString(Charsets.UTF_8).contains("faceValue"))
            assertEquals(120175.0,repo(name).load().receipts.single().optDouble("amount"),0.0)
            file(name).writeBytes(encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            val corrupt = file(name).readBytes()
            assertTrue(runCatching { repo(name).load() }.isFailure)
            assertTrue(runCatching { repo(name).saveBond(bond()) }.isFailure)
            assertArrayEquals(corrupt,file(name).readBytes())
            file(name).writeBytes(encrypted)
            repo(name).saveBond(bond().put("faceValue",1000000),"guest:bond:fixture-1")
            assertEquals(1000000.0,repo(name).load().portfolio.purchases.single().optDouble("faceValue"),0.0)
            repo(name).deleteShare("guest:share:fixture-2")
            assertTrue(repo(name).load().portfolio.holdings.isEmpty())
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("struct.guest.snapshot.$name.v1") }
            val saved = file(name).readBytes()
            assertTrue(runCatching { repo(name).load() }.isFailure)
            assertTrue(runCatching { repo(name).saveBond(bond()) }.isFailure)
            assertArrayEquals(saved,file(name).readBytes())
        } finally { cleanup(name) }
    }
    /** Explicit stages allow the host runner to force-stop/reboot BETWEEN test processes.
     * Skipped in a normal suite, so tests never depend on execution order. */
    @Test fun guestInvestmentsSurviveProcessRestartAndReboot() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("guestPersistenceStage")
        assumeTrue(stage in listOf("write","read","cleanup"))
        val name = "test-guest-phase3-reboot"
        if (stage == "write") {
            assertFalse("Do not overwrite a retained restart fixture",file(name).exists())
            val repository = repo(name)
            repository.saveBond(bond()); repository.saveShare(shares()); repository.setReceipt("guest:bond:fixture-1","2027-01-15",true)
        }
        val loaded = repo(name).load()
        assertEquals("guest:bond:fixture-1",loaded.portfolio.purchases.single().text("id"))
        assertEquals("guest:share:fixture-2",loaded.portfolio.holdings.single().text("id"))
        assertEquals(36615.60,loaded.portfolio.holdings.single().optDouble("netAmountPayable"),0.0)
        assertEquals("manual",loaded.receipts.single().text("method")); assertTrue(loaded.receipts.single().optBoolean("received"))
        if (stage == "cleanup") cleanup(name)
    }
}
