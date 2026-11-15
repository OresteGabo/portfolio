package dev.orestegabo.portfolio.network

/** Native API traffic goes directly to Spring; website hosting and redirects are not in its path. */
const val DEFAULT_ORIGIN = "https://gabo-portfolio-api-639858675564.europe-west1.run.app"
const val LEGACY_WEBSITE_API_ORIGIN = "https://bonds.orestegabo.dev"

/** Only the two verified legacy production gateways migrate. Custom and development servers stay explicit. */
fun isLegacyWebsiteApiOrigin(value: String?): Boolean = value?.trim()?.trimEnd('/')?.lowercase() in
    setOf(LEGACY_WEBSITE_API_ORIGIN, "https://invest.orestegabo.dev")

fun nativeBackendOrigin(savedOrigin: String?): String =
    if (savedOrigin == null || isLegacyWebsiteApiOrigin(savedOrigin)) DEFAULT_ORIGIN else savedOrigin
