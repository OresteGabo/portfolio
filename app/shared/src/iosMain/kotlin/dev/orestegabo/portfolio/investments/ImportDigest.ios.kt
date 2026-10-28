@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256

internal actual fun importPdfDigest(bytes: ByteArray): String {
    val output = ByteArray(32)
    val input = if (bytes.isEmpty()) byteArrayOf(0) else bytes
    input.usePinned { source -> output.usePinned { target ->
        CC_SHA256(source.addressOf(0), bytes.size.toUInt(), target.addressOf(0).reinterpret())
    } }
    return output.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
