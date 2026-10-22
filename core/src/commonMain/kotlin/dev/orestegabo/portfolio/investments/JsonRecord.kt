package dev.orestegabo.portfolio.investments

import kotlinx.serialization.json.*

/** Shared lossless API values: document facts and instrument-specific fields remain intact. */
class JsonRecord private constructor(private val values: MutableMap<String, JsonElement>) {
    constructor() : this(linkedMapOf())
    constructor(raw: String) : this(Json.parseToJsonElement(raw).jsonObject.toMutableMap())
    internal constructor(raw: JsonObject) : this(raw.toMutableMap())
    fun put(key: String, value: Any?): JsonRecord = apply { values[key] = jsonValue(value) }
    fun has(key: String) = values.containsKey(key)
    fun isNull(key: String) = values[key] == null || values[key] == JsonNull
    fun text(key: String) = (values[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun optString(key: String, fallback: String = "") = text(key).ifEmpty { fallback }
    fun optDouble(key: String, fallback: Double = Double.NaN) = (values[key] as? JsonPrimitive)?.doubleOrNull ?: fallback
    fun optInt(key: String, fallback: Int = 0) = optDouble(key, fallback.toDouble()).toInt()
    fun optBoolean(key: String, fallback: Boolean = false) = (values[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
    fun optJSONObject(key: String) = (values[key] as? JsonObject)?.let(::JsonRecord)
    fun optJSONArray(key: String) = (values[key] as? JsonArray)?.let(::JsonList)
    fun getJSONObject(key: String) = requireNotNull(optJSONObject(key)) { "Missing object: $key" }
    fun getJSONArray(key: String) = requireNotNull(optJSONArray(key)) { "Missing list: $key" }
    fun getString(key: String): String { require(!isNull(key)) { "Missing value: $key" }; return text(key) }
    fun getDouble(key: String) = optDouble(key).also { require(it.isFinite()) { "Invalid number: $key" } }
    fun getInt(key: String) = getDouble(key).toInt()
    fun rows(key: String) = optJSONArray(key)?.objects().orEmpty()
    internal fun element() = JsonObject(values.toMap())
    override fun toString() = element().toString()
    companion object { fun quote(value: String) = JsonPrimitive(value).toString() }
}

class JsonList private constructor(private val values: List<JsonElement>) {
    constructor() : this(emptyList())
    constructor(raw: String) : this(Json.parseToJsonElement(raw).jsonArray.toList())
    constructor(raw: Collection<*>) : this(raw.map(::jsonValue))
    internal constructor(raw: JsonArray) : this(raw.toList())
    fun length() = values.size
    fun getString(index: Int) = (values[index] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun optJSONObject(index: Int) = (values.getOrNull(index) as? JsonObject)?.let(::JsonRecord)
    fun objects() = values.mapNotNull { (it as? JsonObject)?.let(::JsonRecord) }
    internal fun element() = JsonArray(values)
    override fun toString() = element().toString()
}
private fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonRecord -> value.element()
    is JsonList -> value.element()
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> {
        require(value.toDouble().isFinite()) { "JSON numbers must be finite." }
        JsonPrimitive(value)
    }
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to jsonValue(it.value) })
    is Collection<*> -> JsonArray(value.map(::jsonValue))
    else -> error("Unsupported JSON value: ${value::class.simpleName}")
}
