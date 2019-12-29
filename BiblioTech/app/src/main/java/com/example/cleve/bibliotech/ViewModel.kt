package com.example.cleve.bibliotech

import android.content.Context
import android.database.sqlite.SQLiteCursor
import android.os.Bundle
import android.os.CancellationSignal
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.example.cleve.bibliotech.MainActivity.Static.ARG_BOOK_LIST
import com.example.cleve.bibliotech.MainActivity.Static.ARG_POSITION
import kotlin.math.min

internal class ViewModel : androidx.lifecycle.ViewModel() {
    companion object Static {
        const val SORT_AUTHOR = "author";
    }
    private var mInitialized = false

    private val mBooksInView = MutableLiveData<List<BookInView>>(ArrayList(0))

    private val mBookList = MutableLiveData<String>("")
    var bookList: String
        get() { return mBookList.value!! }
        set(list) {
            val view = views.find { it.name == list }
            mBooksInView.value = if (view != null) db.getBookDao().getBooksForView(view) else ArrayList(0)
            mView = view
        }

    private val mPosition = MutableLiveData(0)
    var position: Int
        get() { return mPosition.value!! }
        set(pos) {
            mPosition.value = min(adaptor.itemCount, pos)
        }

    private val mViews = MutableLiveData<ArrayList<ViewEntity>>(ArrayList())
    val views: ArrayList<ViewEntity>
        get() = mViews.value!!

    private var mView: ViewEntity? = null
    val view: ViewEntity?
        get() = mView

    lateinit var db: BookRoomDatabase
    lateinit var adaptor: BookAdapter

    val mLookup = BookLookup()
    val mCancel = CancellationSignal()

    fun initialize(context: Context, arguments: Bundle?) {
        // Return if initialized
        if (mInitialized)
            return

        // Open the data base and create the recycler adaptor
        db = BookRoomDatabase.create(context)
        adaptor = BookAdapter(context)

        // initialize the views array list
        var list = db.getViewDao().get()
        if (list.size == 0) {
            db.getViewDao().add(ViewEntity(0, context.getString(R.string.books), 1, SORT_AUTHOR))
            list = db.getViewDao().get()
        }

        // Get the list and position arguments
        var books = bookList
        var pos = position
        if (arguments != null) {
            books = arguments.getString(ARG_BOOK_LIST)?:""
            pos = arguments.getInt((ARG_POSITION))
        }

        // Set the list and position which will initialize
        bookList = books
        position = pos

        mInitialized = true
    }

    fun getBook(bookId: Long): BookAndAuthors? {
        return db.getBookDao().getBook(bookId)
    }

    override fun onCleared() {
        super.onCleared()
        db.close()
    }

    internal inner class AddBookByISBN(private val mList: Long) : BookLookup.LookupDelegate {
        override fun bookLookupResult(result: Array<Book>?, more: Boolean) {
            if (result != null) {
                for (book in result) {
                    db.getBookDao().add(book, mList)
                }
            }
        }

        override fun bookLookupError(error: String?) { // TODO Auto-generated method stub
        }

    }

    internal fun addBookByISBN(isbn: String?, list: Long) {
        addBookByISBN(isbn, AddBookByISBN(list))
    }

    internal fun addBookByISBN(isbn: String?, callback: AddBookByISBN) {
        mLookup.lookupISBN(callback, isbn)
    }
}

internal interface ShareViewModel {
    val provider: ViewModelProvider
}