package dev.orestegabo.portfolio.investments

/** Search metadata contains labels only: never account values or financial figures. */
internal data class SettingsDestination(
    val key: String,
    val title: String,
    val summary: String,
    val entries: List<String>,
)

internal fun settingsDestinations(
    guest: Boolean, owner: Boolean, personalGuest: Boolean, widgets: Boolean, push: Boolean,
    canDeleteAccount: Boolean, canLock: Boolean,
): List<SettingsDestination> = buildList {
    add(SettingsDestination("privacy", "Privacy", "Number visibility, screenshots & documents", buildList {
        addAll(listOf("Protect screenshots & recordings", "Hide numbers", "Shake to hide numbers", "PDF imports & your data"))
        if (canDeleteAccount) add("Delete account")
    }))
    if (!guest) add(SettingsDestination("security", "Security", "Device unlock & sign-in activity", buildList {
        addAll(listOf("Sign-in activity", "Secure sign-in", "Passwords & AutoFill"))
        if (canLock) add("Lock app now")
    }))
    add(SettingsDestination("appearance", "Appearance", "Language, theme & charts",
        listOf("Language", "System", "Light", "Dark", "Chart style")))
    add(SettingsDestination("investing", "Investing", "Goals, cash & transactions", buildList {
        addAll(listOf("Cash & transactions", "Investment goal"))
        if (personalGuest) add("Encrypted backup")
    }))
    if (!guest) {
        add(SettingsDestination("notifications", "Notifications", "Reminders & home screen widgets", buildList {
            add("Notification preferences")
            if (push) add("Phone notifications")
            if (widgets) addAll(listOf("What’s next · Small", "What’s next · Medium"))
        }))
        add(SettingsDestination("tools", "Tools", "Reports, outlook & learning",
            listOf("Portfolio report", "Investment outlook", "Investment simulator", "Bond education", "Find bond purchases")))
    }
    add(SettingsDestination("help", "Help & support", "Help, about Inzira & legal documents", buildList {
        addAll(listOf("Report a problem", "Contact support", "About Inzira", "Replay introduction", "Legal documents", "Inzira"))
        if (!guest) addAll(listOf("Portfolio website", "Password setup"))
    }))
    if (owner && !guest) add(SettingsDestination("administration", "Administration", "Accounts & invitations", listOf("Accounts", "Invitations")))
    if (!guest) add(SettingsDestination("account", "Account", "Guest data & sign out", listOf("Transfer Guest Data", "Continue as Guest", "Sign out")))
}

internal data class SettingsSearchResult(val destination: SettingsDestination, val label: String)

/** Match both translated labels and English keys, including common names for goal controls. */
internal fun searchSettings(
    destinations: List<SettingsDestination>, query: String, translate: (String) -> String,
): List<SettingsSearchResult> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.isEmpty()) return emptyList()
    val matches = destinations.flatMap { destination ->
        val category = "${destination.title} ${translate(destination.title)} ${destination.summary} ${translate(destination.summary)}"
        destination.entries.mapNotNull { label ->
            val aliases = when (label) {
                "Investment goal" -> "Monthly Yearly target ${translate("Monthly")} ${translate("Yearly")} ${translate("Edit investment goal")}"
                "Secure sign-in" -> "biometric fingerprint Face ID Touch ID"
                "Notification preferences" -> "reminders coupon categories ${translate("Reminders")}"
                "Portfolio report" -> "PDF export ${translate("PDF export")}"
                else -> ""
            }
            val option = "$label ${translate(label)} $aliases".lowercase()
            val categoryAndOption = "$category $option".lowercase()
            val rank = when {
                words.all { it in option } -> 3
                words.all { it in "${destination.title} ${translate(destination.title)} $option".lowercase() } -> 2
                words.all { it in categoryAndOption } -> 1
                else -> 0
            }
            if (rank > 0) SettingsSearchResult(destination, label) to rank else null
        }
    }
    // Prefer the requested control over unrelated rows sharing its category description.
    val bestRank = matches.maxOfOrNull { it.second }
    return matches.filter { it.second == bestRank }.map { it.first }
}
