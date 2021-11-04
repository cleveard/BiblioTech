package com.github.cleveard.bibliotech.print

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.github.cleveard.bibliotech.db.Column
import kotlin.collections.HashMap

/**
 * Class to hold the description of a book layout
 * @param inColumns The list of field descriptions for the layout
 * @param inHeaders The list of header fields for the layout. Null means no headers
 * @param horizontalSeparation The distance to separate columns
 * @param verticalSeparation The distance to separate books in the same column
 */
data class LayoutDescription(
    /** Layouts for the columns of the book to print */
    val inColumns: List<FieldLayoutDescription>,
    /** Columns to print as book header. Set to null to leave out the header */
    val inHeaders: List<Column>?
) {
    /**
     *  Create the book layout from this description
     *  @param printer The printer being used
     *  @param columnWidth The width of a column
     *  @param visible The set of visible fields
     *  @param rtl True to create a layout for right-to-left
     */
    fun createLayout(printer: PDFPrinter, columnWidth: Float, visible: Set<String>, rtl: Boolean): BookLayout {
        // Create the book layout
        val bookLayout = BookLayout(
            // The printer
            printer,
            // The layout description
            this,
            // The fields in the layout
            inColumns.map {
                if (visible.contains(it.visibleFlag))
                    it.createLayout(printer, columnWidth)
                else
                    BookLayout.EmptyLayout(printer, it)
            },
            // The column width
            columnWidth,
            // The bounds rectangle excluding margins
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            // The bounds rectangle including margins
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            // The clip rectangle
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            // Right-to-left flag
            rtl
        )

        // Map to map field descriptor to a field
        val map = HashMap<FieldLayoutDescription?, BookLayout.LayoutBounds>()
        // Null maps to the layout bounds for the entire layout
        map[null] = bookLayout.layoutBounds
        // Fill in the map for the fields in the layout
        for (dl in bookLayout.columns)
            map[dl.description] = dl

        // Create the layout alignment dependencies in the layout
        for (dl in bookLayout.columns) {
            // Get the alignment for the field in a local variable
            val align = dl.alignment
            // For each layout alignment description
            for (entry in dl.description.layoutAlignment) {
                // Take the alignment dependencies
                entry.dimensions.asSequence().map {
                    // A map them to a layout dimension on the field
                    map[it.alignTo]?.let {b -> it.align.createAlignment(b) }
                }.filterNotNull().toList().also {list ->
                    // If the list isn't empty add it to the field alignment
                    if (list.isNotEmpty())
                        align[entry.align] = list
                }
            }
        }

        // return the layout
        return bookLayout
    }

    /** Interface for alignment types */
    sealed interface LayoutAlignmentType {
        /** This is a horizontal alignment */
        val horizontal: Boolean

        /**
         * Create an object to return a fields current dimension
         * @param bounds The layout bounds for the field
         */
        fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension

        /**
         * Calculate the alignment for a dimensions type
         * @param target The object where the result is stored
         * @param sources The dimensions used to calculate the alignment
         * The alignment calculation depends on what is being aligned
         * Vertical dimensions are aligned like this:
         *    Top - align to maximum
         *    Bottom - align to minimum
         *    Baseline - align to maximum
         *    Center - align to midpoint of minimum and maximum
         * Horizontal dimensions are aligned like this:
         *    Start, LTR - align left to maximum
         *    Start, RTL - align right to minimum
         *    End, LTR - align right to minimum
         *    End, RTL - align left to maximum
         *    Center - align to midpoint of minimum and maximum
         */
        fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>)
    }

    /** Interface to a dimension description */
    sealed interface LayoutDimensionDescription {
        /** The dimension type */
        val align: LayoutAlignmentType
        /** The field with the dimension. Null is the field parent */
        val alignTo: FieldLayoutDescription?
    }

    /**
     * Object to collect alignment information for a field
     * @param baseline The offset of the baseline from the top of the field
     * @param rtl True for right-to-left layouts
     * @param top The resolved top alignment. Null means it wasn't specified
     * @param left The resolved left alignment. Null means it wasn't specified
     * @param right The resolved right alignment. Null means it wasn't specified
     * @param bottom The resolved bottom alignment. Null means it wasn't specified
     */
    data class AlignmentTarget(
        /** The offset of the baseline from the top of the field */
        var baseline: Float = 0.0f,
        /** True for right-to-left layouts */
        var rtl: Boolean = false,
        /** The resolved top alignment. Null means it wasn't specified */
        var top: Float? = null,
        /** The resolved start alignment. Null means it wasn't specified */
        var left: Float? = null,
        /** The resolved end alignment. Null means it wasn't specified */
        var right: Float? = null,
        /** The resolved bottom alignment. Null means it wasn't specified */
        var bottom: Float? = null,
        /** The resolved horizontal center alignment. Null means it wasn't specified */
        var hCenter: Float? = null,
        /** The resolved vertical center alignment. Null means it wasn't specified */
        var vCenter: Float? = null
    ) {
        /**
         * Clear the collected result
         * @param baseline New baseline offset
         * @param rtl New right-to-left flag
         */
        fun clear(baseline: Float = 0.0f, rtl: Boolean = false) {
            this.baseline = baseline
            this.rtl = rtl
            top = null
            left = null
            right = null
            bottom = null
            hCenter = null
            vCenter = null
        }
    }

    /**
     * Vertical alignment dimension description for layout
     * @param align The dimension we want to align to
     * @param alignTo The field we want to align to
     */
    data class VerticalLayoutDimension(
        /** The alignmentType */
        override val align: Type,
        /** Field aligned to */
        override val alignTo: FieldLayoutDescription?
    ): LayoutDimensionDescription {
        /** The different vertical dimensions we can align to */
        enum class Type: LayoutAlignmentType {
            /** Align to top */
            Top {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a top dimension
                    return BookLayout.TopDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align the top to the max of the source dimensions
                    target.top = sources.maxOf { it.dimension }
                }
            },
            /** Align to bottom */
            Bottom {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a bottom dimension
                    return BookLayout.BottomDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align the bottom to the min of the source dimensions
                    target.bottom = sources.minOf { it.dimension }
                }
            },
            /** Align to baseline */
            BaseLine {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a baseline dimension
                    return BookLayout.BaselineDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align the top to the max of the source dimensions offset by the baseline
                    target.top = sources.maxOf { it.dimension } - target.baseline
                }
            },
            /** Align to center */
            Center {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a center dimension
                    return BookLayout.VCenterDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align center to center of bounds of sources
                    target.vCenter = (sources.minOf { it.dimension } + sources.maxOf { it.dimension }) / 2.0f
                }
            };

            /** @inheritDoc */
            override val horizontal: Boolean = false
        }
    }

    /** Interface for alignment specification */
    sealed interface LayoutAlignment {
        /** The dimension we are aligning */
        val align: LayoutAlignmentType
        /** Dimensions used to calculate the alignment */
        val dimensions: List<LayoutDimensionDescription>
    }

    /**
     * Description for vertical alignment
     * @param align The dimension this description aligns
     * @param dimensions The dimensions used to calculate the alignment
     */
    class VerticalLayoutAlignment(
        /** The dimension this description aligns */
        override val align: VerticalLayoutDimension.Type,
        /** The dimensions used to calculate the alignment */
        override val dimensions: List<VerticalLayoutDimension>
    ): LayoutAlignment {
        /**
         * @inheritDoc
         * Override to prevent duplicates of align in a set
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as VerticalLayoutAlignment
            return align == other.align
        }

        /**
         * @inheritDoc
         * Override to prevent duplicates of align in a set
         */
        override fun hashCode(): Int {
            return align.hashCode()
        }
    }

    /**
     * Horizontal alignment dimension description for layout
     * @param align The dimension we want to align to
     * @param alignTo The field we want to align to
     */
    data class HorizontalLayoutDimension(
        /** The alignmentType */
        override val align: Type,
        /** Field aligned to */
        override val alignTo: FieldLayoutDescription?
    ): LayoutDimensionDescription {
        /** The different horizontal dimensions we can align to */
        enum class Type: LayoutAlignmentType {
            /** Align to start */
            Start {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a start dimension
                    return BookLayout.StartDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // If right-to-left, align right to min of dimensions
                    // Otherwise align left to max of dimensions
                    return if (target.rtl)
                        target.right = sources.minOf { it.dimension }
                    else
                        target.left = sources.maxOf { it.dimension }
                }
            },
            /** Align to end */
            End {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return an end dimension
                    return BookLayout.EndDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // If right-to-left, align left to max of dimensions
                    // Otherwise align right to min of dimensions
                    return if (target.rtl)
                        target.left = sources.maxOf { it.dimension }
                    else
                        target.right = sources.minOf { it.dimension }
                }
            },
            /** Align to center */
            Center {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a center dimension
                    return BookLayout.HCenterDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align center to center of bounds of sources
                    target.hCenter = (sources.minOf { it.dimension } + sources.maxOf { it.dimension }) / 2.0f
                }
            };

            /** @inheritDoc */
            override val horizontal: Boolean = true
        }
    }

    /**
     * Description for horizontal alignment
     * @param align The dimension this description aligns
     * @param dimensions The dimensions used to calculate the alignment
     */
    class HorizontalLayoutAlignment(
        /** The dimension this description aligns */
        override val align: HorizontalLayoutDimension.Type,
        /** The dimensions used to calculate the alignment */
        override val dimensions: List<HorizontalLayoutDimension>
    ): LayoutAlignment {
        /**
         * @inheritDoc
         * Override to prevent duplicates of align in a set
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HorizontalLayoutAlignment
            return align == other.align
        }

        /**
         * @inheritDoc
         * Override to prevent duplicates of align in a set
         */
        override fun hashCode(): Int {
            return align.hashCode()
        }
    }

    /**
     * Layout description for a column from the database
     * @param margin Additional padding in points
     * @param minSize The minimum size in points used by the field
     * @param maxSize The maximum size in points used by the field
     */
    abstract class FieldLayoutDescription(
        /** Flag string used to control visibility for the field */
        val visibleFlag: String,
        /** Additional padding in points */
        val margin: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f),
        /** The minimum size in points used by the field */
        val minSize: PointF = PointF(0.0f, 0.0f),
        /** The maximum size in points used by the field */
        val maxSize: PointF = PointF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    ) {
        /** The layout alignment for the field */
        lateinit var layoutAlignment: Set<LayoutAlignment>

        /**
         * Get/Fill the layout for a field
         * @param printer The printer we are using
         * @param columnWidth The width of a column
         * @param paint The paint used to draw the field
         */
        abstract fun createLayout(printer: PDFPrinter, columnWidth: Float, paint: TextPaint = printer.basePaint): BookLayout.DrawLayout
    }

    /**
     * Static text field
     * @param text The static text to display
     */
    class TextFieldLayoutDescription(visibleFlag: String, private val text: String): FieldLayoutDescription(visibleFlag) {
        /** @inheritDoc */
        override fun createLayout(printer: PDFPrinter, columnWidth: Float, paint: TextPaint): BookLayout.DrawLayout {
            // Return a text field with the StaticLayout
            return BookLayout.TextLayout(printer, columnWidth, this, text, paint).apply {
                setContent()
            }
        }
    }

    /**
     * Text field from a book database column
     * @param column The database column description
     */
    class ColumnTextFieldLayoutDescription(private val column: Column): FieldLayoutDescription(column.name) {
        /** @inheritDoc */
        override fun createLayout(printer: PDFPrinter, columnWidth: Float, paint: TextPaint): BookLayout.DrawLayout {
            // Create the field with the column description, the content holder and the DynamicLayout
            return BookLayout.ColumnTextLayout(printer, columnWidth, this, column, paint)
        }
    }

    /**
     * Text field from a book database column
     * @param column The database column description
     */
    class TitleTextFieldLayoutDescription(): FieldLayoutDescription(Column.TITLE.name) {
        /** @inheritDoc */
        override fun createLayout(printer: PDFPrinter, columnWidth: Float, paint: TextPaint): BookLayout.DrawLayout {
            // Create the field with the column description, the content holder and the DynamicLayout
            return BookLayout.ColumnTextLayout(printer, columnWidth, this, Column.TITLE,
                TextPaint(paint).apply {
                    textSize = when(textSize) {
                        8.0f -> 10.0f
                        10.0f -> 12.5f
                        12.5f -> 16.0f
                        16.0f -> 20.0f
                        20.0f -> 24.0f
                        else -> textSize
                    }
                    typeface = Typeface.create(typeface, Typeface.BOLD)
                }
            )
        }
    }

    /**
     * Text field from a book database column
     * @param large True to use the large thumbnail
     */
    class ColumnBitmapFieldLayoutDescription(val large: Boolean, size: PointF): FieldLayoutDescription(
        if (large) "LargeThumb" else "SmallThumb"
    ) {
        init {
            maxSize.set(size)
        }

        /** @inheritDoc */
        override fun createLayout(printer: PDFPrinter, columnWidth: Float, paint: TextPaint): BookLayout.DrawLayout {
            // Create the field with the column description, the content holder and the DynamicLayout
            return BookLayout.ColumnBitmapLayout(printer, this, columnWidth, large, paint)
        }
    }
}
