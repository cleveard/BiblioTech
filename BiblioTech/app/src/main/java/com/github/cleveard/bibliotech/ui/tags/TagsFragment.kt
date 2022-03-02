package com.github.cleveard.bibliotech.ui.tags

import android.os.Bundle
import android.util.SparseArray
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.MainActivity
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.TagEntity
import com.github.cleveard.bibliotech.ui.books.BooksViewModel
import com.github.cleveard.bibliotech.ui.modes.TagModalAction
import com.github.cleveard.bibliotech.utils.BaseViewModel
import com.github.cleveard.bibliotech.utils.coroutineAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


/**
 * Fragment for handling tags
 */
class TagsFragment : Fragment() {
    /**
     * The view model for the tags fragment
     */
    private val tagViewModel: TagViewModel by activityViewModels()

    /**
     * The view model for the tags fragment
     */
    private val booksViewModel: BooksViewModel by activityViewModels()

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
    private val observerHasSelection: Observer<Int?> = Observer<Int?> { updateMenu() }
    private val observerLastSelection: Observer<Long?> = Observer<Long?> { updateMenu() }

    /**
     * @inheritDoc
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val content = inflater.inflate(R.layout.tags_fragment, container, false)

        // Setup the tag recycler view
        setupRecyclerView(content)
        // Setup the fragment action menu. This fragment creates a separate toolbar
        // for its action menu
        setupActionMenu(content)

        // Observe the tag selection to update the action menu
        tagViewModel.selection.selectedCount.observe(viewLifecycleOwner, observerHasSelection)
        tagViewModel.selection.itemCount.observe(viewLifecycleOwner, observerHasSelection)
        booksViewModel.selection.selectedCount.observe(viewLifecycleOwner, observerHasSelection)
        booksViewModel.selection.itemCount.observe(viewLifecycleOwner, observerHasSelection)
        tagViewModel.selection.lastSelection.observe(viewLifecycleOwner, observerLastSelection)

        return content
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        // Cancel the pager flow job
        pagerJob.cancel()
        // Remove the selection observer
        booksViewModel.selection.selectedCount.removeObserver(observerHasSelection)
        booksViewModel.selection.itemCount.removeObserver(observerHasSelection)
        tagViewModel.selection.itemCount.removeObserver(observerHasSelection)
        tagViewModel.selection.selectedCount.removeObserver(observerHasSelection)
        tagViewModel.selection.lastSelection.removeObserver(observerLastSelection)
        super.onDestroyView()
    }

    /**
     * Setup the recycler view for tags
     */
    private fun setupRecyclerView(content: View) {
        // Get the selected background color
        tagViewModel.adapter.selectColor = ResourcesCompat.getColor(requireContext().resources, R.color.colorSelect, null)

        // Setup the pager for the tag recycler view
        val config = PagingConfig(pageSize = 20)
        val pager = Pager(
            config
        ) {
            tagViewModel.repo.getTags()
        }
        // Setup the flow for the pager
        val flow = pager.flow
            .cachedIn(tagViewModel.viewModelScope)
        pagerJob = tagViewModel.viewModelScope.launch {
            flow.collectLatest { data -> tagViewModel.adapter.submitData(data)
            }
        }
        // Set the layout manager and adapter to the recycler view
        val tags = content.findViewById<RecyclerView>(R.id.tags_list)
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
            R.id.action_add_tags -> {
                // Add tags to books menu item or button
                TagModalAction.doAddTags(this)
                true
            }
            R.id.action_remove_tags -> {
                // Remove tags from books menu item or button
                TagModalAction.doRemoveTags(this)
                true
            }
            R.id.action_replace_tags -> {
                // Replace tags for books menu item or button
                TagModalAction.doReplaceTags(this)
                true
            }
            R.id.action_select_none -> {
                // Deselect all tags
                tagViewModel.selection.selectAllAsync(false)
                return true
            }
            R.id.action_select_all -> {
                // Select all tags
                tagViewModel.selection.selectAllAsync(true)
                return true
            }
            R.id.action_select_invert -> {
                // Invert the tags selected
                tagViewModel.selection.invertAsync()
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
        activity?.menuInflater?.inflate(R.menu.tags_options, menu)

        // Get the menu items that we enable and disable
        BaseViewModel.setupIcon(context, menu, R.id.action_new_tag)
        menuItems.put(R.id.action_delete, BaseViewModel.setupIcon(context, menu, R.id.action_delete))
        menuItems.put(R.id.action_edit, BaseViewModel.setupIcon(context, menu, R.id.action_edit))
        menuItems.put(R.id.action_add_tags, BaseViewModel.setupIcon(context, menu, R.id.action_add_tags))
        menuItems.put(R.id.action_remove_tags, BaseViewModel.setupIcon(context, menu, R.id.action_remove_tags))
        menuItems.put(R.id.action_replace_tags, BaseViewModel.setupIcon(context, menu, R.id.action_replace_tags))
        menuItems.put(R.id.action_select_none, BaseViewModel.setupIcon(context, menu, R.id.action_select_none))
        menuItems.put(R.id.action_select_all, BaseViewModel.setupIcon(context, menu, R.id.action_select_all))
        menuItems.put(R.id.action_select_invert, BaseViewModel.setupIcon(context, menu, R.id.action_select_invert))
    }

    /**
     * Delete the selected tags
     */
    private fun onDeleteTags(): Boolean {
        // Are any selected?
        val selCount = tagViewModel.selection.selectedCount.value?: 0
        if (selCount > 0) {
            // Make sure we really want to delete the tags
            tagViewModel.viewModelScope.launch {
                val result = coroutineAlert(requireContext(), { false }) { alert ->
                    alert.builder.setMessage(
                        requireContext().resources.getQuantityString(
                            R.plurals.ask_delete_tags,
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
                    tagViewModel.repo.deleteSelectedTags()
                }
            }
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
            val tag = if (tagId != 0L) {
                // Got an id, so get the tag
                tagViewModel.repo.getTag(tagId)?: let {
                    // Tag not found. Clear the last selected tag and return
                    tagViewModel.selection.clearLastSelection()
                    return@launch
                }
            } else {
                // Create a new TagEntity
                TagEntity(0, "", "", 0)
            }

            addOrEdit(tag, layoutInflater, tagViewModel.repo, this)
        }
    }

    /**
     * Update the tags fragment action menu
     */
    private fun updateMenu() {
        // Get the has selected state for books and tags
        val selectedCount = tagViewModel.selection.selectedCount.value?: 0
        val itemCount = tagViewModel.selection.itemCount.value?: 0
        val lastSelection = tagViewModel.selection.lastSelection.value
        val booksSelectedCount = booksViewModel.selection.selectedCount.value?: 0

        menuItems.get(R.id.action_delete)?.isEnabled = selectedCount > 0
        menuItems.get(R.id.action_edit)?.isEnabled = lastSelection != null
        // Enable add and remove tags if any books and any tags are selected
        menuItems.get(R.id.action_add_tags)?.isEnabled = booksSelectedCount > 0 && selectedCount > 0
        menuItems.get(R.id.action_remove_tags)?.isEnabled = booksSelectedCount > 0 && selectedCount > 0
        // Enable replace tags if any books are selected
        menuItems.get(R.id.action_replace_tags)?.isEnabled = booksSelectedCount > 0
        // Enable select none when something is selected
        menuItems.get(R.id.action_select_none)?.isEnabled = selectedCount > 0
        // Enable select all when something is not selected
        menuItems.get(R.id.action_select_all)?.isEnabled = selectedCount < itemCount
    }

    companion object {
        suspend fun addOrEdit(tag: TagEntity, layoutInflater: LayoutInflater, repo: BookRepository, scope: CoroutineScope): Long {
            // Inflate the content for the edit tag dialog
            val content = layoutInflater.inflate(R.layout.tags_edit_tag, null)!!
            // Fill in the name
            val name = content.findViewById<EditText>(R.id.edit_tag_name)
            name.setText(tag.name, TextView.BufferType.EDITABLE)
            // Fill in the description
            val desc = content.findViewById<EditText>(R.id.edit_tag_desc)
            desc.setText(tag.desc, TextView.BufferType.EDITABLE)

            return scope.coroutineAlert(layoutInflater.context, { 0L }) { alert ->
                // Build the tag edit dialog. Don't listen for OK or Cancel.
                // OK is handled in an on onClick listener, so we can cancel the OK
                // Cancel doesn't need to do anything
                alert.builder.setTitle(if (tag.id == 0L) R.string.add_title else R.string.edit_title)
                    .setMessage(if (tag.id == 0L) R.string.add_message else R.string.edit_message)
                    .setCancelable(true)
                    .setView(content)
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null)
            }.setPosListener {alert, _, _ ->
                tag.name = name.text.toString().trim { it <= ' ' }
                tag.desc = desc.text.toString().trim { it <= ' ' }

                // Add or update the tag
                val id = repo.addOrUpdateTag(tag) {
                    // We got a conflict, ask the use if that is OK
                    // Here we suspend the coroutine, until the user replies
                    // The return value of CoroutineAlert.show() is the return
                    // value of the this lambda
                    coroutineAlert(layoutInflater.context, { false }) {alert ->
                        // Present the dialog
                        alert.builder.setTitle(R.string.tag_conflict_title)
                            .setMessage(if (tag.id == 0L) R.string.tag_conflict_add_message else R.string.tag_conflict_edit_message)
                            .setCancelable(false)
                            .setPositiveButton(R.string.yes, null)
                            .setNegativeButton(R.string.no, null)   // Don't nee to do anything for No
                    }.setPosListener {alert, _, _ ->
                        alert.result = true
                        true
                    }.show()
                }
                alert.result = id

                // Dismiss if the id isn't 0
                id != 0L
            }.show {
                // Make sure we start with the name selected
                layoutInflater.context.getSystemService(InputMethodManager::class.java)?.also {imm ->
                    name.requestFocus()
                    imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
}