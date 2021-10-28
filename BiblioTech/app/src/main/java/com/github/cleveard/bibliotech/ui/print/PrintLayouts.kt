package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.graphics.PointF
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.print.BookLayout
import com.github.cleveard.bibliotech.print.LayoutDescription

class PrintLayouts(context: Context) {
    private val authorsBy: String = context.resources.getText(R.string.authors_by).toString()
    private val tags: String = context.resources.getString(R.string.tags)

    /**
     * Get a layout based on the column width
     * @param columnWidth The width of a print column
     */
    fun getLayout(columnWidth: Float): LayoutDescription {
        return (layouts.firstOrNull { columnWidth <= it.first }?: layouts.last()).second
    }

    private val layouts = arrayOf(
        Pair(
            /** A narrow layout */
            Float.POSITIVE_INFINITY, LayoutDescription(
                listOf(
                    LayoutDescription.ColumnBitmapFieldLayoutDescription(false, PointF(16.0f, BookLayout.MAX_HEIGHT)).apply {
                        margin.right = 4.5f
                    },
                    LayoutDescription.ColumnBitmapFieldLayoutDescription(true, PointF(72.0f, 112.0f)).apply {
                        margin.left = 4.5f
                    },
                    LayoutDescription.ColumnTextFieldLayoutDescription(Column.TITLE),
                    LayoutDescription.ColumnTextFieldLayoutDescription(Column.SUBTITLE).apply {
                        margin.top = 1.0f
                    },
                    LayoutDescription.TextFieldLayoutDescription(Column.FIRST_NAME.name, authorsBy).apply {
                        margin.top = 1.0f
                        margin.right = 4.5f
                    },
                    LayoutDescription.ColumnTextFieldLayoutDescription(Column.FIRST_NAME),
                    LayoutDescription.TextFieldLayoutDescription(Column.TAGS.name, tags).apply {
                        margin.top = 1.0f
                        margin.right = 4.5f
                    },
                    LayoutDescription.ColumnTextFieldLayoutDescription(Column.TAGS)
                ),
                emptyList()
            ).apply {
                val smallThumb = inColumns[0]
                val largeThumb = inColumns[1]
                val title = inColumns[2]
                val subtitle = inColumns[3]
                val by = inColumns[4]
                val authors = inColumns[5]
                val tagsLabel = inColumns[6]
                val tags = inColumns[7]
                smallThumb.layoutAlignment = setOf(
                    // Small Thumbnail is at the top
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Top,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Top, null)
                        )
                    ),
                    // Small Thumbnail bottom is at the bottom of the authors
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Bottom,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, authors)
                        )
                    ),
                    // Small Thumbnail is at the start
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, null)
                        )
                    )
                )
                largeThumb.layoutAlignment = setOf(
                    // Large Thumbnail is at the top
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Top,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Top, null)
                        )
                    ),
                    // Large Thumbnail is at the end
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, null)
                        )
                    )
                )
                title.layoutAlignment = setOf(
                    // Title is at the top
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Top,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Top, null)
                        )
                    ),
                    // Title is to the right of the small thumbnail
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, smallThumb)
                        )
                    ),
                    // Title is to the left of the large thumbnail
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, largeThumb)
                        )
                    )
                )
                subtitle.layoutAlignment = setOf(
                    // Subtitle is below the title
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Top,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, title)
                        )
                    ),
                    // Subtitle start is aligned with title start
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, title)
                        )
                    ),
                    // Subtitle is to the left of the large thumbnail
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, largeThumb)
                        )
                    )
                )
                by.layoutAlignment = setOf(
                    // By: is below the subtitle
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Top,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, subtitle)
                        )
                    ),
                    // By: is aligned with title start
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, title)
                        )
                    )
                )
                authors.layoutAlignment = setOf(
                    // Authors baseline is aligned with By:
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.BaseLine,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.BaseLine, by)
                        )
                    ),
                    // Authors start follows By:
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, by)
                        )
                    ),
                    // Authors is to the left of the large thumbnail
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, largeThumb)
                        )
                    )
                )
                tagsLabel.layoutAlignment = setOf(
                    // Tags: is below the authors and icon
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.Top,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, authors),
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, smallThumb)
                        )
                    ),
                    // Tags: is aligned with parent start
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, null)
                        )
                    )
                )
                tags.layoutAlignment = setOf(
                    // Tags baseline is aligned with Tags:
                    LayoutDescription.VerticalLayoutAlignment(
                        LayoutDescription.VerticalLayoutDimension.Type.BaseLine,
                        listOf(
                            LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.BaseLine, tagsLabel)
                        )
                    ),
                    // Tags start follows Tags:
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, tagsLabel)
                        )
                    ),
                    // Tags is to the left of the large thumbnail
                    LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, largeThumb)
                        )
                    )
                )
            }
        )
    )
}
