package dev.orestegabo.portfolio.investments

internal actual fun issueReportPlatform(): String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
