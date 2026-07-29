package com.example.familytreeplatform.repository

private val isoLocalDatePattern = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * The API accepts an unknown death date as null, never as an empty string.
 * Keeping this normalization at the repository boundary also repairs legacy
 * queued mutations when they are retried after an app update.
 */
internal fun normalizeLifeStatusDate(lifeStatus: String, deceasedAt: String?): String? {
    if (lifeStatus != "DECEASED") return null
    val normalized = deceasedAt?.trim()?.takeIf(String::isNotEmpty) ?: return null
    require(isoLocalDatePattern.matches(normalized)) {
        "Tanggal wafat harus menggunakan format YYYY-MM-DD."
    }
    return normalized
}
