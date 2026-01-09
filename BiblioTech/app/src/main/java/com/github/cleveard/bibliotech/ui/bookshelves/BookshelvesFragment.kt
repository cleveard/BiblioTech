package com.github.cleveard.bibliotech.ui.bookshelves

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.ManageNavigation
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.db.BookshelfEntity
import com.github.cleveard.bibliotech.ui.books.BooksAdapterData
import com.github.cleveard.bibliotech.ui.gb.GoogleBookLoginFragment
import com.github.cleveard.bibliotech.utils.ParentAccess
import com.github.cleveard.bibliotech.utils.Thumbnails
import com.github.cleveard.bibliotech.utils.coroutineAlert
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BookshelvesFragment : Fragment() {

    companion object {
        fun newInstance() = BookshelvesFragment()

        const val SHELF_SHIFT = 10
        const val BOOK_INTERVAL = 1024.0
    }

    private val shelvesViewModel: BookshelvesViewModel by activityViewModels()

    /**
     * Coroutine job for handling the pager flow
     */
    private lateinit var pagerJob: Job

    /**
     * Tag recycler adapter
     */
    internal var bookshelveAdapter: BookshelvesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val content = inflater.inflate(R.layout.fragment_bookshelves, container, false)
        // Setup the bookshelf recycler view
        setupRecyclerView(content)
        // Setup the fragment action menu. This fragment creates a separate toolbar
        // for its action menu
        setupActionMenu()

        return content
    }

    /**
     * @inheritDoc
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shelvesViewModel.viewModelScope.launch {
            // Make sure we are logged in, cancels coroutine if login fails
            GoogleBookLoginFragment.login(this@BookshelvesFragment)

            shelvesViewModel.viewModelScope.launch {
                shelvesViewModel.refreshBookshelves(
                    requireActivity() as BookCredentials,
                    BookshelvesViewModel.RefreshBooks.NEVER
                ) { conflicts, addedToShelfCount ->
                    handleConflicts(this, conflicts, addedToShelfCount)
                }
            }
        }
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
        val adapter = object: BookshelvesAdapter(shelvesViewModel.viewModelScope) {
            override suspend fun toggleTagAndBookshelfLink(shelf: BookshelfAndTag) {
                shelvesViewModel.toggleTagAndBookshelfLink(shelf)
            }

            override suspend fun onRefreshClicked(shelf: BookshelfAndTag, button: MaterialButton) {
                ProgressDialog().run {
                    shelvesViewModel.refreshShelfAndBooks(
                        requireActivity() as BookCredentials,
                        shelf,
                        BookshelvesViewModel.RefreshBooks.ALWAYS,
                        it
                    ) { conflicts, addedToShelfCount ->
                        handleConflicts(this, conflicts, addedToShelfCount)
                    }
                }
            }
        }
        // Setup the flow for the pager
        val flow = pager.flow
            .cachedIn(shelvesViewModel.viewModelScope)
        pagerJob = shelvesViewModel.viewModelScope.launch {
            flow.collectLatest { data -> adapter.submitData(data)
            }
        }
        // Set the layout manager and adapter to the recycler view
        val shelves = content.findViewById<RecyclerView>(R.id.bookshelves)
        shelves.adapter = adapter
        bookshelveAdapter = adapter
    }


    /**
     * Perform an action when a menu item or button is clicked
     * @param id The id of the menu item or button
     */
    private fun onActionSelected(id: Int): Boolean {
        when (id) {
            // Refresh bookshelves list
            R.id.action_refresh -> {
                shelvesViewModel.viewModelScope.launch {
                    ProgressDialog().run {
                        shelvesViewModel.refreshBookshelves(
                            requireActivity() as BookCredentials,
                            BookshelvesViewModel.RefreshBooks.IF_OUT_OF_DATE,
                            it
                        ) { conflicts, addedToShelf ->
                            handleConflicts(this, conflicts, addedToShelf)
                        }
                    }
                }
            }
            // Undo an action
            R.id.action_undo -> {
                shelvesViewModel.viewModelScope.launch {
                    shelvesViewModel.repo.undo()
                }
            }
            // Redo an undone action
            R.id.action_redo -> {
                shelvesViewModel.viewModelScope.launch {
                    shelvesViewModel.repo.redo()
                }
            }
            // Bring up the settings fragment
            R.id.action_to_settingsFragment -> {
                (activity as? ManageNavigation)?.navigate(
                    BookshelvesFragmentDirections.actionBookshelvesFragmentToSettingsFragment()
                )
            }
            // Login/Logout
            R.id.action_logout -> {
                (activity as? BookCredentials)?.let {credentials ->
                    shelvesViewModel.viewModelScope.launch {
                        if (credentials.isAuthorized)
                            credentials.logout()
                        else
                            credentials.login()
                    }
                }
            }
            else -> return false
        }
        return true
    }
        /**
     * Setup the action menu in a separate toolbar
     */
    private fun setupActionMenu() {
        shelvesViewModel.undoList.observe(viewLifecycleOwner) { }

        // Add options menu provider
        requireActivity().addMenuProvider(
            object: MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    // Inflate the menu
                    menuInflater.inflate(R.menu.bookshelf_options, menu)
                }

                /**
                 * @inheritDoc
                 */
                override fun onPrepareMenu(menu: Menu) {
                    // Method to update description and state of menu items
                    fun update(id: Int, desc: String?, undoId: Int, undoNameId: Int) {
                        menu.findItem(id)?.let {item ->
                            item.isEnabled = desc != null
                            item.title = if (desc.isNullOrEmpty())
                                requireContext().getString(undoId)
                            else
                                requireContext().getString(undoNameId, desc)
                        }
                    }
                    // Enable/Disable Undo item and set the string
                    update(R.id.action_undo, shelvesViewModel.undoList.value?.lastOrNull { !it.isRedo }?.desc,
                        R.string.undo, R.string.undoName)
                    // Enable/Disable Redo item and set the string
                    update(R.id.action_redo, shelvesViewModel.undoList.value?.firstOrNull { it.isRedo }?.desc,
                        R.string.redo, R.string.redoName)
                    // Set the Login/Logout string
                    update(
                        R.id.action_logout,
                        "",
                        if ((requireActivity() as? BookCredentials)?.isAuthorized == true)
                            R.string.logout
                        else
                            R.string.login,
                        0)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    // Direct action to onActionSelected
                    return onActionSelected(menuItem.itemId)
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    /**
     * Handle conflicts for when refreshing books in shelves
     * @param conflictsArg The list of books conflicting
     * @param addedToShelfCount The number of books in the list the were added to the shelf
     */
    suspend fun handleConflicts(shelf: BookshelfEntity, conflictsArg: List<BookAndAuthors>, addedToShelfCount: Int): Boolean {
        return coroutineScope {
            // Setup separate thumbnails for this query
            val thumbnails = Thumbnails("conflicts")
            val conflicts = conflictsArg.map { it.copy() }
            for (i in conflicts.indices) {
                conflicts[i].book.id = i.toLong()
                conflicts[i].book.isSelected = i >= addedToShelfCount
            }

            try {
                // Otherwise display a dialog to select the book
                coroutineAlert(requireContext(), { false }) { alert ->

                    // Get the content view for the dialog
                    val content =
                        requireParentFragment().layoutInflater.inflate(R.layout.resolve_shelf_conflicts, null)

                    // Create an object the book adapter uses to get info
                    val access = object : ParentAccess {
                        // This is the adapter
                        lateinit var adapter: ConflictsAdapter

                        // Toggle the selection for an id
                        override fun toggleSelection(id: Long, editable: Boolean, position: Int) {
                            conflicts[position].book.isSelected = !conflicts[position].book.isSelected
                            adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        }

                        // Get the context
                        override val context: Context
                            get() = this@BookshelvesFragment.requireContext()

                        // Get the coroutine scope
                        override val scope: CoroutineScope
                            get() = this@coroutineScope

                        // Get a thumbnail
                        override suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
                            // Use the thumbnails we constructed above
                            return thumbnails.getThumbnail(bookId, large) { b, l ->
                                // Get the book from the adapter and the url from the book
                                conflicts[b.toInt()].let {
                                    if (l)
                                        it.book.largeThumb
                                    else
                                        it.book.smallThumb
                                }
                            }
                        }
                    }

                    content.findViewById<TextView>(R.id.alert_title).text = access.context.resources.getString(R.string.resolve_conflict_refreshing, shelf.title)

                    fun MaterialButton.setup(enabled: Boolean, range: IntRange, select: Boolean) {
                        isEnabled = enabled
                        setOnClickListener {
                            for (i in range) {
                                if (conflicts[i].book.isSelected != select) {
                                    conflicts[i].book.isSelected = select
                                    access.adapter.notifyItemChanged(i)
                                }
                            }
                        }
                    }
                    content.findViewById<MaterialButton>(R.id.delete_from_shelf)
                        .setup(addedToShelfCount > 0, 0 until addedToShelfCount, false)
                    content.findViewById<MaterialButton>(R.id.retag_in_db)
                        .setup(addedToShelfCount > 0, 0 until addedToShelfCount, true)
                    content.findViewById<MaterialButton>(R.id.untag_in_db)
                        .setup(addedToShelfCount < conflicts.size, addedToShelfCount until conflicts.size, false)
                    content.findViewById<MaterialButton>(R.id.untag_in_db)
                        .setup(addedToShelfCount < conflicts.size, addedToShelfCount until conflicts.size, true)

                    // Find the recycler view and set the layout manager and adapter
                    val titles = content.findViewById<RecyclerView>(R.id.title_buttons)
                    access.adapter = ConflictsAdapter(access)
                    access.adapter.submitList(conflicts)
                    titles.adapter = access.adapter

                    // Create an alert dialog with the content view
                    alert.builder
                        // Specify the list array, the items to be selected by default (null for none),
                        // and the listener through which to receive callbacks when items are selected
                        .setView(content)
                        // Set the action buttons
                        .setPositiveButton(R.string.ok, null)
                        .setNegativeButton(R.string.cancel, null)
                }.show().also {
                    if (it) {
                        for (i in conflicts.indices)
                            conflictsArg[i].book.isSelected = conflicts[i].book.isSelected
                    }
                }
            } finally {
                thumbnails.deleteAllThumbFiles()
            }
        }
    }

    private class ConflictsAdapter(access: ParentAccess): ListAdapter<Any, BooksAdapterData.ViewHolder>(BooksAdapterData.DIFF_CALLBACK) {
        val data = BooksAdapterData(this, access, R.layout.books_adapter_book_item_always, 0)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BooksAdapterData.ViewHolder {
            return data.onCreateViewHolder(parent, viewType)
        }

        override fun onBindViewHolder(holder: BooksAdapterData.ViewHolder, position: Int) {
            data.onBindViewHolder(getItem(position) ?: return, holder)
        }

        override fun onViewRecycled(holder: BooksAdapterData.ViewHolder) {
            data.onViewRecycled(holder)
            super.onViewRecycled(holder)
        }
    }

    private inner class ProgressDialog: BookshelvesViewModel.Progress {
        lateinit var dialog: DialogInterface
        var job: Job? = null
        lateinit var contentView: View
        lateinit var shelfText: TextView
        lateinit var progress: ProgressBar
        var curBookCount: Int = 0
        var curShelfCount: Int = 0
        var bookInterval: Double = 0.0

        private fun setProgress() {
            progress.setProgress((curShelfCount.coerceAtLeast(0) shl SHELF_SHIFT)
                + (curBookCount.coerceAtLeast(0) * bookInterval).roundToInt(), true)
        }
        suspend fun run(block: suspend (progress: BookshelvesViewModel.Progress) -> Unit) {
            try {
                show()
                block(this)
            } finally {
                dismiss()
            }

        }

        override var bookCount: Int = 0
            set(value) {
                field = value
                bookInterval = if (value <= 0) BOOK_INTERVAL else BOOK_INTERVAL / value
                curBookCount = -1
                contentView.post {
                    setProgress()
                }
            }

        override var shelfCount: Int = 0
            set(value) {
                field = value
                curShelfCount = -1
                contentView.post {
                    progress.max = shelfCount shl SHELF_SHIFT
                    setProgress()
                }
            }

        override suspend fun show() {
            job = currentCoroutineContext().job
            @SuppressLint("InflateParams")
            contentView = layoutInflater.inflate(R.layout.shelf_progress, null)
            shelfText = contentView.findViewById(R.id.shelf_title)
            progress = contentView.findViewById(R.id.progress_bar)


            AlertDialog.Builder(requireContext())
                .setView(contentView)
                .setCancelable(false)
                .setPositiveButton(R.string.cancel) { dialog, which -> cancel() }
                .setOnCancelListener {
                    job?.cancel()
                }
                .create().apply {
                    dialog = this
                    show()
                }
        }

        override fun dismiss() {
            dialog.dismiss()
        }

        override fun cancel() {
            dialog.cancel()
        }

        override fun nextShelf(shelf: String) {
            ++curShelfCount
            contentView.post {
                shelfText.text = shelf
                setProgress()
            }
        }

        override fun nextBook() {
            ++curBookCount
            contentView.post {
                setProgress()
            }
        }
    }
}
