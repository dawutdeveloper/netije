package com.example.netije.presentation.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.ReportSummaryRow
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val currencyFmt = DecimalFormat("#,##0.00")
private val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

private fun dateOnlyFormat(locale: Locale) = SimpleDateFormat("yyyy-MM-dd", locale)
private fun dateTimeFormat(locale: Locale) = SimpleDateFormat("yyyy-MM-dd HH:mm", locale)

private fun String.csvEscape(): String =
    "\"${replace("\"", "\"\"").replace("\r", "").replace("\n", "\\n")}\""

private fun personLabel(p: Person?): String = buildString {
    append(p?.name.orEmpty())
    val phone = p?.phones?.firstOrNull()
    if (!phone.isNullOrBlank()) append(" (").append(phone).append(")")
}

private fun safeFileName(s: String): String =
    s.filter { it.isLetterOrDigit() || it in "-_." }.ifBlank { "netije" }

sealed interface ReportExportResult {
    data class Success(val file: File) : ReportExportResult
    data class Failure(val error: Throwable) : ReportExportResult
}

object LocalReportGenerator {

    // ─────────────────────────────────────────────────────────────────────
    //  1. SINGLE TRANSACTION (detail screen)
    // ─────────────────────────────────────────────────────────────────────

    fun writeTransactionCsv(
        file: File,
        tx: Transaction,
        person: Person?
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                val header = listOf(
                    "id", "person", "person_phone", "type", "amount", "currency",
                    "date", "time", "purpose", "note", "attachments_count", "attachments"
                )
                w.write(header.joinToString(",") { it.csvEscape() })
                w.newLine()
                val row = listOf(
                    tx.id,
                    person?.name.orEmpty(),
                    person?.phones?.firstOrNull().orEmpty(),
                    tx.type.name,
                    currencyFmt.format(tx.amount),
                    tx.currency,
                    dfD.format(Date(tx.date)),
                    dfT.format(Date(tx.time)),
                    tx.purpose.orEmpty(),
                    tx.note.orEmpty(),
                    tx.attachments.size.toString(),
                    tx.attachments.joinToString("; ")
                )
                w.write(row.joinToString(",") { it.csvEscape() })
                w.newLine()
            }
        }
        true
    }.getOrElse { false }

    fun writeTransactionXlsx(
        file: File,
        tx: Transaction,
        person: Person?
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        val writer = XlsxWriter()
        writer.newSheet("Transaction")
        val labels = listOf("ID", "Person", "Phone", "Type", "Amount", "Currency", "Date", "Time", "Purpose", "Note", "Attachments")
        writer.addHeaderRow(labels)
        val amtCell = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
        writer.addRow(
            listOf(
                XlsxWriter.Cell.text(tx.id),
                XlsxWriter.Cell.text(person?.name.orEmpty()),
                XlsxWriter.Cell.text(person?.phones?.firstOrNull().orEmpty()),
                XlsxWriter.Cell.text(tx.type.name),
                amtCell,
                XlsxWriter.Cell.text(tx.currency),
                XlsxWriter.Cell.text(dfD.format(Date(tx.date))),
                XlsxWriter.Cell.text(dfT.format(Date(tx.time))),
                XlsxWriter.Cell.text(tx.purpose.orEmpty()),
                XlsxWriter.Cell.text(tx.note.orEmpty()),
                XlsxWriter.Cell.text(tx.attachments.joinToString("; "))
            )
        )
        writer.writeTo(file)
        true
    }.getOrElse { it.printStackTrace(); false }

    fun writeTransactionPdf(
        file: File,
        tx: Transaction,
        person: Person?,
        context: Context
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfDT = dateTimeFormat(locale)
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 22f
            color = android.graphics.Color.parseColor("#1F6FEB")
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 12f
            color = android.graphics.Color.parseColor("#374151")
        }
        val valuePaint = Paint().apply {
            typeface = Typeface.SANS_SERIF
            textSize = 12f
            color = android.graphics.Color.parseColor("#111827")
        }
        val smallPaint = Paint().apply {
            typeface = Typeface.SANS_SERIF
            textSize = 10f
            color = android.graphics.Color.parseColor("#6B7280")
        }

        var y = margin
        canvas.drawText("NETIJE — Transaction Receipt", margin, y, titlePaint)
        y += 32
        canvas.drawText("Generated: ${dfDT.format(Date())}", margin, y, smallPaint)
        y += 24

        val summary = mapOf(
            "ID" to tx.id,
            "Person" to (person?.name ?: "—"),
            "Phone" to (person?.phones?.firstOrNull() ?: "—"),
            "Type" to tx.type.name,
            "Amount" to "${currencyFmt.format(tx.amount)} ${tx.currency}",
            "Date" to dfDT.format(Date(tx.date)),
            "Purpose" to tx.purpose.orEmpty().ifBlank { "—" },
            "Note" to tx.note.orEmpty().ifBlank { "—" },
            "Attachments" to tx.attachments.size.toString()
        )
        val col1 = margin
        val col2 = margin + 110
        for ((k, v) in summary) {
            canvas.drawText("$k:", col1, y, headerPaint)
            drawWrappedText(canvas, v, col2, y, pageWidth - margin - col2, valuePaint)
            y += 20
        }
        y += 20
        if (tx.attachments.isNotEmpty()) {
            canvas.drawText("Attachment paths:", margin, y, headerPaint)
            y += 18
            tx.attachments.forEach { a ->
                drawWrappedText(canvas, "• $a", margin + 12f, y, pageWidth - 2 * margin - 12f, smallPaint)
                y += 18
            }
        }

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        true
    }.getOrElse { it.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    //  2. USER / PERSON REPORT
    // ─────────────────────────────────────────────────────────────────────

    fun writePersonReportCsv(
        file: File,
        pwt: PersonWithTransactions,
        transactions: List<Transaction>,
        currency: String,
        fromDate: Long,
        toDate: Long
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                val recv = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
                val iss = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
                val bal = recv - iss
                w.write("# REPORT FOR".csvEscape() + "," + pwt.person.name.csvEscape())
                w.newLine()
                w.write("# PHONE".csvEscape() + "," + (pwt.person.phones.firstOrNull().orEmpty()).csvEscape())
                w.newLine()
                w.write("# RANGE".csvEscape() + "," + (dfD.format(Date(fromDate)) + " → " + dfD.format(Date(toDate))).csvEscape())
                w.newLine()
                w.write("# RECEIVED".csvEscape() + "," + currencyFmt.format(recv).csvEscape())
                w.newLine()
                w.write("# ISSUED".csvEscape() + "," + currencyFmt.format(iss).csvEscape())
                w.newLine()
                w.write("# BALANCE".csvEscape() + "," + currencyFmt.format(bal).csvEscape())
                w.newLine()
                w.newLine()
                val header = listOf("id", "type", "amount", "currency", "date", "time", "purpose", "note")
                w.write(header.joinToString(",") { it.csvEscape() })
                w.newLine()
                transactions.forEach { tx ->
                    val row = listOf(
                        tx.id, tx.type.name,
                        currencyFmt.format(tx.amount), tx.currency,
                        dfD.format(Date(tx.date)), dfT.format(Date(tx.time)),
                        tx.purpose.orEmpty(), tx.note.orEmpty()
                    )
                    w.write(row.joinToString(",") { it.csvEscape() })
                    w.newLine()
                }
            }
        }
        true
    }.getOrElse { false }

    fun writePersonReportXlsx(
        file: File,
        pwt: PersonWithTransactions,
        transactions: List<Transaction>,
        currency: String,
        fromDate: Long,
        toDate: Long
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        val recv = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
        val iss = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
        val bal = recv - iss
        val writer = XlsxWriter()
        writer.newSheet("Person Report")
        writer.addMetaRow("Person", pwt.person.name)
        writer.addMetaRow("Phone", pwt.person.phones.firstOrNull().orEmpty())
        writer.addMetaRow("Currency", currency)
        writer.addMetaRow("Range", "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow("Total Received", currencyFmt.format(recv))
        writer.addMetaRow("Total Issued", currencyFmt.format(iss))
        writer.addMetaRow("Balance", currencyFmt.format(bal))
        writer.addBlankRow()

        writer.addHeaderRow(listOf("ID", "Type", "Amount", "Currency", "Date", "Time", "Purpose", "Note"))
        for (tx in transactions) {
            val amt = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
            writer.addRow(
                listOf(
                    XlsxWriter.Cell.text(tx.id),
                    XlsxWriter.Cell.text(tx.type.name),
                    amt,
                    XlsxWriter.Cell.text(tx.currency),
                    XlsxWriter.Cell.text(dfD.format(Date(tx.date))),
                    XlsxWriter.Cell.text(dfT.format(Date(tx.time))),
                    XlsxWriter.Cell.text(tx.purpose.orEmpty()),
                    XlsxWriter.Cell.text(tx.note.orEmpty())
                )
            )
        }
        if (transactions.isNotEmpty()) {
            writer.addTotalsRow(
                listOf(
                    XlsxWriter.Cell.text("TOTAL (${transactions.size})"),
                    XlsxWriter.Cell(),
                    XlsxWriter.Cell.num(bal),
                    XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell()
                )
            )
        }
        writer.writeTo(file)
        true
    }.getOrElse { it.printStackTrace(); false }

    fun writePersonReportPdf(
        file: File,
        pwt: PersonWithTransactions,
        transactions: List<Transaction>,
        currencyCode: String,
        fromDate: Long,
        toDate: Long,
        context: Context
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfDT = dateTimeFormat(locale)
        val recv = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
        val iss = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
        val bal = recv - iss

        writeMultiPagePdf(file, pageWidth = 842, pageHeight = 595) { canvas, pageIndex, pageWidth, pageHeight, stateRef ->
            val margin = 40f
            val titlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 22f
                color = android.graphics.Color.parseColor("#1F6FEB")
            }
            val headerPaint = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 12f
                color = android.graphics.Color.WHITE
            }
            val valuePaint = Paint().apply {
                typeface = Typeface.SANS_SERIF
                textSize = 11f
                color = android.graphics.Color.parseColor("#111827")
            }
            val metaPaint = Paint().apply {
                typeface = Typeface.SANS_SERIF
                textSize = 12f
                color = android.graphics.Color.parseColor("#374151")
            }
            val boldMeta = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 12f
                color = android.graphics.Color.parseColor("#111827")
            }
            val smallPaint = Paint().apply {
                typeface = Typeface.SANS_SERIF
                textSize = 10f
                color = android.graphics.Color.parseColor("#6B7280")
            }
            val greenPaint = Paint(valuePaint).apply { color = android.graphics.Color.parseColor("#059669") }
            val redPaint = Paint(valuePaint).apply { color = android.graphics.Color.parseColor("#DC2626") }

            var y = margin
            if (pageIndex == 0) {
                canvas.drawText("NETIJE — Personal Report", margin, y, titlePaint)
                y += 30
                canvas.drawText("Generated: ${dfDT.format(Date())}", margin, y, smallPaint)
                y += 22

                canvas.drawText("Person: ", margin, y, boldMeta); canvas.drawText(pwt.person.name, margin + 90, y, metaPaint)
                y += 18
                canvas.drawText("Phone:  ", margin, y, boldMeta); canvas.drawText(pwt.person.phones.firstOrNull().orEmpty(), margin + 90, y, metaPaint)
                y += 18
                canvas.drawText("Range:  ", margin, y, boldMeta)
                canvas.drawText("${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}", margin + 90, y, metaPaint)
                y += 26
                canvas.drawText("Received: ${currencyFmt.format(recv)} $currencyCode", margin, y, greenPaint)
                y += 18
                canvas.drawText("Issued:   ${currencyFmt.format(iss)} $currencyCode", margin, y, redPaint)
                y += 18
                canvas.drawText("Balance:  ${currencyFmt.format(bal)} $currencyCode", margin, y, boldMeta)
                y += 32
            }

            val contentWidth = pageWidth - 2 * margin
            val colDate = 0f
            val colType = contentWidth * 0.12f
            val colAmt = contentWidth * 0.22f
            val colPurpose = contentWidth * 0.35f
            val colNote = contentWidth * 0.66f

            val headerBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#1F6FEB") }
            canvas.drawRect(margin + colDate, y, margin + contentWidth, y + 22, headerBg)
            y += 16
            canvas.drawText("Date", margin + colDate + 6, y, headerPaint)
            canvas.drawText("Type", margin + colType + 6, y, headerPaint)
            canvas.drawText("Amount", margin + colAmt + 6, y, headerPaint)
            canvas.drawText("Purpose", margin + colPurpose + 6, y, headerPaint)
            canvas.drawText("Note", margin + colNote + 6, y, headerPaint)
            y += 12
            val border = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#E5E7EB"); style = Paint.Style.STROKE; strokeWidth = 0.5f }

            var startIdx = stateRef.get() as? Int ?: 0
            var written = 0
            for (i in startIdx until transactions.size) {
                val tx = transactions[i]
                val rowTop = y
                val rowH = 22f
                val rectPaint = if (i % 2 == 0) android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F9FAFB") } else null
                rectPaint?.let { canvas.drawRect(margin, rowTop, margin + contentWidth, rowTop + rowH, it) }
                canvas.drawLine(margin, rowTop, margin + contentWidth, rowTop, border)
                canvas.drawText(dfD.format(Date(tx.date)), margin + colDate + 6, rowTop + 15f, valuePaint)
                canvas.drawText(tx.type.name.take(6), margin + colType + 6, rowTop + 15f, if (tx.type == TransactionType.RECEIVED) greenPaint else redPaint)
                canvas.drawText("${currencyFmt.format(tx.amount)} $currencyCode", margin + colAmt + 6, rowTop + 15f, valuePaint)
                val purposeText = if (tx.purpose.isNullOrBlank()) "—" else tx.purpose
                drawClipped(canvas, purposeText, margin + colPurpose + 6, rowTop + 15f, colNote - colPurpose - 12, valuePaint)
                val noteText = if (tx.note.isNullOrBlank()) "—" else tx.note
                drawClipped(canvas, noteText, margin + colNote + 6, rowTop + 15f, contentWidth - colNote - 12, valuePaint)
                y += rowH
                written++
                startIdx = i + 1
                if (y > pageHeight - margin - 30) break
            }
            stateRef.set(startIdx)

            val more: Int = transactions.size - (stateRef.get() as? Int ?: 0)
            more
        }
        true
    }.getOrElse { it.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    //  3. SUMMARY REPORT (one row per person)
    // ─────────────────────────────────────────────────────────────────────

    fun writeSummaryCsv(
        file: File,
        rows: List<ReportSummaryRow>,
        total: FinanceSummary,
        currencyCode: String,
        fromDate: Long,
        toDate: Long
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                w.write("# RANGE".csvEscape() + "," + (dfD.format(Date(fromDate)) + " → " + dfD.format(Date(toDate))).csvEscape())
                w.newLine()
                w.write("# CURRENCY".csvEscape() + "," + currencyCode.csvEscape())
                w.newLine()
                w.write("# TOTAL_RECEIVED".csvEscape() + "," + currencyFmt.format(total.totalReceived).csvEscape())
                w.newLine()
                w.write("# TOTAL_ISSUED".csvEscape() + "," + currencyFmt.format(total.totalIssued).csvEscape())
                w.newLine()
                w.write("# TOTAL_BALANCE".csvEscape() + "," + currencyFmt.format(total.totalReceived - total.totalIssued).csvEscape())
                w.newLine()
                w.newLine()
                val header = listOf("person", "phone", "tx_count", "received", "issued", "balance")
                w.write(header.joinToString(",") { it.csvEscape() }); w.newLine()
                rows.forEach { r ->
                    val row = listOf(
                        r.person.name,
                        r.person.phones.firstOrNull().orEmpty(),
                        r.count.toString(),
                        currencyFmt.format(r.received),
                        currencyFmt.format(r.issued),
                        currencyFmt.format(r.balance)
                    )
                    w.write(row.joinToString(",") { it.csvEscape() }); w.newLine()
                }
            }
        }
        true
    }.getOrElse { false }

    fun writeSummaryXlsx(
        file: File,
        rows: List<ReportSummaryRow>,
        total: FinanceSummary,
        currencyCode: String,
        fromDate: Long,
        toDate: Long
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val writer = XlsxWriter()
        writer.newSheet("Summary")
        writer.addMetaRow("Currency", currencyCode)
        writer.addMetaRow("Range", "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow("Total Received", currencyFmt.format(total.totalReceived))
        writer.addMetaRow("Total Issued", currencyFmt.format(total.totalIssued))
        writer.addMetaRow("Net Balance", currencyFmt.format(total.totalReceived - total.totalIssued))
        writer.addMetaRow("Persons", rows.size.toString())
        writer.addBlankRow()

        writer.addHeaderRow(listOf("Person", "Phone", "#Tx", "Received", "Issued", "Balance"))
        for (row in rows) {
            writer.addRow(
                listOf(
                    XlsxWriter.Cell.text(row.person.name),
                    XlsxWriter.Cell.text(row.person.phones.firstOrNull().orEmpty()),
                    XlsxWriter.Cell.num(row.count.toDouble()),
                    XlsxWriter.Cell.positiveNum(row.received),
                    XlsxWriter.Cell.negativeNum(row.issued),
                    XlsxWriter.Cell.num(row.balance)
                )
            )
        }
        writer.addTotalsRow(
            listOf(
                XlsxWriter.Cell.text("TOTALS"),
                XlsxWriter.Cell(),
                XlsxWriter.Cell.num(rows.sumOf { it.count.toDouble() }),
                XlsxWriter.Cell.num(total.totalReceived),
                XlsxWriter.Cell.num(total.totalIssued),
                XlsxWriter.Cell.num(total.totalReceived - total.totalIssued)
            )
        )
        writer.writeTo(file)
        true
    }.getOrElse { it.printStackTrace(); false }

    fun writeSummaryPdf(
        file: File,
        rows: List<ReportSummaryRow>,
        total: FinanceSummary,
        currencyCode: String,
        fromDate: Long,
        toDate: Long,
        context: Context
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfDT = dateTimeFormat(locale)
        writeMultiPagePdf(file, pageWidth = 842, pageHeight = 595) { canvas, pageIndex, pageWidth, pageHeight, stateRef ->
            val margin = 40f
            val titlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 22f
                color = android.graphics.Color.parseColor("#1F6FEB")
            }
            val headerPaint = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 12f; color = android.graphics.Color.WHITE
            }
            val valuePaint = Paint().apply { typeface = Typeface.SANS_SERIF; textSize = 11f; color = android.graphics.Color.parseColor("#111827") }
            val boldMeta = Paint().apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 12f }
            val metaPaint = Paint(valuePaint).apply { textSize = 12f }
            val smallPaint = Paint(valuePaint).apply { textSize = 10f; color = android.graphics.Color.parseColor("#6B7280") }
            val greenPaint = Paint(valuePaint).apply { color = android.graphics.Color.parseColor("#059669") }
            val redPaint = Paint(valuePaint).apply { color = android.graphics.Color.parseColor("#DC2626") }

            var y = margin
            if (pageIndex == 0) {
                canvas.drawText("NETIJE — Finance Summary Report", margin, y, titlePaint); y += 30
                canvas.drawText("Generated: ${dfDT.format(Date())}", margin, y, smallPaint); y += 22
                canvas.drawText("Range:     ${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}", margin, y, metaPaint); y += 18
                canvas.drawText("Currency:  $currencyCode", margin, y, metaPaint); y += 18
                canvas.drawText("Persons:   ${rows.size}", margin, y, metaPaint); y += 26
                canvas.drawText("Received:  ${currencyFmt.format(total.totalReceived)} $currencyCode", margin, y, greenPaint); y += 18
                canvas.drawText("Issued:    ${currencyFmt.format(total.totalIssued)} $currencyCode", margin, y, redPaint); y += 18
                canvas.drawText("Balance:   ${currencyFmt.format(total.totalReceived - total.totalIssued)} $currencyCode", margin, y, boldMeta); y += 30
            }

            val contentWidth = pageWidth - 2 * margin
            val colPerson = 0f
            val colPhone = contentWidth * 0.30f
            val colCount = contentWidth * 0.48f
            val colRecv = contentWidth * 0.55f
            val colIssued = contentWidth * 0.72f
            val colBalance = contentWidth * 0.88f

            val headerBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#1F6FEB") }
            canvas.drawRect(margin + colPerson, y, margin + contentWidth, y + 22, headerBg)
            y += 16
            canvas.drawText("Person", margin + colPerson + 6, y, headerPaint)
            canvas.drawText("Phone", margin + colPhone + 6, y, headerPaint)
            canvas.drawText("#Tx", margin + colCount + 6, y, headerPaint)
            canvas.drawText("Received", margin + colRecv + 6, y, headerPaint)
            canvas.drawText("Issued", margin + colIssued + 6, y, headerPaint)
            canvas.drawText("Balance", margin + colBalance + 6, y, headerPaint)
            y += 12

            val border = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#E5E7EB"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
            var startIdx = stateRef.get() as? Int ?: 0
            for (i in startIdx until rows.size) {
                val r = rows[i]
                val rowTop = y
                val rowH = 22f
                if (i % 2 == 0) {
                    val bg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F9FAFB") }
                    canvas.drawRect(margin, rowTop, margin + contentWidth, rowTop + rowH, bg)
                }
                canvas.drawLine(margin, rowTop, margin + contentWidth, rowTop, border)
                canvas.drawText(r.person.name, margin + colPerson + 6, rowTop + 15f, valuePaint)
                canvas.drawText(r.person.phones.firstOrNull().orEmpty(), margin + colPhone + 6, rowTop + 15f, valuePaint)
                canvas.drawText(r.count.toString(), margin + colCount + 6, rowTop + 15f, valuePaint)
                canvas.drawText("${currencyFmt.format(r.received)} $currencyCode", margin + colRecv + 6, rowTop + 15f, greenPaint)
                canvas.drawText("${currencyFmt.format(r.issued)} $currencyCode", margin + colIssued + 6, rowTop + 15f, redPaint)
                val balPaint = when {
                    r.balance > 0.005 -> greenPaint
                    r.balance < -0.005 -> redPaint
                    else -> valuePaint
                }
                canvas.drawText("${currencyFmt.format(r.balance)} $currencyCode", margin + colBalance + 6, rowTop + 15f, balPaint)
                y += rowH
                startIdx = i + 1
                if (y > pageHeight - margin - 30) break
            }
            stateRef.set(startIdx)
            rows.size - startIdx
        }
        true
    }.getOrElse { it.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    //  4. TRANSACTIONS REPORT (flat list)
    // ─────────────────────────────────────────────────────────────────────

    fun writeTransactionsCsv(
        file: File,
        transactions: List<Transaction>,
        personsById: Map<String, Person>,
        currencyCode: String,
        totalReceived: Double,
        totalIssued: Double,
        fromDate: Long,
        toDate: Long
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                val bal = totalReceived - totalIssued
                w.write("# RANGE".csvEscape() + "," + ("${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}").csvEscape())
                w.newLine()
                w.write("# CURRENCY".csvEscape() + "," + currencyCode.csvEscape())
                w.newLine()
                w.write("# TX_COUNT".csvEscape() + "," + transactions.size.toString().csvEscape())
                w.newLine()
                w.write("# RECEIVED".csvEscape() + "," + currencyFmt.format(totalReceived).csvEscape())
                w.newLine()
                w.write("# ISSUED".csvEscape() + "," + currencyFmt.format(totalIssued).csvEscape())
                w.newLine()
                w.write("# BALANCE".csvEscape() + "," + currencyFmt.format(bal).csvEscape())
                w.newLine()
                w.newLine()
                val header = listOf("id", "person", "phone", "type", "amount", "currency", "date", "time", "purpose", "note")
                w.write(header.joinToString(",") { it.csvEscape() }); w.newLine()
                transactions.forEach { tx ->
                    val p = personsById[tx.personId]
                    val row = listOf(
                        tx.id,
                        p?.name.orEmpty(),
                        p?.phones?.firstOrNull().orEmpty(),
                        tx.type.name,
                        currencyFmt.format(tx.amount),
                        tx.currency,
                        dfD.format(Date(tx.date)),
                        dfT.format(Date(tx.time)),
                        tx.purpose.orEmpty(),
                        tx.note.orEmpty()
                    )
                    w.write(row.joinToString(",") { it.csvEscape() }); w.newLine()
                }
            }
        }
        true
    }.getOrElse { false }

    fun writeTransactionsXlsx(
        file: File,
        transactions: List<Transaction>,
        personsById: Map<String, Person>,
        currencyCode: String,
        totalReceived: Double,
        totalIssued: Double,
        fromDate: Long,
        toDate: Long
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        val writer = XlsxWriter()
        writer.newSheet("Transactions")
        writer.addMetaRow("Currency", currencyCode)
        writer.addMetaRow("Range", "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow("Transactions", transactions.size.toString())
        writer.addMetaRow("Total Received", currencyFmt.format(totalReceived))
        writer.addMetaRow("Total Issued", currencyFmt.format(totalIssued))
        writer.addMetaRow("Net Balance", currencyFmt.format(totalReceived - totalIssued))
        writer.addBlankRow()

        writer.addHeaderRow(listOf("ID", "Person", "Phone", "Type", "Amount", "Currency", "Date", "Time", "Purpose", "Note"))
        for (tx in transactions) {
            val p = personsById[tx.personId]
            val amt = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
            writer.addRow(
                listOf(
                    XlsxWriter.Cell.text(tx.id),
                    XlsxWriter.Cell.text(p?.name.orEmpty()),
                    XlsxWriter.Cell.text(p?.phones?.firstOrNull().orEmpty()),
                    XlsxWriter.Cell.text(tx.type.name),
                    amt,
                    XlsxWriter.Cell.text(tx.currency),
                    XlsxWriter.Cell.text(dfD.format(Date(tx.date))),
                    XlsxWriter.Cell.text(dfT.format(Date(tx.time))),
                    XlsxWriter.Cell.text(tx.purpose.orEmpty()),
                    XlsxWriter.Cell.text(tx.note.orEmpty())
                )
            )
        }
        if (transactions.isNotEmpty()) {
            writer.addTotalsRow(
                listOf(
                    XlsxWriter.Cell.text("TOTAL (${transactions.size})"),
                    XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(),
                    XlsxWriter.Cell.num(totalReceived - totalIssued),
                    XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell()
                )
            )
        }
        writer.writeTo(file)
        true
    }.getOrElse { it.printStackTrace(); false }

    fun writeTransactionsPdf(
        file: File,
        transactions: List<Transaction>,
        personsById: Map<String, Person>,
        currencyCode: String,
        totalReceived: Double,
        totalIssued: Double,
        fromDate: Long,
        toDate: Long,
        context: Context
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfDT = dateTimeFormat(locale)
        writeMultiPagePdf(file, pageWidth = 842, pageHeight = 595) { canvas, pageIndex, pageWidth, pageHeight, stateRef ->
            val margin = 40f
            val titlePaint = Paint().apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 22f; color = android.graphics.Color.parseColor("#1F6FEB") }
            val headerPaint = Paint().apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 12f; color = android.graphics.Color.WHITE }
            val valuePaint = Paint().apply { typeface = Typeface.SANS_SERIF; textSize = 11f; color = android.graphics.Color.parseColor("#111827") }
            val metaPaint = Paint(valuePaint).apply { textSize = 12f; color = android.graphics.Color.parseColor("#374151") }
            val boldMeta = Paint().apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 12f; color = android.graphics.Color.parseColor("#111827") }
            val smallPaint = Paint(valuePaint).apply { textSize = 10f; color = android.graphics.Color.parseColor("#6B7280") }
            val greenPaint = Paint(valuePaint).apply { color = android.graphics.Color.parseColor("#059669") }
            val redPaint = Paint(valuePaint).apply { color = android.graphics.Color.parseColor("#DC2626") }

            var y = margin
            if (pageIndex == 0) {
                canvas.drawText("NETIJE — Transactions Journal", margin, y, titlePaint); y += 30
                canvas.drawText("Generated: ${dfDT.format(Date())}", margin, y, smallPaint); y += 22
                canvas.drawText("Range:        ${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}", margin, y, metaPaint); y += 18
                canvas.drawText("Transactions: ${transactions.size}", margin, y, metaPaint); y += 26
                canvas.drawText("Received:     ${currencyFmt.format(totalReceived)} $currencyCode", margin, y, greenPaint); y += 18
                canvas.drawText("Issued:       ${currencyFmt.format(totalIssued)} $currencyCode", margin, y, redPaint); y += 18
                canvas.drawText("Net Balance:  ${currencyFmt.format(totalReceived - totalIssued)} $currencyCode", margin, y, boldMeta); y += 30
            }

            val contentWidth = pageWidth - 2 * margin
            val colDate = 0f
            val colPerson = contentWidth * 0.14f
            val colType = contentWidth * 0.33f
            val colAmt = contentWidth * 0.42f
            val colPurpose = contentWidth * 0.56f
            val colNote = contentWidth * 0.80f

            val headerBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#1F6FEB") }
            canvas.drawRect(margin + colDate, y, margin + contentWidth, y + 22, headerBg)
            y += 16
            canvas.drawText("Date", margin + colDate + 6, y, headerPaint)
            canvas.drawText("Person", margin + colPerson + 6, y, headerPaint)
            canvas.drawText("Type", margin + colType + 6, y, headerPaint)
            canvas.drawText("Amount", margin + colAmt + 6, y, headerPaint)
            canvas.drawText("Purpose", margin + colPurpose + 6, y, headerPaint)
            canvas.drawText("Note", margin + colNote + 6, y, headerPaint)
            y += 12
            val border = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#E5E7EB"); style = Paint.Style.STROKE; strokeWidth = 0.5f }

            var startIdx = stateRef.get() as? Int ?: 0
            for (i in startIdx until transactions.size) {
                val tx = transactions[i]
                val rowTop = y
                val rowH = 22f
                if (i % 2 == 0) {
                    val bg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F9FAFB") }
                    canvas.drawRect(margin, rowTop, margin + contentWidth, rowTop + rowH, bg)
                }
                canvas.drawLine(margin, rowTop, margin + contentWidth, rowTop, border)
                val p = personsById[tx.personId]
                canvas.drawText(dfD.format(Date(tx.date)), margin + colDate + 6, rowTop + 15f, valuePaint)
                canvas.drawText(p?.name.orEmpty(), margin + colPerson + 6, rowTop + 15f, valuePaint)
                canvas.drawText(tx.type.name, margin + colType + 6, rowTop + 15f, if (tx.type == TransactionType.RECEIVED) greenPaint else redPaint)
                canvas.drawText("${currencyFmt.format(tx.amount)} $currencyCode", margin + colAmt + 6, rowTop + 15f, valuePaint)
                val purposeText = if (tx.purpose.isNullOrBlank()) "—" else tx.purpose
                drawClipped(canvas, purposeText, margin + colPurpose + 6, rowTop + 15f, colNote - colPurpose - 12, valuePaint)
                val noteText = if (tx.note.isNullOrBlank()) "—" else tx.note
                drawClipped(canvas, noteText, margin + colNote + 6, rowTop + 15f, contentWidth - colNote - 12, valuePaint)
                y += rowH
                startIdx = i + 1
                if (y > pageHeight - margin - 30) break
            }
            stateRef.set(startIdx)
            transactions.size - startIdx
        }
        true
    }.getOrElse { it.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private fun writeMultiPagePdf(
        file: File,
        pageWidth: Int,
        pageHeight: Int,
        drawPage: (Canvas, pageIndex: Int, pageWidth: Int, pageHeight: Int, stateRef: PageState) -> Int
    ) {
        val doc = PdfDocument()
        val stateRef = PageState()
        stateRef.set(0)
        var pageIndex = 0
        do {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val remaining = drawPage(page.canvas, pageIndex, pageWidth, pageHeight, stateRef)
            doc.finishPage(page)
            pageIndex++
        } while (remaining > 0 && pageIndex < 1000)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    class PageState {
        private var value: Any? = null
        fun set(v: Any?) { value = v }
        fun get(): Any? = value
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        if (maxWidth <= 0) return
        val layout = android.text.StaticLayout.Builder
            .obtain(text, 0, text.length, android.text.TextPaint(paint), maxWidth.toInt())
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y - paint.textSize)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawClipped(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        if (maxWidth <= 0f) return
        val measured = paint.measureText(text)
        if (measured <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        var t = text
        val ellipsis = "…"
        while (t.isNotEmpty() && paint.measureText(t + ellipsis) > maxWidth) {
            t = t.dropLast(1)
        }
        canvas.drawText(t + ellipsis, x, y, paint)
    }

    fun createOutputFile(
        context: Context,
        baseName: String,
        format: ReportFormat
    ): File {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        return File(dir, "${safeFileName(baseName)}_$ts.${format.ext}")
    }
}
