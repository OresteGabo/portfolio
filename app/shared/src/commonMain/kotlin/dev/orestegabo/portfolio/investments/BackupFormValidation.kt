package dev.orestegabo.portfolio.investments

/** Presentation preflight mirrors the existing backup envelope's password limits; never trims a password. */
internal data class BackupFormErrors(val password: String?, val confirmation: String?) {
    val valid get() = password == null && confirmation == null
}

internal fun backupFormErrors(password: String, confirmation: String, exporting: Boolean): BackupFormErrors = BackupFormErrors(
    password = if (password.length in 12..256) null else "Use a backup password between 12 and 256 characters.",
    confirmation = if (exporting && password != confirmation) "The backup passwords do not match." else null,
)
