package com.github.cleveard.bibliotech.ui.interchange

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.ui.books.BooksAdapter
import com.github.cleveard.bibliotech.utils.PagingSourceList
import com.github.cleveard.bibliotech.utils.ParentAccess
import com.github.cleveard.bibliotech.utils.Thumbnails
import com.github.cleveard.bibliotech.utils.coroutineAlert
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.lang.NumberFormatException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ImportCSV {
    /**
     * Import data
     * @param nextLine Lambda to get lines from the CSV
     */
    suspend fun importData(repo: BookRepository,
                           resolution: ConflictResolution,
                           inContext: Context,
                           inScope: CoroutineScope,
                           nextLine: (MutableList<String>) -> Boolean): Boolean
    {
        // list used to hold the header and data fields
        val data = mutableListOf<String>()
        // Get the first line - should be headers.
        if (!nextLine(data))
            return false   // Empty, just return

        // Parse the headers and get the handler used to import the records
        val handler: ImportHandler<*, *> = processHeader(repo, resolution, inContext, inScope, data)

        // Read and handle the lines in the stream
        while (nextLine(data)) {
            handler.nextLine(data)
        }
        handler.flush()     // Make sure the last one is added

        if (handler.records.size > 0) {
            try {
                val result = withContext(Dispatchers.Main) {
                    var recycler: RecyclerView? = null
                    // Show a dialog with the imported entries
                    coroutineAlert(inContext, { false }) { alert ->
                        val content = LayoutInflater.from(inContext).inflate(R.layout.export_import_select_records, null)
                        // content.findViewById<TextView>(R.id.ask_message).setText(handler.askTitle)
                        recycler = content.findViewById<RecyclerView>(R.id.imported_list).apply {
                            adapter = handler.startAdapter(content)
                        }
                        alert.builder.setView(content)
                            .setPositiveButton(R.string.yes, null)
                            .setNegativeButton(R.string.no, null)
                            .setTitle(handler.askTitle)
                    }.setPosListener { alert, _, _ ->
                        alert.result = true
                        true
                    }.show {
                        recycler?.run {
                            val h = (parent as ViewGroup).height
                            if (height + y > h) {
                                layoutParams.height = (h - y).toInt()
                                requestLayout()
                            }
                        }
                    }
                }

                // Cancel if requested
                if (result)
                    handler.addSelectedRecords()
            } finally {
                handler.closeAdapter()
            }
        } else if (handler.totalCount == 0) {
            // If nothing was imported, that is an error
            throw ImportError(ImportError.ErrorCode.BAD_FORMAT, "No valid entries in CSV")
        }

        return true
    }

    /** Enum to list the possible ways to resolve conflicts */
    enum class ConflictResolution {
        REPLACE,
        KEEP,
        ASK
    }

    /**
     * Error thrown during export and import
     * @param code The error code
     * @param msg Additional message - not localized
     * @param cause Error that generated this error
     */
    class ImportError(val code: ErrorCode, msg: String? = null, cause: Throwable? = null): Exception(msg, cause) {
        enum class ErrorCode {
            CANCELED,
            BAD_FORMAT,
            IO_ERROR
        }
    }

    /**
     * Class to represent an imported column
     * @param T The data type being imported
     * @param setValue An extension lambda to set a value for a column
     * @param columnId A column id. These form bitwise masks to categorize the value
     * @param index The index of the column in the input stream
     */
    private data class ImportColumn<T>(val setValue: T.(value: String, allocated: Int) -> Int, val columnId: Int, val index: Int = -1)

    companion object {
        /** column name for the full author name */
        const val AUTHOR_NAME = "author_name"

        /** ColumnId for values in the book entity */
        private const val BOOK_VALUE = 1
        /** ColumnId for values in the author entity */
        private const val AUTHOR_VALUE = 2
        /** ColumnId for the tag entity name */
        private const val TAG_VALUE = 4
        /** ColumnId for values in the category entity */
        private const val CATEGORY_VALUE = 8
        /** ColumnId for values in the isbn entity */
        private const val ISBN_VALUE = 16
        /** The list of book columns we import */
        private val importBookColumns = mapOf<String, ImportColumn<BookAndAuthors>>(
            BOOK_ID_COLUMN to ImportColumn(
                {v, a -> book.id = v.toLong(); a },
                columnId = BOOK_VALUE
            ),
            TITLE_COLUMN to ImportColumn(
                {v, a ->  book.title = v; a },
                columnId = BOOK_VALUE
            ),
            SUBTITLE_COLUMN to ImportColumn(
                {v, a ->  book.subTitle = v; a },
                columnId = BOOK_VALUE
            ),
            VOLUME_ID_COLUMN to ImportColumn(
                {v, a ->  book.volumeId = if (v.isEmpty()) null else v; a },
                columnId = BOOK_VALUE
            ),
            SOURCE_ID_COLUMN to ImportColumn(
                {v, a ->  book.sourceId = if (v.isEmpty()) null else v; a },
                columnId = BOOK_VALUE
            ),
            DESCRIPTION_COLUMN to ImportColumn(
                {v, a ->  book.description = v; a },
                columnId = BOOK_VALUE
            ),
            PAGE_COUNT_COLUMN to ImportColumn(
                {v, a ->  book.pageCount = v.toInt(); a },
                columnId = BOOK_VALUE
            ),
            BOOK_COUNT_COLUMN to ImportColumn(
                {v, a ->  book.bookCount = v.toInt(); a },
                columnId = BOOK_VALUE
            ),
            VOLUME_LINK to ImportColumn(
                {v, a ->  book.linkUrl = v; a },
                columnId = BOOK_VALUE
            ),
            RATING_COLUMN to ImportColumn(
                {v, a ->  book.rating = v.toDouble(); a },
                columnId = BOOK_VALUE
            ),
            DATE_ADDED_COLUMN to ImportColumn(
                {v, a ->  book.added.time = v.toLong(); a },
                columnId = 0
            ),
            DATE_MODIFIED_COLUMN to ImportColumn(
                {v, a ->  book.modified.time = v.toLong(); a },
                columnId = 0
            ),
            SMALL_THUMB_COLUMN to ImportColumn(
                {v, a ->  book.smallThumb = if (v.isEmpty()) null else v; a },
                columnId = BOOK_VALUE
            ),
            LARGE_THUMB_COLUMN to ImportColumn(
                {v, a ->  book.largeThumb = if (v.isEmpty()) null else v; a },
                columnId = BOOK_VALUE
            ),
            BOOK_FLAGS to ImportColumn(
                {v, a ->  book.flags = v.toInt(); a },
                columnId = BOOK_VALUE
            ),
            AUTHOR_NAME to ImportColumn(
                {v, a ->  newAuthor(a).name = v; a or AUTHOR_VALUE },
                columnId = AUTHOR_VALUE
            ),
            CATEGORY_COLUMN to ImportColumn(
                {v, a ->  newCategory(a).category = v; a or CATEGORY_VALUE },
                columnId = CATEGORY_VALUE
            ),
            TAGS_NAME_COLUMN to ImportColumn(
                {v, a ->  newTag(a).name = v; a or TAG_VALUE },
                columnId = TAG_VALUE
            ),
            TAGS_DESC_COLUMN to ImportColumn(
                {v, a ->  newTag(a).desc = v; a or TAG_VALUE },
                columnId = 0
            ),
            ISBN_COLUMN to ImportColumn(
                {v, a ->  newIsbn(a).isbn = v; a or ISBN_VALUE },
                columnId = 0
            )
        )

        /** ColumnId for values in the view entity */
        private const val VIEW_VALUE = 1
        /** ColumnId for the view name in the book entity */
        private const val VIEW_NAME_VALUE = 2
        /** The columns we import for views */
        private val importViewColumns = mapOf<String, ImportColumn<ViewFlagsEntity>>(
            VIEWS_ID_COLUMN to ImportColumn(
                {v, a ->  view.id = v.toLong(); a },
                columnId = VIEW_VALUE
            ),
            VIEWS_NAME_COLUMN to ImportColumn(
                {v, a ->  view.name = v; a },
                columnId = VIEW_NAME_VALUE
            ),
            VIEWS_DESC_COLUMN to ImportColumn(
                {v, a ->  view.desc = v; a },
                columnId = VIEW_VALUE
            ),
            VIEWS_FILTER_COLUMN to ImportColumn(
                {v, a ->  view.filter = if (v.isEmpty()) null else BookFilter.decodeFromString(v); a },
                columnId = VIEW_VALUE
            )
        )

        private fun BookAndAuthors.newAuthor(allocated: Int): AuthorEntity {
            if ((allocated and AUTHOR_VALUE) == 0)
                (authors as ArrayList).add(AuthorEntity(id = 0L, lastName = "", remainingName = ""))
            return authors.last()
        }

        private fun BookAndAuthors.newCategory(allocated: Int): CategoryEntity {
            if ((allocated and CATEGORY_VALUE) == 0)
                (categories as ArrayList).add(CategoryEntity(id = 0L, category = ""))
            return categories.last()
        }

        private fun BookAndAuthors.newTag(allocated: Int): TagEntity {
            if ((allocated and TAG_VALUE) == 0)
                (tags as ArrayList).add(TagEntity(id = 0L, name = "", desc = "", flags = 0))
            return tags.last()
        }

        private fun BookAndAuthors.newIsbn(allocated: Int): IsbnEntity {
            if ((allocated and ISBN_VALUE) == 0)
                (isbns as ArrayList).add(IsbnEntity(id = 0L, isbn = "", flags = 0))
            return isbns.last()
        }

        private fun <T> ArrayList<T>.popDuplicate(
            match: T.(v: T) -> Boolean = { this == it },
            empty: T.() -> Boolean
        ) {
            val v = last()
            if (v.empty() || indexOfFirst { v.match(it) } < size - 1)
                removeAt(size - 1)
        }

        /**
         * Diff callback for items from the book stream
         */
        val DIFF_CALLBACK_FILTER =
            object: DiffUtil.ItemCallback<ViewFlagsEntity>() {
                /**
                 * @inheritDoc
                 */
                override fun areItemsTheSame(
                    oldFilter: ViewFlagsEntity, newFilter: ViewFlagsEntity): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return oldFilter.view.id == newFilter.view.id
                }

                /**
                 * @inheritDoc
                 */
                @SuppressLint("DiffUtilEquals")
                override fun areContentsTheSame(
                    oldFilter: ViewFlagsEntity, newFilter: ViewFlagsEntity): Boolean {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldFilter == newFilter
                }
            }
    }

    /**
     * Base class for importing books and views
     * @param columns List of valid columns in the stream from the headers
     */
    private abstract class ImportHandler<T, S>(private val resolution: ConflictResolution, val askTitle: Int, protected val columns: Map<String, ImportColumn<T>>) {
        /** Total number records from the filter */
        var totalCount: Int = 0
        /** Number of invalid record */
        var invalidCount: Int = 0
        /** Number of records without conflicts */
        var newCount: Int = 0
        /** Number of records replacing existing rows in the database */
        var replaceCount: Int = 0
        /** Number of records reject because of conflicts */
        var rejectCount: Int = 0

        /** Current record */
        protected var record: T? = null
        /** List of imported records */
        val records = ArrayList<T>()

        /**
         * Process field from the next line
         * @param data The list of fields read
         */
        abstract suspend fun nextLine(data: List<String>)

        /**
         * Add the record to the database
         * @param data The record to check
         */
        abstract suspend fun addToDatabase(data: T, conflictCallback: suspend CoroutineScope.(conflict: S) -> Boolean): Long

        /**
         * Add the current record to the imported records
         */
        suspend fun flush() {
            // Ignore if the record is null
            record?.let {data ->
                ++totalCount
                // Clear the current record
                record = null
                var hadConflict = false
                val id = try {
                    // Add the record to the database
                    addToDatabase(data) {
                        hadConflict = true
                        // If there was a conflict, then replace the conflict, if requested
                        if (resolution == ConflictResolution.REPLACE)
                            true
                        else {
                            // Add to list for asking if requested
                            if (resolution == ConflictResolution.ASK)
                                records.add(data)
                            // Don't replace now
                            false
                        }
                    }
                } catch (e: SQLiteException) {
                    0L
                }
                if (id == 0L) {
                    if (records.size > 0 && records.last() === data)
                        ++rejectCount
                    else
                        ++invalidCount
                } else if (hadConflict)
                    ++replaceCount
                else
                    ++newCount
            }
        }

        /**
         * Add selected records to data base
         */
        abstract suspend fun addSelectedRecords()

        /** Setup the adapter to show the imported books */
        abstract fun startAdapter(content: View): RecyclerView.Adapter<*>

        /** Shut down the adapter */
        abstract suspend fun closeAdapter()

        /**
         * Check to see which values in the input fields are new
         * @param data The fields input from the line
         */
        fun hasValues(data: List<String>): Int {
            var values = 0
            for (c in columns.entries) {
                if (c.value.index < data.size && data[c.value.index].isNotEmpty())
                    values = values or c.value.columnId
            }
            return values
        }
    }

    /**
     * Import handler for books
     * @param columns The book columns in the import stream
     */
    private class BookHandler(private val repo: BookRepository,
                              resolution: ConflictResolution,
                              private val inContext: Context,
                              private val inScope: CoroutineScope,
                              columns: Map<String, ImportColumn<BookAndAuthors>>)
        : ImportHandler<BookAndAuthors, BookAndAuthors>(resolution, R.string.ask_replace_message_books, columns)
    {
        /**
         * @inheritDoc
         */
        override suspend fun nextLine(data: List<String>) {
            try {
                // Do the new values contain new data?
                val values = hasValues(data)
                if (values != 0) {
                    // Yes, If the new values are in the BookEntity, flush the old one
                    if ((values and BOOK_VALUE) != 0) {
                        flush()
                        // Create the new record
                        record = BookAndAuthors(
                            book = BookEntity(
                                id = 0L,
                                title = "",
                                subTitle = "",
                                sourceId = null,
                                volumeId = null,
                                description = "",
                                pageCount = 0,
                                bookCount = 0,
                                linkUrl = "",
                                rating = 0.0,
                                seriesId = null,
                                seriesOrder = null,
                                added = Date(),
                                modified = Date(),
                                smallThumb = "",
                                largeThumb = "",
                                flags = 0
                            ),
                            series = null,
                            tags = ArrayList(),
                            authors = ArrayList(),
                            categories = ArrayList(),
                            isbns = ArrayList()
                        )
                    }

                    // Process the fields. If record is null, then the only fields
                    // that are new are author, tag or category, but without a book
                    record?.let {book ->
                        var allocated: Int = BOOK_VALUE
                        // Fill in the data
                        for (c in columns.entries) {
                            if (c.value.index < data.size) {
                                val v = data[c.value.index]
                                if (v.isNotEmpty())
                                    allocated = c.value.setValue.invoke(book, v, allocated)
                            }
                        }
                        // If an author was allocated make sure it isn't a duplicated
                        if ((allocated and AUTHOR_VALUE) != 0)
                            (book.authors as ArrayList).popDuplicate { lastName.isEmpty() and remainingName.isEmpty() }
                        // If a category was allocated make sure it isn't a duplicated
                        if ((allocated and CATEGORY_VALUE) != 0)
                            (book.categories as ArrayList).popDuplicate { category.isEmpty() }
                        // If a author was allocated make sure it isn't a duplicated
                        if ((allocated and TAG_VALUE) != 0)
                            (book.tags as ArrayList).popDuplicate({ this.name == it.name }) { name.isEmpty() }
                    } ?: ++invalidCount  // Invalid input
                }
            } catch (e: NumberFormatException) {
                // Format error
                ++invalidCount
            }
        }

        /** @inheritDoc */
        override suspend fun addSelectedRecords() {
            for (r in records) {
                if (r.book.isSelected)
                    addToDatabase(r) { true }
            }
        }

        /**
         * @inheritDoc
         */
        override suspend fun addToDatabase(data: BookAndAuthors, conflictCallback: suspend CoroutineScope.(conflict: BookAndAuthors) -> Boolean): Long {
            data.book.id = 0L
            val id = repo.addOrUpdateBook(data, conflictCallback)
            if (records.size > 0 && data == records.last())
                data.book.id = records.size.toLong()
            return id
        }

        private inner class Access: ParentAccess {
            private var source = PagingSourceList<Any>(records)
            val thumbnails = Thumbnails("import")

            fun getSource(): PagingSourceList<Any> {
                source = PagingSourceList(records)
                return source
            }
            override fun toggleSelection(id: Long, position: Int) {
                records[id.toInt() - 1].book.isSelected = !records[id.toInt() - 1].book.isSelected
            }

            override fun toggleExpanded(id: Long) {
                records[id.toInt() - 1].book.isExpanded = !records[id.toInt() - 1].book.isExpanded
            }

            override fun isOpen(id: Long): Boolean {
                return records[id.toInt() - 1].book.isExpanded
            }

            override val context: Context
                get() = inContext
            override val scope: CoroutineScope
                get() = inScope

            override suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
                return thumbnails.getThumbnail(bookId, large) { b, l ->
                    if (l)
                        records[b.toInt() - 1].book.largeThumb
                    else
                        records[b.toInt() - 1].book.smallThumb
                }
            }

            override suspend fun removeTag(ctx: Context, book: BookAndAuthors, tagName: String): Boolean {
                val i = book.tags.indexOfFirst { it.name == tagName }
                if (i >= 0)
                    (book.tags as ArrayList).removeAt(i)
                return true
            }

            override suspend fun addTag(ctx: Context, book: BookAndAuthors, tagName: String): Boolean {
                val i = book.tags.indexOfFirst { it.name == tagName }
                if (i < 0)
                    (book.tags as ArrayList).add(TagEntity(0, tagName, "", 0))
                return true
            }
        }

        var job: Job? = null
        var access: Access? = null
        /** @inheritDoc */
        override fun startAdapter(content: View): RecyclerView.Adapter<*> {
            val adapter: BooksAdapter
            access = Access().also { access ->
                adapter = BooksAdapter(access,
                    R.layout.books_adapter_book_item_always,
                    R.layout.books_adapter_book_item_detail)
                // Create the pager
                val config = PagingConfig(pageSize = 10)
                val pager = Pager(
                    config
                ) {
                    access.getSource()
                }
                // Add headers and cache
                val flow = pager.flow.cachedIn(inScope)
                // Start the flow
                job = inScope.launch {
                    flow.collectLatest { data ->
                        adapter.submitData(data)
                    }
                }
            }

            content.findViewById<MaterialButton>(R.id.action_select_all).setOnClickListener {
                for (r in records)
                    r.book.isSelected = true
                adapter.notifyDataSetChanged()
            }
            content.findViewById<MaterialButton>(R.id.action_select_none).setOnClickListener {
                for (r in records)
                    r.book.isSelected = false
                adapter.notifyDataSetChanged()
            }
            content.findViewById<MaterialButton>(R.id.action_select_invert).setOnClickListener {
                for (r in records)
                    r.book.isSelected = !r.book.isSelected
                adapter.notifyDataSetChanged()
            }

            return adapter
        }

        /** @inheritDoc */
        override suspend fun closeAdapter() {
            job?.cancel()
            job = null
            access?.thumbnails?.deleteAllThumbFiles()
            access = null
        }
    }

    data class ViewFlagsEntity(val view: ViewEntity, var flags: Int) {
        companion object {
            const val SELECTION_FLAG = 1
        }
        var isSelected: Boolean
            get() = (flags and SELECTION_FLAG) != 0
            set(v) {
                flags = if (v)
                    flags or SELECTION_FLAG
                else
                    flags and SELECTION_FLAG.inv()
            }
    }

    /**
     * Import handler for filters
     * @param columns The book columns in the import stream
     */
    private class ViewHandler(private val repo: BookRepository,
                              resolution: ConflictResolution,
                              private val inContext: Context,
                              columns: Map<String, ImportColumn<ViewFlagsEntity>>)
        : ImportHandler<ViewFlagsEntity, ViewEntity>(resolution, R.string.ask_replace_message_filters, columns)
    {
        private class ViewHolder(view: View): RecyclerView.ViewHolder(view)

        /** @inheritDoc */
        override suspend fun nextLine(data: List<String>) {
            try {
                // See if any values were loaded
                val values = hasValues(data)
                if (values != 0) {
                    // Got something
                    // Set the data for the new filter
                    val r = ViewFlagsEntity(
                        view = ViewEntity(
                            id = 0,
                            name = "",
                            desc = ""
                        ),
                        flags = 0
                    )
                    var allocated = 0
                    for (c in columns.entries) {
                        if (c.value.index < data.size) {
                            val v = data[c.value.index]
                            if (v.isNotEmpty())
                                allocated = c.value.setValue.invoke(r, v, allocated)
                        }
                    }

                    // Set the record and add it
                    record = r
                    flush()
                } else
                    ++invalidCount      // No name, invalid
            } catch (e: NumberFormatException) {
                // Format error
                ++invalidCount
            }
        }

        /** @inheritDoc */
        override suspend fun addSelectedRecords() {
            for (r in records) {
                if (r.isSelected)
                    addToDatabase(r) { true }
            }
        }

        /**
         * @inheritDoc
         */
        override suspend fun addToDatabase(data: ViewFlagsEntity, conflictCallback: suspend CoroutineScope.(conflict: ViewEntity) -> Boolean): Long {
            data.view.id = 0L
            return repo.addOrUpdateView(data.view, conflictCallback)
        }

        /** @inheritDoc */
        override fun startAdapter(content: View): RecyclerView.Adapter<*> {
            val adapter = object: ListAdapter<ViewFlagsEntity, ViewHolder>(DIFF_CALLBACK_FILTER) {
                @SuppressLint("InflateParams")
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.export_import_filter_item, null)
                    val holder = ViewHolder(view)
                    view.setOnClickListener {
                        val item = getItem(holder.layoutPosition)
                        item.isSelected = !item.isSelected
                        notifyItemChanged(holder.layoutPosition)
                    }
                    return holder
                }

                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    val item = getItem(holder.layoutPosition)
                    val name = if (item.view.name.isEmpty())
                        inContext.resources.getString(R.string.menu_books)
                    else
                        item.view.name
                    val text = if (item.view.desc.isEmpty())
                        name
                    else
                        inContext.resources.getString(R.string.name_and_desc, name, item.view.desc)
                    holder.itemView.findViewById<TextView>(R.id.view_name).text = text
                    holder.itemView.setBackgroundColor(
                        if (item.isSelected)
                            inContext.resources.getColor(R.color.colorSelect, null)
                        else
                            inContext.resources.getColor(R.color.background, null)
                    )
                }
            }

            adapter.submitList(records)

            content.findViewById<MaterialButton>(R.id.action_select_all).setOnClickListener {
                for (r in records)
                    r.isSelected = true
                adapter.notifyDataSetChanged()
            }
            content.findViewById<MaterialButton>(R.id.action_select_none).setOnClickListener {
                for (r in records)
                    r.isSelected = false
                adapter.notifyDataSetChanged()
            }
            content.findViewById<MaterialButton>(R.id.action_select_invert).setOnClickListener {
                for (r in records)
                    r.isSelected = !r.isSelected
                adapter.notifyDataSetChanged()
            }

            return adapter
        }

        /** @inheritDoc */
        override suspend fun closeAdapter() {
        }
    }

    /**
     * Parse the header list
     * @param header The list of headers from the stream
     */
    private fun processHeader(repo: BookRepository,
                              resolution: ConflictResolution,
                              inContext: Context,
                              inScope: CoroutineScope,
                              header: List<String>): ImportHandler<*, *>
    {
        // Keep track of headers for books and filters
        val bookColumns = hashMapOf<String, ImportColumn<BookAndAuthors>>()
        val viewColumns = hashMapOf<String, ImportColumn<ViewFlagsEntity>>()

        var index = 0
        // Add a found column to one of the maps
        fun <T> HashMap<String, ImportColumn<T>>.addTo(s: String, desc: ImportColumn<T>) {
            // If the map already contains the column, then this is a bad format
            if (containsKey(s))
                throw ImportError(ImportError.ErrorCode.BAD_FORMAT, "Duplicate column in header: $s")
            // Add the column
            this[s] = desc.copy(index = index)
        }
        for (s in header) {
            importBookColumns[s]?.let { bookColumns.addTo(s, it) }
            importViewColumns[s]?.let { viewColumns.addTo(s, it) }
            ++index
        }

        // If we didn't get any columns, then that is a bad format
        if (bookColumns.size == 0 && viewColumns.size == 0)
            throw ImportError(ImportError.ErrorCode.BAD_FORMAT, "No valid columns in header")
        // If we got both book and filter columns, then that is a bad format
        if (bookColumns.size != 0 && viewColumns.size != 0)
            throw ImportError(ImportError.ErrorCode.BAD_FORMAT, "Cannot determine whether to import books or views")

        // If we got filter columns, then return a ViewHandler
        if (viewColumns.size != 0) {
            // For views we need both a name and filter column
            if (!viewColumns.containsKey(VIEWS_NAME_COLUMN) && ! viewColumns.containsKey(VIEWS_FILTER_COLUMN))
                throw ImportError(ImportError.ErrorCode.BAD_FORMAT, "View import must contain \"$VIEWS_NAME_COLUMN\" and \"$VIEWS_FILTER_COLUMN\"")
            return ViewHandler(repo, resolution, inContext, viewColumns)
        }

        // For books we need both a title and author column
        if (!bookColumns.containsKey(TITLE_COLUMN) && !bookColumns.containsKey(AUTHOR_NAME))
            throw ImportError(ImportError.ErrorCode.BAD_FORMAT, "Book import must contain \"$TITLE_COLUMN\" and \"${AUTHOR_NAME}\"")
        return BookHandler(repo, resolution, inContext, inScope, bookColumns)
    }
}