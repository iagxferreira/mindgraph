package dev.mindgraph.storage

import java.security.SecureRandom

/**
 * ULIDs: a 48-bit millisecond timestamp followed by 80 bits of randomness, rendered in
 * Crockford base32. Chosen over UUIDs because they sort chronologically as plain strings,
 * which keeps `ls` on a vault and any id-ordered listing in creation order for free.
 */
object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIME_CHARS = 10
    private const val RANDOM_CHARS = 16

    private val random = SecureRandom()

    fun generate(atMillis: Long = System.currentTimeMillis()): String {
        val builder = StringBuilder(TIME_CHARS + RANDOM_CHARS)

        var timestamp = atMillis
        val time = CharArray(TIME_CHARS)
        for (i in TIME_CHARS - 1 downTo 0) {
            time[i] = ALPHABET[(timestamp % 32).toInt()]
            timestamp /= 32
        }
        builder.append(time)

        repeat(RANDOM_CHARS) { builder.append(ALPHABET[random.nextInt(32)]) }
        return builder.toString()
    }

    /** Lenient by design: anything shaped like a ULID is accepted, so hand-edited files load. */
    fun looksValid(candidate: String): Boolean =
        candidate.length == TIME_CHARS + RANDOM_CHARS && candidate.all { it in ALPHABET }
}
