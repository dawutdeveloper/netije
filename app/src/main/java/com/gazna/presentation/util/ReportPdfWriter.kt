package com.gazna.presentation.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.Closeable
import java.io.File

/** Wraps report content and continues long rows across pages without dropping text. */
internal class ReportPdfWriter : Closeable {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private val width = 842
    private val height = 595
    private val margin = 36f
    private val padding = 6f
    private var y = margin
    private val contentWidth get() = width - margin * 2
    private val bottom get() = height - margin
    private val canvas: Canvas get() = checkNotNull(page).canvas
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = 11f
        color = Color.BLACK
    }
    private val headingPaint = TextPaint(textPaint).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 18f
    }
    private val headerPaint = TextPaint(textPaint).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        color = Color.WHITE
    }
    private val backgroundPaint = Paint().apply { color = Color.rgb(245, 245, 245) }
    private val borderPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

    private fun newPage() {
        page?.let(document::finishPage)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        y = margin
    }

    private fun layout(text: String, width: Float, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    fun heading(text: String) = paragraph(text, headingPaint)

    fun paragraph(text: String) = paragraph(text, textPaint)

    private fun paragraph(text: String, paint: TextPaint) {
        val layout = layout(text, contentWidth, paint)
        var line = 0
        while (line < layout.lineCount) {
            if (page == null) newPage()
            var end = line
            while (end < layout.lineCount &&
                layout.getLineBottom(end) - layout.getLineTop(line) <= bottom - y) end++
            if (end == line) {
                newPage()
                continue
            }
            val sliceHeight = layout.getLineBottom(end - 1) - layout.getLineTop(line)
            drawSlice(layout, line, end, margin, y, contentWidth, sliceHeight.toFloat())
            y += sliceHeight + 8f
            line = end
        }
    }

    fun table(headers: List<String>, weights: List<Float>, rows: List<List<String>>) {
        require(headers.size == weights.size && weights.all { it > 0 })
        require(rows.all { it.size == headers.size })
        val widths = weights.map { contentWidth * it / weights.sum() }
        val headerLayouts = headers.mapIndexed { index, text -> layout(text, widths[index] - 2 * padding, headerPaint) }
        val headerHeight = headerLayouts.maxOf { it.height }.toFloat() + 2 * padding
        fun drawHeader() {
            if (page == null || y + headerHeight + 24f > bottom) newPage()
            canvas.drawRect(margin, y, margin + contentWidth, y + headerHeight, Paint().apply { color = Color.BLACK })
            var x = margin
            headerLayouts.forEachIndexed { index, item ->
                drawSlice(item, 0, item.lineCount, x + padding, y + padding, widths[index] - 2 * padding, item.height.toFloat())
                x += widths[index]
            }
            y += headerHeight
        }
        drawHeader()
        rows.forEachIndexed { rowIndex, values ->
            val layouts = values.mapIndexed { index, value ->
                layout(value.ifBlank { "—" }, widths[index] - 2 * padding, textPaint)
            }
            val lines = layouts.maxOf { it.lineCount }
            val rowHeight = layouts.maxOf { it.height } + 2 * padding
            // Keep ordinary rows together; only split rows taller than a page.
            if (y + rowHeight > bottom && rowHeight <= bottom - margin - headerHeight) {
                newPage()
                drawHeader()
            }
            var startLine = 0
            while (startLine < lines) {
                fun sliceHeight(end: Int): Float = layouts.maxOf { item ->
                    if (startLine >= item.lineCount) 0 else
                        item.getLineBottom(minOf(end, item.lineCount) - 1) - item.getLineTop(startLine)
                }.toFloat() + 2 * padding
                var endLine = startLine
                while (endLine < lines && y + sliceHeight(endLine + 1) <= bottom) endLine++
                if (endLine == startLine) {
                    newPage()
                    drawHeader()
                    continue
                }
                val sliceHeight = sliceHeight(endLine)
                if (rowIndex % 2 == 0) canvas.drawRect(margin, y, margin + contentWidth, y + sliceHeight, backgroundPaint)
                canvas.drawLine(margin, y, margin + contentWidth, y, borderPaint)
                var x = margin
                layouts.forEachIndexed { index, item ->
                    if (startLine < item.lineCount) drawSlice(item, startLine, minOf(endLine, item.lineCount),
                        x + padding, y + padding, widths[index] - 2 * padding, sliceHeight - 2 * padding)
                    x += widths[index]
                }
                y += sliceHeight
                startLine = endLine
                if (startLine < lines) {
                    newPage()
                    drawHeader()
                }
            }
        }
        y += 12f
    }

    private fun drawSlice(layout: StaticLayout, from: Int, to: Int, x: Float, top: Float, width: Float, height: Float) {
        canvas.save()
        canvas.clipRect(x, top, x + width, top + height)
        canvas.translate(x, top - layout.getLineTop(from))
        // Clip to complete lines, including when a cell ends before its neighbors.
        canvas.clipRect(0f, layout.getLineTop(from).toFloat(), width, layout.getLineBottom(to - 1).toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    fun writeTo(file: File) {
        if (page == null) newPage()
        document.finishPage(checkNotNull(page))
        page = null
        file.outputStream().use(document::writeTo)
    }

    override fun close() {
        page?.let(document::finishPage)
        page = null
        document.close()
    }
}
