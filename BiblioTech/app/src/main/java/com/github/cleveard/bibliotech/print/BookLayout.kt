package com.github.cleveard.bibliotech.print

import android.graphics.Canvas
import android.graphics.RectF
import android.text.DynamicLayout
import android.text.Layout
import android.text.SpannableStringBuilder
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column

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
        // If the book is completely visible, then return the height
        val bookTop = y + bounds.top
        val bookBottom = y + bounds.bottom
        val clipTop = bookTop.coerceAtLeast(0.0f) - bookTop
        val clipBottom = bookBottom.coerceAtMost(columnHeight) -  bookTop
        clip.set(bounds.left, clipTop, bounds.right, clipBottom)
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
        /** The field description */
        val description: LayoutDescription.FieldLayoutDescription
    ): LayoutBounds {
        /** The bounds on the page */
        override var bounds: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
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
    }

    open class TextLayout(
        /** The printer */
        val printer: PDFPrinter,
        /** The field description */
        description: LayoutDescription.FieldLayoutDescription,
        /** The column in the database */
        val layout: Layout
    ): DrawLayout(description) {
        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.clipOutRect(clip)
            canvas.translate(bounds.left, bounds.top)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun clip(y: Float, columnHeight: Float): Boolean {
            // Calculate the clip to the column height
            clip.set(bounds)
            clip.top = clip.top.coerceAtLeast(y)
            clip.bottom = clip.bottom.coerceAtMost(columnHeight)
            return !clip.isEmpty
        }

        private fun findBoundary(boundary: Float, bottom: Boolean): Float {
            val lineCount = layout.lineCount
            if (lineCount <= 0)
                return bounds.bottom
            var line = 0

            var l1 = printer.verticalPixelsToPoints(layout.getLineTop(line))
            var l2: Float
            // Need to adjust top clip, to fall on a line boundary
            while (++line < lineCount) {
                l2 = printer.verticalPixelsToPoints(layout.getLineTop(line))
                if (l1 <= boundary && l2 > boundary) {
                    return if (bottom) l2 else l1
                }
                l1 = l2
            }
            return if (bottom) bounds.bottom else l1
        }

        override fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean {
            if (!clip(y, columnHeight))
                return false

            if (clip.top > bounds.top) {
                clip.top = findBoundary(clip.top - bounds.top, exclusive) + bounds.top
            }

            if (clip.bottom < bounds.bottom) {
                clip.bottom = findBoundary(clip.bottom - bounds.top, !exclusive) + bounds.top
            }
            return !clip.isEmpty
        }

        override fun setContent(book: BookAndAuthors) {
            // Just set the bounding rectangle
            bounds.set(0.0f, 0.0f, printer.horizontalPixelsToPoints(layout.width), printer.verticalPixelsToPoints(layout.height))
            baseline = if (layout.lineCount > 0) printer.verticalPixelsToPoints(layout.getLineBaseline(0)) else 0.0f
            clip.set(bounds)
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
            content.replace(0, content.length, column.desc.getValue(book))
            bounds.set(0.0f, 0.0f, printer.horizontalPixelsToPoints(layout.width), printer.verticalPixelsToPoints(layout.height))
            baseline = if (layout.lineCount > 0) printer.verticalPixelsToPoints(layout.getLineBaseline(0)) else 0.0f
            clip.set(bounds)
        }
    }
}