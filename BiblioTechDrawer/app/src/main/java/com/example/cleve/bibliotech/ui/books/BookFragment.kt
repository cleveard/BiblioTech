package com.example.cleve.bibliotech.ui.books

import com.example.cleve.bibliotech.db.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.*

class BooksFragment : Fragment() {

    private lateinit var booksViewModel: BooksViewModel
    private lateinit var adapter: BooksAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        booksViewModel =
            ViewModelProviders.of(this).get(BooksViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_books, container, false)

        val recycler = root.findViewById<RecyclerView>(R.id.book_list)

        recycler.layoutManager = LinearLayoutManager(activity)
        adapter = BooksAdapter(container!!.context)
        BookRepository.repo.books.observe(this,
            Observer<List<BookAndAuthors>> { list -> adapter.submitList(list) })
        recycler.adapter = adapter


        return root
    }
}