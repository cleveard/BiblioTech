package com.github.cleveard.bibliotech

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookEntity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun makeBook(unique: Int = 0, flags: Int = 0): BookEntity {
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
        flags = flags,
    )
}

fun makeBookAndAuthors(unique: Int = 0, flags: Int = 0): BookAndAuthors {
    return BookAndAuthors(
        book = makeBook(unique, flags),
        authors = emptyList(),
        categories = emptyList(),
        tags = emptyList()
    )
}

suspend fun <T> getLive(live: LiveData<T>): T? {
    return withContext(MainScope().coroutineContext) {
        var observer: Observer<T>? = null
        try {
            suspendCoroutine {
                observer = Observer<T> { value ->
                    if (value != null)
                        it.resume(value)
                }.also { obs ->
                    live.observeForever(obs)
                }
            }
        } finally {
            observer?.let { live.removeObserver(it) }
        }
    }
}
