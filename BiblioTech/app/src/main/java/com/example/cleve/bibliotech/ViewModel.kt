package com.example.cleve.bibliotech

import android.content.Context
import android.database.sqlite.SQLiteCursor
import android.os.Bundle
import android.os.CancellationSignal
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.example.cleve.bibliotech.MainActivity.Static.ARG_BOOK_LIST
import com.example.cleve.bibliotech.MainActivity.Static.ARG_POSITION
import kotlin.math.min

internal class ViewList(var name: String, var id: Long, var order: Int, var sortOrder: String)

internal class ViewModel : androidx.lifecycle.ViewModel() {
    private var mInitialized = false

    private val mBookList = MutableLiveData<String>("")
    var bookList: String
        get() { return mBookList.value!! }
        set(list) {
            val view = views.find { it.name == list }
            mView = view
            adaptor.cursor = if (view == null) null else db.getBookList(view.id, view.sortOrder, mCancel)
            if (bookList != list)
                position = 0
            mBookList.value = list
        }

    private val mPosition = MutableLiveData(0)
    var position: Int
        get() { return mPosition.value!! }
        set(pos) {
            mPosition.value = min(adaptor.itemCount, pos)
        }

    private val mViews = MutableLiveData<ArrayList<ViewList>>(ArrayList())
    val views: ArrayList<ViewList>
        get() = mViews.value!!

    private var mView: ViewList? = null
    val view: ViewList?
        get() = mView

    val db = BookDatabase()
    lateinit var adaptor: BookAdapter

    val mLookup = BookLookup()
    val mCancel = CancellationSignal()

    fun initialize(context: Context, arguments: Bundle?) {
        // Return if initialized
        if (mInitialized)
            return

        // Open the data base and create the recycler adaptor
        db.open(context)
        adaptor = BookAdapter(context)

        // initialize the views array list
        val list = views
        val cursor: BookDatabase.ViewCursor = db.getViewList(mCancel)
        cursor.moveToFirst()
        do {
            list.add(ViewList(cursor.name, cursor.id, cursor.order, cursor.sort))
        } while (cursor.moveToNext())
        list.sortBy { it.order }    // Sort by the order field

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

    fun getBook(viewId: Long, bookId: Long): SQLiteCursor {
        return db.getBook(bookId, mCancel)
    }

    override fun onCleared() {
        super.onCleared()
        db.close()
    }

    internal inner class AddBookByISBN(private val mList: Long) : BookLookup.LookupDelegate {
        override fun bookLookupResult(result: Array<Book?>?, more: Boolean) {
            if (result != null && result.isNotEmpty()) {
                for (i in result.indices) {
                    val book = result[i]
                    val bookId: Long = db.addBook(book!!)
                    if (bookId >= 0) {
                        db.addBookToView(mList, bookId)
                    }
                }
            }
        }

        override fun bookLookupError(error: String?) { // TODO Auto-generated method stub
        }

    }

    internal fun addBookByISBN(isbn: String?, list: Int) {
        addBookByISBN(isbn, AddBookByISBN(list.toLong()))
    }

    internal fun addBookByISBN(isbn: String?, callback: AddBookByISBN) {
        mLookup.lookupISBN(callback, isbn)
    }
}

internal interface ShareViewModel {
    val provider: ViewModelProvider
}