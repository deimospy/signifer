package org.sarambi.signifer.content

/** Un instante de calendario, sin zona horaria y sin dependencias. */
data class Moment(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int = 0,
    val minute: Int = 0,
) : Comparable<Moment> {
    /** Si los campos forman una fecha que existe en el calendario. */
    val isValid: Boolean
        get() = year in 1..9999 &&
            month in 1..12 &&
            day in 1..daysInMonth(year, month) &&
            hour in 0..23 &&
            minute in 0..59

    override fun compareTo(other: Moment): Int = sortKey().compareTo(other.sortKey())

    private fun sortKey(): Long =
        ((((year.toLong() * 12 + month) * 31 + day) * 24 + hour) * 60 + minute)

    /** `20260907T143000` — hora local, como la escribio la persona. */
    fun toCalendarStamp(): String =
        "${pad(year, 4)}${pad(month, 2)}${pad(day, 2)}T${pad(hour, 2)}${pad(minute, 2)}00"

    /** `20260907` — para eventos de dia entero, que no llevan hora. */
    fun toDateStamp(): String = "${pad(year, 4)}${pad(month, 2)}${pad(day, 2)}"

    companion object {
        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }

        fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        /** Lee `20260907T143000`, `20260907T143000Z` o `20260907`. */
        fun parse(value: String): Moment? {
            val text = value.trim().removeSuffix("Z")
            val date = text.substringBefore('T')
            if (date.length != 8 || !date.all { it.isDigit() }) return null

            val time = if ('T' in text) text.substringAfter('T') else ""
            if (time.isNotEmpty() && (time.length < 4 || !time.all { it.isDigit() })) return null

            val moment = Moment(
                year = date.substring(0, 4).toInt(),
                month = date.substring(4, 6).toInt(),
                day = date.substring(6, 8).toInt(),
                hour = if (time.length >= 2) time.substring(0, 2).toInt() else 0,
                minute = if (time.length >= 4) time.substring(2, 4).toInt() else 0,
            )
            return if (moment.isValid) moment else null
        }

        private fun pad(value: Int, width: Int): String {
            val text = value.toString()
            if (text.length >= width) return text
            return "0".repeat(width - text.length) + text
        }
    }
}
