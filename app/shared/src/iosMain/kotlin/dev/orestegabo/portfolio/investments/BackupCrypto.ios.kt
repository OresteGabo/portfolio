@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

internal actual fun backupRandom(size: Int): ByteArray = ByteArray(size).also { out ->
    out.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) == 0) }
}
internal actual fun backupKey(password: String, salt: ByteArray): ByteArray {
    val text = password.encodeToByteArray(); val key = ByteArray(64)
    try {
        text.usePinned { p -> salt.usePinned { s -> key.usePinned { k ->
            check(CCKeyDerivationPBKDF(kCCPBKDF2, password, text.size.convert(), s.addressOf(0).reinterpret(), salt.size.convert(), kCCPRFHmacAlgSHA256, 600_000u, k.addressOf(0).reinterpret(), key.size.convert()) == kCCSuccess)
        } } }
        return key
    } finally { text.fill(0) }
}
internal actual fun backupCipher(input: ByteArray, key: ByteArray, iv: ByteArray, encrypt: Boolean): ByteArray = memScoped {
    val out = ByteArray(input.size + 16); val moved = alloc<ULongVar>()
    try {
        input.usePinned { data -> key.usePinned { k -> iv.usePinned { v -> out.usePinned { target ->
            check(CCCrypt(if (encrypt) kCCEncrypt else kCCDecrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding,
                k.addressOf(0), key.size.convert(), v.addressOf(0), data.addressOf(0), input.size.convert(), target.addressOf(0), out.size.convert(), moved.ptr) == kCCSuccess)
        } } } }
        out.copyOf(moved.value.toInt())
    } finally { key.fill(0); out.fill(0) }
}
internal actual fun backupMac(input: ByteArray, key: ByteArray): ByteArray = ByteArray(32).also { out ->
    try { input.usePinned { data -> key.usePinned { k -> out.usePinned { target ->
        CCHmac(kCCHmacAlgSHA256, k.addressOf(0), key.size.convert(), data.addressOf(0), input.size.convert(), target.addressOf(0))
    } } } } finally { key.fill(0) }
}
