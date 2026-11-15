package com.vinay.nexolock.vault

/**
 * Pure filename helpers for restoring decrypted vault copies through SAF.
 *
 * These functions contain no Android types so the exact naming behaviour is
 * unit-tested on the JVM. They never invent file content and never truncate a
 * name in a way that would drop the extension.
 */
object VaultExport {

    const val MAX_NAME_LENGTH = 120

    private const val FALLBACK_NAME = "Restored file"
    private const val MAX_UNIQUE_ATTEMPTS = 10_000

    /** Lower-case extension without the dot, or an empty string. */
    fun extensionOf(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.lastIndex) return ""
        val extension = base.substring(dot + 1)
        if (extension.length > 16) return ""
        if (extension.any { !it.isLetterOrDigit() }) return ""
        return extension.lowercase()
    }

    /**
     * Removes path components, control characters and characters that document
     * providers commonly reject. The result is never blank and is bounded to
     * [MAX_NAME_LENGTH] characters while keeping the extension.
     */
    fun safeName(raw: String?, fallback: String = FALLBACK_NAME): String {
        val cleaned = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { it.isISOControl() }
            .map { character ->
                if (character in INVALID_NAME_CHARACTERS) '_' else character
            }
            .joinToString("")
            .trim()
            .trimEnd('.')
        val base = cleaned.ifBlank { fallback }
        val withoutSeparators = base.replace('/', '_').replace('\\', '_')
        return bounded(withoutSeparators)
    }

    /**
     * Returns [desired] unless the same name (case-insensitive) is already in
     * [taken]; in that case a numbered suffix is inserted before the
     * extension: `photo.jpg`, `photo (1).jpg`, `photo (2).jpg`.
     */
    fun uniqueName(desired: String, taken: Collection<String>): String {
        val base = safeName(desired)
        val existing = taken.mapTo(HashSet()) { it.lowercase() }
        if (base.lowercase() !in existing) return base
        val extension = extensionOf(base)
        val stem = if (extension.isEmpty()) {
            base
        } else {
            base.dropLast(extension.length + 1)
        }.ifBlank { FALLBACK_NAME }
        val extensionPart = if (extension.isEmpty()) "" else ".$extension"
        var index = 1
        while (index <= MAX_UNIQUE_ATTEMPTS) {
            val suffix = " ($index)"
            val room = (MAX_NAME_LENGTH - suffix.length - extensionPart.length).coerceAtLeast(1)
            val candidate = stem.take(room).trimEnd() + suffix + extensionPart
            if (candidate.lowercase() !in existing) return candidate
            index++
        }
        return bounded("$stem ($MAX_UNIQUE_ATTEMPTS)$extensionPart")
    }

    private fun bounded(name: String): String {
        if (name.length <= MAX_NAME_LENGTH) return name
        val extension = extensionOf(name)
        if (extension.isEmpty()) return name.take(MAX_NAME_LENGTH).trimEnd()
        val suffix = ".$extension"
        val room = (MAX_NAME_LENGTH - suffix.length).coerceAtLeast(1)
        return name.take(room).trimEnd() + suffix
    }

    private val INVALID_NAME_CHARACTERS = charArrayOf(':', '*', '?', '"', '<', '>', '|')
}
