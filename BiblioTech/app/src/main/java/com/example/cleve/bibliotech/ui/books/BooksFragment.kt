package com.example.cleve.bibliotech.ui.books

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.MainActivity
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.ui.filter.OrderTable
import com.example.cleve.bibliotech.ui.modes.DeleteModalAction
import com.example.cleve.bibliotech.ui.modes.TagModalAction
import com.example.cleve.bibliotech.ui.tags.TagViewModel
import com.google.android.material.button.MaterialButton

/**
 * Fragment to display the book list
 */
class BooksFragment : Fragment() {
    /**
     * The view model for the books fragment
     */
    private lateinit var booksViewModel: BooksViewModel

    /**
     * The view model for the tags fragment
     */
    private lateinit var tagViewModel: TagViewModel

    /**
     * The drawer layout for the edit and filter drawer
     */
    private lateinit var actionDrawer: DrawerLayout

    /**
     * The menu item used to show and hide the edit and filter drawer
     */
    private var drawerMenuItem: MenuItem? = null

    /**
     * Drawable to use in drawerMenuItem when the edit and filter drawer is visible
     */
    private lateinit var closeDrawer: Drawable

    /**
     * Icon to use in drawerMenuItem when the edit and filter drawer is not visible
     */
    private lateinit var openDrawer: Drawable

    /**
     * UI handler for the filter elements in the edit and filter drawer
     */
    private val orderTable = OrderTable(this)

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
    private val selectionObserver = Observer<Boolean> {
        // Update menu buttons on selection change
        updateMenuAndButtons()
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
        // Get the books and tags view models
        booksViewModel = MainActivity.getViewModel(activity, BooksViewModel::class.java)
        tagViewModel = MainActivity.getViewModel(activity, TagViewModel::class.java)

        // Listen for selection changes to update the UI
        booksViewModel.selection.hasSelection.observe(this, selectionObserver)
        tagViewModel.selection.hasSelection.observe(this, selectionObserver)


        // Get the edit and filter drawer menu icons
        context?.let {context ->
            closeDrawer = context.resources.getDrawable(R.drawable.ic_close_action_drawer_24, null)
            openDrawer = context.resources.getDrawable(R.drawable.ic_open_action_drawer_24, null)
        }

        // Inflate the fragments view
        val root = inflater.inflate(R.layout.fragment_books, container, false)

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
                updateMenuAndButtons()
            }

            override fun onDrawerStateChanged(newState: Int) {
            }
        })

        // Get the recycler view that holds the book list
        val recyclerView = root.findViewById<RecyclerView>(R.id.book_list)

        // Initialize the filter UI handler with the view to the order table
        orderTable.onCreateView(inflater, root.findViewById(R.id.order_table))

        // Set the initial filter.
        orderTable.setOrder(booksViewModel.filter?.orderList)

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
        booksViewModel.buildFlow()
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
        // Set onClickListener for the Apply Filter button in the edit and filter drawer
        root.findViewById<MaterialButton>(R.id.action_apply_filter).setOnClickListener {
            booksViewModel.applyFilter(orderTable.order.value, null)
        }

        // Set the initial state of the menus and buttons
        updateMenuAndButtons()
        return root
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
        booksViewModel.selection.hasSelection.removeObserver(selectionObserver)
        tagViewModel.selection.hasSelection.removeObserver(selectionObserver)
        // Let filter UI cleanup
        orderTable.onDestroyView()
        super.onDestroyView()
    }

    /**
     * @inheritDoc
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu
        inflater.inflate(R.menu.main, menu)
        // Save the edit and filter drawer menu item
        drawerMenuItem = menu.findItem(R.id.action_drawer)
        super.onCreateOptionsMenu(menu, inflater)
    }

    /**
     * Perform an action when a menu item or button is clicked
     * @param id The id of the menu item or button
     */
    private fun onActionSelected(id: Int): Boolean {
        return when (id) {
            R.id.action_drawer -> {
                // Edit and filter draw menu item. Toggle drawer open or closed
                if (actionDrawer.isDrawerOpen(GravityCompat.END))
                    actionDrawer.closeDrawer(GravityCompat.END)
                else
                    actionDrawer.openDrawer(GravityCompat.END)
                return true
            }
            R.id.action_delete -> {
                // Delete menu item or button
                DeleteModalAction.doDelete(this)
                return true
            }
            R.id.action_add_tags -> {
                // Add tags to books menu item or button
                TagModalAction.doAddTags(this)
                return true
            }
            R.id.action_remove_tags -> {
                // Remove tags from books menu item or button
                TagModalAction.doRemoveTags(this)
                return true
            }
            R.id.action_replace_tags -> {
                // Replace tags for books menu item or button
                TagModalAction.doReplaceTags(this)
                return true
            }
            R.id.action_select_none -> {
                // Select no books menu item
                booksViewModel.selection.selectAll(false)
                return true
            }
            R.id.action_select_all -> {
                // Select all books menu item
                booksViewModel.selection.selectAll(true)
                return true
            }
            R.id.action_select_invert -> {
                // Invert book selection menu item
                booksViewModel.selection.invert()
                return true
            }
            else -> false
        }
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
        // Get the has selected state for books and tags
        val booksSelected = booksViewModel.selection.hasSelection.value == true
        val tagsSelected = tagViewModel.selection.hasSelection.value == true

        // Enable delete if any books are selected
        actionButtons[R.id.action_delete]?.isEnabled = booksSelected
        // Enable add and remove tags if any books and any tags are selected
        actionButtons[R.id.action_add_tags]?.isEnabled = booksSelected && tagsSelected
        actionButtons[R.id.action_remove_tags]?.isEnabled = booksSelected && tagsSelected
        // Enable replace tags if any books are selected
        actionButtons[R.id.action_replace_tags]?.isEnabled = booksSelected

        // Change edit and filter menu item based on the current drawer state
        // TODO: Can the images be handled in a StateListDrawable?
        drawerMenuItem?.let {
            val checked = actionDrawer.isDrawerOpen(GravityCompat.END)
            it.isChecked = checked
            it.icon = if (checked) closeDrawer else openDrawer
        }
    }
}