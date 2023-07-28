package com.github.cleveard.bibliotech.testutils

import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookEntity
import java.util.*

fun makeBook(unique: Int = 0, flags: Int = 0, uniqueString: String = unique.toString(), bookTestNow: Long = Calendar.getInstance().timeInMillis): BookEntity {
    return BookEntity(
        id = 0L,
        volumeId = "volumeId$uniqueString",
        sourceId = "sourceId$uniqueString",
        title = "title$uniqueString",
        subTitle = "subTitle$uniqueString",
        description = "description$uniqueString",
        pageCount = 144 + unique * 5,
        bookCount = 5 + unique,
        linkUrl = "linkUrl@unique",
        rating = 3.14 + unique.toFloat() / 10.0f,
        seriesId = null,
        seriesOrder = unique + 4,
        added = Date(bookTestNow),
        modified = Date(bookTestNow),
        smallThumb = "smallThumb$unique",
        largeThumb = "largeThumb$unique",
        flags = flags,
    )
}

fun makeBookAndAuthors(unique: Int = 0, flags: Int = 0, uniqueString: String = unique.toString()): BookAndAuthors {
    return BookAndAuthors(
        book = makeBook(unique, flags, uniqueString),
        authors = emptyList(),
        categories = emptyList(),
        tags = emptyList(),
        isbns = emptyList(),
        series = null
    )
}
