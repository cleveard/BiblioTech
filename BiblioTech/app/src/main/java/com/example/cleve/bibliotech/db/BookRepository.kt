package com.example.cleve.bibliotech.db

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.Range
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executor
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
            mRepo?.mainThreadExecutor = ContextCompat.getMainExecutor(context)
            mRepo?.mainThread = Looper.getMainLooper().thread
        }
        fun close() {
            BookDatabase.close()
            mRepo = null
        }
    }

    fun interface ExecutorCallback<T> {
        fun run(value: T?, error: Throwable?)
    }

    fun interface RunnableWithReturn<T> {
        fun run(): T
    }


    private val _selectChanges = MutableLiveData<List<Range<Int>>>(ArrayList())
    val selectChanges: LiveData<List<Range<Int>>>
        get() = _selectChanges
    private var _selectCount = 0
    val selectCount: Int
        get() = _selectCount
    private val db = BookDatabase.db
    private var executor = object {
        val pool = Executors.newFixedThreadPool(3)
        fun execute(runnable: Runnable) {
            if (mainThread == Thread.currentThread())
                pool.execute(runnable)
            else
                runnable.run()
        }

        fun <T> execute(run: RunnableWithReturn<T>, done: ExecutorCallback<T>) {
            if (mainThread == Thread.currentThread()) {
                pool.execute {
                    var value: T? = null
                    var error: Throwable? = null
                    try {
                        value = run.run()
                    } catch (e: java.lang.Exception) {
                        error = e
                    }
                    mainThreadExecutor.execute {
                        done.run(value, error)
                    }
                }
            } else {
                var value: T? = null
                var error: Throwable? = null
                try {
                    value = run.run()
                } catch (e: java.lang.Exception) {
                    error = e
                }
                done.run(value, error)
            }
        }
    }
    private lateinit var mainThreadExecutor: Executor
    private lateinit var mainThread: Thread

    val books = db.getBookDao().getBooks()

    fun addOrUpdateBook(book: BookAndAuthors) {
        executor.execute {
            db.getBookDao().addOrUpdate(book)
        }
    }

    fun delete(book: BookAndAuthors) {
        if (book.selected) {
            book.selected = false
            _selectCount--
        }
        executor.execute {
            db.getBookDao().delete(book)
        }
    }

    fun getThumbnail(
        book: BookEntity,
        large: Boolean,
        callback: (Bitmap?) -> Unit
    ) {
        executor.execute( {
            db.getBookDao().getThumbnail(book.id, large)
        }) { value, _ ->
            callback(value)
        }
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
