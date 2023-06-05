package com.github.cleveard.bibliotech.ui.filter

import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.db.Order
import com.github.cleveard.bibliotech.db.OrderField
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * UI handler for filters
 */
class OrderTable(private val fragment: Fragment) {
    /**
     * LiveData object used to let the fragment know when
     * the filter has changed
     */
    private val _order: MutableLiveData<Array<OrderField>?> = MutableLiveData()
    val order: LiveData<Array<OrderField>?>
        get() = _order

    /**
     * The table layout for the order list
     */
    private var layout: TableLayout? = null

    /**
     * The header row for the order table layout
     */
    private var headerRow: TableRow? = null

    /**
     * List of rows for order fields in the order table layout
     */
    private val rows: ArrayList<TableRow> = ArrayList()

    /**
     * Map of positions in the order field column drop down to the column enum names
     */
    private lateinit var columnMap: Array<String>

    /**
     * OnClickListener for use header and sort direction in table rows for order fields
     */
    private val filterListener = View.OnClickListener { buildOrder() }

    /**
     * OnClickListener for column in table rows
     */
    private val columnListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            // Build filter when something is selected
            buildOrder()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // Do nothing when nothing is selected
        }
    }

    /**
     * OnClickListener for add row button
     */
    private val addRowListener = View.OnClickListener {
        addRow()
        buildOrder()
    }

    /**
     * OnClickListener for remove row button in table rows
     */
    private val removeRowListener = View.OnClickListener { v ->
        (v?.tag as? Int)?.let {index ->
            removeRow(index)
            buildOrder()
        }
    }

    /**
     * Build the order array from the table rows in the table layout
     */
    private fun buildOrder() {
        // Create the array
        val order = Array(rows.size) { index ->
            // Get the table row
            val row = rows[index]
            // Create the OrderField from the table row
            OrderField(
                // Get the column
                Column.valueOf(
                    columnMap[row.findViewById<Spinner>(R.id.action_order_by).selectedItemPosition]),
                // Get the sort direction
                if (row.findViewById<MaterialButton>(R.id.action_sort_dir).isChecked)
                    Order.Descending
                else
                    Order.Ascending,
                // Get the use header flag
                row.findViewById<SwitchMaterial>(R.id.action_use_header).isChecked
            )
        }

        // Set in the live data
        _order.value = order
    }

    /**
     * Connect listeners for a row
     * @param row The index of the row
     * @param clear True to remove listeners, true to set them
     * The tag for the views is set to the row index
     */
    private fun connectListeners(row: Int, clear: Boolean = false) {
        // Get the rows
        val rowView = rows[row]

        // Add remove row listener
        var view: View = rowView.findViewById(R.id.action_remove_order_row)
        view.setOnClickListener(if (clear) null else removeRowListener)
        view.tag = row

        // add sort direction listener
        view = rowView.findViewById(R.id.action_sort_dir)
        view.setOnClickListener(if (clear) null else filterListener)
        view.tag = row

        // Add column select listener
        val spinner = rowView.findViewById<Spinner>(R.id.action_order_by)
        spinner.onItemSelectedListener = if (clear) null else columnListener
        spinner.tag = row

        // Add use header listener
        view = rowView.findViewById(R.id.action_use_header)
        view.setOnClickListener(if (clear) null else filterListener)
        view.tag = row
    }

    /**
     * Add a row to the order table
     * @param inflater Inflater to use to create the table row. Supply as an optimization
     */
    private fun addRow(inflater: LayoutInflater? = null): Int {
        // Create the table row
        val row = (inflater?: LayoutInflater.from(fragment.context))
            .inflate(R.layout.books_drawer_order_row, layout, false) as TableRow
        // Add the row to rows
        val index = rows.size
        rows.add(row)
        // Add the row to the table layout
        layout!!.addView(row)
        // Connect listeners
        connectListeners(index)
        // Return the row index
        return index
    }

    /**
     * Remove a row from the order table
     * @param row The index of the row
     */
    private fun removeRow(row: Int) {
        // Disconnect listeners
        connectListeners(row, true)
        // Remove row from the table layout and rows
        layout?.removeView(rows[row])
        rows.removeAt(row)

        // Reconnect following rows, to change the row index
        for (i in row until rows.size) {
            connectListeners(row)
        }
    }

    /**
     * Bind the order array to the table layout
     * @param order The order array
     * @param inflater Inflater to use to create the table rows. Supply as an optimization
     * Assume that the table layout has no rows
     */
    private fun bindOrder(order: Array<OrderField>?, inflater: LayoutInflater? = null) {
        // Get an inflater, if one isn't supplied
        val inf = inflater?: LayoutInflater.from(fragment.context)
        // Add the array entries to the table layout
        order?.let {o ->
            for (field in o) {
                val row = addRow(inf)
                bindRow(row, field)
            }
        }
    }

    /**
     * Bind one row to the table layout
     * @param row Index of the row to bind
     * @param field The order field to bind
     */
    private fun bindRow(row: Int, field: OrderField) {
        // Get the row view
        val rowView = rows[row]
        // Set the sort direction
        rowView.findViewById<MaterialButton>(R.id.action_sort_dir).isChecked =
            field.order == Order.Descending
        // Set the column
        rowView.findViewById<Spinner>(R.id.action_order_by).setSelection(columnMap.indexOf(field.column.name))
        // Set use headers
        rowView.findViewById<SwitchMaterial>(R.id.action_use_header).isChecked = field.headers
    }

    fun setOrder(order: Array<OrderField>?) {
        clearRows()
        bindOrder(order)
        _order.value = order
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

        // Get the column map from the resources
        columnMap = fragment.requireContext().resources.getStringArray(R.array.order_by_enum)
        // Remember the layout
        layout = tableLayout
        // Clear anything in the table layout
        tableLayout.removeAllViews()

        // Add the header row
        headerRow = (inflater.inflate(R.layout.books_drawer_order_header, tableLayout, false) as TableRow).also {
            // Set the add row on click listener
            it.findViewById<View>(R.id.action_add_order_row)?.setOnClickListener(addRowListener)
            // Add header row to the table layout
            tableLayout.addView(it)
        }

        // Bind the order array to the table layout
        bindOrder(_order.value, inflater)
    }

    /**
     * Cleanup when the table layout is destroyed
     */
    fun onDestroyView() {
        // Clear the add row on click listener
        headerRow?.let { header ->
            headerRow = null
            header.findViewById<View>(R.id.action_add_order_row)?.setOnClickListener(null)
        }

        // Remove all rows
        rows.clear()
        // Clear header row
        headerRow = null
        // Remove all views from the layout
        layout?.removeAllViews()
    }
}