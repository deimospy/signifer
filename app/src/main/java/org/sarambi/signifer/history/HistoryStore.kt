package org.sarambi.signifer.history

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.decode.CodeFormat

/** El historial, sobre el SQLite del sistema. */
class HistoryStore(context: Context, name: String = NAME) {
    private val helper = Helper(context.applicationContext, name)

    /** Guarda una lectura. */
    fun save(
        text: String,
        format: CodeFormat,
        origin: HistoryOrigin = HistoryOrigin.SCANNED,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val kind = parseContent(text).kind
        val database = helper.writableDatabase
        return database.transaction {
            database.rawQuery(
                "SELECT $COLUMN_ID, $COLUMN_TIMES FROM $TABLE " +
                    "WHERE $COLUMN_TEXT = ? AND $COLUMN_FORMAT = ? LIMIT 1",
                arrayOf(text, format.name),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val existing = cursor.getLong(0)
                    database.update(
                        TABLE,
                        ContentValues().apply {
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_TIMES, cursor.getInt(1) + 1)
                        },
                        "$COLUMN_ID = ?",
                        arrayOf(existing.toString()),
                    )
                    existing
                } else {
                    database.insert(
                        TABLE,
                        null,
                        ContentValues().apply {
                            put(COLUMN_TEXT, text)
                            put(COLUMN_SEARCH, TextNormalizer.normalize(text))
                            put(COLUMN_FORMAT, format.name)
                            put(COLUMN_KIND, kind.name)
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_FAVORITE, 0)
                            put(COLUMN_ORIGIN, origin.name)
                            put(COLUMN_TIMES, 1)
                        },
                    )
                }
            }
        }
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        helper.writableDatabase.update(
            TABLE,
            ContentValues().apply { put(COLUMN_FAVORITE, if (favorite) 1 else 0) },
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
        )
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete(TABLE, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    /** Borra todo menos los favoritos. */
    fun clear(keepFavorites: Boolean = true) {
        val where = if (keepFavorites) "$COLUMN_FAVORITE = 0" else null
        helper.writableDatabase.delete(TABLE, where, null)
    }

    /** Aplica la retencion. */
    fun applyRetention(retention: Retention, now: Long = System.currentTimeMillis()): Int {
        if (retention == Retention.FOREVER) return 0
        val limit = now - retention.days * MILLIS_PER_DAY
        return helper.writableDatabase.delete(
            TABLE,
            "$COLUMN_FAVORITE = 0 AND $COLUMN_CREATED_AT < ?",
            arrayOf(limit.toString()),
        )
    }

    fun entries(filter: HistoryFilter = HistoryFilter(), limit: Int = 500): List<HistoryEntry> {
        val conditions = mutableListOf<String>()
        val arguments = mutableListOf<String>()

        if (filter.query.isNotBlank()) {
            conditions += "instr($COLUMN_SEARCH, ?) > 0"
            arguments += TextNormalizer.normalize(filter.query.trim())
        }
        if (filter.onlyFavorites) conditions += "$COLUMN_FAVORITE = 1"
        if (filter.kinds.isNotEmpty()) {
            conditions += "$COLUMN_KIND IN (${placeholders(filter.kinds.size)})"
            arguments += filter.kinds.map { it.name }
        }
        if (filter.origins.isNotEmpty()) {
            conditions += "$COLUMN_ORIGIN IN (${placeholders(filter.origins.size)})"
            arguments += filter.origins.map { it.name }
        }

        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        val sql = "SELECT $ALL_COLUMNS FROM $TABLE $where " +
            "ORDER BY $COLUMN_FAVORITE DESC, $COLUMN_CREATED_AT DESC LIMIT $limit"

        return helper.readableDatabase.rawQuery(sql, arguments.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEntry())
            }
        }
    }

    fun byId(id: Long): HistoryEntry? =
        helper.readableDatabase.rawQuery(
            "SELECT $ALL_COLUMNS FROM $TABLE WHERE $COLUMN_ID = ?",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEntry() else null }

    fun count(): Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    /** Inserta una entrada completa. */
    fun restore(entry: HistoryEntry): Long = helper.writableDatabase.insert(
        TABLE,
        null,
        ContentValues().apply {
            put(COLUMN_TEXT, entry.text)
            put(COLUMN_SEARCH, TextNormalizer.normalize(entry.text))
            put(COLUMN_FORMAT, entry.format.name)
            put(COLUMN_KIND, entry.kind.name)
            put(COLUMN_CREATED_AT, entry.createdAt)
            put(COLUMN_FAVORITE, if (entry.favorite) 1 else 0)
            put(COLUMN_ORIGIN, entry.origin.name)
            put(COLUMN_TIMES, entry.times)
        },
    )

    /** Restaura un respaldo entero en una sola transaccion. */
    fun restoreAll(entries: List<HistoryEntry>): Int {
        var added = 0
        helper.writableDatabase.transaction {
            for (entry in entries) {
                if (contains(entry.text, entry.format)) continue
                restore(entry)
                added += 1
            }
        }
        return added
    }

    /** Si ya existe la misma carga en el mismo formato. */
    fun contains(text: String, format: CodeFormat): Boolean =
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE WHERE $COLUMN_TEXT = ? AND $COLUMN_FORMAT = ? LIMIT 1",
            arrayOf(text, format.name),
        ).use { it.moveToFirst() }

    fun close() {
        helper.close()
    }

    private fun Cursor.toEntry() = HistoryEntry(
        id = getLong(0),
        text = getString(1),
        format = CodeFormat.byName(getString(2)),
        kind = kindOf(getString(3)),
        createdAt = getLong(4),
        favorite = getInt(5) == 1,
        origin = originOf(getString(6)),
        times = getInt(7),
    )

    private fun kindOf(name: String): ContentKind =
        ContentKind.entries.firstOrNull { it.name == name } ?: ContentKind.TEXT

    private fun originOf(name: String): HistoryOrigin =
        HistoryOrigin.entries.firstOrNull { it.name == name } ?: HistoryOrigin.SCANNED

    private fun placeholders(count: Int) = List(count) { "?" }.joinToString(", ")

    private class Helper(context: Context, name: String) :
        SQLiteOpenHelper(context, name, null, VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_TEXT TEXT NOT NULL,
                    $COLUMN_SEARCH TEXT NOT NULL,
                    $COLUMN_FORMAT TEXT NOT NULL,
                    $COLUMN_KIND TEXT NOT NULL,
                    $COLUMN_CREATED_AT INTEGER NOT NULL,
                    $COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0,
                    $COLUMN_ORIGIN TEXT NOT NULL DEFAULT '${HistoryOrigin.SCANNED.name}',
                    $COLUMN_TIMES INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX idx_created ON $TABLE($COLUMN_CREATED_AT DESC)")
            database.execSQL("CREATE INDEX idx_lookup ON $TABLE($COLUMN_TEXT, $COLUMN_FORMAT)")
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        }
    }

    companion object {
        @Volatile
        private var shared: HistoryStore? = null

        /** La instancia de la aplicacion. */
        fun get(context: Context): HistoryStore =
            shared ?: synchronized(this) {
                shared ?: HistoryStore(context.applicationContext).also { shared = it }
            }

        const val NAME = "history.db"
        const val VERSION = 1

        const val TABLE = "entries"
        const val COLUMN_ID = "id"
        const val COLUMN_TEXT = "text"
        const val COLUMN_SEARCH = "search_text"
        const val COLUMN_FORMAT = "format"
        const val COLUMN_KIND = "kind"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_FAVORITE = "favorite"
        const val COLUMN_ORIGIN = "origin"
        const val COLUMN_TIMES = "times"

        private const val ALL_COLUMNS =
            "$COLUMN_ID, $COLUMN_TEXT, $COLUMN_FORMAT, $COLUMN_KIND, " +
                "$COLUMN_CREATED_AT, $COLUMN_FAVORITE, $COLUMN_ORIGIN, $COLUMN_TIMES"

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
