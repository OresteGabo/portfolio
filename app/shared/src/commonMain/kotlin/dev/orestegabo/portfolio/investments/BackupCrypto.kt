package dev.orestegabo.portfolio.investments

internal const val BACKUP_MAX_BYTES = 48 * 1024 * 1024
private val backupMagic = "SCBK1".encodeToByteArray()
internal expect fun backupRandom(size: Int): ByteArray
internal expect fun backupKey(password: String, salt: ByteArray): ByteArray
internal expect fun backupCipher(input: ByteArray, key: ByteArray, iv: ByteArray, encrypt: Boolean): ByteArray
internal expect fun backupMac(input: ByteArray, key: ByteArray): ByteArray

/** Fixed-version PBKDF2-HMAC-SHA256 / AES-256-CBC / encrypt-then-HMAC-SHA256 envelope.
 * Separate encryption/authentication keys; authentication is checked before decryption. */
internal fun encryptBackup(plain: ByteArray, password: String): ByteArray {
    require(password.length in 12..256) { "Use a backup password between 12 and 256 characters." }
    require(plain.size in 1..BACKUP_MAX_BYTES - 128) { "Backup is too large." }
    val salt = backupRandom(16); val iv = backupRandom(16); val key = backupKey(password, salt)
    try {
        val ciphertext = backupCipher(plain, key.copyOfRange(0, 32), iv, true)
        val body = backupMagic + salt + iv + ciphertext
        return body + backupMac(body, key.copyOfRange(32, 64))
    } finally { key.fill(0) }
}
internal fun decryptBackup(bytes: ByteArray, password: String): ByteArray {
    require(bytes.size in 85..BACKUP_MAX_BYTES && bytes.copyOfRange(0, 5).contentEquals(backupMagic) && (bytes.size - 69) % 16 == 0) { "Choose a valid Struct Capital backup." }
    require(password.length in 12..256) { "Use a backup password between 12 and 256 characters." }
    val key = backupKey(password, bytes.copyOfRange(5, 21))
    try {
        val body = bytes.copyOfRange(0, bytes.size - 32)
        val mac = backupMac(body, key.copyOfRange(32, 64))
        var difference = 0
        for (i in mac.indices) difference = difference or (mac[i].toInt() xor bytes[body.size + i].toInt())
        require(difference == 0) { "Wrong backup password or damaged backup. Your current data was not changed." }
        return backupCipher(bytes.copyOfRange(37, body.size), key.copyOfRange(0, 32), bytes.copyOfRange(21, 37), false)
    } finally { key.fill(0) }
}
