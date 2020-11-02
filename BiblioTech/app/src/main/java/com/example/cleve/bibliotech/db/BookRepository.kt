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

    fun getBooks(filter: BookFilter? = null): PagingSource<Int, BookAndAuthors> {
        return db.getBookDao().getBooks(filter)
    }

    suspend fun getBook(bookId: Long): BookAndAuthors? {
        return db.getBookDao().getBook(bookId)
    }

    suspend fun addOrUpdateBook(book: BookAndAuthors, tagIds: Array<Any>? = null, invert: Boolean = false) {
        db.getBookDao().addOrUpdate(book, tagIds, invert)
    }

    suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false) {
        db.getBookDao().delete(bookIds, invert)
    }

    fun getTags(): PagingSource<Int, Tag> {
        return db.getTagDao().get()
    }

    suspend fun getTag(tagId: Long): TagEntity? {
        return db.getTagDao().get(tagId)
    }

    suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend (conflict: TagEntity) -> Boolean)? = null) {
        db.getTagDao().add(tag, callback)
    }

    suspend fun deleteTags(tagIds: Array<Any>, invert: Boolean = false) {
        db.getTagDao().delete(tagIds, invert)
    }

    suspend fun findTagByName(name: String): TagEntity? {
        return db.getTagDao().findByName(name)
    }

    suspend fun addTagsToBooks(bookIds: Array<Any>, tagIds: Array<Any>,
                               booksInvert: Boolean = false, tagsInvert: Boolean = false) {
        db.getBookTagDao().addTagsToBooks(bookIds, tagIds, booksInvert, tagsInvert)
    }

    suspend fun removeTagsFromBooks(bookIds: Array<Any>, tagIds: Array<Any>,
                               booksInvert: Boolean = false, tagsInvert: Boolean = false) {
        db.getBookTagDao().deleteTagsForBooks(bookIds, booksInvert, tagIds, tagsInvert)
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
