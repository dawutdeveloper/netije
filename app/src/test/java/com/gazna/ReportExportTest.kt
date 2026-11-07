package com.gazna

import com.gazna.domain.model.FinanceSummary
import com.gazna.domain.model.Person
import com.gazna.domain.model.ReportSummaryRow
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.TransactionType
import com.gazna.presentation.util.LocalReportGenerator
import com.gazna.presentation.util.XlsxWriter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ReportExportTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun parts(bytes: ByteArray): Map<String, Document> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    .newDocumentBuilder().parse(ByteArrayInputStream(zip.readBytes())))
            }
        }
    }

    @Test fun summaryContainsEveryTransactionWithPurposeAndNote() {
        val person = Person("person", "Annaberdi", phones = listOf("+993 64 150033"))
        val initial = Transaction("initial", person.id, TransactionType.RECEIVED, 6000.0,
            purpose = "Назначение <товар> & доставка", note = "Примечание\nВторая строка", date = 1, time = 1)
        val payment = Transaction("payment", person.id, TransactionType.ISSUED, 500.0,
            purpose = "Payment", note = "Partial payment", parentTransactionId = initial.id, date = 2, time = 2)
        val file = temporary.newFile("summary.xlsx")
        assertTrue(LocalReportGenerator.writeSummaryXlsx(file,
            listOf(ReportSummaryRow(person, 2, 6000.0, 500.0, 5500.0, listOf(initial, payment))),
            FinanceSummary(6000.0, 500.0), "TMT", 0, 3))
        val xml = parts(file.readBytes())
        val sheets = xml.getValue("xl/workbook.xml").getElementsByTagNameNS("*", "sheet")
        assertEquals(2, sheets.length)
        assertEquals("Transactions", (sheets.item(0) as org.w3c.dom.Element).getAttribute("name"))
        val strings = xml.getValue("xl/sharedStrings.xml").getElementsByTagNameNS("*", "t")
        val shared = (0 until strings.length).map { strings.item(it).textContent }
        assertTrue(shared.contains(initial.purpose))
        assertTrue(shared.contains(initial.note))
        assertTrue(shared.any { it.contains("Original transaction: initial") && it.contains("Partial payment") })
        val cells = xml.getValue("xl/worksheets/sheet1.xml").getElementsByTagNameNS("*", "c")
        val values = (0 until cells.length).associate { index ->
            val cell = cells.item(index) as org.w3c.dom.Element
            cell.getAttribute("r") to if (cell.getAttribute("t") == "s") shared[cell.textContent.toInt()] else cell.textContent
        }
        assertEquals("initial", values["A2"])
        assertEquals("6000.0", values["E2"])
        assertEquals(initial.purpose, values["I2"])
        assertEquals(initial.note, values["J2"])
        assertEquals("payment", values["A3"])
        assertEquals("500.0", values["E3"])
        assertEquals("5500.0", values["E4"])
        // Retain a synthetic fixture for independent spreadsheet-reader verification.
        File("build/outputs/export-verification/summary.xlsx").apply {
            parentFile.mkdirs()
            writeBytes(file.readBytes())
        }
    }

    @Test fun workbookPartsHaveValidOrderingReferencesAndEscapedNames() {
        val writer = XlsxWriter()
        writer.newSheet("First & \"quoted\"")
        writer.addRow(listOf(XlsxWriter.Cell.text("Repeat"), XlsxWriter.Cell.text("Repeat")))
        writer.newSheet("Empty")
        val output = ByteArrayOutputStream()
        writer.writeTo(output)
        val xml = parts(output.toByteArray())
        for (name in listOf("sheet1.xml", "sheet2.xml")) {
            val sheet = xml.getValue("xl/worksheets/$name")
            val children = sheet.documentElement.childNodes
            val order = (0 until children.length).map { children.item(it).localName }.filterNotNull()
            assertEquals(listOf("dimension", "cols", "sheetData"), order)
        }
        val strings = xml.getValue("xl/sharedStrings.xml").documentElement
        assertEquals("2", strings.getAttribute("count"))
        assertEquals("1", strings.getAttribute("uniqueCount"))
        assertEquals("First & \"quoted\"", (xml.getValue("xl/workbook.xml")
            .getElementsByTagNameNS("*", "sheet").item(0) as org.w3c.dom.Element).getAttribute("name"))
        val relationships = xml.getValue("xl/_rels/workbook.xml.rels").getElementsByTagNameNS("*", "Relationship")
        for (i in 0 until relationships.length) {
            val target = (relationships.item(i) as org.w3c.dom.Element).getAttribute("Target")
            assertTrue("Missing relationship target $target", xml.containsKey("xl/$target"))
        }
    }
}
