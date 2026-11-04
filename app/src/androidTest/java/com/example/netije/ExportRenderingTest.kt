package com.example.netije

import android.content.res.Configuration
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.ReportSummaryRow
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.util.LocalReportGenerator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ExportRenderingTest {
    @Test fun reportsRenderAndLongNotesContinueAcrossPages() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale("ru")) })
        val output = File(app.filesDir, "export-verification").apply { mkdirs() }
        val person = Person("test-person", "Тестовый пользователь", phones = listOf("+993 00 000000"))
        val original = Transaction("original", person.id, TransactionType.RECEIVED, 6000.0,
            purpose = "Покупка товара и доставка — PURPOSE_MARKER",
            note = "Примечание к покупке — NOTE_MARKER", date = 1793664000000, time = 1793664000000)
        val payment = Transaction("payment", person.id, TransactionType.ISSUED, 500.0,
            purpose = "Частичная оплата", note = "Начало длинного примечания. " +
                "Текст должен полностью переноситься на следующую страницу. ".repeat(160) + "END_OF_LONG_NOTE",
            parentTransactionId = original.id, date = 1793750400000, time = 1793750400000)
        val entries = listOf(original, payment)
        val rows = listOf(ReportSummaryRow(person, 2, 6000.0, 500.0, 5500.0, entries))
        val summary = File(output, "summary.pdf")
        assertTrue(LocalReportGenerator.writeSummaryPdf(summary, rows, FinanceSummary(6000.0, 500.0),
            "TMT", original.date, payment.date, context))
        ParcelFileDescriptor.open(summary, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue("Long notes must continue beyond the first page", renderer.pageCount > 2)
                for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                    assertTrue(page.width > 0 && page.height > 0)
                }
            }
        }
        assertTrue(LocalReportGenerator.writeSummaryXlsx(File(output, "summary.xlsx"), rows,
            FinanceSummary(6000.0, 500.0), "TMT", original.date, payment.date))
        assertTrue(LocalReportGenerator.writeTransactionPdf(File(output, "transaction.pdf"), payment, person, context))
        assertTrue(LocalReportGenerator.writePersonReportPdf(File(output, "person.pdf"),
            com.example.netije.domain.model.PersonWithTransactions(person, entries), entries,
            "TMT", original.date, payment.date, context))
        assertTrue(LocalReportGenerator.writeTransactionsPdf(File(output, "transactions.pdf"), entries,
            mapOf(person.id to person), "TMT", 6000.0, 500.0, original.date, payment.date, context))
    }
}
