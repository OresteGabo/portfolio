package dev.orestegabo.portfolio

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.orestegabo.portfolio.investments.MAX_PDF_BYTES
import dev.orestegabo.portfolio.investments.NativePdfInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/** Copy granted content while the sending app's temporary URI permission is alive. */
internal fun FragmentActivity.receivePdf(intent: Intent) {
    if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_VIEW)) return
    val uri = runCatching {
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri)
        } else intent.data
    }.getOrNull()
    if (uri == null || uri.scheme != "content") { NativePdfInbox.reject("Could not open the selected PDF."); return }
    val delivery = intent.getStringExtra("struct.pdf.delivery") ?: UUID.randomUUID().toString().also {
        intent.putExtra("struct.pdf.delivery", it)
    }
    lifecycleScope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Could not open the selected PDF.")
            }
            val name = withContext(Dispatchers.IO) { runCatching { contentResolver.query(uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0).take(180) else null
                } }.getOrNull() }
            NativePdfInbox.offer(bytes, delivery, name ?: "Shared PDF")
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
            NativePdfInbox.reject(when (failure.message) {
                "Choose a PDF of up to 10 MB.", "The selected file is not a PDF." -> failure.message!!
                else -> "Could not open the selected PDF."
            })
        }
    }
}
