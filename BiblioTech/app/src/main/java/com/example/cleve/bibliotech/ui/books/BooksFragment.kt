package com.example.cleve.bibliotech.ui.books

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.ui.modes.DeleteModalAction
import com.example.cleve.bibliotech.ui.modes.TagModalAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BooksFragment : Fragment() {

    private lateinit var booksViewModel: BooksViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        booksViewModel =
            ViewModelProviders.of(activity!!).get(BooksViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_books, container, false)

        val recycler = root.findViewById<RecyclerView>(R.id.book_list)

        recycler.layoutManager = LinearLayoutManager(activity)
        booksViewModel.adapter = BooksAdapter(container!!.context, booksViewModel)

        val config = PagingConfig(pageSize = 10)
        val pager = Pager(
            config
        ) {
            BookRepository.repo.getBooks()
        }
        val flow = booksViewModel.applySelectionTransform(pager.flow)
                   .cachedIn(booksViewModel.viewModelScope)
        booksViewModel.viewModelScope.launch {
            flow.collectLatest {
                    data -> booksViewModel.adapter.submitData(data)
            }
        }
        recycler.adapter = booksViewModel.adapter
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
                DeleteModalAction.doDelete(this)
                return true
            }
            R.id.action_add_tags -> {
                TagModalAction.doAddTags(this)
                return true
            }
            R.id.action_remove_tags -> {
                TagModalAction.doRemoveTags(this)
                return true
            }
            R.id.action_replace_tags -> {
                TagModalAction.doReplaceTags(this)
                return true
            }
            R.id.action_select_none -> {
                booksViewModel.selection.selectAll(false)
                return true
            }
            R.id.action_select_all -> {
                booksViewModel.selection.selectAll(true)
                return true
            }
            R.id.action_select_invert -> {
                booksViewModel.selection.invert()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}