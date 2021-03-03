package com.github.cleveard.bibliotech

import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookEntity
import java.util.*

val bookTestNow = Calendar.getInstance().timeInMillis
fun makeBook(unique: Int = 0, flags: Int = 0, uniqueString: String = unique.toString()): BookEntity {
    return BookEntity(
        id = 0L,
        volumeId = "volumeId$uniqueString",
        sourceId = "sourceId$uniqueString",
        ISBN = "ISBN$uniqueString",
        title = "title$uniqueString",
        subTitle = "subTitle$uniqueString",
        description = "description$uniqueString",
        pageCount = 144 + unique * 5,
        bookCount = 5 + unique,
        linkUrl = "linkUrl@unique",
        rating = 3.14 + unique.toFloat() / 10.0f,
        added = Date(bookTestNow + unique.toLong() * (60 * 60* 24)),
        modified = Date(bookTestNow + (unique.toLong() + 22) * (60 * 60* 24)),
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
        tags = emptyList()
    )
}
