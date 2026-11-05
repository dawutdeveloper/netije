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

private fun Transaction.reportNote(context: Context? = null): String = listOfNotNull(
    parentTransactionId?.let { "${reportLabel(context, "Original transaction")}: $it" }, note,
    com.example.netije.domain.model.normalizeTransactionTags(tags).takeIf { it.isNotEmpty() }?.joinToString(" ")
).joinToString("\n")

// Keep the original labels for callers without Android resources (such as JVM exports).
// App exports supply a context configured for the selected language.
private fun reportLabel(context: Context?, fallback: String): String {
    if (context == null) return fallback
    val resource = when (fallback) {
        "Transaction" -> R.string.transaction_detail_title
        "Person Report" -> R.string.report_user_title
        "Transactions" -> R.string.transactions_header
        "Summary" -> R.string.report_summary_title
        "ID", "id" -> R.string.report_id
        "Person", "person", "# REPORT FOR" -> R.string.person_title_placeholder
        "Phone", "phone", "person_phone", "# PHONE" -> R.string.person_phone
        "Type", "type" -> R.string.transaction_detail_type
        "Amount", "amount" -> R.string.tx_amount
        "Currency", "currency", "# CURRENCY" -> R.string.transaction_detail_currency
        "Date", "date" -> R.string.tx_date
        "Time", "time" -> R.string.tx_time
        "Purpose", "purpose" -> R.string.tx_purpose
        "Note", "note" -> R.string.tx_note
        "Attachments", "attachments" -> R.string.tx_attachments
        "attachments_count" -> R.string.report_attachment_count
        "Range", "# RANGE" -> R.string.report_range
        "Total Received", "# RECEIVED", "# TOTAL_RECEIVED" -> R.string.summary_received
        "Received", "received" -> R.string.tx_received
        "Total Issued", "# ISSUED", "# TOTAL_ISSUED" -> R.string.summary_issued
        "Issued", "issued" -> R.string.tx_issued
        "Balance", "Net Balance", "balance", "# BALANCE", "# TOTAL_BALANCE" -> R.string.summary_balance
        "Persons" -> R.string.report_people
        "#Tx", "tx_count", "# TX_COUNT" -> R.string.report_transaction_count
        "TOTALS", "TOTAL" -> R.string.report_total
        "Original transaction" -> R.string.original_transaction
        else -> return fallback
    }
    return context.getString(resource).trimEnd(':')
}

private fun reportLabels(context: Context?, vararg labels: String): List<String> =
    labels.map { reportLabel(context, it) }

private fun Transaction.reportType(context: Context?): String =
    if (context == null) type.name else context.getString(
        if (type == TransactionType.RECEIVED) R.string.tx_received else R.string.tx_issued
    )

object LocalReportGenerator {

    // ─────────────────────────────────────────────────────────────────────
    //  1. SINGLE TRANSACTION (detail screen)
    // ─────────────────────────────────────────────────────────────────────

    fun writeTransactionCsv(
        file: File,
        tx: Transaction,
        person: Person?,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                val header = reportLabels(context,
                    "id", "person", "person_phone", "type", "amount", "currency",
                    "date", "time", "purpose", "note", "attachments_count", "attachments")
                w.write(header.joinToString(",") { it.csvEscape() })
                w.newLine()
                val row = listOf(
                    tx.id,
                    person?.name.orEmpty(),
                    person?.phones?.firstOrNull().orEmpty(),
                    tx.reportType(context),
                    currencyFmt.format(tx.amount),
                    tx.currency,
                    dfD.format(Date(tx.date)),
                    dfT.format(Date(tx.time)),
                    tx.purpose.orEmpty(),
                    tx.reportNote(context),
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
        person: Person?,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        val writer = XlsxWriter()
        writer.newSheet(reportLabel(context, "Transaction"))
        val labels = reportLabels(context,
            "ID", "Person", "Phone", "Type", "Amount", "Currency",
            "Date", "Time", "Purpose", "Note", "Attachments")
        writer.addHeaderRow(labels)
        val amtCell = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
        writer.addRow(
            listOf(
                XlsxWriter.Cell.text(tx.id),
                XlsxWriter.Cell.text(person?.name.orEmpty()),
                XlsxWriter.Cell.text(person?.phones?.firstOrNull().orEmpty()),
                XlsxWriter.Cell.text(tx.reportType(context)),
                amtCell,
                XlsxWriter.Cell.text(tx.currency),
                XlsxWriter.Cell.text(dfD.format(Date(tx.date))),
                XlsxWriter.Cell.text(dfT.format(Date(tx.time))),
                XlsxWriter.Cell.text(tx.purpose.orEmpty()),
                XlsxWriter.Cell.text(tx.reportNote(context)),
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
            pdf.paragraph("${reportLabel(context, "ID")}: ${tx.id}")
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
        toDate: Long,
        context: Context? = null
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
                w.write(reportLabel(context, "# REPORT FOR").csvEscape() + "," + pwt.person.name.csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# PHONE").csvEscape() + "," + (pwt.person.phones.firstOrNull().orEmpty()).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# RANGE").csvEscape() + "," + (dfD.format(Date(fromDate)) + " → " + dfD.format(Date(toDate))).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# RECEIVED").csvEscape() + "," + currencyFmt.format(recv).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# ISSUED").csvEscape() + "," + currencyFmt.format(iss).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# BALANCE").csvEscape() + "," + currencyFmt.format(bal).csvEscape())
                w.newLine()
                w.newLine()
                val header = reportLabels(context,
                    "id", "type", "amount", "currency", "date", "time",
                    "purpose", "note")
                w.write(header.joinToString(",") { it.csvEscape() })
                w.newLine()
                transactions.forEach { tx ->
                    val row = listOf(
                        tx.id, tx.reportType(context),
                        currencyFmt.format(tx.amount), tx.currency,
                        dfD.format(Date(tx.date)), dfT.format(Date(tx.time)),
                        tx.purpose.orEmpty(), tx.reportNote(context)
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
        toDate: Long,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        val recv = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
        val iss = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
        val bal = recv - iss
        val writer = XlsxWriter()
        writer.newSheet(reportLabel(context, "Person Report"))
        writer.addMetaRow(reportLabel(context, "Person"), pwt.person.name)
        writer.addMetaRow(reportLabel(context, "Phone"), pwt.person.phones.firstOrNull().orEmpty())
        writer.addMetaRow(reportLabel(context, "Currency"), currency)
        writer.addMetaRow(reportLabel(context, "Range"), "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow(reportLabel(context, "Total Received"), currencyFmt.format(recv))
        writer.addMetaRow(reportLabel(context, "Total Issued"), currencyFmt.format(iss))
        writer.addMetaRow(reportLabel(context, "Balance"), currencyFmt.format(bal))
        writer.addBlankRow()

        writer.addHeaderRow(reportLabels(context,
            "ID", "Type", "Amount", "Currency", "Date", "Time",
            "Purpose", "Note"))
        for (tx in transactions) {
            val amt = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
            writer.addRow(
                listOf(
                    XlsxWriter.Cell.text(tx.id),
                    XlsxWriter.Cell.text(tx.reportType(context)),
                    amt,
                    XlsxWriter.Cell.text(tx.currency),
                    XlsxWriter.Cell.text(dfD.format(Date(tx.date))),
                    XlsxWriter.Cell.text(dfT.format(Date(tx.time))),
                    XlsxWriter.Cell.text(tx.purpose.orEmpty()),
                    XlsxWriter.Cell.text(tx.reportNote(context))
                )
            )
        }
        if (transactions.isNotEmpty()) {
            writer.addTotalsRow(
                listOf(
                    XlsxWriter.Cell.text("${reportLabel(context, "TOTAL")} (${transactions.size})"),
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
        toDate: Long,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                w.write(reportLabel(context, "# RANGE").csvEscape() + "," + (dfD.format(Date(fromDate)) + " → " + dfD.format(Date(toDate))).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# CURRENCY").csvEscape() + "," + currencyCode.csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# TOTAL_RECEIVED").csvEscape() + "," + currencyFmt.format(total.totalReceived).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# TOTAL_ISSUED").csvEscape() + "," + currencyFmt.format(total.totalIssued).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# TOTAL_BALANCE").csvEscape() + "," + currencyFmt.format(total.totalReceived - total.totalIssued).csvEscape())
                w.newLine()
                w.newLine()
                val header = reportLabels(context, "person", "phone", "tx_count", "received", "issued", "balance")
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
        toDate: Long,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val writer = XlsxWriter()
        // Open on the actual transactions; keep aggregate totals on a separate tab.
        writer.newSheet(reportLabel(context, "Transactions"))
        writer.addTransactionRows(rows.flatMap { it.transactions }, rows.associate { it.person.id to it.person }, context)
        writer.newSheet(reportLabel(context, "Summary"))
        writer.addMetaRow(reportLabel(context, "Currency"), currencyCode)
        writer.addMetaRow(reportLabel(context, "Range"), "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow(reportLabel(context, "Total Received"), currencyFmt.format(total.totalReceived))
        writer.addMetaRow(reportLabel(context, "Total Issued"), currencyFmt.format(total.totalIssued))
        writer.addMetaRow(reportLabel(context, "Net Balance"), currencyFmt.format(total.totalReceived - total.totalIssued))
        writer.addMetaRow(reportLabel(context, "Persons"), rows.size.toString())
        writer.addBlankRow()

        writer.addHeaderRow(reportLabels(context, "Person", "Phone", "#Tx", "Received", "Issued", "Balance"))
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
                XlsxWriter.Cell.text(reportLabel(context, "TOTALS")),
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
        toDate: Long,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val dfT = SimpleDateFormat("HH:mm", locale)
        file.outputStream().use { fos ->
            fos.write(bomBytes)
            fos.bufferedWriter().use { w ->
                val bal = totalReceived - totalIssued
                w.write(reportLabel(context, "# RANGE").csvEscape() + "," + ("${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}").csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# CURRENCY").csvEscape() + "," + currencyCode.csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# TX_COUNT").csvEscape() + "," + transactions.size.toString().csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# RECEIVED").csvEscape() + "," + currencyFmt.format(totalReceived).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# ISSUED").csvEscape() + "," + currencyFmt.format(totalIssued).csvEscape())
                w.newLine()
                w.write(reportLabel(context, "# BALANCE").csvEscape() + "," + currencyFmt.format(bal).csvEscape())
                w.newLine()
                w.newLine()
                val header = reportLabels(context,
                    "id", "person", "phone", "type", "amount", "currency",
                    "date", "time", "purpose", "note")
                w.write(header.joinToString(",") { it.csvEscape() }); w.newLine()
                transactions.forEach { tx ->
                    val p = personsById[tx.personId]
                    val row = listOf(
                        tx.id,
                        p?.name.orEmpty(),
                        p?.phones?.firstOrNull().orEmpty(),
                        tx.reportType(context),
                        currencyFmt.format(tx.amount),
                        tx.currency,
                        dfD.format(Date(tx.date)),
                        dfT.format(Date(tx.time)),
                        tx.purpose.orEmpty(),
                        tx.reportNote(context)
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
        toDate: Long,
        context: Context? = null
    ): Boolean = runCatching {
        val locale = Locale.getDefault()
        val dfD = dateOnlyFormat(locale)
        val writer = XlsxWriter()
        writer.newSheet(reportLabel(context, "Transactions"))
        writer.addMetaRow(reportLabel(context, "Currency"), currencyCode)
        writer.addMetaRow(reportLabel(context, "Range"), "${dfD.format(Date(fromDate))} → ${dfD.format(Date(toDate))}")
        writer.addMetaRow(reportLabel(context, "Transactions"), transactions.size.toString())
        writer.addMetaRow(reportLabel(context, "Total Received"), currencyFmt.format(totalReceived))
        writer.addMetaRow(reportLabel(context, "Total Issued"), currencyFmt.format(totalIssued))
        writer.addMetaRow(reportLabel(context, "Net Balance"), currencyFmt.format(totalReceived - totalIssued))
        writer.addBlankRow()

        writer.addTransactionRows(transactions, personsById, context)
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

    private fun XlsxWriter.addTransactionRows(transactions: List<Transaction>, personsById: Map<String, Person>, context: Context?) {
        val dfD = dateOnlyFormat(Locale.getDefault())
        val dfT = SimpleDateFormat("HH:mm", Locale.getDefault())
        addHeaderRow(reportLabels(context,
            "ID", "Person", "Phone", "Type", "Amount", "Currency",
            "Date", "Time", "Purpose", "Note"))
        for (tx in transactions) {
            val p = personsById[tx.personId]
            val amt = if (tx.type == TransactionType.RECEIVED) XlsxWriter.Cell.positiveNum(tx.amount)
                      else XlsxWriter.Cell.negativeNum(tx.amount)
            addRow(
                listOf(
                    XlsxWriter.Cell.text(tx.id),
                    XlsxWriter.Cell.text(p?.name.orEmpty()),
                    XlsxWriter.Cell.text(p?.phones?.firstOrNull().orEmpty()),
                    XlsxWriter.Cell.text(tx.reportType(context)),
                    amt,
                    XlsxWriter.Cell.text(tx.currency),
                    XlsxWriter.Cell.text(dfD.format(Date(tx.date))),
                    XlsxWriter.Cell.text(dfT.format(Date(tx.time))),
                    XlsxWriter.Cell.text(tx.purpose.orEmpty()),
                    XlsxWriter.Cell.text(tx.reportNote(context))
                )
            )
        }
        if (transactions.isNotEmpty()) {
            addTotalsRow(
                listOf(
                    XlsxWriter.Cell.text("${reportLabel(context, "TOTAL")} (${transactions.size})"),
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
                    "${currencyFmt.format(tx.amount)} ${tx.currency}", tx.purpose.orEmpty(), tx.reportNote(context))
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
