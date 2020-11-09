package com.example.cleve.bibliotech.ui.tags

import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ActionMenuView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.MainActivity
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.TagEntity
import com.example.cleve.bibliotech.utils.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 * Fragment for handling tags
 */
class TagsFragment : Fragment() {
    /**
     * The view model for the tags fragment
     */
    private lateinit var tagViewModel: TagViewModel

    /**
     * Coroutine job for handling the pager flow
     */
    private lateinit var pagerJob: Job

    /**
     * Delete tag menu item: used to enable/disable it
     */
    private lateinit var deleteItem: MenuItem

    /**
     * Edit tag menu item: used to enable/disable it
     */
    private lateinit var editItem: MenuItem

    /**
     * Observer to update menu when selection changes
     */
    private val observer: Observer<Boolean?> = Observer<Boolean?> { updateMenu() }

    /**
     * @inheritDoc
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val content = inflater.inflate(R.layout.fragment_tags, container, false)
        // Get the view model
        tagViewModel =
            MainActivity.getViewModel(activity, TagViewModel::class.java)

        // Setup the tag recycler view
        setupRecyclerView(content)
        // Setup the fragment action menu. This fragment creates a separate toolbar
        // for its action menu
        setupActionMenu(content)

        // Observe the tag selection to update the action menu
        tagViewModel.selection.hasSelection.observe(this, observer)

        return content
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        // Cancel the pager flow job
        pagerJob.cancel()
        // Remove the selection observer
        tagViewModel.selection.hasSelection.removeObserver(observer)
        super.onDestroyView()
    }

    /**
     * Setup the recycler view for tags
     */
    private fun setupRecyclerView(content: View) {
        // Get the selected background color
        tagViewModel.adapter.selectColor = ResourcesCompat.getColor(context!!.resources, R.color.colorSelect, null)

        // Setup the pager for the tag recycler view
        val config = PagingConfig(pageSize = 20)
        val pager = Pager(
            config
        ) {
            tagViewModel.repo.getTags()
        }
        // Setup the flow for the pager
        val flow = tagViewModel.applySelectionTransform(pager.flow)
            .cachedIn(tagViewModel.viewModelScope)
        pagerJob = tagViewModel.viewModelScope.launch {
            flow.collectLatest { data -> tagViewModel.adapter.submitData(data)
            }
        }
        // Set the layout manager and adapter to the recycler view
        val tags = content.findViewById<RecyclerView>(R.id.tags_list)
        tags.layoutManager = LinearLayoutManager(activity)
        tags.adapter = tagViewModel.adapter
    }

    /**
     * @inheritDoc
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_tag -> onNewTag()       // Create a new tag
            R.id.action_delete -> onDeleteTags()    // Delete selected tags
            R.id.action_edit -> onEditTag()         // Edit last selected tag
            R.id.action_select_none -> {
                // Deselect all tags
                tagViewModel.selection.selectAll(false)
                return true
            }
            R.id.action_select_all -> {
                // Select all tags
                tagViewModel.selection.selectAll(true)
                return true
            }
            R.id.action_select_invert -> {
                // Invert the tags selected
                tagViewModel.selection.invert()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Setup the action menu in a separate toolbar
     * @param content The content view for the tags fragment
     */
    private fun setupActionMenu(content: View) {
        // Get the toolbar view
        val actionMenuView = content.findViewById<ActionMenuView>(R.id.tags_toolbar)

        // Get the toolbar menu and add a menu item selected listener for the toolbar
        val menu = actionMenuView.menu
        actionMenuView.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }

        // Inflate a menu xml into the action menu
        activity?.menuInflater?.inflate(R.menu.tag_fragment, menu)

        // Get the menu items that we enable and disable
        deleteItem = BaseViewModel.setupIcon(context, menu, R.id.action_delete)
        editItem = BaseViewModel.setupIcon(context, menu, R.id.action_edit)
    }

    /**
     * Delete the selected tags
     */
    private fun onDeleteTags(): Boolean {
        // Get the tags selected
        val tagIds = tagViewModel.selection.selection
        val inverted = tagViewModel.selection.inverted
        // Are any selected?
        if (inverted || tagIds.isNotEmpty()) {
            // Make sure we really want to delete the tags
            val builder = android.app.AlertDialog.Builder(context)
            builder.setMessage(
                if (inverted)
                    context!!.resources.getString(R.string.delete_tags_unknown)
                else
                    context!!.resources.getQuantityString(
                        R.plurals.ask_delete_tags,
                        tagIds.size,
                        tagIds.size
                    )
            )
                // Set the action buttons
                .setPositiveButton(
                    R.string.ok
                ) { _, _ ->
                    // OK pressed delete the tags
                    tagViewModel.viewModelScope.launch {
                        tagViewModel.selection.selectAll(false)
                        tagViewModel.repo.deleteTags(tagIds, inverted)
                    }
                }
                .setNegativeButton(
                    R.string.cancel
                ) { _, _ ->
                    // Cancel pressed, do nothing
                }
                .show()
        }
        return true
    }

    /**
     * Edit the last selected tag
     */
    private fun onEditTag(): Boolean {
        // Get the last selected tag id and bring up edit dialog
        val id = tagViewModel.selection.lastSelection.value?: return false
        addOrEdit(id)
        return true
    }

    private fun onNewTag(): Boolean {
        // Bring up edit dialog
        addOrEdit(0L)
        return true
    }

    /**
     * Add or edit a tag
     * @param tagId The id of the tag to edit, or 0 to add a new tag
     */
    private fun addOrEdit(tagId: Long) {
        // Do this in a coroutine, so we can use the repo coroutine methods
        tagViewModel.viewModelScope.launch {
            // Get or create the TagEntity for the tag
            val tag: TagEntity?
            if (tagId != 0L) {
                // Got an id, so get the tag
                tag = tagViewModel.repo.getTag(tagId)
                if (tag == null) {
                    // Tag not found. Clear the last selected tag and return
                    tagViewModel.selection.clearLastSelection()
                    return@launch
                }
            } else {
                // Create a new TagEntity
                tag = TagEntity(0, "", "")
            }

            // Inflate the content for the edit tag dialog
            val content = layoutInflater.inflate(R.layout.edit_tag, null)!!
            // Fill in the name
            val name = content.findViewById<EditText>(R.id.edit_tag_name)
            name.setText(tag.name, TextView.BufferType.EDITABLE)
            // Fill in the description
            val desc = content.findViewById<EditText>(R.id.edit_tag_desc)
            desc.setText(tag.desc, TextView.BufferType.EDITABLE)

            // Build the tag edit dialog. Don't listen for OK or Cancel.
            // OK is handled in an on onClick listener, so we can cancel the OK
            // Cancel doesn't need to do anything
            val alert = AlertDialog.Builder(context!!)
                .setTitle(if (tagId == 0L) R.string.add_title else R.string.edit_title)
                .setMessage(if (tagId == 0L) R.string.add_message else R.string.edit_message)
                .setCancelable(true)
                .setView(content)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create()

            // When the dialog is show, we want to setup some things
            alert.setOnShowListener {
                val ok = alert.getButton(AlertDialog.BUTTON_POSITIVE)
                // Make sure we start with the name selected
                context?.getSystemService(InputMethodManager::class.java)?.also {imm ->
                    name.requestFocus()
                    imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
                }

                // Listen for the OK click. When OK is clicked we check to see if the
                // there is a conflict for the tag name and ask the user if it is OK
                ok.setOnClickListener {
                    tag.name = name.text.toString().trim() { it <= ' ' }
                    tag.desc = desc.text.toString().trim() { it <= ' ' }

                    // Do this in a coroutine, to use the repo coroutine methods
                    tagViewModel.viewModelScope.launch {
                        // Add or update the tag
                        val id = tagViewModel.repo.addOrUpdateTag(tag) {
                            // We got a conflict, ask the use if that is OK
                            // Here we suspend the coroutine, until the user replies
                            // The return value of suspendCoroutine is the return
                            // value of the this lambda
                            suspendCoroutine { cont ->
                                // Switch the coroutine back to the main thread so we
                                // can safely present the dialog
                                tagViewModel.viewModelScope.launch {
                                    // This keeps track of the users reply, Yes=true, No=false
                                    var accept = false
                                    // Present the dialog
                                    AlertDialog.Builder(context!!)
                                        .setTitle(R.string.tag_conflict_title)
                                        .setMessage(if (tag.id == 0L) R.string.tag_conflict_add_message else R.string.tag_conflict_edit_message)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.yes) { _, _ ->
                                            // Pressed Yes, so set accept to true
                                            accept = true
                                        }
                                        .setNegativeButton(R.string.no, null)   // Don't nee to do anything for No
                                        .setOnDismissListener {
                                            // When the dialog is dismissed return the
                                            // user's reply to the coroutine
                                            cont.resume(accept)
                                        }
                                        .show()
                                }
                            }
                        }

                        if (id != 0L)
                            alert.dismiss()
                    }
                }
            }

            alert.show()
        }
    }

    /**
     * Update the tags fragment action menu
     */
    private fun updateMenu() {
        // Get the tag selection state
        val selectedTags = tagViewModel.selection.hasSelection.value?: false
        val lastSelection = tagViewModel.selection.lastSelection.value

        deleteItem.isEnabled = selectedTags
        editItem.isEnabled = lastSelection != null
    }
}