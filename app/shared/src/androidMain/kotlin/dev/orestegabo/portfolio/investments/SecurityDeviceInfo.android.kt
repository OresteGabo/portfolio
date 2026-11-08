package dev.orestegabo.portfolio.investments

internal actual fun securityDeviceModel(): String {
    val model = android.os.Build.MODEL.orEmpty()
    val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
    return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
}
