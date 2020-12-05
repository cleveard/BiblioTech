package com.github.cleveard.BiblioTech.ui.tags

import android.graphics.Color
import android.view.LayoutInflater
import com.github.cleveard.BiblioTech.db.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.BiblioTech.R
import com.github.cleveard.BiblioTech.utils.ParentAccess

/**
 * Recycler view adaptor to for tags in the book database
 * @param access The view model for the tag fragment
 */
internal open class TagsAdapter(private val access: ParentAccess) :
    PagingDataAdapter<Tag, TagsAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Background color for selected tags. Loaded from resource. Default to CYAN
     */
    var selectColor: Int = 0x00FFFF     // CYAN

    companion object {
        /**
         * Comparisons for tags
         */
        val DIFF_CALLBACK =
            object: DiffUtil.ItemCallback<Tag>() {
                override fun areItemsTheSame(
                    oldTag: Tag, newTag: Tag): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return oldTag.tag.id == newTag.tag.id
                }
                override fun areContentsTheSame(
                    oldTag: Tag, newTag: Tag): Boolean {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldTag == newTag
                }
            }
    }

    /**
     * ViewHolder for the adapter. Just the same as the Recycler ViewHolder
     */
    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    /**
     * @inheritDoc
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        // Inflate the custom layout
        val contactView: View = inflater.inflate(R.layout.tags_layout, parent, false)
        val holder = ViewHolder(contactView)
        // Set a click listener to toggle the tag selection
        contactView.setOnClickListener {view ->
            (view.tag as? Long)?.let {id ->
                access.toggleSelection(id)
            }
        }

        return holder
    }

    /**
     * @inheritDoc
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = getItem(position)
        val name = holder.itemView.findViewById<TextView>(R.id.tag_name)

        // Set the name of the tag
        name.text = tag?.tag?.name ?: ""
        val id = tag?.tag?.id ?: 0L
        // Set the id of the tag
        holder.itemView.tag = id
        // Set the background color
        holder.itemView.setBackgroundColor(
            if (tag?.selected == true)
                selectColor
            else
                Color.WHITE
        )
        // Set the description.
        val desc = tag?.tag?.desc
        val descView = holder.itemView.findViewById<TextView>(R.id.tags_desc)
        descView.text = desc?: ""
    }
}