package com.github.cleveard.BiblioTech.ui.books

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import com.github.cleveard.BiblioTech.db.*
import com.github.cleveard.BiblioTech.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.BiblioTech.utils.GenericViewModel
import kotlinx.coroutines.launch
import java.lang.StringBuilder
import java.text.DateFormat


/**
 * Paging Adapter for the Books fragment book list
 */
internal open class BooksAdapter(context: Context, private val viewModel: GenericViewModel<BookAndAuthors>) :
    PagingDataAdapter<Any, BooksAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Format to show dates
     */
    private val format = DateFormat.getDateInstance(DateFormat.SHORT, context.resources.configuration.locales[0])

    init {
        // Initialize the no thumbnail image
        getNoThumb(context)
    }

    companion object {
        /**
         * The image to use when a thumbnail isn't available
         */
        private var m_nothumb: Drawable? = null

        /**
         * Change the visibility of the book details view
         * @param visible True for visible, false for gone
         * @param arg1 The parent of the book details view
         */
        private fun changeViewVisibility(
            visible: Boolean,
            arg1: View
        ) {
            val view = arg1.findViewById<View>(R.id.book_list_open)
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }

        /**
         * Toggle the visibility of the book details view
         * @param arg1 The parent of the book details view
         */
        fun toggleViewVisibility(arg1: View): Boolean {
            val view = arg1.findViewById<View>(R.id.book_list_open)
            val visible = view.visibility != View.VISIBLE
            changeViewVisibility(visible, arg1)
            return visible
        }

        /**
         * Get the thumbnail to use before we get the right one
         * @param context Application context
         */
        private fun getNoThumb(context: Context): Drawable? {
            // Is this already done
            if (m_nothumb == null) {
                // Get the thumbnail
                m_nothumb = context.resources.getDrawable(R.drawable.nothumb, null)
            }
            return m_nothumb
        }

        /**
         * Set a string in text view
         * @param parent The parent view of the text view
         * @param id The id of the text view
         */
        private fun String?.setField(parent: View, id: Int) {
            val text = parent.findViewById<TextView>(id)
            text.text = this ?: ""
        }

        /**
         * Build a string from a list and set it on to a text view
         * @param parent The parent view of the text view
         * @param id The id of the text view
         * @param separator The string used to separate items in the list
         * @param toString Lambda to convert the list contents to a string
         */
        private fun <T> List<T>?.setField(parent: View, id: Int, separator: String,
                                          toString: (T) -> String) {
            // Get the text view
            val text = parent.findViewById<TextView>(id)
            if (this == null) {
                // Null list - set the view to ""
                text.text = ""
            } else {
                // Build the string
                val value = StringBuilder()
                for (entry in this) {
                    // Convert the entry to a string
                    val s = toString(entry)
                    if (s.isNotEmpty()) {
                        // Append the separator and then the string
                        if (value.isNotEmpty())
                            value.append(separator)
                        value.append(s)
                    }
                }
                text.text = value
            }
        }

        /**
         * Diff callback for items from the book stream
         */
        val DIFF_CALLBACK =
            object: DiffUtil.ItemCallback<Any>() {
                /**
                 * @inheritDoc
                 */
                override fun areItemsTheSame(
                    oldBook: Any, newBook: Any): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return (oldBook as? BookAndAuthors)?.book?.id == (newBook as? BookAndAuthors)?.book?.id
                }

                /**
                 * @inheritDoc
                 */
                @SuppressLint("DiffUtilEquals")
                override fun areContentsTheSame(
                    oldBook: Any, newBook: Any): Boolean {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldBook == newBook
                }
            }
    }

    /**
     * ViewHolder for the adapter - Nothing is added
     */
    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    /**
     * !inheritDoc
     * We get two types of object from the stream BookAndAuthors objects
     * or string for the separators. Return the appropriate layout id for each
     */
    override fun getItemViewType(position: Int): Int {
        if (getItem(position) is BookAndAuthors)
            return R.layout.books_adapter_book_item
        return R.layout.books_adapter_header_item
    }

    /**
     * !inheritDoc
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        // Inflate the custom layout - Note we use the viewType returned from getItemViewType
        val contactView: View = inflater.inflate(viewType, parent, false)
        // Create the holder
        val holder = ViewHolder(contactView)
        if (viewType == R.layout.books_adapter_book_item) {
            // This is a book we want to display
            contactView.setOnClickListener {
                // When a book is clicked, toggle the visibility of
                // the book details
                toggleViewVisibility(it)
            }
            // When the view flipper is click, change the view and toggle it's selection
            contactView.findViewById<ViewFlipper>(R.id.book_list_flipper).setOnClickListener {
                if (it is ViewFlipper) {
                    getItem(holder.layoutPosition)?.apply {
                        this as BookAndAuthors
                        viewModel.selection.toggle(book.id)
                    }
                }
            }
            // When the books link is clicked go to the web site
            contactView.findViewById<TextView>(R.id.book_list_link).setOnClickListener {
                if (it is TextView) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.text.toString()))
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Log.e("BiblioTech", "Failed to launch browser")
                    }
                }
            }
        }

        // Return a new holder instance
        return holder
    }

    /**
     * Bind a thumbnail to the view to display it
     * @param bookId The book id
     * @param large True for the large thumbnail, false for the small one
     * @param holder The view holder for the item
     * @param viewId The viewId for the view the thumbnail is bound to
     */
    private fun bindThumb(bookId: Long, large: Boolean, holder: ViewHolder, viewId: Int) {
        // Start a coroutine
        viewModel.viewModelScope.launch {
            // Get the thumbnail
            BookRepository.repo.getThumbnail(
                bookId,
                large
            )?.also {
                // Ok we got it, make sure the view holder is referring to the same book
                val pos = holder.layoutPosition
                if (pos in 0 until itemCount) {
                    getItem(pos)?.apply {
                        this as BookAndAuthors
                        // Set the thumbnail if the ids are the same
                        if (book.id == bookId)
                            holder.itemView.findViewById<ImageView>(viewId).setImageBitmap(it)
                    }
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Get the item
        val item = getItem(position)?: return
        val book: BookAndAuthors? = item as? BookAndAuthors

        // If it isn't a book, it had better be a Spannable
        if (book == null) {
            val spannable = item as? Spannable
            // If it isn't either, the make it invisible
            if (spannable == null) {
                holder.itemView.visibility = View.GONE
                return
            }
            // Set the text on the separator view and make it visible
            holder.itemView.visibility = View.VISIBLE
            (holder.itemView as TextView).text = spannable
            return
        }

        // Make sure view is visible
        holder.itemView.visibility = View.VISIBLE
        // Get the views to the thumbnails
        val thumbSmall =
            holder.itemView.findViewById<ImageView>(R.id.book_list_thumb)
        val thumbLarge =
            holder.itemView.findViewById<ImageView>(R.id.book_thumb)

        // Set the fields for the views using the extension functions
        book.book.title.setField(holder.itemView, R.id.book_list_title)
        book.book.subTitle.setField(holder.itemView, R.id.book_list_subtitle)
        book.authors.setField(holder.itemView, R.id.book_list_authors, ", ") {
            // Usually we use "first name last name", but if either is missing, just use the other
            if (it.lastName != "") {
                if (it.remainingName != "")
                    "${it.remainingName} ${it.lastName}"
                else
                    it.lastName
            } else if (it.remainingName != "")
                it.remainingName
            else
                ""
        }
        book.categories.setField(holder.itemView, R.id.book_categories, ", ") {
            it.category
        }
        book.tags.setField(holder.itemView, R.id.book_tags, ", ") {
            it.name
        }
        book.book.description.setField(holder.itemView, R.id.book_desc)
        book.book.volumeId.setField(holder.itemView, R.id.book_volid)
        book.book.ISBN.setField(holder.itemView, R.id.book_isbn)
        book.book.linkUrl.setField(holder.itemView, R.id.book_list_link)
        book.book.pageCount.toString().setField(holder.itemView, R.id.book_list_pages)
        // Set the rating
        holder.itemView.findViewById<RatingBar>(R.id.book_list_rating).rating = book.book.rating.toFloat()
        // Set the dates using the date format
        format.format(book.book.added).setField(holder.itemView, R.id.book_list_added)
        format.format(book.book.modified).setField(holder.itemView, R.id.book_list_modified)
        // Set the icon to the thumbnail or selected icon
        val box = holder.itemView.findViewById<ViewFlipper>(R.id.book_list_flipper)
        box.displayedChild = if (book.selected) 1 else 0
        // Make the details invisible
        changeViewVisibility(false, holder.itemView)

        // Set the default thumbnails and get the real ones
        thumbSmall.setImageDrawable(m_nothumb)
        thumbLarge.setImageResource(0)
        bindThumb(book.book.id, false, holder, R.id.book_list_thumb)
        bindThumb(book.book.id, true, holder, R.id.book_thumb)
    }
}