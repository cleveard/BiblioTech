package com.example.cleve.bibliotech.db

import android.content.Context
import android.graphics.Bitmap
import androidx.paging.PagingSource

class BookRepository {
    companion object {
        private var mRepo: BookRepository? = null
        val repo: BookRepository
            get() = mRepo!!
        fun initialize(context: Context) {
            if (mRepo == null) {
                BookDatabase.initialize(context)
                mRepo = BookRepository()
            }
        }
        fun close() {
            mRepo?.also{
                mRepo = null
            }
            BookDatabase.close()
        }
    }

    private val db = BookDatabase.db

    fun getBooks(): PagingSource<Int, BookAndAuthors> {
        return db.getBookDao().getBooks()
    }

    suspend fun addOrUpdateBook(book: BookAndAuthors) {
        db.getBookDao().addOrUpdate(book)
    }

    suspend fun delete(bookIds: Array<Any>, invert: Boolean = false) {
        db.getBookDao().delete(bookIds, invert)
    }

    suspend fun getThumbnail(
        bookId: Long,
        large: Boolean
    ): Bitmap? {
        try {
            return db.getBookDao().getThumbnail(bookId, large)
        } catch (e: Exception) {}
        return null
    }
}
