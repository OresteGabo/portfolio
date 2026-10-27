package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat

@Composable
internal actual fun rememberAppVersion(): AppVersion? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName?.takeIf { it.isNotBlank() }?.let {
                AppVersion(it, PackageInfoCompat.getLongVersionCode(info).toString())
            }
        }.getOrNull()
    }
}
