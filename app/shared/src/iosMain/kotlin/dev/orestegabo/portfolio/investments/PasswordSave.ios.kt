@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.NSBundle
import platform.Security.SecAddSharedWebCredential
import platform.UIKit.*
import kotlin.coroutines.resume

/** The system owns the shared credential; this does not write it to app preferences or our vault. */
internal suspend fun offerIosPasswordSave(origin: String, email: String, password: String) {
    // Personal Team profiles cannot sign Associated Domains. Do not offer an unusable save flow.
    if (NSBundle.mainBundle.objectForInfoDictionaryKey("GaboPasswordSharingEnabled")?.toString() != "YES") return
    val domain = passwordSharingDomain(origin) ?: return
    if (!passwordSaveConsent(domain)) return
    if (!saveSharedPassword(domain, email, password)) passwordSaveFailed()
}

private suspend fun passwordSaveConsent(domain: String): Boolean = suspendCancellableCoroutine { result ->
    val dialog = UIAlertController.alertControllerWithTitle(localized("Save in Passwords?"),
        localized("Save your sign-in for $domain in Apple Passwords so you can use AutoFill next time."), UIAlertControllerStyleAlert)
    fun finish(save: Boolean) {
        dialog.dismissViewControllerAnimated(true) { if (result.isActive) result.resume(save) }
    }
    dialog.addAction(UIAlertAction.actionWithTitle(localized("Not now"), UIAlertActionStyleCancel) { finish(false) })
    dialog.addAction(UIAlertAction.actionWithTitle(localized("Save password"), UIAlertActionStyleDefault) { finish(true) })
    result.invokeOnCancellation { dialog.dismissViewControllerAnimated(false, null) }
    presentingController().presentViewController(dialog, true, null)
}

@Suppress("DEPRECATION") // Supports iOS 18.2+; the replacement requires a newer SDK/OS.
private suspend fun saveSharedPassword(domain: String, email: String, password: String): Boolean = suspendCancellableCoroutine { result ->
    val host = requireNotNull(ownedCredentialString(domain))
    val account = requireNotNull(ownedCredentialString(email))
    val secret = requireNotNull(ownedCredentialString(password))
    // Keep the strings alive until Apple's asynchronous completion; never display/log its raw error.
    SecAddSharedWebCredential(host, account, secret) { failure ->
        CFRelease(host); CFRelease(account); CFRelease(secret)
        if (result.isActive) result.resume(failure == null)
    }
}

private suspend fun passwordSaveFailed(): Unit = suspendCancellableCoroutine { result ->
    val dialog = UIAlertController.alertControllerWithTitle(localized("Password wasn’t saved"),
        localized("You can continue signing in. Try saving again later, or add the login in Apple Passwords."), UIAlertControllerStyleAlert)
    dialog.addAction(UIAlertAction.actionWithTitle(localized("Continue"), UIAlertActionStyleDefault) {
        dialog.dismissViewControllerAnimated(true) { if (result.isActive) result.resume(Unit) }
    })
    result.invokeOnCancellation { dialog.dismissViewControllerAnimated(false, null) }
    presentingController().presentViewController(dialog, true, null)
}

private fun ownedCredentialString(value: String): CFStringRef? {
    val bytes = value.encodeToByteArray()
    return try {
        bytes.usePinned { CFStringCreateWithBytes(null, it.addressOf(0).reinterpret(), bytes.size.toLong(), kCFStringEncodingUTF8, false) }
    } finally { bytes.fill(0) }
}
