package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*

/** A device preference, shared by every account and both native clients. */
object AppLanguage {
    var preference by mutableStateOf("system")
        private set
    var systemLanguage by mutableStateOf("en")
        internal set
    val code: String get() = resolveLanguage(preference, systemLanguage)
    private var settings: ServerSettings? = null

    fun initialize(settings: ServerSettings) {
        this.settings = settings
        preference = settings.readLanguage()?.takeIf { it in supportedPreferences } ?: "system"
    }
    fun select(value: String) {
        require(value in supportedPreferences)
        settings?.writeLanguage(value)
        preference = value
    }
    val supportedPreferences = listOf("system", "en", "fr")
}

internal fun resolveLanguage(preference: String, system: String): String {
    val requested = if (preference == "system") system else preference
    return if (requested.substringBefore('-').substringBefore('_').lowercase() == "fr") "fr" else "en"
}

@Composable internal expect fun platformSystemLanguage(): String

private data class TranslationTemplate(val pattern: Regex, val replacement: String)
private val translationTemplates by lazy {
    frenchStrings.entries.filter { "{0}" in it.key }.sortedByDescending { it.key.length }.map { (source, target) ->
        val tokens = Regex("\\{(\\d+)\\}").findAll(source).toList()
        var end = 0
        val pattern = buildString {
            append('^')
            tokens.forEach { token ->
                append(Regex.escape(source.substring(end, token.range.first)))
                append("([\\s\\S]*?)")
                end = token.range.last + 1
            }
            append(Regex.escape(source.substring(end)))
            append('$')
        }
        TranslationTemplate(Regex(pattern), target)
    }
}

/** Presentation-only lookup: never use this for routes, storage, API fields or imported data. */
fun localized(text: String, language: String = AppLanguage.code): String {
    if (language != "fr" || text.isEmpty()) return text
    frenchStrings[text]?.let { return it }
    for (template in translationTemplates) {
        val match = template.pattern.matchEntire(text) ?: continue
        return Regex("\\{(\\d+)\\}").replace(template.replacement) { token ->
            // Dynamic values (names, amounts, document titles) are preserved verbatim.
            match.groupValues[token.groupValues[1].toInt() + 1]
        }
    }
    return text
}

internal fun calendarWeekdayLabels(): List<String> = if (AppLanguage.code == "fr")
    listOf("L", "M", "M", "J", "V", "S", "D") else listOf("M", "T", "W", "T", "F", "S", "S")

/** Only known UI categories; free-form portfolio and document content is never rewritten. */
internal fun displayRecordValue(value: String) = when (value) {
    "active", "submitted", "sold", "matured", "estimated", "confirmed", "primary", "secondary", "treasury", "government", "corporate", "municipal", "other" -> localized(value.replaceFirstChar { it.uppercase() })
    else -> value
}
