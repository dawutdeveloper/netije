package com.gazna.presentation.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Compact, localized amounts for narrow day cells, without changing stored precision. */
internal fun formatCalendarAmount(amount: Double, locale: Locale): String =
    DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).apply {
        isGroupingUsed = false
    }.format(if (kotlin.math.abs(amount) < 0.005) 0.0 else amount)
