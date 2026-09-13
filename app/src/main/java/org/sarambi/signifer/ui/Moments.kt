package org.sarambi.signifer.ui

import org.sarambi.signifer.content.Moment
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Del instante del dominio al reloj de Android. */
internal object Moments {
    /** El instante absoluto, para el calendario del sistema. */
    fun epochMillis(moment: Moment): Long {
        val calendar = Calendar.getInstance(zoneOf(moment))
        calendar.clear()
        calendar.set(moment.year, moment.month - 1, moment.day, moment.hour, moment.minute)
        return calendar.timeInMillis
    }

    /** Fecha y hora para leer, en la hora del telefono. */
    fun describe(moment: Moment, allDay: Boolean): String {
        if (allDay) {
            return String.format(Locale.ROOT, "%04d-%02d-%02d", moment.year, moment.month, moment.day)
        }
        val local = Calendar.getInstance()
        local.timeInMillis = epochMillis(moment)
        return String.format(
            Locale.ROOT,
            "%04d-%02d-%02d %02d:%02d",
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH) + 1,
            local.get(Calendar.DAY_OF_MONTH),
            local.get(Calendar.HOUR_OF_DAY),
            local.get(Calendar.MINUTE),
        )
    }

    /** La zona en la que estan escritos los numeros. */
    private fun zoneOf(moment: Moment): TimeZone = when {
        moment.utc -> TimeZone.getTimeZone("UTC")
        moment.zone.isNotEmpty() && moment.zone in knownZones -> TimeZone.getTimeZone(moment.zone)
        else -> TimeZone.getDefault()
    }

    private val knownZones: Set<String> by lazy { TimeZone.getAvailableIDs().toHashSet() }
}
