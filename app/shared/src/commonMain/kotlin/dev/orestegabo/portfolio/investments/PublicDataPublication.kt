package dev.orestegabo.portfolio.investments

internal const val PUBLIC_DATA_PERMISSION_ERROR = "Only the owner or authorized Struct Capital staff can publish shared data."

/** Presentation hint only. The API must check its own capability on every upload chunk. */
internal fun mayPublishSharedData(user: JsonRecord?): Boolean =
    user?.optBoolean("isOwner") == true || user?.optBoolean("canPublishPublicData") == true
