package com.github.cleveard.bibliotech.print

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.github.cleveard.bibliotech.db.BookAndAuthors

const val EPSILON = 1.0e-3

/**
 * The class that calculates the layouts of books
 */
class PageLayoutHandler(
    private val printer: PDFPrinter,
    pageDrawBounds: RectF,
    private val rtl: Boolean = false
) {

    abstract class DrawPosition(
        val position: PointF
    ) {
        abstract fun draw(canvas: Canvas, bookList: List<BookAndAuthors>)
    }

    inner class BookPosition(
        private val bookIndex: Int,
        position: PointF,
        clip: RectF
    ): DrawPosition(position) {
        private val clipRect: RectF = RectF(clip)
        override fun draw(canvas: Canvas, bookList: List<BookAndAuthors>) {
            // Save the current canvas state
            canvas.save()
            // Set the clip rectangle and location
            canvas.translate(position.x, position.y)
            canvas.clipRect(clipRect)
            // Print the book
            layoutBook(bookList[bookIndex])
                .verticalClip(position.y, pageHeight)
                .draw(canvas)
            // Restore the canvas
            canvas.restore()
        }
    }

    inner class SeparatorPosition(
        private val paint: Paint,
        position: PointF
    ): DrawPosition(position) {
        override fun draw(canvas: Canvas, bookList: List<BookAndAuthors>) {
            // Save the current canvas state
            canvas.save()
            // Set the clip rectangle and location
            canvas.translate(position.x, position.y)
            // Print the book
            paint.style = Paint.Style.FILL
            canvas.drawRect(0.0f, 0.0f, columnWidth, printer.separatorLineWidth, paint)
            // Restore the canvas
            canvas.restore()
        }
    }

    data class Page(
        val books: List<DrawPosition>
    )

    private val pages = ArrayList<Page>()
    private var page = ArrayList<DrawPosition>()
    val pageHeight = pageDrawBounds.height()
    private val pageWidth = pageDrawBounds.width()
    private val columnStride = (pageWidth + printer.layoutDescription.horizontalSeparation) / printer.numberOfColumns
    private val columnWidth =  columnStride - printer.layoutDescription.horizontalSeparation
    private var location: PointF = PointF(0.0f, 0.0f)
    private var columnStart: Boolean = true
    private val dependencies: HashMap<BookLayout.LayoutBounds, MutableSet<BookLayout.LayoutBounds>> = HashMap()
    private val calculated: HashSet<BookLayout.LayoutBounds> = HashSet()
    private var laidOutBook: BookAndAuthors? = null
    private val layout: BookLayout by lazy {
        printer.layoutDescription.createLayout(printer, columnWidth, rtl).also {bookLayout ->
            // Build the layout map and dependency map
            dependencies[bookLayout.layoutBounds] = HashSet()
            // Map the FieldLayoutDescriptions to the  DrawLayouts
            // And initialize the dependency map
            for (dl in bookLayout.columns) {
                dependencies[dl] = HashSet<BookLayout.LayoutBounds>().also {
                    dl.alignment.values.asSequence().map { it.asSequence() }.flatten().forEach { dependencies[it.bounds]?.add(dl) }
                }
            }
        }
    }

    /**
     * Add page and start next one
     */
    private fun nextPage() {
        page.let {
            page = ArrayList()
            pages.add(Page(it))
        }
        columnStart = true
    }

    fun layoutPages(books: List<BookAndAuthors>): List<Page> {
        for (i in books.indices) {
            placeBook(i, layoutBook(books[i]))
        }

        if (page.size > 0)
            nextPage()
        return pages
    }

    /**
     * Layout a single book
     * @param book The book to layout
     * @return The layout for the book. All layouts share a single object
     */
    fun layoutBook(book: BookAndAuthors): BookLayout {
        if (book == laidOutBook)
            return layout
        laidOutBook = book
        layout.setContent(book)

        clear()
        do {
            calculateLayout()
        } while (resolveOverlaps())

        return layout
    }

    /**
     * Place the book layout on the page
     * @param index The index of the book
     * @param layout The layout of the  book
     */
    private fun placeBook(index: Int, layout: BookLayout) {
        // If nothing to print the return
        if (layout.bounds.isEmpty)
            return

        // Starting position
        var x = location.x
        var y = location.y
        // The space separating the books
        var separatorSize = if (columnStart)
            -layout.bounds.top
        else
            layout.description.verticalSeparation + printer.separatorLineWidth

        // Bump to the next column and next page if needed
        fun nextColumn(newY: Float) {
            x += columnStride
            if (x + EPSILON >= pageWidth) {
                nextPage()
                x = 0.0f
            }
            columnStart = true
            y = newY
        }

        // Clip the book to the column height
        layout.verticalClip(y + separatorSize, pageHeight)
        layout.handleOrphans(y + separatorSize, columnStart)
        // No space for anything, go to next column
        if (layout.clip.isEmpty) {
            // Go to next column
            nextColumn(0.0f)
            separatorSize = -layout.marginBounds.top
            // Clip the height for the new column
            layout.verticalClip(y + separatorSize, pageHeight)
            layout.handleOrphans(y + separatorSize, columnStart)
        }
        if (!columnStart && printer.separatorLineWidth > 0) {
            page.add(
                SeparatorPosition(
                    printer.basePaint,
                    PointF(x, y + layout.description.verticalSeparation / 2.0f)
                )
            )
        }
        y += separatorSize

        // Break the book layout over the columns and pages
        while (true) {
            if (columnStart && layout.clip.top + y > 0) {
                y = -layout.clip.top
                layout.verticalClip(y, pageHeight)
                layout.handleOrphans(y + separatorSize, columnStart)
            }

            // Get the current print position and clip rectangle
            val pos = PointF(x, y)
            // Add the printout to the page
            page.add(BookPosition(index, pos, layout.clip))
            columnStart = false

            // Is this the end of the book
            val bottom = layout.clip.bottom
            if (bottom + EPSILON >= layout.bounds.bottom)
                break       // Yes

            // Go to the next column and set the position
            // where the layout will be printed
            nextColumn(-bottom)
            // How much can we fit in this column
            layout.verticalClip(y, pageHeight)
            layout.handleOrphans(y + separatorSize, columnStart)
        }

        // Set the location to the end of the book
        location.x = x
        location.y = y + layout.marginBounds.bottom
    }

    private fun clear() {
        calculated.clear()
        calculated.add(layout.layoutBounds)
    }

    /**
     * Find overlapping fields and adjust their width
     */
    private fun resolveOverlaps(): Boolean {
        return false
    }

    /**
     * Calculate the positions for the fields in the layout
     */
    private fun calculateLayout() {
        layout.bounds.setEmpty()
        layout.marginBounds.setEmpty()
        for (dl in layout.columns) {
            calculateLayout(dl)
            if (!dl.bounds.isEmpty) {
                dl.bounds.offset(dl.margins.left, dl.margins.top)
                layout.bounds.union(dl.bounds)
                layout.marginBounds.union(
                    dl.bounds.left - dl.margins.left,
                    dl.bounds.top - dl.margins.top,
                    dl.bounds.right + dl.margins.right,
                    dl.bounds.bottom + dl.margins.bottom
                )
            }
        }
    }

    private fun alignBounds(min: Float, max: Float, alignMin: Float?, alignMax: Float?): Float {
        return if (alignMin == null) {
            if (alignMax == null) {
                if (rtl)
                    columnWidth - max
                else
                    -min
            } else
                alignMax - max
        } else if (alignMax == null)
            alignMin - min
        else
            (alignMin + alignMax - min - max) / 2.0f
    }

    /**
     * Calculate the positions for the fields in the layout
     */
    private fun calculateLayout(
        dl: BookLayout.LayoutBounds,
        target: LayoutDescription.AlignmentTarget = LayoutDescription.AlignmentTarget(dl.baseline, dl.rtl)
    ): BookLayout.LayoutBounds {
        if (calculated.contains(dl))
            return dl

        calculated.add(dl)
        dl.bounds.set(0.0f, 0.0f, dl.bounds.width(), dl.bounds.height())
        dl.alignment.asSequence().map { it.value.asSequence() }.flatten().forEach { calculateLayout(it.bounds, target) }
        target.clear(dl.baseline, dl.rtl)
        dl.alignment.forEach { it.key.calculateAlignemt(target, it.value.asSequence()) }

        alignBounds(dl.bounds.top - dl.margins.top, dl.bounds.bottom + dl.margins.bottom,
            target.top?.minus(dl.margins.top), target.bottom?.plus(dl.margins.bottom)).let {
            dl.bounds.top += it
            dl.bounds.bottom += it
        }
        alignBounds(dl.bounds.left - dl.margins.left, dl.bounds.right + dl.margins.right,
            target.left?.minus(dl.margins.left), target.right?.plus(dl.margins.right)).let {
            dl.bounds.left += it
            dl.bounds.right += it
        }

        return dl
    }
}