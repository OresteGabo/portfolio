package dev.orestegabo.portfolio.investments

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

private fun Context.host(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.host()
    else -> error("Device authentication is unavailable.")
}

private var applicationVault: AndroidSecureSignIn? = null
@Composable actual fun rememberSecureSignIn(): SecureSignIn {
    val context = LocalContext.current
    val activity = context.host()
    val vault = remember(context.applicationContext) {
        applicationVault ?: AndroidSecureSignIn(context.applicationContext).also { applicationVault = it }
    }
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vault.credentialResult?.complete(it.resultCode == Activity.RESULT_OK)
    }
    SideEffect { vault.confirmCredential = { credential.launch(it) } }
    DisposableEffect(vault, activity) {
        vault.attach(activity)
        onDispose { vault.detach(activity) }
    }
    return vault
}

/** Keystore key wraps an in-memory-only data key, enabling session rotation without another prompt.
 * Android 11+ uses auth-per-operation; older OS versions use a five-second device-credential authorization.
 */
private class AndroidSecureSignIn(private val application: Context) : SecureSignIn {
    private var host = WeakReference<FragmentActivity>(null)
    fun attach(activity: FragmentActivity) { host = WeakReference(activity) }
    fun detach(activity: FragmentActivity) { if (host.get() === activity) { lock(); host.clear(); confirmCredential = {} } }
    private val prefs = application.getSharedPreferences("secure-sign-in", Context.MODE_PRIVATE)
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private var dataKey: ByteArray? = null
    private var unlockedOrigin: String? = null
    private var pendingAuth: CancellableContinuation<Cipher?>? = null
    private var currentPrompt: BiometricPrompt? = null
    override var authenticating: Boolean = false
        private set
    var credentialResult: CompletableDeferred<Boolean>? = null
        private set
    var confirmCredential: (Intent) -> Unit = {}
    private val keyguard = application.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val flags = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.DEVICE_CREDENTIAL else 0
    override val available get() = if (Build.VERSION.SDK_INT < 30) keyguard.isDeviceSecure
        else BiometricManager.from(application).canAuthenticate(flags) == BiometricManager.BIOMETRIC_SUCCESS
    // The OS chooses fingerprint, face, iris, or the configured PIN/pattern/password.
    override val unlockLabel get() = if (Build.VERSION.SDK_INT < 30) "screen lock" else "device authentication"
    private fun key(origin: String) = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun alias(origin: String, legacyBiometric: Boolean = false) = "gabo.signin.${key(origin)}" +
        if (Build.VERSION.SDK_INT < 30 && !legacyBiometric) ".device.v2" else ""
    override fun hasSaved(origin: String) = prefs.contains(key(origin)) &&
        (store.containsAlias(alias(origin)) || store.containsAlias(alias(origin, true)))
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
    private fun dataCipher(mode: Int, key: ByteArray, iv: ByteArray? = null): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        if (iv == null) init(mode, SecretKeySpec(key, "AES")) else init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    }
    @Suppress("DEPRECATION")
    private fun wrappingKey(origin: String, legacyBiometric: Boolean = false): SecretKey {
        (store.getKey(alias(origin, legacyBiometric), null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias(origin, legacyBiometric), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        else if (legacyBiometric) spec.setUserAuthenticationValidityDurationSeconds(-1).setInvalidatedByBiometricEnrollment(true)
        else spec.setUserAuthenticationValidityDurationSeconds(5)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
    }
    @Suppress("DEPRECATION")
    private suspend fun authorize(legacyBiometric: Boolean = false, cipherFactory: () -> Cipher): Cipher? = withContext(Dispatchers.Main) {
        val activity = requireNotNull(host.get()) { "Device authentication is unavailable." }
        check(!authenticating) { "Device authentication is already open." }
        authenticating = true
        try {
            if (Build.VERSION.SDK_INT < 30 && !legacyBiometric) {
                // These Android versions cannot combine a CryptoObject with device credentials.
                // Confirm with the system first, then use the short-lived Keystore authorization.
                val intent = keyguard.createConfirmDeviceCredentialIntent(localized("Unlock Gabo"), localized("Access your saved portfolio sign-in"))
                    ?: return@withContext null
                val result = CompletableDeferred<Boolean>()
                credentialResult = result
                try {
                    confirmCredential(intent)
                    return@withContext if (result.await()) cipherFactory() else null
                } finally { credentialResult = null }
            }
            val cipher = cipherFactory()
            suspendCancellableCoroutine { continuation ->
                pendingAuth = continuation
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(result.cryptoObject?.cipher)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
                currentPrompt = prompt
                val builder = BiometricPrompt.PromptInfo.Builder().setTitle(localized("Unlock Gabo")).setSubtitle(localized("Access your saved portfolio sign-in")).setAllowedAuthenticators(flags)
                if (Build.VERSION.SDK_INT < 30) builder.setNegativeButtonText(localized("Cancel"))
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
                prompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
            }
        } finally { authenticating = false; pendingAuth = null; currentPrompt = null }
    }
    override suspend fun read(origin: String): String? {
        lock()
        val raw = prefs.getString(key(origin), null) ?: return null
        try {
            require(raw.length <= 100_000)
            val record = JsonRecord(raw)
            val legacy = Build.VERSION.SDK_INT < 30 && record.text("keyPolicy") != "device-v2"
            val wrapping = wrappingKey(origin, legacy)
            val authenticated = authorize(legacy) {
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, wrapping, GCMParameterSpec(128, decode(record.text("keyIv"))))
                }
            } ?: return null
            val secret = authenticated.doFinal(decode(record.text("wrappedKey")))
            require(secret.size == 32)
            val value = dataCipher(Cipher.DECRYPT_MODE, secret, decode(record.text("dataIv"))).doFinal(decode(record.text("data")))
            dataKey = secret
            unlockedOrigin = origin
            return value.decodeToString().also { value.fill(0) }
        } catch (failure: CancellationException) { lock(); throw failure }
        catch (_: Exception) { lock(); error("Saved sign-in is unavailable. Use your password to sign in again.") }
    }
    override suspend fun save(origin: String, value: String): Boolean {
        require(value.length <= 48_000) { "Saved sign-in is too large." }
        check(available) { "Set up device biometrics or a screen lock first." }
        var existing = prefs.getString(key(origin), null)?.let(::JsonRecord)
        var secret = dataKey?.takeIf { unlockedOrigin == origin }
        if (secret == null || existing == null) {
            lock()
            if (existing == null && store.containsAlias(alias(origin))) store.deleteEntry(alias(origin))
            val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val wrapping = wrappingKey(origin)
            val authenticated = authorize {
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapping) }
            } ?: run { generated.fill(0); return false }
            existing = JsonRecord().put("keyPolicy", "device-v2").put("keyIv", encode(authenticated.iv)).put("wrappedKey", encode(authenticated.doFinal(generated)))
            secret = generated
        }
        val cipher = dataCipher(Cipher.ENCRYPT_MODE, secret)
        val bytes = value.encodeToByteArray()
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        existing.put("dataIv", encode(cipher.iv)).put("data", encode(encrypted))
        val persisted = withContext(Dispatchers.IO) { prefs.edit().putString(key(origin), existing.toString()).commit() }
        check(persisted) { "Could not securely save your sign-in." }
        dataKey = secret; unlockedOrigin = origin
        return true
    }
    override fun delete(origin: String) {
        lock()
        val removed = prefs.edit().remove(key(origin)).commit()
        listOf(alias(origin), alias(origin, true)).distinct().forEach { if (store.containsAlias(it)) store.deleteEntry(it) }
        check(removed) { "Could not remove saved sign-in." }
    }
    override fun lock() { credentialResult?.cancel(); pendingAuth?.cancel(); currentPrompt?.cancelAuthentication(); dataKey?.fill(0); dataKey = null; unlockedOrigin = null }
}


@Composable actual fun NativePrivacyCover(protectCaptures: Boolean, protectDocument: Boolean) {
    val window = LocalContext.current.host().window
    var inactive by remember(window) { mutableStateOf(false) }
    fun updateProtection() {
        // Foreground protection is shared number masking, so normal screenshots can save.
        // Original PDF pixels also need the full window shield while capture protection is enabled.
        if (protectCaptureWindow(inactive) || protectCaptures && protectDocument) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        inactive = true
        updateProtection()
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        inactive = false
        updateProtection()
    }
    SideEffect { updateProtection() }
    // Do not drop protection during disposal: the final frame may become a recents thumbnail.
}
