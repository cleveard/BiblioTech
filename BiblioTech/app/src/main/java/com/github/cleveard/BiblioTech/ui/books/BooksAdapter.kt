package com.github.cleveard.BiblioTech.ui.books

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import com.github.cleveard.BiblioTech.db.*
import com.github.cleveard.BiblioTech.*
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.BiblioTech.ui.widget.ChipBox
import com.github.cleveard.BiblioTech.utils.ParentAccess
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.StringBuilder
import java.text.DateFormat

/**
 * Paging Adapter for the Books fragment book list
 */
internal open class BooksAdapter(private val access: ParentAccess, private val bookLayout: Int = R.layout.books_adapter_book_item) :
    PagingDataAdapter<Any, BooksAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Format to show dates
     */
    private val format = DateFormat.getDateInstance(DateFormat.SHORT, access.context.resources.configuration.locales[0])

    init {
        // Initialize the no thumbnail image
        getNoThumb(access.context)
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
            view?.visibility = if (visible) View.VISIBLE else View.GONE
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
            text?.text = this ?: ""
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
            parent.findViewById<TextView>(id)?.let { text ->
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
        }

        /**
         * Set chips in a ChipBox
         * @param parent The parent view of the text view
         * @param id The id of the text view
         * @param toString Lambda to convert the list contents to a string
         */
        private fun <T> List<T>?.setChips(scope: CoroutineScope, parent: View, id: Int,
                                          toString: (T) -> String) {
            // Get the text view
            parent.findViewById<ChipBox>(id)?.let { box ->
                box.setChips(scope, this?.run {
                    this.asSequence().map(toString)
                }?: emptySequence())
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
     * Get the book at position or null
     * @param position The position of the book
     */
    fun getBook(position: Int): BookAndAuthors? {
        return if (position in 0 until itemCount)
            getItem(position) as? BookAndAuthors
        else
            null
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
            return bookLayout
        return R.layout.books_adapter_header_item
    }

    /**
     * Listener to open and close the book item
     */
    private val openCloseListener: View.OnClickListener = View.OnClickListener {
        // When a book is clicked, toggle the visibility of
        // the book details
        toggleViewVisibility(it)
    }

    /**
     * Toggle the visibility of the book details view
     * @param arg1 The parent of the book details view
     */
    private fun toggleViewVisibility(arg1: View): Boolean {
        val view = arg1.findViewById<View>(R.id.book_list_open)
        val visible = view?.visibility != View.VISIBLE
        changeViewVisibility(visible, arg1)
        (arg1.tag as? Long)?.let {id -> access.toggleOpen(id) }
        return visible
    }

    /**
     * Listener to select a book when flipper is clicked
     */
    private inner class ClickFlipperListener(private val holder: ViewHolder): View.OnClickListener {
        override fun onClick(v: View?) {
            if (v is ViewFlipper) {
                val pos = holder.layoutPosition
                getItem(pos)?.apply {
                    this as BookAndAuthors
                    access.toggleSelection(book.id, pos)
                }
            }
        }
    }

    /**
     * Listener to launch browser when link is clicked
     */
    private val clickLinkListener: View.OnClickListener = View.OnClickListener {
        if (it is TextView) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.text.toString()))
                access.context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("BiblioTech", "Failed to launch browser")
            }
        }
    }

    /**
     * Delegate for interacting with ChipBox
     */
    private inner class ChipBoxDelegate(val holder: ViewHolder): ChipBox.Delegate {
        /**
         * Job used to get the autocomplete Cursors
         */
        private var autoCompleteJob: Job? = null

        /**
         * Provide a CoroutineScope for the ChipBox
         */
        override val scope = access.scope

        /**
         * Create a chip
         */
        override suspend fun onCreateChip(
            chipBox: ChipBox,
            text: String,
            scope: CoroutineScope
        ): Chip? {
            if (holder.layoutPosition in 0 until itemCount &&
                (getItem(holder.layoutPosition) as? BookAndAuthors)?.let { book ->
                    access.addTag(chipBox.context, book, text)
                } == true
            ) {
                // We added the tag, create the chip
                return super.onCreateChip(chipBox, text, scope)
            }

            // No tag, return null
            return null
        }

        /**
         * Remove the tag when the chip is removed
         */
        override suspend fun onChipRemoved(chipBox: ChipBox, chip: View, scope: CoroutineScope) {
            chip as Chip
            (getItem(holder.layoutPosition) as? BookAndAuthors)?.let { book ->
                access.removeTag(chipBox.context, book, chip.text.toString())
            }
        }

        private fun getQuery(edit: EditText): Cursor {
            // Extract the token at the end of the selection
            val token = edit.text.toString().trim { it <= ' ' }
            // return the query string
            return ColumnDataDescriptor.buildAutoCompleteCursor(
                BookRepository.repo, TAGS_ID_COLUMN,
                TAGS_NAME_COLUMN, TAGS_TABLE, token
            )
        }

        override fun onEditorFocusChange(chipBox: ChipBox, edit: View, hasFocus: Boolean) {
            // Cancel existing jobs
            autoCompleteJob?.let {
                it.cancel()
                autoCompleteJob = null
            }
            // Get the value field
            edit as AutoCompleteTextView
            if (hasFocus) {
                // Setting focus, setup adapter and set it in the text view
                // This is done in a coroutine job and we use the job
                // to flag that the job is still active. When we lose focus
                // we cancel the job if it is still active
                autoCompleteJob = access.scope.launch {
                    // Get the cursor for the column, null means no auto complete
                    val cursor = withContext(BookRepository.repo.queryScope.coroutineContext) {
                        getQuery(edit)
                    }

                    // Get the adapter from the column description
                    val adapter = SimpleCursorAdapter(
                        edit.context,
                        R.layout.books_drawer_filter_auto_complete,
                        cursor,
                        arrayOf("_result"),
                        intArrayOf(R.id.auto_complete_item),
                        0
                    )
                    adapter.stringConversionColumn = cursor.getColumnIndex("_result")
                    adapter.setFilterQueryProvider { getQuery(edit) }

                    // Set the adapter on the text view
                    edit.setAdapter(adapter)
                    // Flag that the job is done
                    autoCompleteJob = null
                }
            } else {
                // Clear the adapter
                edit.setAdapter(null)
            }
        }
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
        if (viewType != R.layout.books_adapter_header_item) {
            // This is a book we want to display
            contactView.setOnClickListener(openCloseListener)
            // When the view flipper is click, change the view and toggle it's selection
            contactView.findViewById<ViewFlipper>(R.id.book_list_flipper)?.setOnClickListener(ClickFlipperListener(holder))
            // When the books link is clicked go to the web site
            contactView.findViewById<TextView>(R.id.book_list_link)?.setOnClickListener(clickLinkListener)
            contactView.findViewById<ChipBox>(R.id.book_tags)?.let {
                it.delegate = ChipBoxDelegate(holder)
                val edit = it.textView as AutoCompleteTextView
                edit.threshold = 1
                // If the view is an AutoComplete view, then add a chip when an item is selected
                edit.onItemClickListener =
                    AdapterView.OnItemClickListener { _, _, _, _ ->
                        it.onCreateChipAction()
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
        holder.itemView.findViewById<ImageView>(viewId)?.let { imageView ->
            // Start a coroutine
            access.scope.launch {
                // Get the thumbnail
                access.getThumbnail(
                    bookId,
                    large
                )?.also {
                    // Ok we got it, make sure the view holder is referring to the same book
                    val pos = holder.layoutPosition
                    if (pos in 0 until itemCount) {
                        getItem(pos)?.apply {
                            (this as? BookAndAuthors)?.let { book ->
                                // Set the thumbnail if the ids are the same
                                if (book.book.id == bookId)
                                    imageView.setImageBitmap(it)
                            }
                        }
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

        holder.itemView.tag = book.book.id
        // Make sure view is visible
        holder.itemView.visibility = View.VISIBLE

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
        book.tags.setChips(access.scope, holder.itemView, R.id.book_tags) {
            it.name
        }
        book.book.description.setField(holder.itemView, R.id.book_desc)
        book.book.volumeId.setField(holder.itemView, R.id.book_volid)
        book.book.ISBN.setField(holder.itemView, R.id.book_isbn)
        book.book.linkUrl.setField(holder.itemView, R.id.book_list_link)
        book.book.pageCount.toString().setField(holder.itemView, R.id.book_list_pages)
        // Set the rating
        holder.itemView.findViewById<RatingBar>(R.id.book_list_rating)?.rating = book.book.rating.toFloat()
        // Set the dates using the date format
        format.format(book.book.added).setField(holder.itemView, R.id.book_list_added)
        format.format(book.book.modified).setField(holder.itemView, R.id.book_list_modified)
        // Set the icon to the thumbnail or selected icon
        val box = holder.itemView.findViewById<ViewFlipper>(R.id.book_list_flipper)
        box?.displayedChild = if (book.selected) 1 else 0
        // Make the details invisible
        changeViewVisibility(access.isOpen(book.book.id), holder.itemView)
        // Set the default thumbnails and get the real ones
        holder.itemView.findViewById<ImageView>(R.id.book_list_thumb)?.setImageDrawable(m_nothumb)
        holder.itemView.findViewById<ImageView>(R.id.book_thumb)?.setImageResource(0)
        bindThumb(book.book.id, false, holder, R.id.book_list_thumb)
        bindThumb(book.book.id, true, holder, R.id.book_thumb)
    }
}
