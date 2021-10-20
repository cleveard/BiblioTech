package com.github.cleveard.bibliotech.print

import android.graphics.Canvas
import android.graphics.RectF
import android.text.DynamicLayout
import android.text.Layout
import android.text.SpannableStringBuilder
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import kotlin.math.ceil

/** Class to hold absolute layout for a book */
data class BookLayout(
    /** The printer */
    val printer: PDFPrinter,
    /** The description of the book layout */
    val description: LayoutDescription,
    /** The column and their bounds after layout */
    val columns: List<DrawLayout>,
    /** The width of the print column */
    val columnWidth: Float,
    /** Bounding box of the book. This does not includes */
    val bounds: RectF,
    /** Bounding box of the book. This includes all margins in the layout */
    val marginBounds: RectF,
    /** Clip rectangle for the book */
    val clip: RectF,
    /** Right-to-left text */
    val rtl: Boolean
) {
    sealed interface LayoutBounds {
        val bounds: RectF
        val baseline: Float
        val rtl: Boolean
        /** alignment criteria for the field */
        val alignment: MutableMap<LayoutDescription.LayoutAlignmentType, List<LayoutDimension>>
        val margins: RectF
    }

    val layoutBounds: LayoutBounds = BookLayoutBounds()

    fun verticalClip(y: Float, columnHeight: Float): BookLayout {
        // Initialize the clip rectangles
        columns.forEach { it.clip.set(it.bounds) }

        // Calculate the layout clip rectangle
        val bookTop = y + bounds.top
        val bookBottom = y + bounds.bottom
        val clipTop = bookTop.coerceAtLeast(0.0f) - bookTop
        val clipBottom = bookBottom.coerceAtMost(columnHeight) -  bookTop
        clip.set(bounds.left, clipTop, bounds.right, clipBottom)

        // If the book is completely visible, then return the layout
        if (bookTop >= 0 && bookBottom <= columnHeight)
            return this

        // We need to do some clipping
        // Find the position of the highest field that crosses the clip boundary
        clip.top = columns.minOf {
            if (it.verticalClip(clipTop, clipBottom, false))
                it.clip.top
            else
                bounds.bottom
        }

        clip.bottom = columns.maxOf {
            if (it.verticalClip(clip.top, clipBottom - clipTop + clip.top, true))
                it.clip.bottom
            else
                bounds.top
        }

        return this
    }

    fun handleOrphans(y: Float, columnStart: Boolean) {
        // Don't bother with orphans if orphans aren't important
        // Or the entire layout isn't visible
        // Or the bottom of the layout is visible.
        if (printer.orphans < 1 || clip.isEmpty || clip.bottom + EPSILON >= bounds.bottom)
            return

        // Create a list of the top and bottom of all lines in the layout
        val lines: List<Pair<Float,Float>> = columns.asSequence()            // All of the fields
            .map {l ->
                // Map each field to a sequence of line vertical bounds
                // or null if the textLayout is null or doesn't have any lines
                if (l.bounds.isEmpty)
                    null
                else {
                    l.textLayout?.let { t ->
                        (0 until t.lineCount).asSequence()
                            .map {
                                Pair(
                                    l.bounds.top + printer.verticalPixelsToPoints(t.getLineTop(it)),
                                    l.bounds.top + printer.verticalPixelsToPoints(t.getLineTop(it + 1))
                                )
                            }
                    }
                }
            } // All of the text layouts from the fields
            .filterNotNull()                        // Filter out fields without a text layout
            .flatten()
            .toMutableList().apply {
                sortBy { it.first }
            }

        var end = lines.size
        while (true) {
            // Check for orphans at the start
            if (!columnStart && clip.top <= bounds.top && clip.bottom > bounds.top) {
                val visibleCount = lines.count { it.second > clip.top && it.first < clip.bottom }
                if (visibleCount < printer.orphans) {
                    // Not enough visible lines force next column
                    clip.top = bounds.top
                    clip.bottom = bounds.top
                    return
                }
            }

            // We return above when the bottom of the layout is being displayed
            // So when we get here we need to check the number of lines going
            // to the next column
            val nextCount = lines.count { it.first >= clip.bottom }
            if (nextCount <= 0 || nextCount >= printer.orphans)
                return

            // Need to move some lines to the next column
            while (--end >= 0) {
                // Find the last line above the current clip bottom
                if (lines[end].first < clip.bottom) {
                    // Clip to the top of that line
                    verticalClip(y, y + lines[end].first)
                    if (clip.isEmpty)
                        return
                    break
                }
            }
        }
    }
    /**
     * Set the content of the layout from a book
     * @param book The book to set the content from
     */
    fun setContent(book: BookAndAuthors) {
        for (d in columns)
            d.setContent(book)
    }

    /**
     * Draw the book on a canvas
     * @param canvas The canvas
     */
    fun draw(canvas: Canvas) {
        for (dl in columns)
            dl.draw(canvas)
    }

    private inner class BookLayoutBounds: LayoutBounds {
        override val bounds: RectF
            get() = this@BookLayout.bounds
        override val baseline: Float
            get() = 0.0f
        override val rtl: Boolean
            get() = this@BookLayout.rtl
        override val alignment = HashMap<LayoutDescription.LayoutAlignmentType, List<LayoutDimension>>()
        override val margins: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
    }

    sealed interface LayoutDimension {
        val bounds: LayoutBounds
        val dimension: Float
    }

    class StartDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = if (bounds.rtl) bounds.bounds.right else bounds.bounds.left
    }

    class EndDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = if (bounds.rtl) bounds.bounds.left else bounds.bounds.right
    }

    class HCenterDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = (bounds.bounds.left + bounds.bounds.right) / 2.0f
    }

    class TopDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.bounds.top
    }

    class BottomDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.bounds.bottom
    }

    class BaselineDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.bounds.top + bounds.baseline
    }

    class VCenterDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = (bounds.bounds.top + bounds.bounds.bottom) / 2.0f
    }

    abstract class DrawLayout(
        /** The printer */
        val printer: PDFPrinter,
        /** The field description */
        val description: LayoutDescription.FieldLayoutDescription,
    ): LayoutBounds {
        /** The bounds on the page */
        override var bounds: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** Text layout for text. Null for non-text fields */
        open val textLayout: Layout? = null
        /** Current clip rectangle */
        var clip: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** Baseline location of the first line */
        override var baseline: Float = 0.0f
        /** True means text is right to left */
        override var rtl: Boolean = false
        /** alignment criteria for the field */
        override val alignment: MutableMap<LayoutDescription.LayoutAlignmentType, List<LayoutDimension>> = LinkedHashMap()
        override val margins: RectF
            get() = description.margin

        /**
         * Draw the layout
         */
        abstract fun draw(canvas: Canvas)

        /**
         * Clip the top of the field to the print area
         * @param y The position of the book layout
         * @param columnHeight The height of the column
         * @param exclusive True to exclude parts of the field that
         *                  straddle the print area boundary. False
         *                  to include those parts
         * @return True if the field is visible
         */
        abstract fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean

        /**
         * Set the content of the layout from a book
         * @param book The book to set the content from
         */
        abstract fun setContent(book: BookAndAuthors)

        protected fun clip(y: Float, columnHeight: Float): Boolean {
            // Calculate the clip to the column height
            clip.set(bounds)
            clip.top = clip.top.coerceAtLeast(y)
            clip.bottom = clip.bottom.coerceAtMost(columnHeight)
            return !clip.isEmpty
        }
    }

    open class TextLayout(
        /** The printer */
        printer: PDFPrinter,
        /** The field description */
        description: LayoutDescription.FieldLayoutDescription,
        /** The column in the database */
        override var textLayout: Layout
    ): DrawLayout(printer, description) {
        override fun draw(canvas: Canvas) {
            if (!clip.isEmpty) {
                canvas.save()
                canvas.clipRect(clip)
                canvas.translate(bounds.left, bounds.top)
                textLayout.draw(canvas)
                canvas.restore()
            }
        }

        private fun findBoundary(boundary: Float, bottom: Boolean): Float {
            val line = textLayout.getLineForVertical(printer.verticalPointstoPixels(boundary))
            val t = printer.verticalPixelsToPoints(textLayout.getLineTop(line))
            val b = printer.verticalPixelsToPoints(textLayout.getLineTop(line + 1))
            return when {
                t >= boundary -> t
                b <= boundary -> b
                bottom -> b
                else -> t
            }
        }

        override fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean {
            if (!clip(y, columnHeight))
                return false

            val lineCount = textLayout.lineCount
            if (lineCount <= 0) {
                clip.top = bounds.top
                clip.bottom = bounds.top
                return false
            }

            clip.top = findBoundary(clip.top - bounds.top, exclusive) + bounds.top
            clip.bottom = findBoundary(clip.bottom - bounds.top, !exclusive) + bounds.top

            return !clip.isEmpty
        }

        protected val maxLineEnd: Float
            get() = printer.horizontalPixelsToPoints(ceil((0 until textLayout.lineCount).maxOf { textLayout.getLineMax(it) }).toInt())

        override fun setContent(book: BookAndAuthors) {
            // Just set the bounding rectangle
            bounds.set(0.0f, 0.0f, maxLineEnd, printer.verticalPixelsToPoints(textLayout.height))
            baseline = if (!bounds.isEmpty && textLayout.lineCount > 0)
                printer.verticalPixelsToPoints(textLayout.getLineBaseline(0))
            else {
                bounds.set(0.0f, 0.0f, 0.0f, 0.0f)
                0.0f
            }
        }
    }

    open class ColumnLayout(
        /** The printer */
        printer: PDFPrinter,
        /** The field description */
        description: LayoutDescription.FieldLayoutDescription,
        val column: Column,
        val content: SpannableStringBuilder,
        /** The column in the database */
        layout: DynamicLayout
    ): TextLayout(printer, description, layout) {
        override fun setContent(book: BookAndAuthors) {
            val value = column.desc.getDisplayValue(book)
            content.replace(0, content.length, value)
            bounds.set(0.0f, 0.0f, maxLineEnd, printer.verticalPixelsToPoints(textLayout.height))
            baseline = if (!bounds.isEmpty && textLayout.lineCount > 0)
                printer.verticalPixelsToPoints(textLayout.getLineBaseline(0))
            else {
                bounds.set(0.0f, 0.0f, 0.0f, 0.0f)
                0.0f
            }
        }
    }
}