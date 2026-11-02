package com.example.netije

import com.example.netije.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class InputRulesTest {
    @Test fun formattedMoneyKeepsItsValue() {
        for (input in listOf("1234.56", "1,234.56", "1.234,56", "1 234,56", "1\u00a0234.56")) {
            assertEquals(input, 1234.56, parseMoney(input)!!, 0.0)
        }
        assertNull(parseMoney("1,234"))
        assertEquals(0.25, parseMoney(",25")!!, 0.0)
    }
    @Test fun malformedAmountsAreRejectedInsteadOfTruncated() {
        for (input in listOf("0", "-10", "1,23.45", "12.3456", "1..2", "100abc", "NaN", "1e9", "1000000000001")) {
            assertNull(input, parseMoney(input))
        }
    }
    @Test fun dateRangeIncludesEntireEndDayAcrossDaylightSaving() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val day = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 1, 12, 0) }.timeInMillis
            val start = startOfDayMillis(day)
            val end = endOfDayMillis(day)
            assertEquals(25 * 60 * 60 * 1000L, end - start + 1)
            val time = Calendar.getInstance().apply { timeInMillis = end }
            assertEquals(23, time.get(Calendar.HOUR_OF_DAY))
            assertEquals(999, time.get(Calendar.MILLISECOND))
        } finally { TimeZone.setDefault(previous) }
    }
}
