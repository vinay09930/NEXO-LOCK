package com.vinay.nexolock.vault

/**
 * Presentation-neutral file classification used by the Personal Vault UI and
 * by JVM tests. MIME strings are matched defensively because document
 * providers are allowed to report `application/octet-stream` for a perfectly
 * valid DOCX/PDF.
 */
enum class VaultFileCategory(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    OTHER("Other"),
    NOTES("Notes"),
}

fun VaultFile.category(): VaultFileCategory = when {
    mimeType.startsWith("image/", ignoreCase = true) -> VaultFileCategory.PHOTOS
    mimeType.startsWith("video/", ignoreCase = true) -> VaultFileCategory.VIDEOS
    isAudioMime(mimeType, displayName) -> VaultFileCategory.AUDIO
    isDocumentMime(mimeType, displayName) -> VaultFileCategory.DOCUMENTS
    else -> VaultFileCategory.OTHER
}

fun isAudioMime(mimeType: String, displayName: String = ""): Boolean {
    if (mimeType.startsWith("audio/", ignoreCase = true)) return true
    // A few providers report application/ogg or octet-stream for audio. Only
    // classify by extension for an explicit, unambiguous audio allow-list.
    val mime = mimeType.lowercase()
    if (mime == "application/ogg" || mime == "application/x-ogg") return true
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return extension in AUDIO_EXTENSIONS
}

fun isDocumentMime(mimeType: String, displayName: String): Boolean {
    val mime = mimeType.lowercase()
    if (mime == "application/pdf") return true
    if (mime.startsWith("text/")) return true
    if (mime == "application/rtf" || mime == "application/json" || mime == "application/xml") {
        return true
    }
    if (mime == "application/msword" || mime == "application/vnd.ms-excel" ||
        mime == "application/vnd.ms-powerpoint"
    ) {
        return true
    }
    if (mime.startsWith("application/vnd.openxmlformats-officedocument.")) return true
    if (mime.startsWith("application/vnd.oasis.opendocument.")) return true
    // Some providers report octet-stream; fall back to the extension only for
    // a small, explicit document allow-list.
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return extension in DOCUMENT_EXTENSIONS
}

fun isPreviewableMime(mimeType: String): Boolean {
    val mime = mimeType.lowercase()
    return mime.startsWith("image/") ||
        mime.startsWith("video/") ||
        mime.startsWith("audio/") ||
        mime == "application/pdf" ||
        mime.startsWith("text/") ||
        mime == "application/json" ||
        mime == "application/xml"
}

fun isImageMime(mimeType: String): Boolean =
    mimeType.startsWith("image/", ignoreCase = true)

fun isVideoMime(mimeType: String): Boolean =
    mimeType.startsWith("video/", ignoreCase = true)

/**
 * Removes path components and control characters from a provider-supplied
 * display name. Unicode letters are preserved; the result is bounded so a
 * hostile provider cannot create an unbounded index row.
 */
fun sanitizeDisplayName(raw: String?): String {
    val base = raw.orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filterNot { it.isISOControl() }
        .trim()
    return base.take(120).ifBlank { "Imported file" }
}

private val DOCUMENT_EXTENSIONS = setOf(
    "pdf", "txt", "md", "csv", "json", "xml", "rtf",
    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "amr", "m4b", "mid", "midi",
)
