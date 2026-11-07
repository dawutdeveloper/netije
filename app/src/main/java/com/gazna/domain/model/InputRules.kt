package com.gazna.domain.model

import java.math.BigDecimal
import java.util.Calendar

/** Accept decimal comma/dot and explicit groups, without silently deleting typed characters. */
fun parseMoney(input: String): Double? {
    val text = input.trim().replace('\u00a0', ' ').replace('\u202f', ' ')
    val decimal = when {
        text.contains(',') && text.contains('.') -> if (text.lastIndexOf(',') > text.lastIndexOf('.')) ',' else '.'
        text.contains(',') -> ','
        else -> '.'
    }
    val other = if (decimal == ',') '.' else ','
    val groupedOnly = Regex("[0-9]{1,3}(?: [0-9]{3})+").matches(text) ||
        Regex("[0-9]{1,3}(?:,[0-9]{3}){2,}").matches(text) ||
        Regex("[0-9]{1,3}(?:\\.[0-9]{3}){2,}").matches(text)
    val normalized = if (groupedOnly) text.replace(Regex("[., ]"), "") else {
        val pieces = text.split(decimal)
        if (pieces.size > 2 || (pieces.size == 2 && !Regex("[0-9]{1,2}").matches(pieces[1]))) return null
        val whole = pieces[0].ifEmpty { "0" }
        if (!Regex("[0-9]+").matches(whole) &&
            !Regex("[0-9]{1,3}(?:${Regex.escape(other.toString())}[0-9]{3})+").matches(whole) &&
            !Regex("[0-9]{1,3}(?: [0-9]{3})+").matches(whole)) return null
        whole.replace(other.toString(), "").replace(" ", "") + if (pieces.size == 2) ".${pieces[1]}" else ""
    }
    val amount = normalized.toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO || amount > BigDecimal("1000000000000")) return null
    return amount.toDouble().takeIf { it.isFinite() }
}

fun startOfDayMillis(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = value
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDayMillis(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDayMillis(value)
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis - 1
