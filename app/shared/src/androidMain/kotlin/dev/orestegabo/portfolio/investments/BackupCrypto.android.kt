package dev.orestegabo.portfolio.investments

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal actual fun backupRandom(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
internal actual fun backupKey(password: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, 600_000, 512)
    return try {
        val factory = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") } catch (_: java.security.NoSuchAlgorithmException) { null }
        factory?.generateSecret(spec)?.encoded ?: backupCompatibilityKey(password, salt)
    } finally { spec.clearPassword() }
}
internal actual fun backupCipher(input: ByteArray, key: ByteArray, iv: ByteArray, encrypt: Boolean): ByteArray = try {
    Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }.doFinal(input)
} finally { key.fill(0) }
internal actual fun backupMac(input: ByteArray, key: ByteArray): ByteArray = try {
    Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(input)
} finally { key.fill(0) }

/** RFC 8018 section 5.2, using platform HMAC; Android 7 lacks the SHA-256 SecretKeyFactory alias. */
internal fun backupCompatibilityKey(password: String, salt: ByteArray): ByteArray {
    val bytes = password.encodeToByteArray()
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(bytes, "HmacSHA256")) }
    val result = ByteArray(64)
    try {
        for (block in 1..2) {
            var u = mac.doFinal(salt + byteArrayOf(0,0,0,block.toByte()))
            val mixed = u.copyOf()
            try {
                repeat(599_999) {
                    val next = mac.doFinal(u); u.fill(0); u = next
                    for (i in mixed.indices) mixed[i] = (mixed[i].toInt() xor u[i].toInt()).toByte()
                }
                mixed.copyInto(result, (block - 1) * 32)
            } finally { u.fill(0); mixed.fill(0) }
        }
        return result
    } finally { bytes.fill(0) }
}
