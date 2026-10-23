package io.github.peanutpersimmon.rilo.domain.model

/** Stable persistence keys for the fixed category palette. */
enum class CategoryColorKey(val storageKey: String) {
    NEUTRAL("neutral"),
    RED("red"),
    ORANGE("orange"),
    AMBER("amber"),
    OLIVE("olive"),
    GREEN("green"),
    TEAL("teal"),
    BLUE("blue"),
    PURPLE("purple"),
    PINK("pink"),
    BROWN("brown"),
    ;

    companion object {
        fun fromStorage(value: String): CategoryColorKey? =
            entries.firstOrNull { it.storageKey == value }

        fun fromStorageOrNeutral(value: String?): CategoryColorKey =
            value?.let(::fromStorage) ?: NEUTRAL
    }
}
