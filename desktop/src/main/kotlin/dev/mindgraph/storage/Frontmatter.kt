package dev.mindgraph.storage

/**
 * A deliberately small YAML subset — enough for node frontmatter, no dependency, no surprises:
 * `key: scalar`, inline `key: [a, b]`, and block sequences of `- item`. Quotes are optional on
 * read and added on write only where a value would otherwise be ambiguous.
 *
 * Keys it doesn't recognize are kept in [extras] and written back out untouched, so a field you
 * add by hand in another editor survives a round trip through the app.
 */
data class Frontmatter(
    private val values: Map<String, Value>,
) {
    sealed interface Value {
        data class Scalar(val text: String) : Value
        data class Sequence(val items: List<String>) : Value
    }

    fun string(key: String): String? =
        (values[key] as? Value.Scalar)?.text?.takeIf { it.isNotEmpty() }

    fun list(key: String): List<String> = when (val value = values[key]) {
        is Value.Sequence -> value.items
        is Value.Scalar -> value.text.takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
        null -> emptyList()
    }

    fun extras(known: Set<String>): Map<String, Value> = values.filterKeys { it !in known }

    companion object {
        private const val FENCE = "---"

        /** Splits a document into its frontmatter and body. A file without a fence is all body. */
        fun split(document: String): Pair<Frontmatter, String> {
            val normalized = document.replace("\r\n", "\n")
            if (!normalized.startsWith("$FENCE\n")) return Frontmatter(emptyMap()) to normalized

            val closing = normalized.indexOf("\n$FENCE", startIndex = FENCE.length)
            if (closing < 0) return Frontmatter(emptyMap()) to normalized

            val block = normalized.substring(FENCE.length + 1, closing)
            val afterFence = closing + FENCE.length + 1
            val body = normalized.substring(afterFence).removePrefix("\n")
            return parse(block) to body
        }

        fun parse(block: String): Frontmatter {
            val values = LinkedHashMap<String, Value>()
            var pendingKey: String? = null
            var pendingItems = mutableListOf<String>()

            fun flushPending() {
                val key = pendingKey ?: return
                values[key] = Value.Sequence(pendingItems.toList())
                pendingKey = null
                pendingItems = mutableListOf()
            }

            for (rawLine in block.lines()) {
                val line = rawLine.trimEnd()
                if (line.isBlank() || line.trimStart().startsWith("#")) continue

                val itemMatch = Regex("^\\s+-\\s+(.*)$").find(line)
                if (itemMatch != null && pendingKey != null) {
                    pendingItems.add(unquote(itemMatch.groupValues[1]))
                    continue
                }

                flushPending()

                val separator = line.indexOf(':')
                if (separator <= 0) continue
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()

                when {
                    value.isEmpty() -> pendingKey = key
                    value.startsWith("[") && value.endsWith("]") ->
                        values[key] = Value.Sequence(splitInline(value.substring(1, value.length - 1)))
                    else -> values[key] = Value.Scalar(unquote(value))
                }
            }
            flushPending()
            return Frontmatter(values)
        }

        private fun splitInline(inner: String): List<String> =
            inner.split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }

        private fun unquote(raw: String): String {
            val trimmed = raw.trim()
            val isQuoted = trimmed.length >= 2 &&
                ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'")))
            if (!isQuoted) return trimmed
            return trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }

        /** Quotes only when leaving a value bare would change how it parses back. */
        fun quote(value: String): String {
            val needsQuotes = value.isEmpty() ||
                value != value.trim() ||
                value.first() in "-[]{}#&*!|>%@`\"'" ||
                value.contains(": ") ||
                value.endsWith(":")
            if (!needsQuotes) return value
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }

        fun renderValue(value: Value): String = when (value) {
            is Value.Scalar -> quote(value.text)
            is Value.Sequence -> "[" + value.items.joinToString(", ") { quote(it) } + "]"
        }
    }
}
