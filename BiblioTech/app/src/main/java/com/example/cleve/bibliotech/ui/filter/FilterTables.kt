package com.example.cleve.bibliotech.ui.filter

import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.BookFilter
import com.google.android.material.button.MaterialButton

class FilterTables(private val fragment: Fragment) {

    private val _filter: MutableLiveData<BookFilter> = MutableLiveData()
    val filter: LiveData<BookFilter>
        get() = _filter
    private var layout: TableLayout? = null
    private var headerRow: TableRow? = null
    private val fieldRows: ArrayList<TableRow> = ArrayList()
    private lateinit var columnMap: Array<String>

    private val filterListener = View.OnClickListener { buildFilter() }
    private val columnListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            buildFilter()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            buildFilter()
        }
    }

    private val addRowListener = View.OnClickListener {
        addRow()
        buildFilter()
    }

    private val removeRowListener = View.OnClickListener { v ->
        (v?.tag as? Int)?.let {index ->
            removeRow(index)
            buildFilter()
        }
    }

    private fun buildFilter() {
        val filter = BookFilter(Array(fieldRows.size) { index ->
            val row = fieldRows[index]
            BookFilter.OrderField(
                BookFilter.Column.valueOf(
                    columnMap[row.findViewById<Spinner>(R.id.action_order_by).selectedItemPosition]),
                if (row.findViewById<MaterialButton>(R.id.action_sort_dir).isChecked)
                    BookFilter.Order.Descending
                else
                    BookFilter.Order.Ascending,
                row.findViewById<Switch>(R.id.action_use_header).isChecked
            )
        },
        emptyArray())

        if (_filter.value != filter)
            _filter.value = filter
    }

    private fun connectListeners(row: Int, clear: Boolean = false) {
        val rowView = fieldRows[row]
        var view: View

        view = rowView.findViewById(R.id.action_remove_order_row)
        view.setOnClickListener(if (clear) null else removeRowListener)
        view.tag = row

        view = rowView.findViewById(R.id.action_sort_dir)
        view.setOnClickListener(if (clear) null else filterListener)
        view.tag = row

        val spinner = rowView.findViewById<Spinner>(R.id.action_order_by)
        spinner.onItemSelectedListener = if (clear) null else columnListener
        spinner.tag = row

        view = rowView.findViewById(R.id.action_use_header)
        view.setOnClickListener(if (clear) null else filterListener)
        view.tag = row
    }

    private fun addRow(inflater: LayoutInflater? = null): Int {
        val row = (inflater?: LayoutInflater.from(fragment.context))
            .inflate(R.layout.order_row, layout, false) as TableRow
        val index = fieldRows.size
        fieldRows.add(row)
        layout!!.addView(row)
        connectListeners(index)
        return index
    }

    private fun removeRow(row: Int) {
        connectListeners(row, true)
        layout?.removeView(fieldRows[row])
        fieldRows.removeAt(row)

        for (i in row until fieldRows.size) {
            connectListeners(row)
        }
    }

    private fun bindFilter(filter: BookFilter?, inflater: LayoutInflater? = null) {
        val inf = inflater?: LayoutInflater.from(fragment.context)
        filter?.let { f ->
            for (field in f.orderList) {
                val row = addRow(inf)
                bindRow(row, field)
            }
        }
    }

    private fun bindRow(row: Int, field: BookFilter.OrderField) {
        val rowView = fieldRows[row]
        rowView.findViewById<MaterialButton>(R.id.action_sort_dir).isChecked =
            field.order == BookFilter.Order.Descending
        rowView.findViewById<Spinner>(R.id.action_order_by).setSelection(columnMap.indexOf(field.column.name))
        rowView.findViewById<Switch>(R.id.action_use_header).isChecked = field.headers
    }

    fun setFilter(filter: BookFilter?) {
        clearRows()
        bindFilter(filter)
    }

    private fun clearRows() {
        for (row in fieldRows.size - 1 downTo 0) {
            removeRow(row)
        }
    }

    fun onCreateView(inflater: LayoutInflater, tableLayout: TableLayout) {
        onDestroyView()

        columnMap = fragment.context?.resources?.getStringArray(R.array.order_by_enum)!!
        layout = tableLayout
        tableLayout.removeAllViews()

        headerRow = (inflater.inflate(R.layout.order_header, tableLayout, false) as TableRow).also {
            it.findViewById<View>(R.id.action_add_order_row)?.setOnClickListener(addRowListener)
            tableLayout.addView(it)
        }

        bindFilter(_filter.value, inflater)
    }

    fun onDestroyView() {
        headerRow?.let { header ->
            headerRow = null
            header.findViewById<View>(R.id.action_add_order_row)?.setOnClickListener(null)
        }

        layout?.removeAllViews()
    }
}