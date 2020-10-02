package com.example.cleve.bibliotech.ui.books

import com.example.cleve.bibliotech.db.*
import android.os.Bundle
import android.util.Range
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.*
import com.example.cleve.bibliotech.ui.modes.DeleteModalAction

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
        BookRepository.repo.selectChanges.observe(this,
            Observer<List<Range<Int>>> { list -> run {
                    for (range in list)
                        adapter.notifyItemRangeChanged(range.lower, range.upper + 1 - range.lower)
                }
            })
        recycler.adapter = adapter
        setHasOptionsMenu(true)


        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                DeleteModalAction(this, BookRepository.repo).start(activity)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}