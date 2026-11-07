package com.gazna

import android.content.res.Configuration
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gazna.data.local.datastore.SettingsDataStore
import com.gazna.domain.model.AppLanguage
import com.gazna.domain.model.Person
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.TransactionType
import com.gazna.presentation.util.LocalReportGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class TurkishLocalizationTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val turkishContext get() = app.createConfigurationContext(
        Configuration(app.resources.configuration).apply { setLocale(Locale("tr")) }
    )

    @Test fun turkishSelectionPersistsForNextLaunch() = runBlocking {
        val store = SettingsDataStore(app)
        val original = store.settings.first().language
        try {
            store.setLanguage(AppLanguage.TR)
            assertEquals(AppLanguage.TR, store.settings.first().language)
            assertEquals(AppLanguage.TR, SettingsDataStore(app).readLanguageSync())
            assertEquals(AppLanguage.TR, AppLanguage.fromCode("tr"))
            assertEquals("Türkçe", AppLanguage.TR.displayName)
        } finally {
            store.setLanguage(original)
        }
    }

    @Test fun turkishLabelsFormatsAndCalendarUseSelectedLocale() {
        val resources = turkishContext.resources
        assertEquals("Ayarlar", resources.getString(R.string.settings_title))
        assertEquals("Dil", resources.getString(R.string.settings_language))
        assertEquals("Başlangıç: 05.11.2026", resources.getString(R.string.report_date_from, "05.11.2026"))
        assertEquals("Eklenen: 3", resources.getString(R.string.attach_count, 3))
        val date = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 5) }.time
        assertEquals("Kasım 2026", SimpleDateFormat("MMMM yyyy", resources.configuration.locales[0]).format(date))
    }

    @Test fun turkishReportsTranslateLabelsAndPreserveUserText() {
        val context = turkishContext
        val person = Person("tr-person", "Işıl Öztürk")
        val transaction = Transaction("tr-transaction", person.id, TransactionType.RECEIVED, 125.50,
            date = 1793836800000, time = 1793836800000,
            purpose = "Borç ödemesi", note = "Çeşme, İstanbul, ığüşöçİ")
        val directory = File(app.cacheDir, "turkish-localization-test").apply { mkdirs() }
        val csv = File(directory, "transaction.csv")
        val xlsx = File(directory, "transaction.xlsx")
        val pdf = File(directory, "transaction.pdf")
        try {
            assertTrue(LocalReportGenerator.writeTransactionCsv(csv, transaction, person, context))
            val csvText = csv.readText()
            listOf("Kimlik", "Kişi", "Tutar", "Alınan", person.name, transaction.note!!).forEach {
                assertTrue("CSV must contain $it", csvText.contains(it))
            }
            assertTrue(LocalReportGenerator.writeTransactionXlsx(xlsx, transaction, person, context))
            ZipFile(xlsx).use { zip ->
                val xml = zip.entries().asSequence().filter { it.name.endsWith(".xml") }
                    .joinToString("\n") { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }
                listOf("İşlem ayrıntıları", "Kişi", "Tutar", "Alınan", person.name, transaction.note!!).forEach {
                    assertTrue("Spreadsheet must contain $it", xml.contains(it))
                }
            }
            assertTrue(LocalReportGenerator.writeTransactionPdf(pdf, transaction, person, context))
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertTrue(it.pageCount > 0) }
            }
        } finally {
            listOf(csv, xlsx, pdf).forEach { it.delete() }
            directory.delete()
        }
    }
}
