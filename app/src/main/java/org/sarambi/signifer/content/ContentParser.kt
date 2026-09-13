package org.sarambi.signifer.content

/** Del texto leido al tipo de contenido. */
fun parseContent(raw: String): CodeContent {
    val value = raw.trim().trimStart('\uFEFF').trim()
    if (value.isEmpty()) return PlainText(raw)

    val upper = value.uppercase()
    return when {
        upper.startsWith("WIFI:") -> parseWifi(value) ?: PlainText(raw)
        upper.startsWith("BEGIN:VCARD") -> parseVCard(value) ?: PlainText(raw)
        upper.startsWith("MECARD:") -> parseMecard(value) ?: PlainText(raw)
        upper.startsWith("BEGIN:VCALENDAR") || upper.startsWith("BEGIN:VEVENT") ->
            parseEvent(value) ?: PlainText(raw)
        upper.startsWith("MAILTO:") -> parseMailto(value) ?: PlainText(raw)
        upper.startsWith("MATMSG:") -> parseMatmsg(value) ?: PlainText(raw)
        upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> parseSms(value) ?: PlainText(raw)
        upper.startsWith("TEL:") -> PhoneNumber(value.substring(4).trim())
        upper.startsWith("GEO:") -> parseGeo(value) ?: PlainText(raw)
        upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> Website(value)
        else -> PlainText(raw)
    }
}

private fun parseWifi(value: String): CodeContent? {
    val body = value.substring("WIFI:".length)
    var ssid: String? = null
    var password = ""
    var security = WifiSecurity.WPA
    var hidden = false

    for (field in splitUnescaped(body, ';')) {
        if (field.length < 2 || field[1] != ':') continue
        val content = field.substring(2)
        when (field[0].uppercaseChar()) {
            'S' -> ssid = unescapeWifi(content)
            'P' -> password = unescapeWifi(content)
            'H' -> hidden = unescapeWifi(content).equals("true", ignoreCase = true)
            'T' -> security = when (unescapeWifi(content).uppercase()) {
                "WEP" -> WifiSecurity.WEP
                "SAE" -> WifiSecurity.SAE
                "", "NOPASS" -> WifiSecurity.NONE
                else -> WifiSecurity.WPA
            }
            else -> Unit
        }
    }

    val name = ssid ?: return null
    return WifiNetwork(
        ssid = name,
        password = if (security == WifiSecurity.NONE) "" else password,
        security = security,
        hidden = hidden,
    )
}

private fun parseVCard(value: String): CodeContent? {
    var firstName = ""
    var lastName = ""
    var fullName = ""
    var organization = ""
    var title = ""
    var phone = ""
    var mobile = ""
    var email = ""
    var url = ""
    var address = ""
    var note = ""

    for (line in unfold(value)) {
        val separator = line.indexOf(':')
        if (separator <= 0) continue
        val head = line.substring(0, separator)
        val body = line.substring(separator + 1)
        val property = head.substringBefore(';').uppercase()
        val parameters = head.substringAfter(';', "").uppercase()

        when (property) {
            "N" -> {
                val parts = splitUnescaped(body, ';')
                lastName = unescapeVCard(parts.getOrElse(0) { "" })
                firstName = unescapeVCard(parts.getOrElse(1) { "" })
            }
            "FN" -> fullName = unescapeVCard(body)
            "ORG" -> organization = unescapeVCard(splitUnescaped(body, ';').first())
            "TITLE" -> title = unescapeVCard(body)
            "TEL" -> if ("CELL" in parameters || "MOBILE" in parameters) {
                if (mobile.isEmpty()) mobile = unescapeVCard(body)
            } else {
                if (phone.isEmpty()) phone = unescapeVCard(body)
            }
            "EMAIL" -> if (email.isEmpty()) email = unescapeVCard(body)
            "URL" -> if (url.isEmpty()) url = unescapeVCard(body)
            "ADR" -> if (address.isEmpty()) {
                address = splitUnescaped(body, ';')
                    .map { unescapeVCard(it) }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            }
            "NOTE" -> note = unescapeVCard(body)
            else -> Unit
        }
    }

    if (firstName.isEmpty() && lastName.isEmpty() && fullName.isNotEmpty()) {
        firstName = fullName.substringBefore(' ')
        lastName = fullName.substringAfter(' ', "")
    }

    val empty = listOf(firstName, lastName, organization, phone, mobile, email, url).all {
        it.isBlank()
    }
    if (empty) return null

    return Contact(
        firstName = firstName,
        lastName = lastName,
        organization = organization,
        title = title,
        phone = phone,
        mobile = mobile,
        email = email,
        url = url,
        address = address,
        note = note,
    )
}

/** MECARD, el formato de las camaras japonesas. */
private fun parseMecard(value: String): CodeContent? {
    val body = value.substring("MECARD:".length)
    var firstName = ""
    var lastName = ""
    var organization = ""
    var phone = ""
    var email = ""
    var url = ""
    var address = ""
    var note = ""

    for (field in splitUnescaped(body, ';')) {
        val separator = field.indexOf(':')
        if (separator <= 0) continue
        val content = unescapeBackslash(field.substring(separator + 1))
        when (field.substring(0, separator).uppercase()) {
            "N" -> {
                val parts = splitUnescaped(field.substring(separator + 1), ',')
                lastName = unescapeBackslash(parts.getOrElse(0) { "" })
                firstName = unescapeBackslash(parts.getOrElse(1) { "" })
            }
            "ORG" -> organization = content
            "TEL" -> if (phone.isEmpty()) phone = content
            "EMAIL" -> if (email.isEmpty()) email = content
            "URL" -> if (url.isEmpty()) url = content
            "ADR" -> address = content
            "NOTE" -> note = content
            else -> Unit
        }
    }

    if (listOf(firstName, lastName, organization, phone, email).all { it.isBlank() }) return null

    return Contact(
        firstName = firstName,
        lastName = lastName,
        organization = organization,
        phone = phone,
        email = email,
        url = url,
        address = address,
        note = note,
    )
}

private fun parseEvent(value: String): CodeContent? {
    var summary = ""
    var location = ""
    var description = ""
    var start: Moment? = null
    var end: Moment? = null
    var allDay = false

    for (line in unfold(value)) {
        val separator = line.indexOf(':')
        if (separator <= 0) continue
        val head = line.substring(0, separator).uppercase()
        val body = line.substring(separator + 1)
        val property = head.substringBefore(';')
        when {
            property == "SUMMARY" -> summary = unescapeVCard(body)
            property == "LOCATION" -> location = unescapeVCard(body)
            property == "DESCRIPTION" -> description = unescapeVCard(body)
            head.startsWith("DTSTART") -> {
                start = Moment.parse(body)
                if ("VALUE=DATE" in head && "DATE-TIME" !in head) allDay = true
            }
            head.startsWith("DTEND") -> end = Moment.parse(body)
            else -> Unit
        }
    }

    val begins = start ?: return null
    if (summary.isEmpty() && location.isEmpty()) return null
    return CalendarEvent(
        summary = summary,
        location = location,
        description = description,
        start = begins,
        end = end,
        allDay = allDay,
    )
}

private fun parseMailto(value: String): CodeContent? {
    val body = value.substring("mailto:".length)
    val address = percentDecode(body.substringBefore('?')).trim()
    if (address.isEmpty()) return null

    var subject = ""
    var text = ""
    val query = body.substringAfter('?', "")
    if (query.isNotEmpty()) {
        for (pair in query.split('&')) {
            val name = pair.substringBefore('=').lowercase()
            val content = percentDecode(pair.substringAfter('=', ""))
            when (name) {
                "subject" -> subject = content
                "body" -> text = content
                else -> Unit
            }
        }
    }
    return EmailMessage(address, subject, text)
}

/** `MATMSG:TO:...;SUB:...;BODY:...;;`, el formato de correo de los codigos antiguos. */
private fun parseMatmsg(value: String): CodeContent? {
    val body = value.substring("MATMSG:".length)
    var address = ""
    var subject = ""
    var text = ""
    for (field in splitUnescaped(body, ';')) {
        val separator = field.indexOf(':')
        if (separator <= 0) continue
        val content = unescapeBackslash(field.substring(separator + 1))
        when (field.substring(0, separator).uppercase()) {
            "TO" -> address = content
            "SUB" -> subject = content
            "BODY" -> text = content
            else -> Unit
        }
    }
    if (address.isBlank()) return null
    return EmailMessage(address, subject, text)
}

private fun parseSms(value: String): CodeContent? {
    val body = value.substringAfter(':')
    if (body.isEmpty()) return null

    val number: String
    val message: String
    if ('?' in body && body.indexOf('?') < body.indexOf(':').let { if (it < 0) body.length else it }) {
        number = body.substringBefore('?')
        val query = body.substringAfter('?')
        message = query.split('&')
            .firstOrNull { it.substringBefore('=').equals("body", ignoreCase = true) }
            ?.let { percentDecode(it.substringAfter('=', "")) }
            .orEmpty()
    } else {
        number = body.substringBefore(':')
        message = body.substringAfter(':', "")
    }

    if (number.isBlank()) return null
    return SmsMessage(number.trim(), message)
}

private fun parseGeo(value: String): CodeContent? {
    val body = value.substring("geo:".length)
    val coordinates = body.substringBefore('?')
    val parts = coordinates.split(',')
    if (parts.size < 2) return null

    val latitude = parts[0].trim().toDoubleOrNull() ?: return null
    val longitude = parts[1].trim().substringBefore(';').toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    val q = body.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.startsWith("q=", ignoreCase = true) }
        ?.substring(2)
        .orEmpty()
    return if ('(' in q && q.endsWith(')')) {
        GeoPoint(latitude, longitude, label = percentDecode(q.substringAfter('(').dropLast(1)))
    } else {
        GeoPoint(latitude, longitude, query = percentDecode(q.replace('+', ' ')))
    }
}

/** Reune las lineas plegadas de vCard e iCalendar. */
private fun unfold(value: String): List<String> {
    val lines = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val result = mutableListOf<String>()
    for (line in lines) {
        if (line.isEmpty()) continue
        if ((line[0] == ' ' || line[0] == '\t') && result.isNotEmpty()) {
            result[result.size - 1] = result.last() + line.substring(1)
        } else {
            result.add(line)
        }
    }
    return result
}
