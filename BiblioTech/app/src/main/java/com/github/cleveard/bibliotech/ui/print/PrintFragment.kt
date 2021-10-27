package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.print.PrintManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.github.cleveard.bibliotech.MainActivity
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.ui.books.BooksViewModel
import com.github.cleveard.bibliotech.utils.getLive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PrintFragment : Fragment() {

    companion object {
        fun newInstance() = PrintFragment()
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.print_fragment, container, false)
    }

    private fun setupNumberPicker(
        picker: NumberPicker,
        min: Int,
        max: Int,
        value: Int,
        setter: (Int) -> Unit,
        formatter: NumberPicker.Formatter? = null
    ) {
        picker.maxValue = min
        picker.maxValue = max
        picker.value = value
        formatter?.let { picker.setFormatter(it) }
        picker.setOnValueChangedListener { _, _, newVal -> setter(newVal)  }
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
        val adapter = ArrayAdapter<ViewName>(view.context, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        viewSpinner.adapter = adapter
        // Add the view names to the spinner
        booksViewModel.repo.getViewNames().also {live ->
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

        setupNumberPicker(
            view.findViewById(R.id.columns),
            1,
            10,
            viewModel.pdfPrinter.numberOfColumns,
            {
                viewModel.pdfPrinter.numberOfColumns = it
            }
        )
        setupNumberPicker(
            view.findViewById(R.id.separator),
            0,
            40,
            (viewModel.pdfPrinter.separatorLineWidth * 4.0f).roundToInt(),
            {
                viewModel.pdfPrinter.separatorLineWidth = it.toFloat() / 4.0f
            }
        ) { "%4.2f".format(it.toFloat() / 4.0f) }
        setupNumberPicker(
            view.findViewById(R.id.orphans),
            1,
            10,
            viewModel.pdfPrinter.orphans,
            {
                viewModel.pdfPrinter.orphans = it
            }
        )
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