package com.example.netije.presentation.util

import android.content.Context
import com.example.netije.R
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.ReportSummaryRow
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val currencyFmt get() = DecimalFormat("#,##0.00")
private val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

private fun dateOnlyFormat(locale: Locale) = SimpleDateFormat("yyyy-MM-dd", locale)

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

private fun Transaction.reportNote(): String = listOfNotNull(
    parentTransactionId?.let { "Original transaction: $it" }, note,
    com.example.netije.domain.model.normalizeTransactionTags(tags).takeIf { it.isNotEmpty() }?.joinToString(" ")
).joinToString("\n")

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
                    tx.reportNote(),
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
                XlsxWriter.Cell.text(tx.reportNote()),
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
        ReportPdfWriter().use { pdf ->
            pdf.heading("NETIJE - ${context.getString(R.string.transaction_detail_title)}")
            pdf.paragraph("ID: ${tx.id}")
            pdf.paragraph("${context.getString(R.string.person_name)}: ${person?.name.orEmpty()}")
            pdf.transactionTable(listOf(tx), listOfNotNull(person).associateBy { it.id }, context)
            if (tx.attachments.isNotEmpty()) {
                pdf.paragraph("${context.getString(R.string.tx_attachments)}: ${tx.attachments.size}")
                tx.attachments.forEach(pdf::paragraph)
            }
            pdf.writeTo(file)
        }
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
                        tx.purpose.orEmpty(), tx.reportNote()
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
                    XlsxWriter.Cell.text(tx.reportNote())
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
        val received = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
        val issued = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
        ReportPdfWriter().use { pdf ->
            pdf.heading("NETIJE - ${context.getString(R.string.report_user_title)}")
            pdf.paragraph(personLabel(pwt.person))
            pdf.reportHeader(fromDate, toDate, currencyCode, FinanceSummary(received, issued), context)
            pdf.transactionTable(transactions, mapOf(pwt.person.id to pwt.person), context)
            pdf.writeTo(file)
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
        // Open on the actual transactions; keep aggregate totals on a separate tab.
        writer.newSheet("Transactions")
        writer.addTransactionRows(rows.flatMap { it.transactions }, rows.associate { it.person.id to it.person })
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
        ReportPdfWriter().use { pdf ->
            pdf.heading("NETIJE - ${context.getString(R.string.report_summary_title)}")
            pdf.reportHeader(fromDate, toDate, currencyCode, total, context)
            pdf.table(
                listOf(context.getString(R.string.person_name),
                    context.getString(R.string.person_phone),
                    "#", context.getString(R.string.tx_received),
                    context.getString(R.string.tx_issued),
                    context.getString(R.string.summary_balance)),
                listOf(2.2f, 1.5f, 0.5f, 1.4f, 1.4f, 1.4f),
                rows.map { row -> listOf(row.person.name, row.person.firstPhone.orEmpty(), row.count.toString(),
                    currencyFmt.format(row.received), currencyFmt.format(row.issued), currencyFmt.format(row.balance)) }
            )
            pdf.heading(context.getString(R.string.report_transactions_title))
            pdf.transactionTable(rows.flatMap { it.transactions }, rows.associate { it.person.id to it.person }, context)
            pdf.writeTo(file)
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
                        tx.reportNote()
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
        val writer = XlsxWriter()
        writer.newSheet("Transactions")
        writer.addMetaRow("Currency", currencyCode)
        writer.addMetaRow("Range", "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow("Transactions", transactions.size.toString())
        writer.addMetaRow("Total Received", currencyFmt.format(totalReceived))
        writer.addMetaRow("Total Issued", currencyFmt.format(totalIssued))
        writer.addMetaRow("Net Balance", currencyFmt.format(totalReceived - totalIssued))
        writer.addBlankRow()

        writer.addTransactionRows(transactions, personsById)
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
        ReportPdfWriter().use { pdf ->
            pdf.heading("NETIJE - ${context.getString(R.string.report_transactions_title)}")
            pdf.reportHeader(fromDate, toDate, currencyCode, FinanceSummary(totalReceived, totalIssued), context)
            pdf.transactionTable(transactions, personsById, context)
            pdf.writeTo(file)
        }
        true
    }.getOrElse { it.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private fun XlsxWriter.addTransactionRows(transactions: List<Transaction>, personsById: Map<String, Person>) {
        val dfD = dateOnlyFormat(Locale.getDefault())
        val dfT = SimpleDateFormat("HH:mm", Locale.getDefault())
        addHeaderRow(listOf("ID", "Person", "Phone", "Type", "Amount", "Currency", "Date", "Time", "Purpose", "Note"))
        for (tx in transactions) {
            val p = personsById[tx.personId]
            val amt = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
            addRow(
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
                    XlsxWriter.Cell.text(tx.reportNote())
                )
            )
        }
        if (transactions.isNotEmpty()) {
            addTotalsRow(
                listOf(
                    XlsxWriter.Cell.text("TOTAL (${transactions.size})"),
                    XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(),
                    XlsxWriter.Cell.num(transactions.sumOf { if (it.type == TransactionType.RECEIVED) it.amount else -it.amount }),
                    XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell(), XlsxWriter.Cell()
                )
            )
        }
    }

    private fun ReportPdfWriter.reportHeader(
        from: Long, to: Long, currency: String, total: FinanceSummary, context: Context
    ) {
        val date = dateOnlyFormat(Locale.getDefault())
        paragraph("${context.getString(R.string.report_date_from, date.format(Date(from)))} - " +
            context.getString(R.string.report_date_to, date.format(Date(to))))
        paragraph("${context.getString(R.string.tx_received)}: ${currencyFmt.format(total.totalReceived)} $currency   " +
            "${context.getString(R.string.tx_issued)}: ${currencyFmt.format(total.totalIssued)} $currency   " +
            "${context.getString(R.string.summary_balance)}: ${currencyFmt.format(total.balance)} $currency")
    }

    private fun ReportPdfWriter.transactionTable(
        transactions: List<Transaction>, people: Map<String, Person>, context: Context
    ) {
        val date = dateOnlyFormat(Locale.getDefault())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
        table(
            listOf(context.getString(R.string.tx_date),
                context.getString(R.string.person_name),
                context.getString(R.string.transaction_detail_type).trimEnd(':'),
                context.getString(R.string.tx_amount),
                context.getString(R.string.tx_purpose),
                context.getString(R.string.tx_note)),
            listOf(1.2f, 1.4f, 1f, 1.3f, 2f, 2.5f),
            transactions.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.time }.thenBy { it.id }).map { tx ->
                listOf("${date.format(Date(tx.date))}\n${time.format(Date(tx.time))}",
                    people[tx.personId]?.name.orEmpty(),
                    context.getString(if (tx.type == TransactionType.RECEIVED) R.string.tx_received else R.string.tx_issued),
                    "${currencyFmt.format(tx.amount)} ${tx.currency}", tx.purpose.orEmpty(), tx.reportNote())
            }
        )
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
