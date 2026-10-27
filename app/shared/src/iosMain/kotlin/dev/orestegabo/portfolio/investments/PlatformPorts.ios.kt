@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.CoreGraphics.CGRectMake
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun NSData.toBytes(): ByteArray = ByteArray(length.toInt()).also { output ->
    if (output.isNotEmpty()) output.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
internal fun ByteArray.toData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
internal fun presentingController(): UIViewController {
    val window = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
        ?: UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull()
    var controller = requireNotNull(window?.rootViewController) { "No active window is available." }
    while (controller.presentedViewController != null) controller = controller.presentedViewController!!
    return controller
}
@Composable
actual fun rememberServerSettings(): ServerSettings = remember { object : ServerSettings {
    override fun readScreenCaptureAllowed() = NSUserDefaults.standardUserDefaults.boolForKey("portfolio.screenCaptureAllowed")
    override fun writeScreenCaptureAllowed(allowed: Boolean) { NSUserDefaults.standardUserDefaults.setBool(allowed, forKey = "portfolio.screenCaptureAllowed") }
    override fun readShakeToHideEnabled() = NSUserDefaults.standardUserDefaults.objectForKey("portfolio.shakeToHideEnabled") == null || NSUserDefaults.standardUserDefaults.boolForKey("portfolio.shakeToHideEnabled")
    override fun writeShakeToHideEnabled(enabled: Boolean) { NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = "portfolio.shakeToHideEnabled") }
    override fun readNumbersHidden() = NSUserDefaults.standardUserDefaults.boolForKey("portfolio.numbersHidden")
    override fun writeNumbersHidden(hidden: Boolean) { NSUserDefaults.standardUserDefaults.setBool(hidden, forKey = "portfolio.numbersHidden") }
    override fun forgetMonthlyTarget(account: String) { NSUserDefaults.standardUserDefaults.removeObjectForKey("portfolio.monthlyTarget:$account") }
    override fun readMonthlyTarget(account: String) = NSUserDefaults.standardUserDefaults.stringForKey("portfolio.monthlyTarget:$account")?.toLongOrNull()
    override fun writeMonthlyTarget(account: String, amount: Long) { NSUserDefaults.standardUserDefaults.setObject(amount.toString(), forKey = "portfolio.monthlyTarget:$account") }
    override fun readChartStyle() = NSUserDefaults.standardUserDefaults.stringForKey("portfolio.chartStyle")
    override fun writeChartStyle(style: String) { NSUserDefaults.standardUserDefaults.setObject(style, forKey = "portfolio.chartStyle") }
    override fun readLanguage() = NSUserDefaults.standardUserDefaults.stringForKey("portfolio.language")
    override fun writeLanguage(language: String) { NSUserDefaults.standardUserDefaults.setObject(language, forKey = "portfolio.language") }
    override fun readTheme() = NSUserDefaults.standardUserDefaults.stringForKey("portfolio.theme")
    override fun writeTheme(theme: String) { NSUserDefaults.standardUserDefaults.setObject(theme, forKey = "portfolio.theme") }
    override fun readOrigin() = NSUserDefaults.standardUserDefaults.stringForKey("portfolio.serverOrigin")
    override fun writeOrigin(origin: String) { NSUserDefaults.standardUserDefaults.setObject(origin, forKey = "portfolio.serverOrigin") }
} }
private class IosActions : NativeActions {
    private var savingPassword = false
    override val interactingWithSystem get() = picker != null || savingPassword
    override suspend fun offerPasswordSave(origin: String, email: String, password: String) = withContext(Dispatchers.Main) {
        savingPassword = true
        try { offerIosPasswordSave(origin, email, password) }
        finally { savingPassword = false }
    }
    private var delegate: UIDocumentPickerDelegateProtocol? = null
    private var picker: UIDocumentPickerViewController? = null
    private suspend fun select(controller: UIDocumentPickerViewController): NSURL? = suspendCancellableCoroutine { result ->
        check(picker == null) { "A document picker is already open." }
        picker = controller
        delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val selected = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                // Finish UIKit's picker transition before shared UI opens the review screen.
                controller.dismissViewControllerAnimated(true) {
                    if (result.isActive) result.resume(selected)
                    picker = null; delegate = null
                }
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                controller.dismissViewControllerAnimated(true) {
                    if (result.isActive) result.resume(null)
                    picker = null; delegate = null
                }
            }
        }
        controller.delegate = delegate
        result.invokeOnCancellation { controller.dismissViewControllerAnimated(true, null); picker = null; delegate = null }
        presentingController().presentViewController(controller, animated = true, completion = null)
    }
    override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = withContext(Dispatchers.Main) {
        val url = select(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)) ?: return@withContext null
        onSelected()
        val access = url.startAccessingSecurityScopedResource()
        try {
            withContext(Dispatchers.Default) {
                val stream = NSInputStream(uRL = url)
                stream.open()
                try {
                    val chunks = mutableListOf<ByteArray>()
                    var total = 0
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = buffer.usePinned { stream.read(it.addressOf(0).reinterpret(), maxLength = buffer.size.toULong()) }.toInt()
                        require(count >= 0) { "Could not read the selected PDF." }
                        if (count == 0) break
                        total += count
                        require(total <= MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
                        chunks += buffer.copyOf(count)
                    }
                    require(total > 0) { "The selected PDF is empty." }
                    ByteArray(total).also { output ->
                        var offset = 0
                        chunks.forEach { it.copyInto(output, offset); offset += it.size }
                    }
                } finally { stream.close() }
            }
        } finally { if (access) url.stopAccessingSecurityScopedResource() }
    }
    override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean = withContext(Dispatchers.Main) {
        val directory = NSTemporaryDirectory() + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "investment.pdf" }
        val url = NSURL.fileURLWithPath("$directory/$safeName")
        try {
            withContext(Dispatchers.Default) { check(bytes.toData().writeToURL(url, atomically = true)) { "Unable to prepare the document." } }
            select(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)) != null
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, error = null) }
    }
    override fun openUrl(url: String) {
        val destination = requireNotNull(NSURL.URLWithString(url))
        require(destination.scheme in listOf("https", "http"))
        UIApplication.sharedApplication.openURL(destination, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
    override fun openEmail(address: String) {
        val destination = requireNotNull(NSURL.URLWithString("mailto:$address"))
        UIApplication.sharedApplication.openURL(destination, options = emptyMap<Any?, Any?>()) { opened ->
            if (!opened) shareText(address)
        }
    }
    override fun shareText(text: String) {
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        val presenter = presentingController()
        controller.popoverPresentationController?.sourceView = presenter.view
        controller.popoverPresentationController?.sourceRect = CGRectMake(0.0, 0.0, 1.0, 1.0)
        presentingController().presentViewController(controller, animated = true, completion = null)
    }
}
@Composable actual fun rememberNativeActions(): NativeActions = remember { IosActions() }
@Composable actual fun rememberInvestmentEngine(): InvestmentEngine = remember { IosInvestmentEngine() }
// JavaScriptCore has no visual view and needs no hidden browser on iOS.
@Composable actual fun InvestmentEngineHost(engine: InvestmentEngine) = Unit
// iOS uses the visible shared Back / Cancel controls; there is no Android-style hardware back event.
@Composable actual fun PlatformBackHandler(enabled: Boolean, back: () -> Unit) = Unit

// SwiftUI's preferredColorScheme observes the same saved appearance preference.
@Composable actual fun PlatformSystemBarAppearance(darkTheme: Boolean) {}
