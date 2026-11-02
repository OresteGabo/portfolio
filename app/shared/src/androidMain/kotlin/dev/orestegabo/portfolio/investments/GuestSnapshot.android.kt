package dev.orestegabo.portfolio.investments

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private var applicationGuestRepository: GuestPortfolioRepository? = null
@Composable actual fun rememberGuestPortfolioRepository(): GuestPortfolioRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { applicationGuestRepository ?: LocalGuestPortfolioRepository(AndroidGuestSnapshotStorage(context)).also { applicationGuestRepository = it } }
}

/** Independent non-exportable key. AtomicFile's backup holds ciphertext too. Never shares auth-vault keys. */
class AndroidGuestSnapshotStorage(context: Context, storeName: String = "personal") : GuestSnapshotStorage {
    init { require(storeName.matches(Regex("[a-z0-9-]{1,60}"))) }
    private val directory = File(context.noBackupFilesDir, "guest-portfolios")
    private val file = AtomicFile(File(directory, "$storeName.scg"))
    private val alias = "struct.guest.snapshot.$storeName.v1"
    private val header = "SCG1".toByteArray(Charsets.US_ASCII)
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withContext null
        val encrypted = file.openRead().use { input ->
            require(input.channel.size() in 32L..(GUEST_STORE_MAX_BYTES + 32L)) { "Invalid guest storage size." }
            input.readBytes()
        }
        require(encrypted.copyOfRange(0, 4).contentEquals(header)) { "Unsupported encrypted storage version." }
        val key = store().getKey(alias, null) as? SecretKey ?: error("Guest storage key is unavailable. Data was not changed.")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(4, 16)))
        cipher.updateAAD(header)
        cipher.doFinal(encrypted, 16, encrypted.size - 16)
    }
    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        require(bytes.size in 1..GUEST_STORE_MAX_BYTES)
        check(directory.isDirectory || directory.mkdirs()) { "Guest storage is unavailable." }
        val keys = store()
        val existingFile = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val key = keys.getKey(alias, null) as? SecretKey ?: run {
            check(!existingFile) { "Guest storage key is unavailable. Data was not changed." }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key); updateAAD(header) }
        val encrypted = cipher.doFinal(bytes)
        check(cipher.iv.size == 12)
        val stream = file.startWrite()
        try { stream.write(header); stream.write(cipher.iv); stream.write(encrypted); stream.fd.sync(); file.finishWrite(stream) }
        catch (failure: Throwable) { file.failWrite(stream); throw failure }
        check(file.openRead().use { it.readBytes() }.contentEquals(header + cipher.iv + encrypted)) { "Guest storage commit could not be verified. Reopen the portfolio before retrying." }
    }
}
