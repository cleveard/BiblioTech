package com.github.cleveard.bibliotech.ui.bookshelves

import android.icu.util.Calendar
import android.os.Bundle
import android.util.SparseArray
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookshelfEntity
import com.github.cleveard.bibliotech.utils.coroutineAlert
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

        // Observe the bookshelf selection to update the action menu
        shelvesViewModel.selection.selectedCount.observe(viewLifecycleOwner, observerHasSelection)
        shelvesViewModel.selection.itemCount.observe(viewLifecycleOwner, observerHasSelection)
        shelvesViewModel.selection.lastSelection.observe(viewLifecycleOwner, observerLastSelection)
        shelvesViewModel.editItems.observe(viewLifecycleOwner, observerHasSelection)

        return content
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        // Cancel the pager flow job
        pagerJob.cancel()
        // Remove the selection observer
        shelvesViewModel.selection.itemCount.removeObserver(observerHasSelection)
        shelvesViewModel.selection.selectedCount.removeObserver(observerHasSelection)
        shelvesViewModel.selection.lastSelection.removeObserver(observerLastSelection)
        shelvesViewModel.editItems.removeObserver(observerHasSelection)
        super.onDestroyView()
    }

    /**
     * Setup the recycler view for tags
     */
    private fun setupRecyclerView(content: View) {
        // Get the selected background color
        shelvesViewModel.adapter.selectColor = ResourcesCompat.getColor(requireContext().resources, R.color.colorSelect, null)

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

    private fun onDeleteShelf(): Boolean {
        // Are any selected?
        val selCount = shelvesViewModel.selection.selectedCount.value?: 0
        if (selCount > 0) {
            // Make sure we really want to delete the tags
            shelvesViewModel.viewModelScope.launch {
                val result = coroutineAlert(requireContext(), { false }) { alert ->
                    alert.builder.setMessage(
                        requireContext().resources.getQuantityString(
                            R.plurals.ask_delete_shelves,
                            selCount,
                            selCount
                        )
                    )
                        // Set the action buttons
                        .setPositiveButton(R.string.ok, null)
                        .setNegativeButton(R.string.cancel, null)
                }.setPosListener { alert, _, _ ->
                    alert.result = true
                    true
                }.show()

                if (result) {
                    // OK pressed delete the tags
                    shelvesViewModel.repo.deleteSelectedShelves()
                }
            }
        }
        return true
    }

    private fun onNewShelf(): Boolean {
        if ()
        val now = Calendar.getInstance().timeInMillis
        val entity = BookshelfEntity(
            id = null,
            bookshelfId = 0,
            title = "",
            description = "",
            selfLink = "",
            modified = now,
            booksModified = now,
            tagId = null,
            flags = BookshelfEntity.EDITING
        )

        return true
    }

    /**
     * Setup the action menu in a separate toolbar
     * @param content The content view for the tags fragment
     */
    private fun setupActionMenu(content: View) {
        view?.let {content ->
            content.findViewById<FloatingActionButton>(R.id.new_shelf).setOnClickListener {
                onNewShelf()
            }
            content.findViewById<FloatingActionButton>(R.id.commit).setOnClickListener {

            }
            content.findViewById<FloatingActionButton>(R.id.revert).setOnClickListener {

            }
        }

        updateMenu()
    }

    private fun updateMenu() {
        view?.let {content ->
            val showEdit = (shelvesViewModel.editItems.value?.size?: 0) > 0
            content.findViewById<View>(R.id.ok_or_cancel).visibility = if (showEdit) View.VISIBLE else View.GONE
            content.findViewById<View>(R.id.new_shelf).visibility = if (!showEdit) View.VISIBLE else View.GONE
        }
    }
}
