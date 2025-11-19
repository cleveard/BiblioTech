package com.github.cleveard.bibliotech.ui.bookshelves

import android.os.Bundle
import android.util.SparseArray
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.ui.scan.ScanViewModel
import com.github.cleveard.bibliotech.utils.coroutineAlert
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookshelvesFragment : Fragment() {

    companion object {
        fun newInstance() = BookshelvesFragment()
    }

    private val shelvesViewModel: BookshelvesViewModel by activityViewModels()

    /**
     * Coroutine job for handling the pager flow
     */
    private lateinit var pagerJob: Job

    /**
     * Menu items that need to be enabled and disabled
     */
    private val menuItems: SparseArray<MenuItem?> = SparseArray<MenuItem?>()

    /**
     * Observers to update menu when selection changes
     */
    private val observerHasSelection: Observer<Int?> = Observer { updateMenu() }
    private val observerLastSelection: Observer<Long?> = Observer { updateMenu() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val content = inflater.inflate(R.layout.tags_fragment, container, false)

        // Setup the bookshelf recycler view
        setupRecyclerView(content)
        // Setup the fragment action menu. This fragment creates a separate toolbar
        // for its action menu
        setupActionMenu(content)

        return content
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        // Cancel the pager flow job
        pagerJob.cancel()
        super.onDestroyView()
    }

    /**
     * Setup the recycler view for tags
     */
    private fun setupRecyclerView(content: View) {
        // Setup the pager for the tag recycler view
        val config = PagingConfig(pageSize = 20)
        val pager = Pager(
            config
        ) {
            shelvesViewModel.repo.getShelves()
        }
        // Setup the flow for the pager
        val flow = pager.flow
            .cachedIn(shelvesViewModel.viewModelScope)
        pagerJob = shelvesViewModel.viewModelScope.launch {
            flow.collectLatest { data -> shelvesViewModel.adapter.submitData(data)
            }
        }
        // Set the layout manager and adapter to the recycler view
        val tags = content.findViewById<RecyclerView>(R.id.tags_list)
        tags.adapter = shelvesViewModel.adapter
    }

    /**
     * Setup the action menu in a separate toolbar
     * @param content The content view for the tags fragment
     */
    private fun setupActionMenu(content: View) {
        view?.let {content ->
        }

        updateMenu()
    }

    private fun updateMenu() {
        view?.let {content ->
        }
    }
}
