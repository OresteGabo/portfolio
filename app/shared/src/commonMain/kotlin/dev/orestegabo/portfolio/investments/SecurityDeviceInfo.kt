package dev.orestegabo.portfolio.investments

internal expect fun securityDeviceModel(): String

internal fun securityDeviceTitle(record: JsonRecord): String = record.text("deviceModel").ifBlank {
    when (record.text("platform")) { "ios" -> "iPhone / iPad"; "android" -> "Android"; else -> localized("Unknown device") }
}

internal fun securityDeviceSoftware(record: JsonRecord): String = listOf(
    record.text("osVersion"), record.text("appVersion").takeIf { it.isNotBlank() }?.let { "Struct Capital $it" }.orEmpty()
).filter { it.isNotBlank() }.joinToString(" · ")
