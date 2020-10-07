package com.example.cleve.bibliotech.db

import android.content.Context
import android.graphics.Bitmap
import android.util.Range
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.*

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
                it.scope.cancel()
                mRepo = null
            }
            BookDatabase.close()
        }
    }

    private val _selectChanges = MutableLiveData<List<Range<Int>>>(ArrayList())
    val selectChanges: LiveData<List<Range<Int>>>
        get() = _selectChanges
    private var _selectCount = 0
    val selectCount: Int
        get() = _selectCount
    private val db = BookDatabase.db

    val scope = MainScope()
    private lateinit var _books: LiveData<List<BookAndAuthors>>
    var books: LiveData<List<BookAndAuthors>>
        get() {
            return _books
        }
        set(books: LiveData<List<BookAndAuthors>>) {
            _books = books
            for (o in observers)
                o.onChanged(_books)
        }
    private val observers = HashSet<Observer<LiveData<List<BookAndAuthors>>>>()
    init {
        scope.launch {
            books = db.getBookDao().getBooks()
        }
    }

    fun observeBooks(observer: Observer<LiveData<List<BookAndAuthors>>>) {
        observers.add(observer)
        if (this::_books.isInitialized)
            observer.onChanged(_books)
    }

    fun removeObserver(observer: Observer<LiveData<List<BookAndAuthors>>>) {
        observers.remove(observer)
        if (this::_books.isInitialized)
            observer.onChanged(_books)
    }

    fun addOrUpdateBook(book: BookAndAuthors) {
        scope.launch { db.getBookDao().addOrUpdate(book) }
    }

    fun delete(book: BookAndAuthors) {
        if (book.selected) {
            book.selected = false
            _selectCount--
        }
        scope.launch { db.getBookDao().delete(book) }
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

    fun select(index: Int, select: Boolean): Int {
        val book = books.value?.get(index)
        if (book != null && select != book.selected) {
            book.selected = select
            _selectCount += if (select) 1 else -1
            _selectChanges.value = listOf(Range(index, index))
        }
        return _selectCount
    }

    fun selectAll(select: Boolean): Int {
        val list = books.value ?: return 0
        val finalSize = if (select) list.size else 0
        if (_selectCount != finalSize) {
            var start = -1
            val changes = ArrayList<Range<Int>>()
            for (i in list.indices) {
                val book = list[i]
                if (book.selected != select) {
                    book.selected = select
                    if (start == -1)
                        start = i
                } else if (start != -1) {
                    changes.add(Range(start, i - 1))
                    start = -1
                }
            }
            if (start != -1)
                changes.add(Range(start, list.size - 1))
            _selectCount = finalSize
            _selectChanges.value = changes
        }
        return finalSize
    }
}
