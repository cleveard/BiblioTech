package com.example.cleve.bibliotech.ui.tags

import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ActionMenuView
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.TagEntity
import com.example.cleve.bibliotech.utils.BaseViewModel
import kotlinx.android.synthetic.main.book_layout.view.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 * Fragment for handling tags
 */
class TagsFragment : Fragment() {
    private lateinit var tagViewModel: TagViewModel
    private lateinit var pagerJob: Job
    private lateinit var addItem: MenuItem
    private lateinit var deleteItem: MenuItem
    private lateinit var editItem: MenuItem
    private val observer: Observer<Boolean?> = Observer<Boolean?> { updateMenu() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val content = inflater.inflate(R.layout.fragment_tags, container, false)
        tagViewModel =
            ViewModelProviders.of(activity!!).get(TagViewModel::class.java)

        setupRecyclerView(content)
        setupActionMenu(content)

        tagViewModel.selection.hasSelection.observe(this, observer)

        return content
    }

    override fun onDestroyView() {
        pagerJob.cancel()
        tagViewModel.selection.hasSelection.removeObserver(observer)
        super.onDestroyView()
    }

    private fun setupRecyclerView(content: View) {
        tagViewModel.adapter = TagsAdapter(tagViewModel)

        val config = PagingConfig(pageSize = 20)
        val pager = Pager(
            config
        ) {
            tagViewModel.repo.getTags()
        }
        val flow = tagViewModel.applySelectionTransform(pager.flow)
            /* .map { pagingData ->
                pagingData.insertHeaderItem(Tag(TagEntity(0, "", "")))
            }*/
            .cachedIn(tagViewModel.viewModelScope)
        pagerJob = tagViewModel.viewModelScope.launch {
            flow.collectLatest { data -> tagViewModel.adapter.submitData(data)
            }
        }
        val tags = content.findViewById<RecyclerView>(R.id.tags_list)
        tags.layoutManager = LinearLayoutManager(activity)
        tags.adapter = tagViewModel.adapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_tag -> onNewTag()
            R.id.action_delete -> onDeleteTags()
            R.id.action_edit -> onEditTag()
            R.id.action_select_none -> {
                tagViewModel.selection.selectAll(false)
                return true
            }
            R.id.action_select_all -> {
                tagViewModel.selection.selectAll(true)
                return true
            }
            R.id.action_select_invert -> {
                tagViewModel.selection.invert()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupActionMenu(content: View) {
        val actionMenuView = content.findViewById<ActionMenuView>(R.id.tags_toolbar)

        val menu = actionMenuView.menu
        actionMenuView.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }

        // open a menu xml into the action menu
        activity?.menuInflater?.inflate(R.menu.tag_fragment, menu)

        deleteItem = BaseViewModel.setupIcon(context, menu, R.id.action_delete)
        editItem = BaseViewModel.setupIcon(context, menu, R.id.action_edit)
    }

    private fun onDeleteTags(): Boolean {
        val tagIds = tagViewModel.selection.selection
        val inverted = tagViewModel.selection.inverted
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
                    // OK pressed delete the books
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

    private fun onEditTag(): Boolean {
        val id = tagViewModel.selection.lastSelection.value?: return false
        addOrEdit(id)
        return true
    }

    private fun onNewTag(): Boolean {
        addOrEdit(0L)
        return true
    }

    private fun addOrEdit(tagId: Long) {
        tagViewModel.viewModelScope.launch {
            var tag: TagEntity?
            if (tagId != 0L) {
                tag = tagViewModel.repo.getTag(tagId)
                if (tag == null)
                    tagViewModel.selection.clearLastSelection()
            } else
                tag = TagEntity(0, "", "")

            if (tag != null) {
                val content = layoutInflater.inflate(R.layout.edit_tag, null)!!
                val name = content.findViewById<EditText>(R.id.edit_tag_name)
                name.setText(tag.name, TextView.BufferType.EDITABLE)
                val desc = content.findViewById<EditText>(R.id.edit_tag_desc)
                desc.setText(tag.desc, TextView.BufferType.EDITABLE)

                val alert = AlertDialog.Builder(context!!)
                    .setTitle(if (tagId == 0L) R.string.add_title else R.string.edit_title)
                    .setMessage(if (tagId == 0L) R.string.add_message else R.string.edit_message)
                    .setCancelable(true)
                    .setView(content)
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null)
                    .create()

                alert.setOnShowListener {
                    val ok = alert.getButton(AlertDialog.BUTTON_POSITIVE)
                    context?.getSystemService(InputMethodManager::class.java)?.also {imm ->
                        name.requestFocus()
                        imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
                    }

                    ok.setOnClickListener {
                        tag.name = name.text.toString()
                        tag.desc = desc.text.toString()

                        tagViewModel.viewModelScope.launch {
                            tagViewModel.repo.addOrUpdateTag(tag) {_ ->
                                suspendCoroutine { cont ->
                                    tagViewModel.viewModelScope.launch {
                                        var accept = false
                                        AlertDialog.Builder(context!!)
                                            .setTitle(R.string.tag_conflict_title)
                                            .setMessage(if (tag.id == 0L) R.string.tag_conflict_add_message else R.string.tag_conflict_edit_message)
                                            .setCancelable(false)
                                            .setPositiveButton(R.string.yes, { _, _ ->
                                                accept = true
                                            })
                                            .setNegativeButton(R.string.no, null)
                                            .setOnDismissListener {
                                                cont.resume(accept)
                                            }
                                            .show()
                                    }
                                }
                            }

                            alert.dismiss()
                        }
                    }
                }

                alert.show()
            }
        }
    }

    private fun updateMenu() {
        val selectedTags = tagViewModel.selection.hasSelection.value?: false
        val lastSelection = tagViewModel.selection.lastSelection.value

        deleteItem.isEnabled = selectedTags
        editItem.isEnabled = lastSelection != null
    }
}