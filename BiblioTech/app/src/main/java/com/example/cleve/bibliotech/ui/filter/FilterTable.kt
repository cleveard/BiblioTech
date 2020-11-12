package com.example.cleve.bibliotech.ui.filter

import android.content.Context
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.BookFilter
import com.example.cleve.bibliotech.db.Column
import com.example.cleve.bibliotech.db.FilterField
import com.example.cleve.bibliotech.db.Predicate
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
        private val columnSpinner: Spinner = topRow.findViewById<Spinner>(R.id.action_filter_by).also {
            it.adapter = columnAdapter
            it.setSelection(initialColumnPosition)
        }
        private val predicateAdapter = ArrayAdapter<String>(fragment.context!!,
            android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        private val predicateSpinner: Spinner = topRow.findViewById<Spinner>(R.id.action_filter_predicate).also {
            it.adapter = predicateAdapter
            it.setSelection(0)
        }
        private var predicateMap: Array<String> = emptyArray()

        init {
            changeColumn()
        }

        fun changeColumn() {
            // Create the new predicate map.
            val column = Column.valueOf(columnMap[columnSpinner.selectedItemPosition])
            val predicates = column.desc.predicates
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

        var predicate: String?
            get() {
                val pos = predicateSpinner.selectedItemPosition
                if (pos == -1)
                    return null
                return predicateMap[pos]
            }
            set (name) {
                // Find the position of the predicate
                var pos = -1
                if (name != null && name.isNotEmpty()) {
                    pos = predicateMap.indexOf(name)
                    if (pos == -1 && predicateMap.isNotEmpty())
                        pos = 0
                }
                // Set it
                predicateSpinner.setSelection(pos)
            }

        var values: Array<String>
            get() {
                return valueRow.findViewById<EditText>(R.id.filter_value)
                    .text?.split(Regex("\\s*;\\s*"))?.toTypedArray()?: emptyArray()
            }
            set(value) {
                valueRow.findViewById<EditText>(R.id.filter_value)
                    .text = SpannableStringBuilder(value.joinToString(";") { it })
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
            val column = Column.valueOf(
                columnMap[row.topRow.findViewById<Spinner>(R.id.action_filter_by).selectedItemPosition])
            FilterField(column,
                row.predicate?.let { Predicate.valueOf(it) }?: Predicate.ONE_OF,
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
        val edit = rowItem.valueRow.findViewById<EditText>(R.id.filter_value)
        if (clear)
            edit.removeTextChangedListener(valueListener)
        else
            edit.addTextChangedListener(valueListener)
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
        rowItem.predicate = field.predicate.name
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