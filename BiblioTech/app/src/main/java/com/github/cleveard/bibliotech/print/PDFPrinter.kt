package com.github.cleveard.bibliotech.print

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.print.PageRange
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.text.*
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.lang.IllegalStateException
import kotlin.math.roundToInt

private val title = LayoutDescription.ColumnFieldLayoutDescription(Column.TITLE)
private val subtitle = LayoutDescription.ColumnFieldLayoutDescription(Column.SUBTITLE).apply {
    margin.top = 4.5f
}
private val authors = LayoutDescription.ColumnFieldLayoutDescription(Column.FIRST_NAME).apply {
    margin.top = 4.5f
}
private val alignTitleTop = LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Top, null).also {
    title.verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
}
private val alignTitleStart = LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, null).also {
    title.horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
}
private val alignSubtitleTop = LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Bottom, title).also {
    subtitle.verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
}
private val alignSubtitleStart = LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, title).also {
    subtitle.horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
}
private val alignAuthorsTop = LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Bottom, subtitle).also {
    authors.verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
}
private val alignAuthorsStart = LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, title).also {
    authors.horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
}
private val simpleLayout = LayoutDescription(
    listOf(title, subtitle, authors),
    emptyList(),
    18.0f,
    9.0f
)

class PDFPrinter: Closeable {
    private var pdf: PrintedPdfDocument? = null
        private set(v) {
            field.let {
                field = v
                if (it != v) {
                    it?.close()
                    if (v == null) {
                        pages = null
                    }
                }
            }
        }

    private val margins: RectF = RectF(72.0f, 72.0f, 72.0f, 72.0f)
    private var attributes: PrintAttributes = defaultAttributes
        set(v) {
            if (v != field)
                pdf = null
            field = v
        }
    var bookList: List<BookAndAuthors>? = null
        set(v) { if (v != field) pdf = null; field = v }
    private var pages: List<PageLayoutHandler.Page>? = null
    val pageCount: Int
        get() = pages?.size?: 0
    var layoutDescription: LayoutDescription = simpleLayout
        set(v) { if (v != field) pdf = null; field = v }
    var separatorLineWidth: Float = 0.5f
        set(v) { if (v != field) pdf = null; field = v }
    var numberOfColumns: Int = 2
        set(v) { if (v != field) pdf = null; field = v }
    var orphans: Int = 2
        set(v) { if (v != field) pdf = null; field = v }
    private val pageDrawBounds = RectF()
    private var pageLayoutHandler: PageLayoutHandler = PageLayoutHandler(this, calculateDrawBounds())
    val basePaint = TextPaint()

    fun horizontalPointsToPixels(length: Float): Int {
        return pointsToPixels(length)
    }

    fun verticalPointstoPixels(length: Float): Int {
        return pointsToPixels(length)
    }

    fun horizontalPixelsToPoints(length: Int): Float {
        return pixelsToPoints(length)
    }

    fun verticalPixelsToPoints(length: Int): Float {
        return pixelsToPoints(length)
    }

    override fun close() {
        pdf = null
        attributes = defaultAttributes
    }

    fun changeLayout(newAttributes: PrintAttributes? = null, newMargins: RectF? = null): Boolean {
        attributes = newAttributes?: defaultAttributes
        if (newMargins != null && newMargins != margins) {
            margins.set(newMargins)
            pdf = null
        }
        return pdf == null
    }

    suspend fun computePageCount(): Int {
        return withContext(Dispatchers.IO) {
            layoutPages().size
        }
    }

    suspend fun writePDF(
        context: Context,
        pageRanges: Array<PageRange>,
        writtenRanges: MutableList<PageRange>
    ): PrintedPdfDocument {
        pdf?.let { return@writePDF it }
        val doc = PrintedPdfDocument(context, attributes)
        pdf = doc
        bookList?.let { _ ->
            withContext(Dispatchers.IO) {
                pages = layoutPages().also {
                    processPages(it, pageRanges, writtenRanges)
                }
            }
        }
        return doc
    }

    private fun calculateDrawBounds(): RectF {
        val size = attributes.mediaSize?: defaultAttributes.mediaSize!!
        val minMargins = attributes.minMargins?: defaultAttributes.minMargins!!
        return RectF(
            margins.left.coerceAtLeast(minMargins.leftMils.toFloat() * 72.0f / 1000.0f),
            margins.top.coerceAtLeast(minMargins.topMils.toFloat() * 72.0f / 1000.0f),
            size.widthMils.toFloat() * 72.0f / 1000.0f - margins.right.coerceAtLeast(minMargins.rightMils.toFloat() * 72.0f / 1000.0f),
            size.heightMils.toFloat() * 72.0f / 1000.0f - margins.bottom.coerceAtLeast(minMargins.bottomMils.toFloat() * 72.0f / 1000.0f)
        ).also {
            pageDrawBounds.set(it)
        }
    }

    private fun layoutPages(): List<PageLayoutHandler.Page> {
        return bookList?.let {books ->
            pages?: run {
                pageLayoutHandler = PageLayoutHandler(this, calculateDrawBounds())
                pageLayoutHandler.layoutPages(books).let {pages ->
                    if (pages.isEmpty())
                        throw IllegalStateException("No pages were found to print")
                    this.pages = pages
                    pages
                }
            }
        }?: throw IllegalStateException("A list of books must be selected to print")
    }

    /**
     * Process the pages we are printing
     * @param pages The layouts of all of the pages
     */
    private fun processPages(pages: List<PageLayoutHandler.Page>, pageRanges: Array<PageRange>, writtenRanges: MutableList<PageRange>) {
        pdf?.let { doc ->
            bookList?.let { books ->
                // start and end of the current print range
                var start = Int.MIN_VALUE
                var end = Int.MIN_VALUE

                // For each page
                for (pageNumber in pages.indices) {
                    // If the page is not in the range we are printing, go to the next page
                    if (pageRanges.indexOfFirst { it.start <= pageNumber && it.end >= pageNumber } < 0)
                        continue
                    // If we are continuing a range, then just bump the end
                    if (end + 1 == pageNumber)
                        ++end
                    else {
                        // Write out the current range, if there is one
                        if (start >= 0)
                            writtenRanges.add(PageRange(start, end))
                        // Set the start and end of the current range
                        start = pageNumber
                        end = pageNumber
                    }

                    // Get the page
                    val page = doc.startPage(pageNumber)

                    // Get the canvas for printing
                    val canvas = page.canvas
                    canvas.translate(pageDrawBounds.left, pageDrawBounds.top)

                    // Debugging - Draw border of margin area
                    canvas.drawRect(-0.5f, -0.5f, pageDrawBounds.width() + 0.5f, 0.0f, basePaint)
                    canvas.drawRect(-0.5f, 0.0f, 0.0f, pageDrawBounds.height(), basePaint)
                    canvas.drawRect(pageDrawBounds.width(), 0.0f, pageDrawBounds.width() + 0.5f, pageDrawBounds.height(), basePaint)
                    canvas.drawRect(-0.5f, pageDrawBounds.height(), pageDrawBounds.width() + 0.5f, pageDrawBounds.height() + 0.5f, basePaint)

                    // Print each book on the page
                    for (bp in pages[pageNumber].books) {
                        // Print the book
                        bp.draw(canvas, books)
                    }

                    // Finish the page
                    doc.finishPage(page)
                }

                // save the last range we printed
                if (start >= 0)
                    writtenRanges.add(PageRange(start, end))
            }
        }
    }

    companion object {
        private val defaultAttributes: PrintAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LEDGER)
            .setResolution(PrintAttributes.Resolution("1", "300DPI", 300, 300))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
            .build()

        fun pixelsToPoints(length: Int, res: Int = 72): Float {
            return length.toFloat() * 72.0f / res.toFloat()
        }

        fun pointsToPixels(length: Float, res: Int = 72): Int {
            return ((length * res) / 72.0f).roundToInt()
        }
    }

}
