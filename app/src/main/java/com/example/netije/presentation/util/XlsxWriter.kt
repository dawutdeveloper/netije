package com.example.netije.presentation.util

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Lightweight, dependency-free .xlsx (SpreadsheetML / OOXML) writer.
 *
 * Real .xlsx files are just ZIP archives containing XML parts. This class
 * assembles a minimal but valid workbook so Excel, Google Sheets, LibreOffice
 * and Apple Numbers all open the produced files natively.
 *
 * Supported features:
 *  • Multiple sheets per workbook
 *  • Bold, colored headers (blue row with white text)
 *  • Numeric cells as `<v>` REAL numbers (not strings) so SUM / formulas work
 *  • String cells stored in Shared String Table (smaller files)
 *  • Cell styling: borders, background fills, bold fonts, center alignment
 *  • Currency number format `#,##0.00` applied to amount cells
 *  • Totals row style (grey background, bold)
 *  • Positive/Negative conditional coloring: light-green / light-coral fills
 *
 * This avoids Apache POI 5.x's hard requirement of
 * `MethodHandle.invoke/invokeExact` (API 26+) and fits minSdk 24 perfectly.
 */
internal class XlsxWriter {

    // ──────────────────── public API ────────────────────

    fun newSheet(name: String) {
        sheets.add(Sheet(name))
    }

    fun currentSheetOrNew(): Sheet {
        if (sheets.isEmpty()) sheets.add(Sheet("Sheet1"))
        return sheets.last()
    }

    fun addRow(values: List<Cell>) {
        currentSheetOrNew().rows.add(Row(values))
    }

    fun addHeaderRow(values: List<String>) {
        currentSheetOrNew().rows.add(
            Row(values.map { Cell(text = it, style = STYLE_HEADER) })
        )
    }

    fun addMetaRow(label: String, value: String) {
        currentSheetOrNew().rows.add(
            Row(
                listOf(
                    Cell(text = label, style = STYLE_META_BOLD),
                    Cell(text = value, style = STYLE_META)
                )
            )
        )
    }

    fun addBlankRow() {
        currentSheetOrNew().rows.add(Row(emptyList()))
    }

    fun addTotalsRow(values: List<Cell>) {
        currentSheetOrNew().rows.add(Row(values.map { it.copy(style = STYLE_TOTAL) }))
    }

    fun writeTo(file: File) {
        FileOutputStream(file).use { writeTo(it) }
    }

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            // [Content_Types].xml
            zip.putEntry("[Content_Types].xml", contentTypesXml())
            // _rels/.rels
            zip.putEntry("_rels/.rels", rootRelsXml())
            // workbook.xml
            zip.putEntry("xl/workbook.xml", workbookXml())
            // workbook rels
            zip.putEntry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            // shared strings
            zip.putEntry("xl/sharedStrings.xml", sharedStringsXml())
            // styles
            zip.putEntry("xl/styles.xml", stylesXml())
            // sheet files
            sheets.forEachIndexed { i, s ->
                zip.putEntry("xl/worksheets/sheet${i + 1}.xml", sheetXml(s))
            }
            zip.flush()
        }
    }

    // ──────────────────── data classes ────────────────────

    enum class Kind { TEXT, NUMBER, BLANK }

    data class Cell(
        val text: String? = null,
        val number: Double = Double.NaN,
        val kind: Kind = if (!number.isNaN()) Kind.NUMBER else if (text != null) Kind.TEXT else Kind.BLANK,
        val style: Int = STYLE_NORMAL
    ) {
        companion object {
            fun text(v: String, style: Int = STYLE_NORMAL) = Cell(text = v, style = style)
            fun num(v: Double, style: Int = STYLE_NUM) = Cell(number = v, style = style)
            fun positiveNum(v: Double) = Cell(number = v, style = STYLE_POSITIVE)
            fun negativeNum(v: Double) = Cell(number = v, style = STYLE_NEGATIVE)
        }
    }

    data class Row(val cells: List<Cell>)
    data class Sheet(val name: String, val rows: MutableList<Row> = mutableListOf())

    // ──────────────────── style constants (keep in sync with styles.xml below) ────────────────

    companion object {
        const val STYLE_NORMAL = 0
        const val STYLE_HEADER = 1
        const val STYLE_NUM = 2
        const val STYLE_POSITIVE = 3
        const val STYLE_NEGATIVE = 4
        const val STYLE_TOTAL = 5
        const val STYLE_META_BOLD = 6
        const val STYLE_META = 7
    }

    private val sheets = mutableListOf<Sheet>()

    // ──────────────────── XML assembly helpers ────────────────────

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        val bytes = content.toByteArray(Charsets.UTF_8)
        write(bytes, 0, bytes.size)
        closeEntry()
    }

    private inline fun buildStringWriter(block: (Writer) -> Unit): String {
        val sb = StringBuilder(1024)
        val w = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) { sb.appendRange(cbuf, off, off + len) }
            override fun flush() {}
            override fun close() {}
        }
        w.use(block)
        return sb.toString()
    }

    // ──────────────────── Shared String Table ────────────────────

    private fun collectSharedStrings(): List<String> {
        val set = linkedSetOf<String>()
        for (s in sheets) for (r in s.rows) for (c in r.cells) if (c.kind == Kind.TEXT && c.text != null) set += c.text
        return set.toList()
    }

    private fun indexOfShared(strings: List<String>, text: String): Int = strings.indexOf(text)

    // ──────────────────── Content_Types ────────────────────

    private fun contentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
          ${sheets.indices.joinToString("") { i ->
              """<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
          }}
        </Types>
    """.trimIndent() + "\n"

    // ──────────────────── root rels ────────────────────

    private fun rootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent() + "\n"

    // ──────────────────── workbook ────────────────────

    private fun workbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            ${sheets.mapIndexed { i, s ->
                val id = i + 1
                val nameXml = s.name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                """<sheet name="$nameXml" sheetId="$id" r:id="rId$id"/>"""
            }.joinToString("")}
          </sheets>
        </workbook>
    """.trimIndent() + "\n"

    private fun workbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId0" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
          <Relationship Id="rIdX" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
          ${sheets.indices.joinToString("") { i ->
              """<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
          }}
        </Relationships>
    """.trimIndent()
        .replace("rId0", "rId${sheets.size + 1}")
        .replace("rIdX", "rId${sheets.size + 2}") + "\n"

    // ──────────────────── shared strings ────────────────────

    private fun sharedStringsXml(): String = buildStringWriter { w ->
        val strings = collectSharedStrings()
        w.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        w.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${strings.size}\" uniqueCount=\"${strings.size}\">")
        for (s in strings) {
            w.append("<si><t xml:space=\"preserve\">")
            w.append(s.xmlEscape())
            w.append("</t></si>")
        }
        w.append("</sst>\n")
    }

    // ──────────────────── styles ────────────────────

    private fun stylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <!-- 8 numFmts: id 164..167 custom -->
          <numFmts count="1">
            <numFmt numFmtId="164" formatCode="#,##0.00"/>
          </numFmts>
          <!-- Fonts: Calibri 11 normal, Calibri 11 bold white, Calibri 11 bold, Calibri 11 -->
          <fonts count="4">
            <font><sz val="11"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><name val="Calibri"/></font>
          </fonts>
          <!-- Fills: none + gray125 (required defaults) + DARK_BLUE + LIGHT_GREEN + LIGHT_CORAL + GREY_25 -->
          <fills count="6">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF000080"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFC6EFCE"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFD9D9D9"/></patternFill></fill>
          </fills>
          <!-- Borders: none + thin all around -->
          <borders count="2">
            <border><left/><right/><top/><bottom/><diagonal/></border>
            <border>
              <left style="thin"/>
              <right style="thin"/>
              <top style="thin"/>
              <bottom style="thin"/>
              <diagonal/>
            </border>
          </borders>
          <!-- cellStyleXfs (required placeholder) -->
          <cellStyleXfs count="1">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
          </cellStyleXfs>
          <!-- cellXfs (indexes match STYLE_* constants):
               0=NORMAL text default thin border
               1=HEADER bold white fill dark-blue centered border
               2=NUM thin-border #,##0.00
               3=POSITIVE green-fill num-format border
               4=NEGATIVE coral-fill num-format border
               5=TOTAL grey-fill bold num-format border
               6=META_BOLD no-fill bold text
               7=META plain text
           -->
          <cellXfs count="8">
            <xf numFmtId="0"    fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
            <xf numFmtId="0"    fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
              <alignment horizontal="center" vertical="center"/>
            </xf>
            <xf numFmtId="164"  fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
            <xf numFmtId="164"  fontId="0" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1"/>
            <xf numFmtId="164"  fontId="0" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1"/>
            <xf numFmtId="164"  fontId="2" fillId="5" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
            <xf numFmtId="0"    fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0"    fontId="0" fillId="0" borderId="0" xfId="0"/>
          </cellXfs>
          <!-- cellStyles (required placeholder) -->
          <cellStyles count="1">
            <cellStyle name="Normal" xfId="0" builtinId="0"/>
          </cellStyles>
        </styleSheet>
    """.trimIndent() + "\n"

    // ──────────────────── sheet XML ────────────────────

    private fun sheetXml(sheet: Sheet): String = buildStringWriter { w ->
        val strings = collectSharedStrings()
        w.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        w.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

        // approximate column widths (first 10 cols = 15 chars default)
        w.append("<cols>")
        val maxCols = sheet.rows.maxOfOrNull { it.cells.size } ?: 0
        for (c in 0 until maxCols.coerceAtLeast(4)) {
            val wd = when (c) {
                0 -> 32.0   // Person/ID col wide
                1 -> 20.0   // phone
                4, 5 -> 16.0   // date/time
                3, 6, 7 -> 18.0
                else -> 14.0
            }
            w.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$wd\" customWidth=\"1\"/>")
        }
        w.append("</cols>")

        w.append("<sheetData>")
        sheet.rows.forEachIndexed { rIdx, row ->
            val rNum = rIdx + 1
            w.append("<row r=\"$rNum\">")
            row.cells.forEachIndexed { cIdx, cell ->
                val ref = columnLetter(cIdx) + rNum
                val styleAttr = if (cell.style != STYLE_NORMAL) " s=\"${cell.style}\"" else ""
                w.append("<c r=\"$ref\"$styleAttr")
                when (cell.kind) {
                    Kind.TEXT -> {
                        val text = cell.text ?: ""
                        val si = indexOfShared(strings, text)
                        w.append(" t=\"s\"><v>$si</v></c>")
                    }
                    Kind.NUMBER -> {
                        w.append("><v>${cell.number}</v></c>")
                    }
                    Kind.BLANK -> {
                        w.append("/>")
                    }
                }
            }
            w.append("</row>")
        }
        w.append("</sheetData>")

        // Auto sheet dimension for sanity
        val lastRow = sheet.rows.size
        val lastCol = sheet.rows.maxOfOrNull { it.cells.size } ?: 0
        val dimRef = if (lastRow == 0) "A1" else "A1:${columnLetter(lastCol - 1)}$lastRow"
        w.append("<dimension ref=\"$dimRef\"/>")
        w.append("</worksheet>\n")
    }

    // ──────────────────── tiny util ────────────────────

    private fun columnLetter(idx: Int): String {
        var i = idx
        val sb = StringBuilder()
        while (true) {
            sb.append('A' + (i % 26))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.reverse().toString()
    }

    private fun String.xmlEscape(): String {
        var s = this
        if (s.any { it in "&<>'\"" || it.code < 0x20 && it !in listOf('\t', '\n', '\r') }) {
            val sb = StringBuilder(length + 16)
            for (c in s) {
                when (c) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '\'' -> sb.append("&apos;")
                    '"' -> sb.append("&quot;")
                    else -> if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        // drop control chars — invalid in XML 1.0
                    } else sb.append(c)
                }
            }
            s = sb.toString()
        }
        return s
    }
}
