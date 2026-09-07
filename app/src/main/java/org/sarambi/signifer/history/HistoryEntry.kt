package org.sarambi.signifer.history

import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.decode.CodeFormat

/** De donde salio una entrada del historial. */
enum class HistoryOrigin {
    SCANNED,
    CREATED,
    IMPORTED,
}

/** Una linea del historial. */
data class HistoryEntry(
    val id: Long = 0,
    val text: String,
    val format: CodeFormat,
    val kind: ContentKind,
    val createdAt: Long,
    val favorite: Boolean = false,
    val origin: HistoryOrigin = HistoryOrigin.SCANNED,

    /** Cuantas veces se ha leido lo mismo. */
    val times: Int = 1,
)

/** Que se ensena en el historial. */
data class HistoryFilter(
    val query: String = "",
    val onlyFavorites: Boolean = false,
    val kinds: Set<ContentKind> = emptySet(),
    val origins: Set<HistoryOrigin> = emptySet(),
) {
    val isEmpty: Boolean
        get() = query.isBlank() && !onlyFavorites && kinds.isEmpty() && origins.isEmpty()
}

/** Cuanto se conserva el historial. */
enum class Retention(val days: Int) {
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    FOREVER(0),
    ;

    companion object {
        fun ofDays(days: Int): Retention = entries.firstOrNull { it.days == days } ?: FOREVER
    }
}
