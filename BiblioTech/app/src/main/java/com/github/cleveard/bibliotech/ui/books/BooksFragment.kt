package com.github.cleveard.bibliotech.ui.books

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.SparseArray
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.*
import com.github.cleveard.bibliotech.db.BookFilter
import com.github.cleveard.bibliotech.db.ColumnDataDescriptor
import com.github.cleveard.bibliotech.db.UndoTransactionEntity
import com.github.cleveard.bibliotech.db.ViewEntity
import com.github.cleveard.bibliotech.ui.filter.FilterTable
import com.github.cleveard.bibliotech.ui.filter.OrderTable
import com.github.cleveard.bibliotech.ui.modes.DeleteModalAction
import com.github.cleveard.bibliotech.ui.tags.TagViewModel
import com.github.cleveard.bibliotech.utils.BaseViewModel
import com.github.cleveard.bibliotech.utils.coroutineAlert
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Fragment to display the book list
 */
class BooksFragment : Fragment() {
    /**
     * The view model for the books fragment
     */
    private val booksViewModel: BooksViewModel by activityViewModels()

    /**
     * The view model for the tags fragment
     */
    private val tagViewModel: TagViewModel by activityViewModels()

    /**
     * The drawer layout for the edit and filter drawer
     */
    private lateinit var actionDrawer: DrawerLayout

    /**
     * The menu item used to show and hide the edit and filter drawer
     */
    private var drawerMenuItem: MenuItem? = null

    /**
     * The menu item to delete books
     */
    private var deleteMenuItem: MenuItem? = null

    /**
     * The menu item to select all books
     */
    private var selectAll: MenuItem? = null

    /**
     * The menu item to select no books
     */
    private var selectNone: MenuItem? = null

    /**
     * The menu item to select no books
     */
    private var logoutMenuItem: MenuItem? = null

    /**
     * Drawable to use in drawerMenuItem when the edit and filter drawer is visible
     */
    private lateinit var closeDrawer: Drawable

    /**
     * Icon to use in drawerMenuItem when the edit and filter drawer is not visible
     */
    private lateinit var openDrawer: Drawable

    private val args: BooksFragmentArgs by navArgs()

    /**
     * UI handler for the filter order elements in the edit and filter drawer
     */
    private val orderTable = OrderTable(this)

    /**
     * UI handler for the filter filter elements in the edit and filter drawer
     */
    private val filterTable = FilterTable(this).also {
        it.actionListener = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                saveFilter()
                return@OnEditorActionListener true
            }
            false
        }
    }

    /**
     * OnClick handler for buttons in the edit and filter drawer
     */
    private val clickHandler = View.OnClickListener { v ->
        // Just pass the click on to onActionSelected
        if (v != null)
            onActionSelected(v.id)
    }

    /**
     * Array of action buttons in the edit and filter drawer
     * This is used to get the view for when disabling, etc.
     */
    private val actionButtons = SparseArray<View>()

    /**
     * Observer for selection changes
     */
    private val selectionObserver = Observer<Int?> {
        // Update menu buttons on selection change
        updateMenuAndButtons()
    }

    /**
     * Observer for BooksViewModel filterView changes
     */
    private val filterViewObserver = Observer<ViewEntity?> {filterView ->
        booksViewModel.buildFlow()
        activity?.findViewById<Toolbar>(R.id.toolbar)?.let {
            // The view changed, set the title and subtitle
            it.title = filterView?.name.let { name ->
                if (name.isNullOrEmpty())
                    requireContext().resources.getString(R.string.menu_books)
                else
                    name
            }
            it.subtitle = filterView?.desc?: ""
            // Put the filter into the UI
            orderTable.setOrder(filterView?.filter?.orderList)
            filterTable.setFilter(filterView?.filter?.filterList)
        }
    }

    /**
     * Observer for the undoList
     */
    @Suppress("ObjectLiteralToLambda")  // Kotlin messes up if this is a lambda
    private val undoListObserver = object: Observer<List<UndoTransactionEntity>> {
        override fun onChanged(t: List<UndoTransactionEntity>?) { }
    }

    /**
     * Set onClickListener for clickable views contained in view
     * @param view The parent of the views to be set
     */
    private fun setActionClickListener(view: View?) {
        (view as? ViewGroup)?.let {
            // Loop through the children
            for (child in it.children) {
                // If the child is clickable and its id is not 0
                if (child.isClickable && child.id != 0) {
                    // Set the listener and add the child to the actionButtons
                    child.setOnClickListener(clickHandler)
                    actionButtons.put(child.id, child)
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Initialize the view model observers
        booksViewModel.selection.selectedCount.observe(viewLifecycleOwner, selectionObserver)
        booksViewModel.selection.itemCount.observe(viewLifecycleOwner, selectionObserver)
        booksViewModel.filterView.observe(viewLifecycleOwner, filterViewObserver)
        booksViewModel.undoList.observe(viewLifecycleOwner, undoListObserver)
        tagViewModel.selection.selectedCount.observe(viewLifecycleOwner, selectionObserver)
        tagViewModel.selection.itemCount.observe(viewLifecycleOwner, selectionObserver)

        // Get the edit and filter drawer menu icons
        context?.let {context ->
            closeDrawer = ResourcesCompat.getDrawable(context.resources,
                R.drawable.ic_close_action_drawer_24, null)!!
            openDrawer = ResourcesCompat.getDrawable(context.resources,
                R.drawable.ic_open_action_drawer_24, null)!!
        }

        // Inflate the fragments view
        val root = inflater.inflate(R.layout.books_fragment, container, false)

        // Get the edit and filter drawer layout and add a listener to update buttons
        // when the drawer is opened or closed
        actionDrawer = root.findViewById(R.id.drawer_layout)
        actionDrawer.addDrawerListener(object: DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            }

            override fun onDrawerOpened(drawerView: View) {
                updateMenuAndButtons()
            }

            override fun onDrawerClosed(drawerView: View) {
                // Dismiss the keyboard
                val imm =
                    context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(actionDrawer.rootView.windowToken, 0)
                updateMenuAndButtons()
            }

            override fun onDrawerStateChanged(newState: Int) {
            }
        })

        // Get the recycler view that holds the book list
        val recyclerView = root.findViewById<RecyclerView>(R.id.book_list)

        // Initialize the filter UI handler with the view to the order table
        orderTable.onCreateView(inflater, root.findViewById(R.id.order_table))
        filterTable.onCreateView(inflater, root.findViewById(R.id.filter_table))

        // Set the initial filter.
        orderTable.setOrder(booksViewModel.filterView.value?.filter?.orderList)
        filterTable.setFilter(booksViewModel.filterView.value?.filter?.filterList)

        // Create the layout manager for the book list. When the layout
        // changes, update the header
        booksViewModel.layoutManager = object: LinearLayoutManager(activity) {
            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                setHeader()
            }

            override fun offsetChildrenVertical(dy: Int) {
                super.offsetChildrenVertical(dy)
                setHeader()
            }
        }

        // Set the layout manager on the recycler view.
        recyclerView.layoutManager = booksViewModel.layoutManager
        // Setup the adapter data stream and set the adapter on the recycler view
        recyclerView.adapter = booksViewModel.adapter

        // When the recycler view scrolls, update the list header
        recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    setHeader()
                }
                super.onScrollStateChanged(recyclerView, newState)
            }
        })

        // Let the system know we have an options menu
        setHasOptionsMenu(true)

        // Set onClickListeners for buttons in the edit and filter drawer
        setActionClickListener(root.findViewById<ConstraintLayout>(R.id.action_drawer_view))

        // Set the initial state of the menus and buttons
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.findViewById<TextView>(R.id.book_stats)?.visibility = View.VISIBLE
        // Setup the filter in the view model
        booksViewModel.applyView(args.filterName)
        ColumnDataDescriptor.setDateLocale(requireContext().resources.configuration.locales[0])

    }

    /**
     * Save the filter as set in the current UI
     */
    private fun saveFilter() {
        booksViewModel.viewModelScope.launch {
            // close the drawer
            actionDrawer.closeDrawer(GravityCompat.END)
            // apply the filter
            booksViewModel.saveFilter(orderTable.order.value, filterTable.filter.value)
        }
    }

    /**
     * Add a new filter
     */
    private fun newFilter() {
        booksViewModel.viewModelScope.launch {
            // Ask for name and description and save it
            addNewFilterView(booksViewModel.filterView.value)?.let {
                // Navigate to the new filter
                (activity as? ManageNavigation)?.navigate(MobileNavigationDirections.filterBooks(it.name))
            }
        }
    }

    /**
     * Remove the current filter
     */
    private fun removeFilter() {
        // Can't delete the default view
        if (booksViewModel.filterView.value?.name.isNullOrEmpty())
            return

        // Switch the coroutine back to the main thread so we
        // can safely present the dialog
        booksViewModel.viewModelScope.launch {

            val yes = coroutineAlert(requireContext(), { false }) { alert ->
                // Present the dialog
                alert.builder.setTitle(R.string.remove_view_title)
                    .setMessage(R.string.remove_view_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, null)
                    .setNegativeButton(R.string.no, null)   // Don't nee to do anything for No
            }
            .setPosListener {alert, _, _ ->
                alert.result = true
                true
            }
            .show()

            // Remove it if the use OKs
            if (yes)
                booksViewModel.removeFilter()
        }

    }

    /**
     * Set the contents of the header
     */
    fun setHeader() {
        // Make sure we still have a view
        view?.let { root ->
            // Build the header contents and set it on the header view
            val text = booksViewModel.buildHeader()
            val headerView = root.findViewById<TextView>(R.id.header_view)
            headerView.text = text
            // Set the visibility based on whether text is null or not
            val vis = if (text == null) View.GONE else View.VISIBLE
            // If visibility changes change the visibility and request a layout
            if (vis != headerView.visibility) {
                headerView.visibility = vis
                headerView.requestLayout()
            }
        }
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        // Remove selection observers
        booksViewModel.selection.selectedCount.removeObserver(selectionObserver)
        booksViewModel.selection.itemCount.removeObserver(selectionObserver)
        booksViewModel.filterView.removeObserver(filterViewObserver)
        booksViewModel.undoList.removeObserver(undoListObserver)
        tagViewModel.selection.selectedCount.removeObserver(selectionObserver)
        tagViewModel.selection.itemCount.removeObserver(selectionObserver)
        // Let filter UI cleanup
        orderTable.onDestroyView()
        filterTable.onDestroyView()
        super.onDestroyView()
    }

    /**
     * @inheritDoc
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu
        inflater.inflate(R.menu.books_options, menu)
        // Save the edit and filter drawer menu item
        drawerMenuItem = menu.findItem(R.id.action_drawer)
        deleteMenuItem = BaseViewModel.setupIcon(context, menu, R.id.action_delete)
        selectAll = menu.findItem(R.id.action_select_all)
        selectNone = menu.findItem(R.id.action_select_none)
        logoutMenuItem = menu.findItem(R.id.action_logout)
        super.onCreateOptionsMenu(menu, inflater)
        updateMenuAndButtons()
    }

    /**
     * @inheritDoc
     */
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        fun update(id: Int, desc: String?, undoId: Int, undoNameId: Int) {
            menu.findItem(id)?.let {item ->
                item.isEnabled = desc != null
                item.title = if (desc.isNullOrEmpty())
                    requireContext().getString(undoId)
                else
                    requireContext().getString(undoNameId, desc)
            }
        }
        update(R.id.action_undo, booksViewModel.undoList.value?.lastOrNull { !it.isRedo }?.desc,
            R.string.undo, R.string.undoName)
        update(R.id.action_redo, booksViewModel.undoList.value?.firstOrNull { it.isRedo }?.desc,
            R.string.redo, R.string.redoName)
    }

    /**
     * Perform an action when a menu item or button is clicked
     * @param id The id of the menu item or button
     */
    private fun onActionSelected(id: Int): Boolean {
        when (id) {
            R.id.action_drawer -> {
                // Edit and filter draw menu item. Toggle drawer open or closed
                if (actionDrawer.isDrawerOpen(GravityCompat.END))
                    actionDrawer.closeDrawer(GravityCompat.END)
                else
                    actionDrawer.openDrawer(GravityCompat.END)
            }
            R.id.action_delete -> {
                // Delete menu item or button
                DeleteModalAction.doDelete(this)
            }
            R.id.action_select_none -> {
                // Select no books menu item
                booksViewModel.selection.selectAllAsync(false)
            }
            R.id.action_select_all -> {
                // Select all books menu item
                booksViewModel.selection.selectAllAsync(true)
            }
            R.id.action_select_invert -> {
                // Invert book selection menu item
                booksViewModel.selection.invertAsync()
            }
            R.id.action_new_filter -> {
                newFilter()
            }
            R.id.action_save_filter -> {
                saveFilter()
            }
            R.id.action_remove_filter -> {
                removeFilter()
            }
            R.id.action_undo -> {
                booksViewModel.viewModelScope.launch {
                    booksViewModel.repo.undo()
                }
            }
            R.id.action_redo -> {
                booksViewModel.viewModelScope.launch {
                    booksViewModel.repo.redo()
                }
            }
            R.id.action_to_settingsFragment -> {
                (activity as? ManageNavigation)?.navigate(
                    BooksFragmentDirections.actionNavBooksToSettingsFragment()
                )
            }
            R.id.action_logout -> {
                (activity as? BookCredentials)?.let {credentials ->
                    booksViewModel.viewModelScope.launch {
                        if (credentials.isAuthorized)
                            credentials.logout()
                        else
                            credentials.login()
                        updateMenuAndButtons()
                    }
                }
            }
            R.id.action_to_print_fragment -> {
                (activity as? ManageNavigation)?.navigate(
                    BooksFragmentDirections.actionNavBooksToPrintFragment(booksViewModel.filterName)
                )
            }
            else -> return false
        }
        return true
    }

    /**
     * @inheritDoc
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Direct action to onActionSelected
        return onActionSelected(item.itemId) || super.onOptionsItemSelected(item)
    }

    /**
     * Update menu and buttons
     */
    private fun updateMenuAndButtons() {
        // Get the has selected state for books
        val booksSelected = booksViewModel.selection.selectedCount.value?: 0
        val booksCount = booksViewModel.selection.itemCount.value?: 0

        activity?.findViewById<TextView>(R.id.book_stats)?.text =
            getString(R.string.book_stats, booksCount, booksSelected)

        // Enable delete if this is not the global list
        actionButtons[R.id.action_remove_filter].isEnabled = args.filterName?.isNotEmpty() ?: false

        // Enable delete if any books are selected
        deleteMenuItem?.isEnabled = booksSelected > 0
        // Enable select none when something is selected
        selectNone?.isEnabled = booksSelected > 0
        // Enable select all when something is not selected
        selectAll?.isEnabled = booksSelected < booksCount
        // Enable logout menu item
        logoutMenuItem?.title = if ((requireActivity() as? BookCredentials)?.isAuthorized == true)
            requireContext().resources.getString(R.string.logout)
        else
            requireContext().resources.getString(R.string.login)

        // Change edit and filter menu item based on the current drawer state
        // TODO: Can the images be handled in a StateListDrawable?
        drawerMenuItem?.let {
            val checked = actionDrawer.isDrawerOpen(GravityCompat.END)
            it.isChecked = checked
            it.icon = if (checked) closeDrawer else openDrawer
        }
    }

    /**
     * Add a new filter view or update an existing one
     * @param currentView The current filter view
     */
    private suspend fun addNewFilterView(currentView: ViewEntity?): ViewEntity? {
        return coroutineScope {
            // Get the filter from the UI
            val filterList = filterTable.filter.value
            val orderList = orderTable.order.value
            // Create an entity to add to the database. Set the filter to the filter from the UI
            val entity = currentView?.copy(
                filter = if (filterList.isNullOrEmpty() && orderList.isNullOrEmpty())
                    null
                else {
                    BookFilter(
                        orderList ?: emptyArray(),
                        filterList ?: emptyArray()
                    )
                }
            ) ?: ViewEntity(0, "", "")

            // Get the content view for the dialog
            val content = requireParentFragment().layoutInflater.inflate(R.layout.books_drawer_new_filter, null)
            val name = content.findViewById<EditText>(R.id.edit_view_name)
                .also { it.text = SpannableStringBuilder(entity.name) }
            val desc = content.findViewById<EditText>(R.id.edit_view_desc)
                .also { it.text = SpannableStringBuilder(entity.desc) }

            // Create an alert dialog with the content view
            return@coroutineScope coroutineAlert<ViewEntity?>(requireContext(), { null }) { alert ->
                alert.builder.setTitle(R.string.new_filter_title)
                    .setTitle(R.string.add_view_title)
                    .setMessage(R.string.add_view_message)
                    // Specify the list array, the items to be selected by default (null for none),
                    // and the listener through which to receive callbacks when items are selected
                    .setView(content)
                    // Set the action buttons
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null)
            }.setPosListener {alert,  _, _ ->
                // Present the dialog and return the new view entity, or null if it wasn't added
                entity.name = name.text.toString().trim { it <= ' ' }
                entity.desc = desc.text.toString().trim { it <= ' ' }

                // Add or update the tag
                entity.id = booksViewModel.repo.addOrUpdateView(entity) {
                    // We got a conflict, ask the user if that is OK
                    // Return true for OK and false for not ok
                    coroutineAlert(requireContext(), { false }) { alert ->
                        alert.builder.setTitle(R.string.view_conflict_title)
                            .setMessage(R.string.view_conflict_message)
                            .setCancelable(false)
                            .setPositiveButton(R.string.yes, null)
                            .setNegativeButton(
                                R.string.no,
                                null
                            )   // Don't nee to do anything for No
                    }.setPosListener {alert,  _, _ ->
                        // OK sets the result to true
                        alert.result = true
                        // Dismiss the dialog
                        true
                    }.show()
                }

                // Entity id is non-zero if we added, or updated it
                if (entity.id != 0L)
                    alert.result = entity     // Done return the result
                entity.id != 0L         // Dismiss the dialog if we are done
            }.show {
                // Make sure we start with the name selected
                context?.getSystemService(InputMethodManager::class.java)?.also { imm ->
                    name.requestFocus()
                    imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
}