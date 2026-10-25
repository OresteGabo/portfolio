@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.CoreFoundation.*
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.*
import platform.LocalAuthentication.*
import platform.Security.*
import platform.UIKit.*
import kotlin.coroutines.resume

@Composable actual fun rememberSecureSignIn(): SecureSignIn {
    val vault = remember { IosSecureSignIn() }
    DisposableEffect(vault) { onDispose { vault.lock() } }
    return vault
}

/** Passcode-bound, non-migrating Keychain item whose ACL requires system user presence on read. */
private class IosSecureSignIn : SecureSignIn {
    private val defaults = NSUserDefaults.standardUserDefaults
    private var context: LAContext? = null
    private var contextAuthorized = false
    override var authenticating = false
        private set
    private val service = "dev.orestegabo.portfolio.secure-sign-in.v1"
    override val available get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
    override val unlockLabel: String get() {
        val auth = LAContext()
        if (!auth.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) return "device passcode"
        return when (auth.biometryType) { LABiometryTypeFaceID -> "Face ID"; LABiometryTypeTouchID -> "Touch ID"; else -> "device passcode" }
    }
    private fun flag(origin: String) = "portfolio.secureSignIn.$origin"
    override fun hasSaved(origin: String) = defaults.boolForKey(flag(origin))
    private fun newDictionary(): CFMutableDictionaryRef = requireNotNull(CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr))
    private fun CFMutableDictionaryRef.put(key: CFStringRef?, value: CFTypeRef?) { CFDictionarySetValue(this, key, value) }
    private fun CFMutableDictionaryRef.string(key: CFStringRef?, value: String) {
        val string = requireNotNull(CFStringCreateWithCString(null, value, kCFStringEncodingUTF8))
        try { put(key, string) } finally { CFRelease(string) }
    }
    private fun CFMutableDictionaryRef.auth(context: LAContext) {
        val retained = CFBridgingRetain(context)
        try { put(kSecUseAuthenticationContext, retained) } finally { CFRelease(retained) }
    }
    private fun query(origin: String): CFMutableDictionaryRef = newDictionary().apply {
        put(kSecClass, kSecClassGenericPassword)
        string(kSecAttrService, service)
        string(kSecAttrAccount, origin)
    }
    private fun readItem(origin: String, auth: LAContext): String? = memScoped {
        val query = query(origin)
        try {
            query.auth(auth)
            query.string(kSecUseOperationPrompt, localized("Unlock your saved Gabo sign-in"))
            query.put(kSecReturnData, kCFBooleanTrue)
            query.put(kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecUserCanceled || status == errSecAuthFailed || status == errSecInteractionNotAllowed) return@memScoped null
            check(status == errSecSuccess) { "Saved sign-in is unavailable. Use your password to sign in again." }
            val data = CFBridgingRelease(result.value) as? NSData ?: error("Saved sign-in is invalid.")
            val length = data.length.toLong()
            require(length in 1..48_000) { "Saved sign-in is invalid." }
            val bytes = data.toBytes()
            try { bytes.decodeToString() } finally { bytes.fill(0) }
        } finally { CFRelease(query) }
    }
    private suspend fun authorize(auth: LAContext): Boolean = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { auth.invalidate() }
        auth.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = localized("Unlock your saved Gabo sign-in")) { success, _ ->
            if (continuation.isActive) continuation.resume(success)
        }
    }
    override suspend fun read(origin: String): String? {
        lock()
        val auth = LAContext()
        context = auth
        authenticating = true
        return try {
            if (!authorize(auth)) { lock(); null }
            else {
                contextAuthorized = true
                withContext(Dispatchers.Default) { readItem(origin, auth) }.also { if (it == null) lock() }
            }
        } finally { authenticating = false }
    }
    override suspend fun save(origin: String, value: String): Boolean {
        require(value.length <= 48_000) { "Saved sign-in is too large." }
        check(available) { "Set up Face ID, Touch ID, or a device passcode first." }
        val existed = hasSaved(origin)
        val auth = context ?: LAContext().also { context = it }
        authenticating = true
        try {
            if (!contextAuthorized) {
                if (!authorize(auth)) { lock(); return false }
                contextAuthorized = true
            }
            withContext(Dispatchers.Default) {
                val query = query(origin)
                val update = newDictionary()
                val bytes = value.encodeToByteArray()
                val data = bytes.usePinned { requireNotNull(CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong())) }
                try {
                    query.auth(auth)
                    update.put(kSecValueData, data)
                    var status = SecItemUpdate(query, update)
                    if (status == errSecItemNotFound) {
                        val access = requireNotNull(SecAccessControlCreateWithFlags(null, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, kSecAccessControlUserPresence, null))
                        try {
                            query.put(kSecAttrAccessControl, access)
                            query.put(kSecValueData, data)
                            status = SecItemAdd(query, null)
                        } finally { CFRelease(access) }
                    }
                    check(status == errSecSuccess) { "Could not securely save your sign-in." }
                } finally { bytes.fill(0); CFRelease(data); CFRelease(update); CFRelease(query) }
            }
            val verified = withContext(Dispatchers.Default) { readItem(origin, auth) } == value
            if (verified) defaults.setBool(true, forKey = flag(origin))
            else if (!existed) delete(origin)
            return verified
        } finally { authenticating = false }
    }
    override fun delete(origin: String) {
        lock()
        val query = query(origin)
        defaults.removeObjectForKey(flag(origin))
        try {
            val status = SecItemDelete(query)
            check(status == errSecSuccess || status == errSecItemNotFound) { "Could not remove saved sign-in." }
            defaults.removeObjectForKey(flag(origin))
        } finally { CFRelease(query) }
    }
    override fun lock() { context?.invalidate(); context = null; contextAuthorized = false }
}

@Composable actual fun NativePrivacyCover(protectCaptures: Boolean) {
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        var cover: UIView? = null
        val background = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            val window = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
            if (window != null && cover == null) {
                cover = UIView(frame = window.bounds).apply {
                    backgroundColor = UIColor(red = 0.98, green = 0.97, blue = 0.95, alpha = 1.0)
                    autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                    isAccessibilityElement = true
                    accessibilityLabel = "Gabo"
                    accessibilityViewIsModal = true
                    window.addSubview(this)
                }
            }
        }
        val foreground = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            cover?.removeFromSuperview(); cover = null
        }
        onDispose { center.removeObserver(background); center.removeObserver(foreground); cover?.removeFromSuperview() }
    }
}
