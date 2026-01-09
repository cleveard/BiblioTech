package com.github.cleveard.bibliotech.ui.tags

import android.graphics.Color
import android.view.LayoutInflater
import com.github.cleveard.bibliotech.db.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.utils.ParentAccess

/**
 * Recycler view adaptor to for tags in the book database
 * @param access The view model for the tag fragment
 */
internal open class TagsAdapter(private val access: ParentAccess) :
    PagingDataAdapter<TagEntity, TagsAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Background color for selected tags. Loaded from resource. Default to CYAN
     */
    var selectColor: Int = 0x00FFFF     // CYAN

    companion object {
        /**
         * Comparisons for tags
         */
        val DIFF_CALLBACK =
            object: DiffUtil.ItemCallback<TagEntity>() {
                override fun areItemsTheSame(
                    oldTag: TagEntity, newTag: TagEntity
                ): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return oldTag.id == newTag.id
                }
                override fun areContentsTheSame(
                    oldTag: TagEntity, newTag: TagEntity
                ): Boolean {
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
            (view.tag as? TagEntity)?.let {tag ->
                access.toggleSelection(tag.id, !tag.hasBookshelf, holder.layoutPosition)
                notifyItemChanged(holder.layoutPosition)
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
        name.text = tag?.name ?: ""
        // Set the id of the tag
        holder.itemView.tag = tag
        // Set the background color
        holder.itemView.setBackgroundColor(
            if (tag?.isSelected == true)
                selectColor
            else
                Color.WHITE
        )
        // Set the description.
        val desc = tag?.desc
        val descView = holder.itemView.findViewById<TextView>(R.id.tags_desc)
        descView.text = desc?: ""
    }
}