package com.gazna

import com.gazna.presentation.util.formatCalendarAmount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CalendarAmountsTest {
    @Test fun wholeAmountsHaveNoDecimalSuffixOrScientificNotation() {
        assertEquals("600", formatCalendarAmount(600.0, Locale.US))
        assertEquals("0", formatCalendarAmount(0.0, Locale.US))
        assertEquals("1000000000000", formatCalendarAmount(1_000_000_000_000.0, Locale.US))
    }

    @Test fun fractionalAmountsKeepTheirCentsAndLocalDecimalSeparator() {
        val russian = Locale.forLanguageTag("ru")
        assertEquals("600,5", formatCalendarAmount(600.50, russian))
        assertEquals("600,01", formatCalendarAmount(600.01, russian))
        assertEquals("600.5", formatCalendarAmount(600.50, Locale.US))
        assertEquals("0.01", formatCalendarAmount(0.01, Locale.US))
    }

    @Test fun withdrawalsStayNegativeWithoutDisplayingFloatingPointNegativeZero() {
        assertEquals("-600", formatCalendarAmount(-600.0, Locale.US))
        assertEquals("-600.5", formatCalendarAmount(-600.50, Locale.US))
        assertEquals("0", formatCalendarAmount(-0.00000001, Locale.US))
    }
}
