package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.graphics.PointF
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.print.BookLayout
import com.github.cleveard.bibliotech.print.LayoutDescription

class PrintLayouts(context: Context) {
    private val authorsBy: String = context.resources.getText(R.string.authors_by).toString()

    /** A simple layout, Title, subtitle and authors */
    val simpleLayout = LayoutDescription(
        listOf(
            LayoutDescription.ColumnBitmapFieldLayoutDescription(false, PointF(16.0f, BookLayout.MAX_HEIGHT)).apply {
                margin.right = 4.5f
            },
            LayoutDescription.ColumnTextFieldLayoutDescription(Column.TITLE),
            LayoutDescription.ColumnTextFieldLayoutDescription(Column.SUBTITLE).apply {
                margin.top = 1.0f
            },
            LayoutDescription.TextFieldLayoutDescription(authorsBy).apply {
                margin.top = 1.0f
                margin.right = 4.5f
            },
            LayoutDescription.ColumnTextFieldLayoutDescription(Column.FIRST_NAME)
        ),
        emptyList(),                // No headers
        18.0f,      // Separation between print columns
        9.0f            // Separation between books
    ).apply {
        val thumb = inColumns[0]
        val title = inColumns[1]
        val subtitle = inColumns[2]
        val by = inColumns[3]
        val authors = inColumns[4]
        thumb.layoutAlignment = setOf(
            // Thumbnail is at the top
            LayoutDescription.VerticalLayoutAlignment(
                LayoutDescription.VerticalLayoutDimension.Type.Top,
                listOf(
                    LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Top, null)
                )
            ),
            // Thumbnail bottom is at the bottom of the authors
            LayoutDescription.VerticalLayoutAlignment(
                LayoutDescription.VerticalLayoutDimension.Type.Bottom,
                listOf(
                    LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, authors)
                )
            ),
            // Thumbnail is at the start
            LayoutDescription.HorizontalLayoutAlignment(
                LayoutDescription.HorizontalLayoutDimension.Type.Start,
                listOf(
                    LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, null)
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
            // Title is to the right of the thumbnail
            LayoutDescription.HorizontalLayoutAlignment(
                LayoutDescription.HorizontalLayoutDimension.Type.Start,
                listOf(
                    LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, thumb)
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
            )
        )
    }
}
