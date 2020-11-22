package com.example.cleve.bibliotech.ui.filter

import android.content.Context
import android.database.Cursor
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cleve.bibliotech.MainActivity
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.*
import com.example.cleve.bibliotech.ui.books.BooksViewModel
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * UI handler for filters
 */
class FilterTable(private val fragment: Fragment) {
    /**
     * LiveData object used to let the fragment know when
     * the filter has changed
     */
    private val _filter: MutableLiveData<Array<FilterField>> = MutableLiveData()
    val filter: LiveData<Array<FilterField>>
        get() {
            valueListener.maybeBuildFilter()
            return _filter
        }

    /**
     * Action Listener for keyboard actions when entering filter values
     */
    var actionListener: TextView.OnEditorActionListener? = null
        set(listener) {
            field = listener
            for (ui in rows) {
                ui.valueRow.findViewById<EditText>(R.id.filter_value)?.setOnEditorActionListener(listener)
            }
        }

    /**
     * View model
     */
    private lateinit var booksViewModel: BooksViewModel

    /**
     * The table layout for the filter list
     */
    private var layout: TableLayout? = null

    /**
     * The header row for the filter table layout
     */
    private var headerRow: TableRow? = null

    /**
     * Class for each row in the filter column
     */
    private inner class UIRow(
        val topRow: TableRow,
        val valueRow: View,
    ) {
        /** The column spinner in the row */
        private val columnSpinner: Spinner = topRow.findViewById<Spinner>(R.id.action_filter_by).also {
            it.adapter = columnAdapter
            it.setSelection(initialColumnPosition)
        }
        /** Array Adapter for the predicate spinner */
        private val predicateAdapter = ArrayAdapter<String>(fragment.context!!,
            android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        /** The predicate spinner in the row */
        private val predicateSpinner: Spinner = topRow.findViewById<Spinner>(R.id.action_filter_predicate).also {
            it.adapter = predicateAdapter
            it.setSelection(0)
        }
        /** Map of predicate spinner positions to Predicate enums */
        private var predicateMap: Array<String> = emptyArray()
        /** Autocomplete cursor job */
        var autoCompleteJob: Job? = null

        init {
            // Initialize fields that depend on the column
            changeColumn()
        }

        /**
         * Change the fields that depend on the column
         */
        fun changeColumn() {
            // Create the new predicate map.
            val newColumn = column
            val predicates = newColumn.desc.predicates
            val newMap = Array(predicates.size) {
                predicates[it].name
            }

            // If the new and existing maps are the same
            // We don't need to update the array in the spinner
            if (BookFilter.equalArray(newMap, predicateMap))
                return

            // Array needs updating. First see if the current
            // predicate is applicable to the current column
            // Find the position in this predicate list, or default to 0
            var position = predicateSpinner.selectedItemPosition
            val curPredicate = if (position in predicateMap.indices) predicateMap[position] else null
            position = newMap.indexOf(curPredicate)
            if (position == -1 && predicates.isNotEmpty())
                position = 0
            predicateMap = newMap

            // Add the predicates to the new list
            val resource = fragment.context!!.resources
            predicateAdapter.clear()
            for (p in predicates) {
                predicateAdapter.add(resource.getString(p.desc.nameResourceId))
            }
            predicateAdapter.notifyDataSetChanged()
            // Select the predicate
            predicateSpinner.setSelection(position)
        }

        /**
         * Get the auto complete query string for a column
         */
        fun getQuery(): Cursor {
            // Extract the token at the end of the selection
            val edit = valueRow.findViewById<EditText>(R.id.filter_value)
            val values = edit.text.toString()
            val cursor = edit.selectionEnd
            val tokenStart = autoCompleteTokenizer.findTokenStart(values, cursor)
            val tokenEnd = autoCompleteTokenizer.findTokenEnd(values, cursor)
            val token = values.substring(tokenStart, tokenEnd)
            // return the query string
            return column.desc.getAutoCompleteCursor(BookRepository.repo, token)
        }

        /**
         * Handle a focus change on the filter, we only create the cursor
         * and adapter when a value field gets focus
         */
        fun valueFocusChange(hasFocus: Boolean) {
            // Get the value field
            val edit = valueRow.findViewById<MultiAutoCompleteTextView>(R.id.filter_value)
            if (hasFocus) {
                // No autocomplete, don't do anything
                if (!column.desc.hasAutoComplete())
                    return

                // Setting focus, setup adapter and set it in the text view
                // This is done in a coroutine job and we use the job
                // to flag that the job is still active. When we lose focus
                // we cancel the job if it is still active
                autoCompleteJob =  booksViewModel.viewModelScope.launch {
                    // Get the cursor for the column, null means no auto complete
                    val cursor = withContext(booksViewModel.repo.queryScope.coroutineContext) {
                        getQuery()
                    }

                    // Get the adapter from the column description
                    val adapter = SimpleCursorAdapter(
                        fragment.context!!,
                        R.layout.auto_complete,
                        cursor,
                        arrayOf("_result"),
                        intArrayOf(R.id.auto_complete_item),
                        0
                    )
                    adapter.stringConversionColumn = cursor.getColumnIndex("_result")
                    adapter.setFilterQueryProvider { getQuery() }

                    // Set the adapter on the text view
                    edit.setAdapter(adapter)
                    // Flag that the job is done
                    autoCompleteJob = null
                }
            } else {
                // If we lose focus and the set focus job isn't done, cancel it
                autoCompleteJob?.let {
                    it.cancel()
                    autoCompleteJob = null
                }
                // Clear the adapter
                edit.setAdapter(null)
            }
        }

        /** The column from the column spinner */
        val column: Column
            get() {
                return Column.valueOf(columnMap[columnSpinner.selectedItemPosition])
            }

        /** The predicate name from the predicate spinner */
        var predicate: Predicate?
            get() {
                val pos = predicateSpinner.selectedItemPosition
                if (pos == -1)
                    return null
                return Predicate.valueOf(predicateMap[pos])
            }
            set (value) {
                // Find the position of the predicate
                var pos = -1
                if (value != null) {
                    pos = predicateMap.indexOf(value.name)
                    if (pos == -1 && predicateMap.isNotEmpty())
                        pos = 0
                }
                // Set it
                predicateSpinner.setSelection(pos)
            }

        /** The array of values from the value text view */
        var values: Array<String>
            get() {
                return valueRow.findViewById<EditText>(R.id.filter_value)
                    // split the text using ;
                    .text?.split(Regex(";"))
                    // Trim whitespace for each value
                    ?.map {str -> str.trim { it <= ' ' } }
                    // Remove empty values
                    ?.filter { it.isNotEmpty() }
                    // Convert to array of string
                    ?.toTypedArray()?: emptyArray()
            }
            set(value) {
                // Form the value text by joining the values
                valueRow.findViewById<EditText>(R.id.filter_value)
                    .text = SpannableStringBuilder(value.joinToString(" ; ") { it })
            }
    }

    /**
     * List of rows for filter fields in the filter table layout
     */
    private val rows: ArrayList<UIRow> = ArrayList()

    /**
     * Map of positions in the filter field column drop down to the column enum names
     */
    private lateinit var columnMap: Array<String>

    /**
     * ArrayAdapter for the column spinners
     */
    private lateinit var columnAdapter: ArrayAdapter<String>

    /**
     * Initial column setting
     */
    private var initialColumnPosition: Int = 0

    /**
     * OnClickListener for column in table rows
     */
    private val columnListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            (parent?.tag as? Int)?.let { rows[it].changeColumn() }
            // Build filter when something is selected
            buildFilter()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // Do nothing when nothing is selected
        }
    }

    /**
     * OnClickListener for column in table rows
     */
    private val predicateListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            // Build filter when something is selected
            buildFilter()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // Do nothing when nothing is selected
        }
    }

    /**
     * Listener for changes on the value text
     * We add a delay to group changes into one
     */
    private val valueListener = object: TextWatcher {
        /** Main thread scope used to wait to stop typing */
        private val mainScope = MainScope()
        /** Time to stop waiting */
        private var then: Long = 0L
        /** job that waits for typing to stop */
        var job: Job? = null

        /**
         * @inheritDoc
         */
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        /**
         * @inheritDoc
         */
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // This is when we will build the filter
            then = Calendar.getInstance().timeInMillis + 1000
            // Start the job if it isn't started
            job = job?: mainScope.launch {
                while (true) {
                    // Wait
                    delay(500)
                    val now = Calendar.getInstance().timeInMillis
                    // If there isn't any input break
                    if (now >= then)
                        break
                }
                maybeBuildFilter()
            }
        }

        /**
         * @inheritDoc
         */
        override fun afterTextChanged(s: Editable?) {
        }

        /**
         * Build the filter and stop the job
         */
        fun maybeBuildFilter() {
            job?.let {
                // Build the filter
                buildFilter()
                // Clear the job
                job = null
                it.cancel()
            }

        }
    }

    /**
     * Listener for the value fields to get and lose focus
     * This is used to setup and tear down the cursor adaptor for auto complete
     */
    private val valueFocusListener: View.OnFocusChangeListener =
        View.OnFocusChangeListener { v, hasFocus ->
            (v?.tag as? Int)?.let { index ->
                if (index < rows.size)
                    rows[index].valueFocusChange(hasFocus)
            }
        }

    /**
     * Tokenizer for the value items
     */
    private val autoCompleteTokenizer: MultiAutoCompleteTextView.Tokenizer = object: MultiAutoCompleteTextView.Tokenizer {
        override fun findTokenStart(text: CharSequence?, cursor: Int): Int {
            text!!
            var start = cursor
            for (i in cursor - 1 downTo 0) {
                val c = text[i]
                if (c == ';')
                    break
                if (c > ' ')
                    start = i
            }
            return start
        }

        override fun findTokenEnd(text: CharSequence?, cursor: Int): Int {
            text!!
            var end = cursor
            for (i in cursor until text.length) {
                val c = text[i]
                if (c == ';')
                    break
                if (c > ' ')
                    end = i
            }
            return end
        }

        override fun terminateToken(text: CharSequence?): CharSequence {
            return text.toString() + " ; "
        }
    }

    /**
     * OnClickListener for add row button
     */
    private val addRowListener = View.OnClickListener {
        addRow()
        buildFilter()
    }

    /**
     * OnClickListener for remove row button in table rows
     */
    private val removeRowListener = View.OnClickListener { v ->
        (v?.tag as? Int)?.let {index ->
            removeRow(index)
            buildFilter()
        }
    }

    /**
     * Build the filter array from the table rows in the table layout
     */
    private fun buildFilter() {
        // Create the array
        val filter = Array(rows.size) { index ->
            // Get the table row
            val row = rows[index]
            // Create the FilterField from the table row
            val column = row.column
            FilterField(column,
                row.predicate?: Predicate.ONE_OF,
                row.values
            )
        }

        // Set in the live data
        _filter.value = filter
    }

    /**
     * Connect listeners for a row
     * @param row The index of the row
     * @param clear True to remove listeners, true to set them
     * The tag for the views is set to the row index
     */
    private fun connectListeners(row: Int, clear: Boolean = false) {
        // Get the rows
        val rowItem = rows[row]
        val view: View

        // Add remove row listener
        view = rowItem.topRow.findViewById(R.id.action_remove_filter_row)
        view.setOnClickListener(if (clear) null else removeRowListener)
        view.tag = row

        // Add column select listener
        var spinner = rowItem.topRow.findViewById<Spinner>(R.id.action_filter_by)
        spinner.onItemSelectedListener = if (clear) null else columnListener
        spinner.tag = row

        // Add predicate listener
        spinner = rowItem.topRow.findViewById(R.id.action_filter_predicate)
        spinner.onItemSelectedListener = if (clear) null else predicateListener
        spinner.tag = row

        // Add values listener
        val edit = rowItem.valueRow.findViewById<MultiAutoCompleteTextView>(R.id.filter_value)
        if (clear) {
            edit.setOnEditorActionListener(null)
            edit.onFocusChangeListener = null
            edit.setTokenizer(null)
            edit.removeTextChangedListener(valueListener)
        } else {
            edit.setOnEditorActionListener(actionListener)
            edit.onFocusChangeListener = valueFocusListener
            edit.setTokenizer(autoCompleteTokenizer)
            edit.threshold = 1
            edit.addTextChangedListener(valueListener)
        }
        rowItem.valueRow.tag = row
    }

    /**
     * Add a row to the filter table
     * @param inflater Inflater to use to create the table row. Supply as an optimization
     */
    private fun addRow(inflater: LayoutInflater? = null): Int {
        val inf = (inflater?: LayoutInflater.from(fragment.context))
        // Create the table row
        val topRow = inf.inflate(R.layout.filter_row, layout, false) as TableRow
        val valueRow = inf.inflate(R.layout.filter_values, layout, false)
        val rowItem = UIRow(topRow, valueRow)
        // Add the row to rows
        val index = rows.size
        rows.add(rowItem)
        // Add the row to the table layout
        layout!!.addView(rowItem.topRow)
        layout!!.addView(rowItem.valueRow)
        // Connect listeners
        connectListeners(index)
        // Return the row index
        return index
    }

    /**
     * Remove a row from the filter table
     * @param row The index of the row
     */
    private fun removeRow(row: Int) {
        // Disconnect listeners
        connectListeners(row, true)
        // Remove row from the table layout and rows
        layout?.removeView(rows[row].topRow)
        layout?.removeView(rows[row].valueRow)
        rows.removeAt(row)

        // Reconnect following rows, to change the row index
        for (i in row until rows.size) {
            connectListeners(row)
        }
    }

    /**
     * Bind the filter array to the table layout
     * @param filter The filter array
     * @param inflater Inflater to use to create the table rows. Supply as an optimization
     * Assume that the table layout has no rows
     */
    private fun bindFilter(filter: Array<FilterField>?, inflater: LayoutInflater? = null) {
        // Get an inflater, if one isn't supplied
        val inf = inflater?: LayoutInflater.from(fragment.context)
        // Add the array entries to the table layout
        filter?.let { o ->
            for (field in o) {
                val row = addRow(inf)
                bindRow(row, field)
            }
        }
    }

    /**
     * Bind one row to the table layout
     * @param row Index of the row to bind
     * @param field The filter field to bind
     */
    private fun bindRow(row: Int, field: FilterField) {
        // Get the row view
        val rowItem = rows[row]
        // Set the column
        rowItem.topRow.findViewById<Spinner>(R.id.action_filter_by).setSelection(columnMap.indexOf(field.column.name))
        // Make sure the predicate map is correct
        rowItem.changeColumn()
        // Set the predicate
        rowItem.predicate = field.predicate
        // Set the value
        rowItem.values = field.values
    }

    fun setFilter(filter: Array<FilterField>?) {
        clearRows()
        bindFilter(filter)
        _filter.value = filter
    }

    /**
     * Clear all rows from the table layout
     */
    private fun clearRows() {
        // Delete the rows from end to beginning
        for (row in rows.size - 1 downTo 0) {
            removeRow(row)
        }
    }

    /**
     * Setup the table layout
     * @param inflater Inflater for layouts in the table layout
     * @param tableLayout The table layout view
     */
    fun onCreateView(inflater: LayoutInflater, tableLayout: TableLayout) {
        // Tear down anything already setup
        onDestroyView()

        // Get the view model
        booksViewModel = MainActivity.getViewModel(fragment.activity, BooksViewModel::class.java)

        // Initialize the column spinner
        initColumnSpinnerAdapter(inflater.context)
        // Remember the layout
        layout = tableLayout
        // Clear anything in the table layout
        tableLayout.removeAllViews()

        // Add the header row
        headerRow = (inflater.inflate(R.layout.filter_header, tableLayout, false) as TableRow).also {
            // Set the add row on click listener
            it.findViewById<View>(R.id.action_add_filter_row)?.setOnClickListener(addRowListener)
            // Add header row to the table layout
            tableLayout.addView(it)
        }

        // Bind the filter array to the table layout
        bindFilter(_filter.value, inflater)
    }

    private fun initColumnSpinnerAdapter(context: Context) {
        val resources = context.resources
        // Class to put localized names with columns
        class ColumnName(val column: Column) {
            val name: String = resources.getString(column.desc.nameResourceId)
        }

        // First collect the columns that have predicates
        val filterColumns = ArrayList<ColumnName>()
        for (f in Column.values()) {
            if (f.desc.predicates.isNotEmpty())
                filterColumns.add(ColumnName(f))
        }

        // Now sort by the localized name
        filterColumns.sortBy { it.name }

        // Create the array of localized names used in the spinner
        val names = Array(filterColumns.size) { filterColumns[it].name }
        // An create the column map for those name
        columnMap = Array(filterColumns.size) { filterColumns[it].column.name }
        // Finally build the column spinner ArrayAdapter
        columnAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        columnAdapter.setDropDownViewResource(android.R.layout
            .simple_spinner_dropdown_item)
        initialColumnPosition = columnMap.indexOf(Column.ANY.name)
        if (initialColumnPosition == -1 && columnMap.isNotEmpty())
            initialColumnPosition = 0
    }
    /**
     * Cleanup when the table layout is destroyed
     */
    fun onDestroyView() {
        // Build the filter if there were any text changes
        valueListener.maybeBuildFilter()

        // Clear the add row on click listener
        headerRow?.let { header ->
            headerRow = null
            header.findViewById<View>(R.id.action_add_filter_row)?.setOnClickListener(null)
        }

        // Remove all rows
        rows.clear()
        // Clear header row
        headerRow = null
        // Remove all views from the layout
        layout?.removeAllViews()
    }
}