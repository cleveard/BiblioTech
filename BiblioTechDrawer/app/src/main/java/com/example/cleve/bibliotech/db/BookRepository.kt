package com.example.cleve.bibliotech

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

class BookRepository {
    companion object {
        private var mRepo: BookRepository? = null
        val repo: BookRepository
            get() = mRepo!!
        fun initialize(context: Context) {
            if (!::defaultViewName.isInitialized)
                defaultViewName = context.getString(R.string.default_book_list_name)
            if (mRepo == null) {
                BookDatabase.initialize(context)
                mRepo = BookRepository()
            }
        }
        fun close() {
            BookDatabase.close()
            mRepo = null
        }
        lateinit private var defaultViewName: String
    }

    private val db = BookDatabase.db
    private var executor = Executors.newFixedThreadPool(3)

    private var mList: String = defaultViewName
    var list: String
        get() = mList
        set(list) {
            mList = list
            getBooks()
        }
    private var mViews:LiveData<List<ViewEntity>> = MutableLiveData<List<ViewEntity>>().apply {
        executor.execute {
            var views = db.getViewDao().get()
            if (views.isEmpty()) {
                db.getViewDao().add(ViewEntity(0, defaultViewName, 1000000000, BookEntity.SORT_BY_AUTHOR_LAST_FIRST))
                views = db.getViewDao().get()
            }
            postValue(views)
        }
        observeForever {
            this@BookRepository.getBooks()
        }
    }
    val views
        get() = mViews

    private val mBooks = MutableLiveData<List<BookInView>>().apply {
        value = ArrayList(0)
    }
    val books: LiveData<List<BookInView>>
    get() = mBooks

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

    fun getBooks() {
        val view = mViews.value?.firstOrNull { it.name == mList }
        if (view == null)
            mBooks.value = ArrayList(0)
        else {
            executor.execute {
                mBooks.postValue(db.getViewDao().getBooksForView(view.id)?.books?:ArrayList(0))
            }
        }
    }
}
