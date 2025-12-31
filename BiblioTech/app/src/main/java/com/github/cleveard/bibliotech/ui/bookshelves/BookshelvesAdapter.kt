package com.github.cleveard.bibliotech.ui.bookshelves

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal abstract class BookshelvesAdapter(private val scope: CoroutineScope) :
    PagingDataAdapter<BookshelfAndTag, BookshelvesAdapter.ViewHolder>(DIFF_CALLBACK) {

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

    abstract suspend fun toggleTagAndBookshelfLink(shelf: BookshelfAndTag)

    abstract suspend fun onRefreshClicked(shelf: BookshelfAndTag, button: MaterialButton)

    /**
     * ViewHolder for the adapter. Just the same as the Recycler ViewHolder
     */
    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var shelf: BookshelfAndTag? = null
    }

    /**
     * @inheritDoc
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        // Inflate the custom layout
        val contentView: View = inflater.inflate(R.layout.bookshelf_content, parent, false)
        val holder = ViewHolder(contentView)

        return holder
    }

    /**
     * @inheritDoc
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shelf = getItem(position)
        holder.shelf = shelf

        fun goneOrVisible(id: Int, value: String?) {
            holder.itemView.findViewById<TextView>(id).let {
                if (value.isNullOrEmpty())
                    it.visibility = View.GONE
                else {
                    it.visibility = View.VISIBLE
                    it.text = value
                }
            }
        }
        // Set the name of the tag
        goneOrVisible(R.id.bookshelf_title, shelf?.bookshelf?.title)
        // Set the description.
        goneOrVisible(R.id.bookshelf_description, shelf?.bookshelf?.description)
        // Set the self link
        goneOrVisible(R.id.bookshelf_self_link, shelf?.bookshelf?.selfLink)
        // Note whether this shelf is linked to a tag
        holder.itemView.findViewById<MaterialButton>(R.id.linked).let<MaterialButton, Unit> { button ->
            button.isChecked = shelf?.tag != null
            button.setOnClickListener {
                holder.shelf?.let {shelf ->
                    scope.launch {
                        toggleTagAndBookshelfLink(shelf)
                        notifyItemChanged(holder.layoutPosition)
                    }
                }
            }
        }
        // Action to refresh the shelf and volumes
        holder.itemView.findViewById<MaterialButton>(R.id.refresh_shelf).let<MaterialButton, Unit> { button ->
            button.setOnClickListener {
                holder.shelf?.let { shelf ->
                    scope.launch {
                        onRefreshClicked(shelf, button)
                        notifyItemChanged(holder.layoutPosition)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.itemView.findViewById<MaterialButton>(R.id.refresh_shelf).setOnClickListener(null)
        holder.itemView.findViewById<MaterialButton>(R.id.linked).setOnClickListener(null)
    }
}
