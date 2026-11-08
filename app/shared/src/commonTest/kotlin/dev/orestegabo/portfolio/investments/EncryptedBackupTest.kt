package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class EncryptedBackupTest {
    private val password = "SyntheticBackup!2026"
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun nativeCryptoReadsTheSameOpenSslReferenceEnvelope() {
        // Independently generated with Python hashlib/hmac and OpenSSL AES-256-CBC.
        val fixture = hex("5343424b31000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f5ddaebee5cab9420b3b6f767a5fad799de20734bfcca20e6a225da03b0a67a299e8baf8aa266b7158e4c65c90d999e2b6171eeef7e42868cdcbe2e9d493da2c6")
        assertEquals("Synthetic portable backup", decryptBackup(fixture,password).decodeToString())
        assertFails { decryptBackup(fixture,"WrongPassword!2026") }
        fixture[40] = (fixture[40].toInt() xor 1).toByte()
        assertFails { decryptBackup(fixture,password) }
    }
    @Test fun encryptionIsRandomizedAndChecksEnvelopeBounds() {
        val plain = "Synthetic private data".encodeToByteArray()
        val a=encryptBackup(plain,password);val b=encryptBackup(plain,password)
        assertFalse(a.contentEquals(b));assertFalse(a.decodeToString().contains("Synthetic private data"))
        assertContentEquals(plain,decryptBackup(a,password))
        assertFails { encryptBackup(plain,"short") };assertFails { decryptBackup(a.copyOf(20),password) }
    }
    private class Disk: GuestSnapshotStorage {
        var data: ByteArray?=null
        override suspend fun read()=data?.copyOf()
        override suspend fun write(bytes: ByteArray) { data=bytes.copyOf() }
    }
    private class Files: GuestDocumentStorage {
        val data=mutableMapOf<String,ByteArray>();var fail=false
        override suspend fun read(id: String)=data[id]?.copyOf()
        override suspend fun write(id: String,bytes: ByteArray) { check(!fail);data[id]=bytes.copyOf() }
        override suspend fun delete(id: String) { data.remove(id) }
    }
    private fun repo(disk: Disk,files: Files)=LocalGuestPortfolioRepository(disk,now={"2027-02-01T12:00:00Z"},today={LocalDate(2027,2,1)},workDispatcher=Dispatchers.Unconfined,documentStorage=files)
    @Test fun backupRestoresOriginalPdfsReceiptsAndSalesWithoutDuplicatingBlobs()=runTest {
        val disk=Disk();val files=Files();val repository=repo(disk,files)
        val pdf="%PDF-1.7\nSynthetic guest PDF\n%%EOF".encodeToByteArray()
        val saved=repository.importPdf(pdf,"synthetic.pdf","equity",guestShareFixture().put("isin","FIXTURE-SHARE"))
        val lot=saved.portfolio.holdings.single();val doc=saved.portfolio.documents.single()
        val sale=JsonRecord().put("id","sale-one").put("kind","sale").put("holdingId",lot.text("id")).put("date","2026-11-01").put("currency","RWF").put("shares",100).put("price",110)
        repository.saveLedgerEntry(sale)
        val backup=repository.exportBackup()
        repository.saveBond(guestBondFixture())
        val restored=repository.restoreBackup(backup)
        assertTrue(restored.portfolio.purchases.isEmpty())
        assertEquals(1,restored.metadata.rows("cashLedger").size)
        assertEquals(300.0,restored.portfolio.holdings.single().optDouble("remainingShares"))
        assertContentEquals(pdf,repository.documentBytes(doc.text("id")))
        assertEquals(1,files.data.size)
        repository.restoreBackup(backup)
        assertEquals(1,files.data.size)
        assertFails { repository.deleteShare(lot.text("id")) }
        repository.deleteLedgerEntry("sale-one")
        assertEquals(400.0,repository.load().portfolio.holdings.single().optDouble("remainingShares"))
    }
    @Test fun invalidBackupOrFailedStagingLeavesCurrentInvestmentsIntact()=runTest {
        val disk=Disk();val files=Files();val repository=repo(disk,files)
        val pdf="%PDF-1.7\nSynthetic guest PDF\n%%EOF".encodeToByteArray()
        repository.importPdf(pdf,"synthetic.pdf","equity",guestShareFixture().put("isin","FIXTURE-SHARE"))
        val backup=repository.exportBackup()
        repository.saveBond(guestBondFixture())
        val before=repository.load()
        val bad=JsonRecord(backup.decodeToString());bad.put("workspace", bad.getJSONObject("workspace").put("schemaVersion",999))
        assertFails { repository.restoreBackup(bad.toString().encodeToByteArray()) }
        files.fail=true
        assertFails { repository.restoreBackup(backup) }
        files.fail=false
        assertEquals(before.portfolio.purchases.single().text("id"),repository.load().portfolio.purchases.single().text("id"))
        assertContentEquals(pdf,repository.documentBytes(before.portfolio.documents.single().text("id")))
    }
}
