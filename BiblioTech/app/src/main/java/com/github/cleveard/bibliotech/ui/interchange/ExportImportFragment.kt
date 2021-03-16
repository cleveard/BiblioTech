package com.github.cleveard.bibliotech.ui.interchange

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.utils.coroutineAlert
import com.github.cleveard.bibliotech.utils.getLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.StringBuilder
import java.util.*

class ExportImportFragment : Fragment() {

    /**
     * Class to represent an exported column
     * @param T The data type being exported
     * @param name The name of the column header
     * @param getValue An extension lambda function to get the column value from the object. An object
     *                 can required multiple lines, and the argument is the 0 based line being output for the object.
     */
    private data class ExportColumn<T>(val name: String, val getValue: T.(Int) -> String)

    companion object {
        fun newInstance() = ExportImportFragment()

        /** Array book of columns we export */
        private val exportBookColumns = arrayOf<ExportColumn<BookAndAuthors>>(
            ExportColumn(BOOK_ID_COLUMN) { if (it > 0) "" else book.id.toString() },
            ExportColumn(TITLE_COLUMN) { if (it > 0) "" else book.title },
            ExportColumn(SUBTITLE_COLUMN) { if (it > 0) "" else book.subTitle },
            ExportColumn(VOLUME_ID_COLUMN) { if (it > 0) "" else book.volumeId ?: "" },
            ExportColumn(SOURCE_ID_COLUMN) { if (it > 0) "" else book.sourceId ?: "" },
            ExportColumn(ISBN_COLUMN) { if (it > 0) "" else book.ISBN ?: "" },
            ExportColumn(DESCRIPTION_COLUMN) { if (it > 0) "" else book.description },
            ExportColumn(PAGE_COUNT_COLUMN) { if (it > 0) "" else book.pageCount.toString() },
            ExportColumn(BOOK_COUNT_COLUMN) { if (it > 0) "" else book.bookCount.toString() },
            ExportColumn(VOLUME_LINK) { if (it > 0) "" else book.linkUrl },
            ExportColumn(RATING_COLUMN) { if (it > 0) "" else book.rating.toString() },
            ExportColumn(DATE_ADDED_COLUMN) { if (it > 0) "" else book.added.time.toString() },
            ExportColumn(DATE_MODIFIED_COLUMN) { if (it > 0) "" else book.modified.time.toString() },
            ExportColumn(SMALL_THUMB_COLUMN) { if (it > 0) "" else book.smallThumb ?: "" },
            ExportColumn(LARGE_THUMB_COLUMN) { if (it > 0) "" else book.largeThumb ?: "" },
            ExportColumn(BOOK_FLAGS) { if (it > 0) "" else book.flags.toString() },
            ExportColumn(ImportCSV.AUTHOR_NAME) { if (it >= authors.size) "" else authors[it].name },
            ExportColumn(CATEGORY_COLUMN) { if (it >= categories.size) "" else categories[it].category },
            ExportColumn(TAGS_NAME_COLUMN) { if (it >= tags.size) "" else tags[it].name },
            ExportColumn(TAGS_DESC_COLUMN) { if (it >= tags.size) "" else tags[it].desc }
        )

        /** Array view of columns we export */
        private val exportViewColumns = arrayOf<ExportColumn<ViewEntity>>(
            ExportColumn(VIEWS_ID_COLUMN) { id.toString() },
            ExportColumn(VIEWS_NAME_COLUMN) { name },
            ExportColumn(VIEWS_DESC_COLUMN) { desc },
            ExportColumn(VIEWS_FILTER_COLUMN) { BookFilter.encodeToString(filter) ?: "" }
        )

        /** Regular expression to check whether an export column needs quoting */
        private val quote = Regex("[\",\n]")

        /**
         * Quote a string for CSV output
         */
        private fun String?.quoteForCSV(): String {
            return if (this?.contains(quote) == true)
                "\"${replace("\"", "\"\"")}\""
            else
                this?: ""
        }
    }

    /** Fragment navigation arguments */
    private val args: ExportImportFragmentArgs by navArgs()

    /** The fragment view model */
    private lateinit var viewModel: ExportImportViewModel

    /**
     * Class used to display view names in the UI
     * @param name The view name
     * @param displayName The display name for the view name
     */
    private class ViewName(val name: String?, val displayName: String) {
        companion object {
            /**
             * Make a ViewName object for a string
             * @param name The string
             * @param resources Resources to get the string for empty and null names
             * @return The ViewName
             */
            fun makeDisplay(name: String?, resources: Resources): ViewName {
                return ViewName(name,
                    when {
                        name == null -> resources.getString(R.string.all_books)
                        name.isEmpty() -> resources.getString(R.string.menu_books)
                        else -> name
                    }
                )
            }
        }

        /**
         * @inheritDoc
         * Only use the name for the comparison
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ViewName
            return name == other.name
        }

        /**
         * @inheritDoc
         * Only use the name for comparison
         */
        override fun hashCode(): Int {
            return name.hashCode()
        }

        /**
         * @inheritDoc
         * Use the displayName for the string representation
         */
        override fun toString(): String {
            return displayName
        }
    }

    /** The filter used for the export */
    private var filter: ViewName = ViewName(null, "")

    /** Launcher used to get the content for exporting books */
    private val exportBooksLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(object: ActivityResultContracts.CreateDocument() {
            /**
             * @inheritDoc
             * Set the intent type to "text/csv"
             */
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                intent.type = "text/csv"
                return intent
            }
        }) {
            // Export books when we get the URI
            if (it != null)
                exportBooks(it)
        }

    /** Launcher used to get the content for exporting filters */
    private val exportFiltersLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(object: ActivityResultContracts.CreateDocument() {
            /**
             * @inheritDoc
             * Set the intent type to "text/csv"
             */
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                intent.type = "text/csv"
                return intent
            }
        }) {
            // Export filters when we get the URI
            if (it != null)
                exportFilters(it)
        }

    /** Launcher used to get the content for importing books or filters */
    private val importLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(object: ActivityResultContracts.OpenDocument() {
            /**
             * @inheritDoc
             * Set the intent type to "text/csv"
             */
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                intent.type = "text/csv"
                return intent
            }
        }) {
            // Export books or filters when we get the URI
            if (it != null)
                importData(it)
        }

    /**
     * @inheritDoc
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.export_import_fragment, container, false)
    }

    /**
     * @inheritDoc
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Create the view model
        viewModel = ViewModelProvider(this).get(ExportImportViewModel::class.java)
        // Launch a coroutine to finish the initialization
        viewModel.viewModelScope.launch {
            // Create the ViewName for the filter from the arguments
            filter = ViewName.makeDisplay(args.filterName, requireContext().resources)
            // Get the Spinner for the filters
            val viewSpinner = view.findViewById<Spinner>(R.id.select_filter)
            // Listen for selections
            viewSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // Set the filter to the selected filter
                    filter = (parent?.getItemAtPosition(position) as? ViewName)?: ViewName(null, "")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Set the filter to the first filter
                    filter = (parent?.getItemAtPosition(0) as? ViewName)?: ViewName(null, "")
                }
            }
            // Create the adaptor for the spinner and set it
            val adapter = ArrayAdapter<ViewName>(view.context, android.R.layout.simple_spinner_item).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            viewSpinner.adapter = adapter
            // Add the view names to the spinner
            viewModel.repo.getViewNames().also {live ->
                // Observe the view names for changes
                live.observe(viewLifecycleOwner) {list ->
                    // Set the current set of view names
                    adapter.clear()
                    list?.let {
                        val resources = requireContext().resources
                        adapter.add(ViewName.makeDisplay(null, resources))
                        adapter.add(ViewName.makeDisplay("", resources))
                        for (s in it) {
                            // Don't add the empty name again
                            if (s.isNotEmpty())
                                adapter.add(ViewName.makeDisplay(s, resources))
                        }
                        adapter.notifyDataSetChanged()
                        val pos = adapter.getPosition(filter).coerceAtLeast(0)
                        viewSpinner.setSelection(pos)
                    }
                }
            }
        }

        // Start export when export button clicked
        view.findViewById<Button>(R.id.action_export_books).setOnClickListener {
            exportBooksLauncher.launch("books.csv", null)
        }

        // Start export when export button clicked
        view.findViewById<Button>(R.id.action_export_filters).setOnClickListener {
            exportFiltersLauncher.launch("filters.csv", null)
        }

        // Start import when import button clicked
        view.findViewById<Button>(R.id.action_import).setOnClickListener {
            importLauncher.launch(arrayOf("text/*"), null)
        }

        // Default to reject all conflicts
        view.findViewById<RadioButton>(R.id.reject_all).isChecked = true
    }

    /**
     * Present a toast to the user
     * @param msgId The resource id for the toast message
     * @param args Additional args for the toast message
     */
    private suspend fun toast(msgId: Int, vararg args: Any) {
        withContext(Dispatchers.Main) {
            val msg = requireContext().resources.getString(msgId, *args)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Present a modal information message to the user
     * @param msgId The resource id for the message
     * @param args Additional args for the message
     */
    private suspend fun info(msgId: Int, vararg args: Any) {
        withContext(Dispatchers.Main) {
            val msg = requireContext().resources.getString(msgId, *args)
            coroutineAlert(requireContext(), {}) {alert ->
                alert.builder.setMessage(msg)
                    .setPositiveButton(R.string.ok, null)
            }.show()
        }
    }

    /**
     * Do an export
     * @param path The path to the export content
     * @param export A suspend extension function to write the export content. Return false
     * from the lambda to delete the export file. Return true to keep it.
     * This method handle opening the URI and error reporting
     */
    private suspend fun doExport(path: Uri, export: suspend CoroutineScope.(file: Writer) -> Boolean) {
        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
            try {
                // Get the file
                val file = requireContext().contentResolver.openOutputStream(path, "w")
                file?.let {
                    val writer = OutputStreamWriter(it)
                    val stream = BufferedWriter(writer)
                    var keep = false
                    try {
                        // Call the exporter
                        keep = export(stream)
                    } finally {
                        // Close the stream
                        stream.close()
                        writer.close()
                        it.close()
                        // Delete it, if we don't want to keep it
                        if (!keep) {
                            DocumentFile.fromSingleUri(requireContext(), path)?.delete()
                        }
                    }
                }
            } catch (e: ImportCSV.ImportError) {
                // Report errors
                val id = when (e.code) {
                    ImportCSV.ImportError.ErrorCode.IO_ERROR -> R.string.io_error_export
                    else -> return@withContext
                }
                info(id)
            } catch (e: IOException) {
                info(R.string.io_error_export)
            }
        }

        toast(R.string.export_complete)
    }

    /**
     * Execute a lambda for each object in a PagingSource
     * @param callback The lambda to execute
     */
    private suspend fun <K: Any, D: Any> PagingSource<K, D>.forEach(callback: (D) -> Unit) {
        suspend fun nextPage(params: PagingSource.LoadParams<K>): K? {
            val result = this.load(params) as PagingSource.LoadResult.Page
            for (data in result.data)
                callback(data)
            return result.nextKey
        }
        var key = nextPage(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false
            )
        )
        while(key != null) {
            key = nextPage(
                PagingSource.LoadParams.Append(
                    key = key,
                    loadSize = 20,
                    placeholdersEnabled = false
                )
            )
        }
    }

    /**
     * Export books
     * @param path The path to the exported content
     */
    private fun exportBooks(path: Uri) {
        viewModel.viewModelScope.launch {
            // Get the book filter for the export
            val bookFilter = filter.name?.let { viewModel.repo.findViewByName(it) }?.filter
            // Get the PageSource for the books
            val source = bookFilter?.let { viewModel.repo.getBooks(it, requireContext()) }?: viewModel.repo.getBooks()
            @Suppress("BlockingMethodInNonBlockingContext")
            // Start the export
            doExport(path) {stream ->
                // Output the headers
                for (i in exportBookColumns.indices) {
                    if (i > 0)
                        stream.write(",")
                    // Shouldn't need to quote these names
                    stream.write(exportBookColumns[i].name)
                }
                stream.write("\n")

                var count = 0
                // Output the data
                source.forEach {b ->
                    ++count
                    // Output as many lines as needed to include
                    // all authors, tags and categories and the book
                    repeat(b.authors.size.coerceAtLeast(b.tags.size)
                        .coerceAtLeast(b.categories.size)
                        .coerceAtLeast(1)) { j ->
                        // Output each field on this line.
                        for (i in exportBookColumns.indices) {
                            if (i > 0)
                                stream.write(",")
                            val s = exportBookColumns[i].getValue.invoke(b, j).quoteForCSV()
                            stream.write(s)
                        }
                        stream.write("\n")
                    }
                }
                // If no books were exported, let the user know
                if (count == 0)
                    toast(R.string.no_books_selected_for_export)
                // Delete the output if nothing was written
                count > 0
            }
        }
    }

    /**
     * Export filters
     * @param path The path to the exported content
     */
    private fun exportFilters(path: Uri) {
        viewModel.viewModelScope.launch {
            @Suppress("BlockingMethodInNonBlockingContext")
            doExport(path) {stream ->
                // Get the list of filters
                viewModel.repo.getViewNames().getLive()?.let {viewList ->
                    // Output the headers
                    for (i in exportViewColumns.indices) {
                        if (i > 0)
                            stream.write(",")
                        // Shouldn't need to quote these names
                        stream.write(exportViewColumns[i].name)
                    }
                    stream.write("\n")

                    val repo = viewModel.repo
                    var count = 0
                    // Output each view
                    for (b in viewList) {
                        repo.findViewByName(b)?.let {v ->
                            ++count
                            // Output the fields for the view
                            for (i in exportViewColumns.indices) {
                                if (i > 0)
                                    stream.write(",")
                                val s = exportViewColumns[i].getValue.invoke(v, 0).quoteForCSV()
                                stream.write(s)
                            }
                            stream.write("\n")
                        }
                    }
                    // Let the use know if nothing was exported
                    if (count == 0)
                        toast(R.string.no_views_selected_for_export)
                    // Delete the file if nothing was exported
                    count > 0
                }?: false
            }
        }
    }

    /**
     * CSV Reader class
     * @param stream The stream the CSV is read from
     */
    private class CSVReader(private val stream: InputStream) {
        private val rawReader = InputStreamReader(stream)
        /** The reader for the input stream */
        private val reader = BufferedReader(rawReader)
        /** Flag for end of line */
        private var eol = false
        /** flag for end of file */
        private var eof = false

        /** Clear the eol flag at the start of a line */
        private fun startLine() {
            eol = false
        }

        /** Close the stream */
        fun close() {
            reader.close()
            rawReader.close()
            stream.close()
        }

        /**
         * Get the next character from the stream
         * @param quote Set to true if the column is quoted
         * @return The next character
         */
        private fun nextChar(quote: Boolean): Char {
            // Only read the stream if not at eof or eol.
            if (!eof && !eol) {
                // Get the next character
                val int = reader.read()
                // Are we at eof
                if (int >= 0) {
                    // No, got a character. If we aren't quoting, set eol if we reach it
                    val ch = int.toChar()
                    eol = ch == '\n' && !quote
                    return ch
                }
                // End of file set eof and eol
                eof = true
                eol = true
            }
            // Return newline if we didn't get a character
            return '\n'
        }

        /**
         * Get the next field from the stream
         * @param builder A string builder where the field contents is placed
         * @return True if the field was present
         */
        private fun nextField(builder: StringBuilder): Boolean {
            // clear the token
            builder.clear()
            // If we are at eof or eol at the start of the field, return false
            var ch = nextChar(false)
            // At eof return false
            if (eol)
                return false

            // Check to see if this field is quoted
            var quote = ch == '"'
            if (quote) {
                // If quoted, get start of quoted string
                ch = nextChar(true)
                // At eof, return false
                if (eol)
                    return false
            }

            while (true) {
                // If quoting and we see a " character, see if quoting should stop
                if (quote && ch == '"') {
                    // Don't quote, now so eol will terminate the field
                    ch = nextChar(false)
                    if (eol)
                        return true
                    // Turn quoting off if it isn't ""
                    if (ch != '"')
                        quote = false
                }

                // If eol or we find an unquoted comma, then we are done
                if (eol || (!quote && ch == ','))
                    return true
                // Append the character
                builder.append(ch)
                // Get the next character
                ch = nextChar(quote)
            }
        }

        /**
         * Get all of the fields in a line
         * @param list A list to hold all of the fields
         * @return True if we got something
         */
        fun nextLine(list: MutableList<String>): Boolean {
            // If we are at eof, the return false
            if (eof)
                return false
            // Clear the list
            list.clear()
            // Start the line
            startLine()

            // Read each field and add it to the list
            val builder = StringBuilder()
            while (nextField(builder)) {
                list.add(builder.toString())
            }
            // return true if we are not at end of file or something was read
            return !eof || list.size > 0
        }
    }

    /**
     * Do an import
     * @param path The path to the content
     * @param import A suspend extension function to read the data from the content
     * This method handle opening the stream and error reporting
     */
    private fun doImport(path: Uri, import: suspend CoroutineScope.(CSVReader) -> Boolean) {
        @Suppress("BlockingMethodInNonBlockingContext")
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            // Open the stream
            val file = requireContext().contentResolver.openInputStream(path)
            file?.let {
                // Make the reader for the content
                val stream = CSVReader(it)
                try {
                    // Start a transaction, so we can abort the whole import if needed
                    BookDatabase.db.withTransaction {
                        // Import the data
                        import(stream)
                        // Close the stream. Done hear so an exception will abort the transaction
                        stream.close()
                    }
                } catch (e: ImportCSV.ImportError) {
                    // Report any errors
                    val id = when (e.code) {
                        ImportCSV.ImportError.ErrorCode.BAD_FORMAT -> R.string.bad_format_import
                        ImportCSV.ImportError.ErrorCode.IO_ERROR -> R.string.io_error_import
                        ImportCSV.ImportError.ErrorCode.CANCELED -> return@let
                    }
                    info(id)
                } finally {
                    // Close the stream if we didn't before
                    stream.close()
                }
            }

            toast(R.string.import_complete)
        }
    }

    /**
     * Import data
     * @param path The path to the stream content
     */
    private fun importData(path: Uri) {
        val resolveConflict = when (view?.findViewById<RadioGroup>(R.id.conflict_handling)?.checkedRadioButtonId) {
            R.id.reject_all -> ImportCSV.ConflictResolution.KEEP
            R.id.accept_all -> ImportCSV.ConflictResolution.REPLACE
            else -> ImportCSV.ConflictResolution.ASK
        }
        doImport(path) {stream ->
            ImportCSV().importData(viewModel.repo, resolveConflict, requireContext(), viewModel.viewModelScope) { stream.nextLine(it) }
        }
    }
}
