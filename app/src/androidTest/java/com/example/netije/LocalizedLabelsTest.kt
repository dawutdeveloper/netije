package com.example.netije

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizedLabelsTest {
    private fun resources(language: String): android.content.res.Resources {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale(language))
        return context.createConfigurationContext(configuration).resources
    }

    @Test fun statusLabelsExistInEverySupportedLanguage() {
        val labels = mapOf(
            "en" to listOf("Active", "Archive", "Settled"),
            "ru" to listOf("Активный", "Архив", "Погашен"),
            "tk" to listOf("Işjeň", "Arhiwlenen", "Hasaplaşylan"),
            "tr" to listOf("Aktif", "Arşiv", "Hesaplaşıldı")
        )
        labels.forEach { (language, expected) ->
            val resources = resources(language)
            assertEquals(expected, listOf(R.string.filter_active, R.string.filter_archived, R.string.filter_settled).map {
                resources.getString(it)
            })
        }
    }

    @Test fun turkmenReportDatesUseShortDateThenSuffix() {
        val resources = resources("tk")
        assertEquals("01.11.2026 seneden", resources.getString(R.string.report_date_from, "01.11.2026"))
        assertEquals("02.11.2026 çenli", resources.getString(R.string.report_date_to, "02.11.2026"))
    }
}
