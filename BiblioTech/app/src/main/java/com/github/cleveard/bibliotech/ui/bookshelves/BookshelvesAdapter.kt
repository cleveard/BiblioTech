package com.github.cleveard.bibliotech.ui.bookshelves

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.utils.ParentAccess

internal class BookshelvesAdapter(private val access: ParentAccess) :
    PagingDataAdapter<BookshelfAndTag, BookshelvesAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Background color for selected shelves. Loaded from resource. Default to CYAN
     */
    var selectColor: Int = 0x00FFFF     // CYAN

    companion object {
        /**
         * Comparisons for shelves
         */
        val DIFF_CALLBACK =
            object: DiffUtil.ItemCallback<BookshelfAndTag>() {
                override fun areItemsTheSame(
                    oldShelf: BookshelfAndTag, newShelf: BookshelfAndTag
                ): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return oldShelf.bookshelf.id == newShelf.bookshelf.id
                }
                override fun areContentsTheSame(
                    oldShelf: BookshelfAndTag, newShelf: BookshelfAndTag
                ): Boolean {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldShelf == newShelf
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
        val contactView: View = inflater.inflate(R.layout.bookshelf_content, parent, false)
        val holder = ViewHolder(contactView)
        // Set a click listener to toggle the shelf selection
        contactView.setOnClickListener {view ->
            (view.tag as? Long)?.let {id ->
                access.toggleSelection(id, holder.layoutPosition)
                notifyItemChanged(holder.layoutPosition)
            }
        }

        return holder
    }

    /**
     * @inheritDoc
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shelf = getItem(position)
        val name = holder.itemView.findViewById<TextView>(R.id.tag_name)

        // Set the name of the tag
        holder.itemView.findViewById<EditText>(R.id.title).setText(shelf?.bookshelf?.title ?: "", TextView.BufferType.EDITABLE)
        val id = shelf?.id ?: 0L
        // Set the id of the tag
        holder.itemView.tag = id
        // Set the background color
        holder.itemView.setBackgroundColor(
            if (shelf?.bookshelf?.isSelected == true)
                selectColor
            else
                Color.WHITE
        )
        // Set the description.
        holder.itemView.findViewById<EditText>(R.id.bookshelf_description).setText(shelf?.bookshelf?.description ?: "", TextView.BufferType.EDITABLE)
        // Set the self link
        holder.itemView.findViewById<TextView>(R.id.bookshelf_description).text = shelf?.bookshelf?.selfLink ?: ""
        // Note whether this shelf is linked to a tag
        holder.itemView.findViewById<SwitchCompat>(R.id.linked).isChecked = shelf?.tag!= null
    }
}
