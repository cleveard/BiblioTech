package com.example.cleve.bibliotech.db

import android.content.Context
import android.util.Range
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

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
            BookDatabase.close()
            mRepo = null
        }
    }

    private val _selectChanges = MutableLiveData<List<Range<Int>>>(ArrayList());
    val selectChanges: LiveData<List<Range<Int>>>
        get() = _selectChanges
    private var _selectCount = 0
    val selectCount: Int
        get() = _selectCount
    private val db = BookDatabase.db
    private var executor = Executors.newFixedThreadPool(3)

    val books = db.getBookDao().getBooks()

    fun addOrUpdateBook(book: BookAndAuthors) {
        executor.execute {
            db.getBookDao().addOrUpdate(book)
        }
    }

    fun delete(book: BookAndAuthors) {
        if (book.selected) {
            book.selected = false;
            _selectCount--;
        }
        executor.execute {
            db.getBookDao().delete(book)
        }
    }

    fun select(index: Int, select: Boolean): Int {
        val book = books.value?.get(index)
        if (book != null && select != book.selected) {
            book.selected = select
            _selectCount += if (select) 1 else -1
            _selectChanges.value = listOf(Range<Int>(index, index))
        }
        return _selectCount
    }

    fun selectAll(select: Boolean): Int {
        val list = books.value
        if (list == null)
            return 0
        val finalSize = if (select) list.size else 0
        if (_selectCount != finalSize) {
            var start = -1;
            var changes = ArrayList<Range<Int>>()
            for (i in 0..list.size - 1) {
                val book = list[i]
                if (book.selected != select) {
                    book.selected = select
                    if (start == -1)
                        start = i
                } else if (start != -1) {
                    changes.add(Range(start, i - 1))
                    start = -1;
                }
            }
            if (start != -1)
                changes.add(Range(start, list.size - 1))
            _selectCount = finalSize
            _selectChanges.value = changes;
        }
        return finalSize
    }
}
