package com.example.cleve.bibliotech

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.example.cleve.bibliotech.MainActivity.Static.ARG_BOOK_LIST
import com.example.cleve.bibliotech.MainActivity.Static.ARG_POSITION
import kotlin.math.min

internal class ViewModel : androidx.lifecycle.ViewModel() {
    lateinit var mRepo: BookRepository

    private val mBookList = MutableLiveData<String>("")
    var bookList: String
        get() { return mBookList.value!! }
        set(list) {
            // mView = views.find { it.name == list }
            // mRepo.getBooks(mView)
        }

    private val mPosition = MutableLiveData(0)
    var position: Int
        get() { return mPosition.value!! }
        set(pos) {
            mPosition.value = min(adaptor.itemCount, pos)
        }

    private lateinit var mViews: LiveData<List<ViewEntity>>
    val views: List<ViewEntity>
        get() = mViews.value!!

    private var mView: ViewEntity? = null
    val view: ViewEntity?
        get() = mView

    private lateinit var mBooks: LiveData<List<BookAndAuthors>>
    lateinit var adaptor: BookAdapter

    val mLookup = BookLookup()

    fun initialize(context: Context, arguments: Bundle?) {
        // Return if initialized
        if (::mRepo.isInitialized)
            return

        mRepo = BookRepository()
        mRepo.open(context)

        // initialize the views array list
        mViews = mRepo.views
        // mRepo.addView(context.getString(R.string.books), 1, BookEntity.SORT_BY_AUTHOR_LAST_FIRST)

        mRepo.getBooks(mView);
        adaptor = BookAdapter(context)

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
    }

    override fun onCleared() {
        super.onCleared()
        mRepo.close()
    }

    internal inner class AddBookByISBN(private val mList: Long) : BookLookup.LookupDelegate {
        override fun bookLookupResult(result: Array<Book>?, more: Boolean) {
            if (result != null) {
                for (book in result) {
                    mRepo.addBook(book, mView!!)
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