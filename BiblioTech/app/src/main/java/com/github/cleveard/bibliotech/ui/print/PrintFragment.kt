package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ListAdapter
import android.os.Bundle
import android.print.PrintManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.children
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.MainActivity
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.ui.books.BooksViewModel
import com.github.cleveard.bibliotech.utils.getLive
import kotlinx.coroutines.launch

class PrintFragment : Fragment() {

    companion object {
        fun newInstance() = PrintFragment()

        private fun columnName(column: Column): Pair<Int, String> = Pair(column.desc.nameResourceId, column.name)
        val visibleFieldNames: MutableList<Pair<Int,String>> = mutableListOf(
            Pair(R.string.small_thumb, "SmallThumb"),
            Pair(R.string.large_thumb, "LargeThumb"),
            columnName(Column.TITLE),
            columnName(Column.SUBTITLE),
            Pair(R.string.author, Column.FIRST_NAME.name),
            columnName(Column.TAGS)
        )
    }

    /** Fragment navigation arguments */
    private val args: PrintFragmentArgs by navArgs()

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

    private lateinit var viewModel: PrintViewModel
    private lateinit var booksViewModel: BooksViewModel

    class ViewHolder(
        checkBox: CheckBox,
        var field: Pair<Int, String> = Pair(0, "")
    ): RecyclerView.ViewHolder(checkBox)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.print_fragment, container, false)
    }

    private fun setupSpinner(
        picker: Spinner,
        selected: Int,
        values: Array<String>,
        setter: (String?) -> Unit,
    ) {
        picker.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setter(
                    if (position >= 0 && position < values.size)
                        values[position]
                    else
                        null
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                setter(null)
            }
        }
        if (selected >= 0)
            picker.setSelection(selected)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(PrintViewModel::class.java).apply {
            // Create the print layouts and the PDF printer
            initialize(requireContext()) {id, large ->
                booksViewModel.getThumbnail(id, large)
            }
        }
        booksViewModel = MainActivity.getViewModel(activity, BooksViewModel::class.java)

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
        val filterAdapter = ArrayAdapter<ViewName>(view.context, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        viewSpinner.adapter = filterAdapter
        // Add the view names to the spinner
        booksViewModel.repo.getViewNames().also {live ->
            // Observe the view names for changes
            live.observe(viewLifecycleOwner) {list ->
                // Set the current set of view names
                filterAdapter.clear()
                list?.let {
                    val resources = requireContext().resources
                    filterAdapter.add(ViewName.makeDisplay(null, resources))
                    filterAdapter.add(ViewName.makeDisplay("", resources))
                    for (s in it) {
                        // Don't add the empty name again
                        if (s.isNotEmpty())
                            filterAdapter.add(ViewName.makeDisplay(s, resources))
                    }
                    filterAdapter.notifyDataSetChanged()
                    val pos = filterAdapter.getPosition(filter).coerceAtLeast(0)
                    viewSpinner.setSelection(pos)
                }
            }
        }

        requireContext().resources.getStringArray(R.array.column_values).let {values ->
            setupSpinner(
                view.findViewById(R.id.columns),
                values.indexOfFirst { viewModel.pdfPrinter.numberOfColumns == it.toIntOrNull() },
                values
            ) {value ->
                value?.toIntOrNull()?.let { viewModel.pdfPrinter.numberOfColumns = it }
            }
        }
        requireContext().resources.getStringArray(R.array.separator_values).let {values ->
            setupSpinner(
                view.findViewById(R.id.separator),
                values.indexOfFirst { viewModel.pdfPrinter.separatorLineWidth == it.toFloatOrNull() },
                values
            ) {value ->
                value?.toFloatOrNull()?.let { viewModel.pdfPrinter.separatorLineWidth = it }
            }
        }
        requireContext().resources.getStringArray(R.array.orphans_values).let {values ->
            setupSpinner(
                view.findViewById(R.id.orphans),
                values.indexOfFirst { viewModel.pdfPrinter.orphans == it.toIntOrNull() },
                values
            ) {value ->
                value?.toIntOrNull()?.let { viewModel.pdfPrinter.orphans = it }
            }
        }
        requireContext().resources.getStringArray(R.array.size_values).let {values ->
            setupSpinner(
                view.findViewById(R.id.size),
                values.indexOfFirst { viewModel.pdfPrinter.basePaint.textSize == it.toFloatOrNull() },
                values
            ) {value ->
                value?.toFloatOrNull()?.let {
                    if (viewModel.pdfPrinter.basePaint.textSize != it) {
                        viewModel.pdfPrinter.basePaint.textSize = it
                        viewModel.pdfPrinter.invalidateLayout()
                    }
                }
            }
        }

        val visible = view.findViewById<RecyclerView>(R.id.visible_fields)
        visible.adapter = object: ListAdapter<Pair<Int, String>, ViewHolder>(object: DiffUtil.ItemCallback<Pair<Int, String>>() {
            override fun areItemsTheSame(oldItem: Pair<Int, String>, newItem: Pair<Int, String>): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Pair<Int, String>, newItem: Pair<Int, String>): Boolean {
                return oldItem == newItem
            }
        }) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                return ViewHolder(CheckBox(requireContext()))
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                holder.field = getItem(position)
                (holder.itemView as CheckBox).let {
                    val visibleFields = viewModel.pdfPrinter.visibleFields
                    it.isChecked = visibleFields.contains(holder.field.second)
                    it.text = requireContext().resources.getString(holder.field.first)
                    it.setOnClickListener {
                        if (visibleFields.contains(holder.field.second))
                            visibleFields.remove(holder.field.second)
                        else
                            visibleFields.add(holder.field.second)
                        notifyItemChanged(position)
                    }
                }
            }
        }.apply {
            submitList(visibleFieldNames)
        }
        visible.layoutManager = GridLayoutManager(requireContext(), 3, GridLayoutManager.VERTICAL, false)

        view.findViewById<Button>(R.id.action_print).setOnClickListener {
            print()
        }
    }

    private fun print() {
        viewModel.viewModelScope.launch {
            val context = requireContext()
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "${context.getString(R.string.app_name)} Document"

            // Get the book filter for the export
            val bookFilter = filter.name?.let {name -> booksViewModel.repo.findViewByName(name) }?.filter
            // Get the PageSource for the books
            val source = bookFilter?.let {filter -> booksViewModel.repo.getBookList(filter, requireContext()) } ?: booksViewModel.repo.getBookList()
            viewModel.pdfPrinter.bookList = source.getLive()?: return@launch

            printManager.print(jobName, BookPrintAdapter(viewModel.pdfPrinter, context, viewModel.viewModelScope), null)
        }
    }
}