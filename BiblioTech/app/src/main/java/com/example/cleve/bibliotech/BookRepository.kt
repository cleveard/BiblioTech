package com.example.cleve.bibliotech

import android.content.Context
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

class BookRepository() {
    private lateinit var db: BookRoomDatabase
    private var executor = Executors.newFixedThreadPool(3)

    private lateinit var mViews: LiveData<List<ViewEntity>>
    val views
        get() = mViews
    private val mBooks = MutableLiveData<List<BookAndAuthors>>(ArrayList<BookAndAuthors>(0))
    val books
        get() = mBooks

    fun open(context: Context) {
        db = BookRoomDatabase.create(context)
        mViews = db.getViewDao().get()
    }

    fun close() {

    }

    fun addView(name: String, order: Int, sort: String) {
        executor.execute {
            db.getViewDao().add(ViewEntity(0, name, order, sort))
        }
    }

    fun addBook(book: Book, view: ViewEntity) {
        executor.execute {
            db.getBookDao().add(book, view.id)
        }
    }

    fun getBooks(view: ViewEntity?) {
        if (view == null)
            mBooks.value = ArrayList(0)
        else {
            executor.execute {
                mBooks.postValue(db.getViewDao().getBooksForView(view.id)?.books?:ArrayList(0))
            }
        }
    }
}
