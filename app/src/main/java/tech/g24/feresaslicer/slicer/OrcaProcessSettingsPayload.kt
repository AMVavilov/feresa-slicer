// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.Collections
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Stable wire contract for an OrcaSlicer process configuration.
 *
 * Orca setting names and primitive values are deliberately kept opaque here. This lets the
 * Android UI, cloud profiles and the native slicer exchange the complete Orca setting namespace
 * without maintaining a second whitelist or renaming values at the JNI boundary.
 *
 * The native-facing representation is a canonical UTF-8 JSON object: object keys are sorted
 * lexicographically, insignificant whitespace is omitted, and strings are escaped by
 * [JSONObject]. Only JSON primitives are accepted; nested objects and arrays are configuration
 * errors rather than values the native layer might silently ignore.
 */
class OrcaProcessSettingsPayload private constructor(
    source: Map<String, Any?>,
) {
    private val entries: Map<String, Any?> = Collections.unmodifiableMap(LinkedHashMap(source))

    val size: Int
        get() = entries.size

    val keys: Set<String>
        get() = entries.keys

    operator fun contains(key: String): Boolean = entries.containsKey(key)

    operator fun get(key: String): Any? = entries[key]

    /** Returns a defensive, insertion-ordered snapshot of the original settings. */
    fun asMap(): Map<String, Any?> = LinkedHashMap(entries)

    /** Returns deterministic JSON suitable for golden tests, caching and the JNI boundary. */
    fun toCanonicalJson(): String = entries.keys
        .sorted()
        .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
            "${JSONObject.quote(key)}:${encodePrimitive(entries.getValueOrNull(key))}"
        }

    /** Returns exactly [toCanonicalJson] encoded as UTF-8, with no terminator or BOM. */
    fun toUtf8Payload(): ByteArray = toCanonicalJson().toByteArray(StandardCharsets.UTF_8)

    override fun equals(other: Any?): Boolean = other is OrcaProcessSettingsPayload &&
        toCanonicalJson() == other.toCanonicalJson()

    override fun hashCode(): Int = toCanonicalJson().hashCode()

    override fun toString(): String = toCanonicalJson()

    companion object {
        /**
         * Creates a payload without filtering or translating Orca setting names and values.
         *
         * A copy is taken before returning, so later mutations of [settings] cannot affect the
         * native request.
         */
        fun from(settings: Map<String, *>): OrcaProcessSettingsPayload {
            val validated = LinkedHashMap<String, Any?>(settings.size)
            settings.forEach { (key, value) ->
                validateKey(key)
                validated[key] = validatePrimitive(key, value)
            }
            return OrcaProcessSettingsPayload(validated)
        }

        /** Parses the same primitive-only JSON object accepted by the native contract. */
        fun parse(json: String): OrcaProcessSettingsPayload {
            val tokener = JSONTokener(json)
            val root = runCatching { tokener.nextValue() }
                .getOrElse { error -> throw IllegalArgumentException("Invalid process settings JSON", error) }
            require(root is JSONObject) { "Process settings payload must be a JSON object" }
            require(tokener.nextClean() == '\u0000') { "Unexpected content after process settings JSON" }

            val parsed = LinkedHashMap<String, Any?>()
            root.keys().asSequence().sorted().forEach { key ->
                val value = root.get(key).let { if (it === JSONObject.NULL) null else it }
                validateKey(key)
                parsed[key] = validatePrimitive(key, value)
            }
            return OrcaProcessSettingsPayload(parsed)
        }

        fun builder(): Builder = Builder()

        private fun validateKey(key: String) {
            require(key.isNotBlank()) { "Orca process setting key must not be blank" }
            require(key.none(Char::isISOControl)) {
                "Orca process setting key must not contain control characters: ${JSONObject.quote(key)}"
            }
        }

        private fun validatePrimitive(key: String, value: Any?): Any? = when (value) {
            null, is String, is Boolean, is Byte, is Short, is Int, is Long,
            is BigInteger, is BigDecimal -> value
            is Float -> value.also {
                require(it.isFinite()) { "Orca process setting '$key' must be a finite number" }
            }
            is Double -> value.also {
                require(it.isFinite()) { "Orca process setting '$key' must be a finite number" }
            }
            else -> throw IllegalArgumentException(
                "Orca process setting '$key' must be a JSON primitive, got ${value::class.java.name}",
            )
        }

        private fun encodePrimitive(value: Any?): String = when (value) {
            null -> "null"
            is String -> JSONObject.quote(value)
            is Boolean -> value.toString()
            is Number -> JSONObject.numberToString(value)
            else -> error("Unvalidated Orca process setting value: ${value::class.java.name}")
        }

        private fun Map<String, Any?>.getValueOrNull(key: String): Any? =
            if (containsKey(key)) get(key) else error("Missing process setting '$key'")
    }

    class Builder internal constructor() {
        private val settings = LinkedHashMap<String, Any?>()

        fun put(key: String, value: Any?): Builder = apply {
            validateKey(key)
            settings[key] = validatePrimitive(key, value)
        }

        fun putAll(values: Map<String, *>): Builder = apply {
            values.forEach { (key, value) -> put(key, value) }
        }

        fun build(): OrcaProcessSettingsPayload = OrcaProcessSettingsPayload(settings)
    }
}
