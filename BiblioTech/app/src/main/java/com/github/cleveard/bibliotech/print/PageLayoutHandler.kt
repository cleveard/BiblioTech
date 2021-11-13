package com.github.cleveard.bibliotech.print

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.github.cleveard.bibliotech.db.BookAndAuthors
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

const val EPSILON = 1.0e-3

/**
 * The class that calculates the layouts of books
 * @param printer The PDF printer
 * @param pageDrawBounds The bounds of the drawing area on the page
 * @param rtl True to layout right-to-left
 */
class PageLayoutHandler(
    /** The PDF printer */
    private val printer: PDFPrinter,
    pageDrawBounds: RectF,
    /** True to layout right-to-left */
    private val rtl: Boolean = false
) {

    /**
     * Base class for drawables on a page
     * @param position The position of the drawable
     */
    abstract class DrawPosition(
        /** The position of the drawable */
        val position: PointF
    ) {
        /**
         * Draw the drawable
         * @param canvas The canvas to draw on
         * @param bookList The list of books to draw
         * @param handler A PageLayoutHandler used to layout out the book for drawing
         */
        abstract suspend fun draw(canvas: Canvas, bookList: List<BookAndAuthors>, handler: PageLayoutHandler)
    }

    /**
     * Drawable for a book
     * @param bookIndex The index of the book
     * @param position The position of the drawable
     * @param clip The clip rectangle for the book
     */
    class BookPosition(
        /** The index of the book */
        private val bookIndex: Int,
        position: PointF,
        clip: RectF
    ): DrawPosition(position) {
        /** The clip rectangle */
        private val clipRect: RectF = RectF(clip)

        /** @inheritDoc */
        override suspend fun draw(canvas: Canvas, bookList: List<BookAndAuthors>, handler: PageLayoutHandler) {
            // Save the current canvas state
            canvas.save()
            // Set the clip rectangle and location
            canvas.translate(position.x, position.y)
            canvas.clipRect(clipRect)
            // Print the book
            handler.layoutBook(bookList[bookIndex])
                .verticalClip(position.y, handler.pageHeight)
                .draw(canvas)
            // Restore the canvas
            canvas.restore()
        }
    }

    /**
     * Drawable for the separator line
     * @param paint The paint to use for drawing
     * @param position The position of the drawable
     */
    class SeparatorPosition(
        /** The paint used for drawing */
        private val paint: Paint,
        position: PointF
    ): DrawPosition(position) {
        /** @inheritDoc */
        override suspend fun draw(canvas: Canvas, bookList: List<BookAndAuthors>, handler: PageLayoutHandler) {
            // Save the current canvas state
            canvas.save()
            // Set the location
            canvas.translate(position.x, position.y)
            // Print the line
            paint.style = Paint.Style.FILL
            canvas.drawRect(0.0f, 0.0f, handler.columnWidth, handler.printer.separatorLineWidth, paint)
            // Restore the canvas
            canvas.restore()
        }
    }

    /**
     * Class holds drawables on a page
     * @param books The drawables
     */
    data class Page(
        /** List of drawables on the page */
        val books: List<DrawPosition>
    )

    /** The list of laid out pages */
    private val pages = ArrayList<Page>()
    /** The page we are currently laying out */
    private var page = ArrayList<DrawPosition>()
    /** The drawable height of the page */
    val pageHeight = pageDrawBounds.height()
    /** The drawable width of the page */
    private val pageWidth = pageDrawBounds.width()
    /** The horizontal distance from on column to the next */
    private val columnStride = (pageWidth + printer.horizontalSeparation) / printer.numberOfColumns
    /** The width of a column */
    val columnWidth =  columnStride - printer.horizontalSeparation
    /** The current location on the page we are currently laying out */
    private var location: PointF = PointF(0.0f, 0.0f)
    /** True to indicate we are at the top of a column */
    private var columnStart: Boolean = true
    /** The set of bounds whose position has been calculated */
    private val calculated: HashSet<BookLayout.LayoutBounds> = HashSet()
    /** The last book we laid out */
    private var laidOutBook: BookAndAuthors? = null
    /** The layout of the last book we laid out. This layout is shared by all books */
    private val layout: BookLayout by lazy {
        printer.getLayout(columnWidth)
            .createLayout(printer, columnWidth, printer.visibleFields, rtl)
    }

    /**
     * Add page and start next one
     */
    private fun nextPage() {
        // Add current page to the list of pages, and create a new one
        page.let {
            page = ArrayList()
            pages.add(Page(it))
        }
        // Show we are at the top of a column
        columnStart = true
    }

    /**
     * Layout pages for a list of books
     * @param books The list of books
     * @return The list of laid out pages
     */
    suspend fun layoutPages(books: List<BookAndAuthors>): List<Page> {
        // For each book in the list place its layout on a page
        for (i in books.indices) {
            // Check for canceled job
            coroutineContext.ensureActive()
            // Place the book on the page
            placeBook(i, layoutBook(books[i]))
        }

        // Add the last page to the list of pages
        if (page.size > 0)
            nextPage()
        // Return the list of pages
        return pages
    }

    /**
     * Layout a single book
     * @param book The book to layout
     * @return The layout for the book. All layouts share a single object
     */
    suspend fun layoutBook(book: BookAndAuthors): BookLayout {
        // If we ask for the book again, return the previous layout
        if (book == laidOutBook)
            return layout
        // Remember the book we are laid out
        laidOutBook = book
        // Set the book contents to the layout
        layout.setContent(book)

        while (true) {
            // Calculate the layout. Stop when we don't need to recalculate
            // and there are no overlaps
            if (!calculateLayout() && !resolveOverlaps())
                break
        }

        // Return the layout
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
            printer.verticalSeparation + printer.separatorLineWidth

        /**
         * Bump to the next column and next page if needed
         * @param newY The y location of the layout in the next column
         */
        fun nextColumn(newY: Float) {
            // Bump horizontal column location
            x += columnStride
            // Are we past the page width
            if (x + EPSILON >= pageWidth) {
                // Yes, go to the next page
                nextPage()
                // Horizontal location of first column
                x = 0.0f
            }
            // We are at the start of a column
            columnStart = true
            // Set the new y location. This can vary depending
            // on how the book layout is clipped
            y = newY
        }

        // Clip the book to the column height, and adjust for orphan lines
        layout.verticalClip(y + separatorSize, pageHeight)
        layout.handleOrphans(y + separatorSize, columnStart)
        // No space for anything, go to next column
        if (layout.clip.isEmpty) {
            // Go to next column
            nextColumn(0.0f)
            separatorSize = -layout.marginBounds.top
            // Clip the height for the new column, and adjust for orphan lines
            layout.verticalClip(y + separatorSize, pageHeight)
            layout.handleOrphans(y + separatorSize, columnStart)
        }

        // If we aren't at the top of a column and we are drawing separator lines,
        // Then add the separator line drawable
        if (!columnStart && printer.separatorLineWidth > 0) {
            page.add(
                SeparatorPosition(
                    printer.basePaint,
                    PointF(x, y + printer.verticalSeparation / 2.0f)
                )
            )
        }
        // Adjust y to the top of the book
        y += separatorSize

        // Break the book layout over the columns and pages
        while (true) {
            // If we are at the top of a column and we aren't drawing
            // at the top of the column, then adjust so we draw at the
            // top. This can happen when field in the layout has a top
            // margin.
            if (columnStart && layout.clip.top + y > 0.0f) {
                // Adjust the y location
                y = -layout.clip.top
                // We need to redo the vertical clipping and orphan line handling
                layout.verticalClip(y, pageHeight)
                layout.handleOrphans(y + separatorSize, columnStart)
            }

            // Get the current print position and clip rectangle
            val pos = PointF(x, y)
            // Add the book drawable to the page
            page.add(BookPosition(index, pos, layout.clip))
            // Not at the top of the column any longer
            columnStart = false

            // Is this the end of the book
            val bottom = layout.clip.bottom
            if (bottom + EPSILON >= layout.bounds.bottom)
                break       // Yes

            // Go to the next column and set the position
            // where the layout will be printed. Setting
            // the new y location to -bottom, pushes the
            // layout up to print the stuff below what we
            // just printed.
            nextColumn(-bottom)
            // Clip the remainder of the book to the column
            // and adjust for orphan lines
            layout.verticalClip(y, pageHeight)
            layout.handleOrphans(y + separatorSize, columnStart)
        }

        // Set the location to the end of the book
        // Make sure we include margins at the bottom
        location.x = x
        location.y = y + layout.marginBounds.bottom
    }

    /**
     * Clear layout variables at the start of laying out a book
     */
    private fun clear() {
        // Nothing has been calculated
        calculated.clear()
        // Add the parent layout as calculated
        calculated.add(layout.layoutBounds)
    }

    /**
     * Find overlapping fields and adjust their width
     */
    private fun resolveOverlaps(): Boolean {
        // Not implemented yet. It isn't needed for straight
        // forward layouts.
        return false
    }

    /**
     * Calculate the positions for the fields in the layout
     */
    private fun calculateLayout(): Boolean {
        // Set the bounds of the layout as empty
        layout.bounds.set(0.0f, 0.0f, columnWidth, 0.0f)
        layout.marginBounds.setEmpty()

        var recalculate = false
        // Work area for keeping alignment information
        val target = LayoutDescription.AlignmentTarget()
        // Move all of the bounds to (0,0)
        for (dl in layout.columns) {
            dl.bounds.set(0.0f, 0.0f, dl.bounds.width(), dl.bounds.height())
        }

        // Clear the layout working variables
        clear()
        // Horizontally position each field in the layout
        for (dl in layout.columns) {
            // Calculate the layout for a field in the book
            recalculate = calculateLayout(dl, true, target) || recalculate
        }
        if (recalculate)
            return true

        // Clear the layout working variables
        clear()
        // Vertically position each field in the layout
        for (dl in layout.columns) {
            // Calculate the layout for a field in the book
            recalculate = calculateLayout(dl, false, target) || recalculate
            // If the bounds are empty, don't include margins
            if (!dl.bounds.isEmpty) {
                // Add the field bounds to the layout bounds
                layout.bounds.union(dl.bounds.left + dl.position.x, dl.bounds.top + dl.position.y,
                    dl.bounds.right + dl.position.x, dl.bounds.bottom + dl.position.y)
                // Add the field with margin bounds to the margin bounds
                layout.marginBounds.union(
                    dl.bounds.left - if (dl.rtl) dl.margins.right else dl.margins.left + dl.position.x,
                    dl.bounds.top - dl.margins.top + dl.position.y,
                    dl.bounds.right + if (dl.rtl) dl.margins.left else dl.margins.right + dl.position.x,
                    dl.bounds.bottom + dl.margins.bottom + dl.position.y
                )
            }
        }

        return recalculate
    }

    /**
     * Calculate the offset to align a field
     * @param dl The layout bounds being aligned
     * @param minSpec The specified top/left of the field. Null means it wasn't specified
     * @param maxSpec The specified bottom/right of the field. Null means it wasn't specified
     * @param centerSpec The specified center of the field. Null means it wasn't specified
     * @param rtl True to position unspecified alignment at the right side of the layout.
     *                    False to position unspecified alignment at the top or left side of the layout
     * @return True if the layout needs to be recalculated
     */
    private fun alignHorizontalBounds(dl: BookLayout.LayoutBounds, minSpec: Float?, maxSpec: Float?, centerSpec: Float?, rtl: Boolean): Boolean {
        val min: Float
        val max: Float

        if (rtl) {
            // Set min to specified value, or the value of the parent
            max = when {
                centerSpec != null -> centerSpec + dl.bounds.width() / 2.0f
                maxSpec != null -> maxSpec
                minSpec != null -> (minSpec + dl.width).coerceAtMost(columnWidth)
                else -> columnWidth
            } - dl.margins.left
            // Set max to specified value, or the value of the parent
            min = when {
                centerSpec != null -> max - dl.width
                minSpec != null -> minSpec
                maxSpec != null -> (max - dl.width).coerceAtLeast(0.0f)
                else -> 0.0f
            } + dl.margins.right
            // Calculate the position to move bounds.right to max
            dl.position.x = max - dl.bounds.right
            // If the bounds are empty to include the margins
            if (dl.bounds.isEmpty)
                dl.position.x += dl.margins.left
        } else {
            // Set min to specified value, or the value of the parent
            min = when {
                centerSpec != null -> centerSpec - dl.bounds.width() / 2.0f
                minSpec != null -> minSpec
                maxSpec != null -> (maxSpec - dl.width).coerceAtLeast(0.0f)
                else -> 0.0f
            } + dl.margins.left
            // Set max to specified value, or the value of the parent
            max = when {
                centerSpec != null -> min + dl.width
                maxSpec != null -> maxSpec
                minSpec != null -> min + dl.width
                else -> columnWidth
            } - dl.margins.right
            // Calculate the position to move bounds.left to min
            dl.position.x = min - dl.bounds.left
            // If the bounds are empty to include the margins
            if (dl.bounds.isEmpty)
                dl.position.x -= dl.margins.left
        }

        // Don't change the width unless both min and max spec are given
        if (minSpec == null || maxSpec == null)
            return false

        // Calculate the width
        var width = max - min
        dl.description?.let { width = width.coerceAtLeast(it.minSize.x).coerceAtMost(it.maxSize.x) }
        return if (abs(width - dl.width) >= EPSILON) {
            dl.width = width
            true
        } else
            false
    }

    /**
     * Calculate the offset to align a field
     * @param dl The layout bounds being aligned
     * @param minSpec The specified top/left of the field. Null means it wasn't specified
     * @param maxSpec The specified bottom/right of the field. Null means it wasn't specified
     * @param centerSpec The specified center of the field. Null means it wasn't specified
     * @return True if the layout should be recalculated
     */
    private fun alignVerticalBounds(dl: BookLayout.LayoutBounds, minSpec: Float?, maxSpec: Float?, centerSpec: Float?): Boolean {
        val min = when {
            centerSpec != null -> centerSpec - dl.bounds.height() / 2.0f
            minSpec != null -> minSpec
            maxSpec != null -> (maxSpec - dl.height).coerceAtLeast(0.0f)
            else -> 0.0f
        } + dl.margins.top

        // Calculate the adjustment to move the top to min. If the bounds are empty
        // make sure the margins are not included
        dl.position.y = (if (dl.bounds.isEmpty) min - dl.margins.top else min) - dl.bounds.top

        // Don't change the height unless both min and max spec are given
        if (minSpec == null || maxSpec == null)
            return false

        // Calculate the height
        var height = maxSpec - dl.margins.bottom - minSpec + dl.margins.top
        dl.description?.let { height = height.coerceAtLeast(it.minSize.y).coerceAtMost(it.maxSize.y) }
        return if (abs(height - dl.height) >= EPSILON) {
            dl.height = height
            true
        } else
            false
    }

    /**
     * Calculate the positions for the fields in the layout
     * @param dl The layout bounds for a field
     * @param target Combined result of the layout criteria.
     *               Initialized at the top level, and reused
     *               when called recursively
     * @return The input layout bounds
     * This method is called recursively to calculate the positions
     * of fields the current fields depends on
     */
    private fun calculateLayout(
        dl: BookLayout.LayoutBounds,
        horizontal: Boolean,
        target: LayoutDescription.AlignmentTarget
    ): Boolean {
        // If the layout has already been calculated, then just return its bounds
        if (calculated.contains(dl))
            return false

        var recalculate = false
        // Mark the layout as calculated. This is used to prevent infinite cycles
        calculated.add(dl)
        // Calculate all of the dependencies
        dl.alignment.asSequence()
            .filter { it.key.horizontal == horizontal }
            .map { it.value.asSequence() }
            .flatten()
            .forEach {
                recalculate = calculateLayout(it.bounds, horizontal, target) || recalculate
            }
        // Clear the combined result for this field
        target.clear(dl.baseline, dl.rtl)
        // Calculate all of the alignments and store the results in target
        dl.alignment.forEach {
            if (it.key.horizontal == horizontal)
                it.key.calculateAlignment(target, it.value.asSequence())
        }

        // Align the field and return whether the alignment needs recalculating
        return if (horizontal)
            alignHorizontalBounds(dl, target.left, target.right, target.hCenter, rtl) || recalculate
        else
            alignVerticalBounds(dl, target.top, target.bottom, target.vCenter) || recalculate
    }
}
