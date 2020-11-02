package com.example.cleve.bibliotech.ui.books

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.ui.filter.FilterTables
import com.example.cleve.bibliotech.ui.modes.DeleteModalAction
import com.example.cleve.bibliotech.ui.modes.TagModalAction
import com.example.cleve.bibliotech.ui.tags.TagViewModel
import com.google.android.material.button.MaterialButton

class BooksFragment : Fragment() {

    private lateinit var booksViewModel: BooksViewModel
    private lateinit var tagViewModel: TagViewModel
    private lateinit var actionDrawer: DrawerLayout
    private var drawerMenuItem: MenuItem? = null
    private lateinit var closeDrawer: Drawable
    private lateinit var openDrawer: Drawable
    private val filter = FilterTables(this)
    private val clickHandler = View.OnClickListener { v ->
        if (v != null)
            onActionSelected(v.id)
    }
    private val actionButtons = HashMap<Int, View>()
    private val selectionObserver = Observer<Boolean> {
        updateMenuAndButtons()
    }

    private fun setActionClickListener(view: View?) {
        (view as? ViewGroup)?.let {
            for (child in it.children) {
                if (child.isClickable && child.id != 0) {
                    child.setOnClickListener(clickHandler)
                    actionButtons[child.id] = child
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        booksViewModel =
            ViewModelProviders.of(activity!!).get(BooksViewModel::class.java)
        tagViewModel =
            ViewModelProviders.of(activity!!).get(TagViewModel::class.java)
        booksViewModel.selection.hasSelection.observe(this, selectionObserver)
        tagViewModel.selection.hasSelection.observe(this, selectionObserver)

        val context = container!!.context
        closeDrawer = context.resources.getDrawable(R.drawable.ic_close_action_drawer_24, null)
        openDrawer = context.resources.getDrawable(R.drawable.ic_open_action_drawer_24, null)

        val root = inflater.inflate(R.layout.fragment_books, container, false)
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

        val recyclerView = root.findViewById<RecyclerView>(R.id.book_list)
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
        recyclerView.layoutManager = booksViewModel.layoutManager
        booksViewModel.adapter = BooksAdapter(context!!, booksViewModel)
        booksViewModel.buildFlow(filter.filter.value)
        recyclerView.adapter = booksViewModel.adapter
        recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    setHeader()
                }
                super.onScrollStateChanged(recyclerView, newState)
            }
        })
        setHasOptionsMenu(true)

        setActionClickListener(root.findViewById<ConstraintLayout>(R.id.action_drawer_view))
        root.findViewById<MaterialButton>(R.id.action_apply_filter).setOnClickListener {
            booksViewModel.buildFlow(filter.filter.value)
        }

        filter.onCreateView(inflater, root.findViewById(R.id.order_table))

        updateMenuAndButtons()
        return root
    }

    fun setHeader() {
        view?.let { root ->
            val text = booksViewModel.buildHeader(context!!)
            val headerView = root.findViewById<TextView>(R.id.header_view)
            headerView.text = text
            val vis = if (text == null) View.GONE else View.VISIBLE
            if (vis != headerView.visibility) {
                headerView.visibility = vis
                headerView.requestLayout()
            }
        }
    }

    override fun onDestroyView() {
        booksViewModel.selection.hasSelection.removeObserver(selectionObserver)
        tagViewModel.selection.hasSelection.removeObserver(selectionObserver)
        filter.onDestroyView()
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        drawerMenuItem = menu.findItem(R.id.action_drawer)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun onActionSelected(id: Int): Boolean {
        return when (id) {
            R.id.action_drawer -> {
                if (actionDrawer.isDrawerOpen(GravityCompat.END))
                    actionDrawer.closeDrawer(GravityCompat.END)
                else
                    actionDrawer.openDrawer(GravityCompat.END)
                return true
            }
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
            else -> false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return onActionSelected(item.itemId) || super.onOptionsItemSelected(item)
    }

    private fun updateMenuAndButtons() {
        val booksSelected = booksViewModel.selection.hasSelection.value == true
        val tagsSelected = tagViewModel.selection.hasSelection.value == true

        actionButtons[R.id.action_delete]?.isEnabled = booksSelected
        actionButtons[R.id.action_add_tags]?.isEnabled = booksSelected && tagsSelected
        actionButtons[R.id.action_remove_tags]?.isEnabled = booksSelected && tagsSelected
        actionButtons[R.id.action_replace_tags]?.isEnabled = booksSelected

        drawerMenuItem?.let {
            val checked = actionDrawer.isDrawerOpen(GravityCompat.END)
            it.isChecked = checked
            it.icon = if (checked) closeDrawer else openDrawer
        }
    }
}