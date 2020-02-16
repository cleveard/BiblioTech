package com.example.cleve.bibliotech.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cleve.bibliotech.R
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

    private val db = BookDatabase.db
    private var executor = Executors.newFixedThreadPool(3)

    val books = db.getBookDao().getBooks()

    fun addOrUpdateBook(book: BookAndAuthors) {
        executor.execute {
            db.getBookDao().addOrUpdate(book)
        }
    }
}
