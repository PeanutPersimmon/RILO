package io.github.peanutpersimmon.rilo.domain.service

import java.text.Normalizer
import java.util.Locale

object CategoryKeywordNormalizer {
    private val consecutiveWhitespace = Regex("[\\s\\p{Z}]+")

    /** trim -> Unicode NFKC -> lowercase(Locale.ROOT) -> collapse whitespace. */
    fun normalize(value: String): String = Normalizer
        .normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(consecutiveWhitespace, " ")
}

/** Sleep vocabulary stays owned by the sleep subsystem, regardless of which UI writes rules. */
object ReservedKeywordPolicy {
    private val reservedFragments = setOf(
        "午睡",
        "小睡",
        "小憩",
        "睡了一会儿",
        "睡一会儿",
        "打盹",
        "打个盹",
        "夜间睡眠",
        "睡觉",
        "睡眠",
        "入睡",
        "起床",
        "醒来",
        "补觉",
    ).map(CategoryKeywordNormalizer::normalize)

    fun isReserved(keyword: String): Boolean {
        val normalized = CategoryKeywordNormalizer.normalize(keyword)
        return normalized.isNotEmpty() && reservedFragments.any(normalized::contains)
    }
}
