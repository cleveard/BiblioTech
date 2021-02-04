package com.github.cleveard.bibliotech

import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookEntity
import java.util.*

fun makeBook(unique: Int = 0): BookEntity {
    return BookEntity(
        id = 0L,
        volumeId = "volumeId$unique",
        sourceId = "sourceId$unique",
        ISBN = "ISBN$unique",
        title = "title$unique",
        subTitle = "subTitle$unique",
        description = "description$unique",
        pageCount = 144 + unique,
        bookCount = 5 + unique,
        linkUrl = "linkUrl@unique",
        rating = 3.14 + unique.toFloat() / 10.0f,
        added = Date(15 + unique.toLong()),
        modified = Date(22 + unique.toLong()),
        smallThumb = "smallThumb$unique",
        largeThumb = "largeThumb$unique",
        flags = unique,
    )
}

fun makeBookAndAuthors(unique: Int = 0): BookAndAuthors {
    return BookAndAuthors(
        book = makeBook(unique),
        authors = emptyList(),
        categories = emptyList(),
        tags = emptyList()
    )
}
