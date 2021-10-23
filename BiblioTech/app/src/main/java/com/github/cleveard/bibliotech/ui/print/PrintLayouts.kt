package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.print.LayoutDescription

class PrintLayouts(context: Context) {
    private val authorsBy: String = context.resources.getText(R.string.authors_by).toString()

    /** A simple layout, Title, subtitle and authors */
    val simpleLayout = LayoutDescription(
        listOf(
            LayoutDescription.ColumnFieldLayoutDescription(Column.TITLE),
            LayoutDescription.ColumnFieldLayoutDescription(Column.SUBTITLE).apply {
                margin.top = 4.5f
            },
            LayoutDescription.TextFieldLayoutDescription(authorsBy).apply {
                margin.top = 4.5f
                margin.right = 4.5f
            },
            LayoutDescription.ColumnFieldLayoutDescription(Column.FIRST_NAME)
        ),
        emptyList(),                // No headers
        18.0f,      // Separation between print columns
        9.0f            // Separation between books
    ).apply {
        val title = inColumns[0]
        val subtitle = inColumns[1]
        val by = inColumns[2]
        val authors = inColumns[3]
        title.layoutAlignment = setOf(
            // Title is at the top
            LayoutDescription.VerticalLayoutAlignment(
                LayoutDescription.VerticalLayoutDimension.Type.Top,
                listOf(
                    LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Top, null)
                )
            ),
            // Title is at the left
            LayoutDescription.HorizontalLayoutAlignment(
                LayoutDescription.HorizontalLayoutDimension.Type.Start,
                listOf(
                    LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, null)
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
