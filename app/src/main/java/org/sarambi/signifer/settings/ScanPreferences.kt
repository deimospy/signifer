package org.sarambi.signifer.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.decode.ScanOptions

/** Los ajustes que caben en un archivo de preferencias. */
class ScanPreferences(context: Context) {
    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Los formatos activos. */
    fun formats(): Set<CodeFormat> {
        val stored = store.getStringSet(KEY_FORMATS, null) ?: return CodeFormat.ALL
        val formats = stored.mapNotNullTo(LinkedHashSet()) { name ->
            CodeFormat.byName(name).takeIf { it != CodeFormat.UNKNOWN }
        }
        return formats.ifEmpty { CodeFormat.ALL }
    }

    fun setFormats(formats: Set<CodeFormat>) {
        store.edit { putStringSet(KEY_FORMATS, formats.mapTo(HashSet()) { it.name }) }
    }

    /** Si se acepta pagar mas tiempo por fotograma a cambio de mas aciertos. */
    var tryHarder: Boolean
        get() = store.getBoolean(KEY_TRY_HARDER, false)
        set(value) = store.edit { putBoolean(KEY_TRY_HARDER, value) }

    /** Codigos claros sobre fondo oscuro. */
    var tryInvert: Boolean
        get() = store.getBoolean(KEY_TRY_INVERT, true)
        set(value) = store.edit { putBoolean(KEY_TRY_INVERT, value) }

    /** Aviso sonoro y vibracion al leer. */
    var beepOnRead: Boolean
        get() = store.getBoolean(KEY_BEEP, false)
        set(value) = store.edit { putBoolean(KEY_BEEP, value) }

    var vibrateOnRead: Boolean
        get() = store.getBoolean(KEY_VIBRATE, true)
        set(value) = store.edit { putBoolean(KEY_VIBRATE, value) }

    /** Guardar lo leido en el historial. */
    var saveHistory: Boolean
        get() = store.getBoolean(KEY_SAVE_HISTORY, true)
        set(value) = store.edit { putBoolean(KEY_SAVE_HISTORY, value) }

    /** Guardar tambien lo que contiene claves. */
    var saveSensitive: Boolean
        get() = store.getBoolean(KEY_SAVE_SENSITIVE, false)
        set(value) = store.edit { putBoolean(KEY_SAVE_SENSITIVE, value) }

    /** Dias que se conserva el historial. */
    var retentionDays: Int
        get() = store.getInt(KEY_RETENTION, 0)
        set(value) = store.edit { putInt(KEY_RETENTION, value) }

    fun scanOptions(): ScanOptions = ScanOptions.LIVE.copy(
        formats = formats(),
        tryHarder = tryHarder,
        tryInvert = tryInvert,
    )

    private companion object {
        const val NAME = "signifer"
        const val KEY_FORMATS = "formats"
        const val KEY_TRY_HARDER = "try_harder"
        const val KEY_TRY_INVERT = "try_invert"
        const val KEY_BEEP = "beep"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_SAVE_HISTORY = "save_history"
        const val KEY_SAVE_SENSITIVE = "save_sensitive"
        const val KEY_RETENTION = "retention_days"
    }
}
