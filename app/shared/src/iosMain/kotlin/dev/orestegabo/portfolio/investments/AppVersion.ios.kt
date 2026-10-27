package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle

@Composable
internal actual fun rememberAppVersion(): AppVersion? = remember {
    val bundle = NSBundle.mainBundle
    val name = (bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)?.takeIf { it.isNotBlank() }
    val build = (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.takeIf { it.isNotBlank() }
    name?.let { AppVersion(it, build) }
}
