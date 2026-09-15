package org.sarambi.signifer.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Los idiomas de la aplicacion, cada uno escrito en su propia lengua. */
object AppLanguage {
    val ALL = listOf(
        "de" to "Deutsch",
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "pt" to "Português",
        "fi" to "Suomi",
        "tr" to "Türkçe",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
    )

    /** La etiqueta elegida a mano, o null si sigue al telefono. */
    fun current(): String? {
        val chosen = AppCompatDelegate.getApplicationLocales()[0] ?: return null
        return ALL.firstOrNull { (tag, _) ->
            val option = Locale.forLanguageTag(tag)
            option.language == chosen.language && (option.country.isEmpty() || option.country == chosen.country)
        }?.first
    }

    /** Null vuelve al idioma del telefono; AppCompat recuerda la eleccion y redibuja la pantalla. */
    fun choose(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
