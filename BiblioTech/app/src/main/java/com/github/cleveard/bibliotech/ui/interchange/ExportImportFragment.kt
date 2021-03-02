package com.github.cleveard.bibliotech.ui.interchange

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withCreated
import androidx.navigation.fragment.navArgs
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.RegExp
import java.io.InputStream
import java.io.OutputStream

class ExportImportFragment : Fragment() {

    companion object {
        fun newInstance() = ExportImportFragment()

        val exportBookFields = arrayOf<Pair<String, (Int, BookAndAuthors) -> String>>(
            Pair(BOOK_ID_COLUMN, {i, b -> if (i > 0) "" else b.book.id.toString() }),
            Pair(TITLE_COLUMN, {i, b -> if (i > 0) "" else b.book.title }),
            Pair(SUBTITLE_COLUMN, {i, b -> if (i > 0) "" else b.book.subTitle }),
            Pair(VOLUME_ID_COLUMN, {i, b -> if (i > 0) "" else b.book.volumeId?: "" }),
            Pair(SOURCE_ID_COLUMN, {i, b -> if (i > 0) "" else b.book.sourceId?: "" }),
            Pair(ISBN_COLUMN, {i, b -> if (i > 0) "" else b.book.ISBN?: "" }),
            Pair(DESCRIPTION_COLUMN, {i, b -> if (i > 0) "" else b.book.description }),
            Pair(PAGE_COUNT_COLUMN, {i, b -> if (i > 0) "" else b.book.pageCount.toString() }),
            Pair(BOOK_COUNT_COLUMN, {i, b -> if (i > 0) "" else b.book.bookCount.toString() }),
            Pair(VOLUME_LINK, {i, b -> if (i > 0) "" else b.book.linkUrl }),
            Pair(RATING_COLUMN, {i, b -> if (i > 0) "" else b.book.rating.toString() }),
            Pair(DATE_ADDED_COLUMN, {i, b -> if (i > 0) "" else b.book.added.time.toString() }),
            Pair(DATE_MODIFIED_COLUMN, {i, b -> if (i > 0) "" else b.book.modified.time.toString() }),
            Pair(SMALL_THUMB_COLUMN, {i, b -> if (i > 0) "" else b.book.smallThumb.toString() }),
            Pair(LARGE_THUMB_COLUMN, {i, b -> if (i > 0) "" else b.book.largeThumb.toString() }),
            Pair(BOOK_FLAGS, {i, b -> if (i > 0) "" else b.book.flags.toString() }),
            Pair(LAST_NAME_COLUMN, { i, b -> if (i >= b.authors.size) "" else b.authors[i].lastName }),
            Pair(REMAINING_COLUMN, { i, b -> if (i >= b.authors.size) "" else b.authors[i].remainingName }),
            Pair(CATEGORY_COLUMN, { i, b -> if (i >= b.categories.size) "" else b.categories[i].category }),
            Pair(TAGS_NAME_COLUMN, { i, b -> if (i >= b.tags.size) "" else b.tags[i].name }),
            Pair(TAGS_DESC_COLUMN, { i, b -> if (i >= b.tags.size) "" else b.tags[i].desc })
        )
    }

    private val args: ExportImportFragmentArgs by navArgs()

    private lateinit var viewModel: ExportImportViewModel

    private class ViewName(val name: String?, val displayName: String) {
        companion object {
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
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ViewName
            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String {
            return displayName
        }
    }

    private var filter: ViewName = ViewName(null, "")

    private val exportBooksLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(object: ActivityResultContracts.CreateDocument() {
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                intent.setType("text/csv")
                return intent
            }
        }) {
            exportBooks(it)
        }

    private val exportFiltersLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(object: ActivityResultContracts.CreateDocument() {
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                intent.setType("text/csv")
                return intent
            }
        }) {
            exportFilters(it)
        }

    private val importLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(object: ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                intent.setType("text/csv")
                return intent
            }
        }) {
            importData(it)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.export_import_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(ExportImportViewModel::class.java)
        viewModel.viewModelScope.launch {
            filter = ViewName.makeDisplay(args.filterName, requireContext().resources)
            val viewSpinner = view.findViewById<Spinner>(R.id.select_filter)
            viewSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    filter = (parent?.getItemAtPosition(position) as? ViewName)?: ViewName(null, "")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    filter = (parent?.getItemAtPosition(0) as? ViewName)?: ViewName(null, "")
                }
            }
            val adapter = ArrayAdapter<ViewName>(view.context, android.R.layout.simple_spinner_item).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            viewSpinner.adapter = adapter
            viewModel.repo.getViewNames().also {live ->
                live.observe(viewLifecycleOwner) {list ->
                    adapter.clear()
                    list?.let {
                        val resources = requireContext().resources
                        adapter.add(ViewName(null, resources.getString(R.string.all_books)))
                        adapter.add(ViewName("", resources.getString(R.string.menu_books)))
                        for (s in it) {
                            if (s.isNotEmpty())
                                adapter.add(ViewName(s, s))
                        }
                        adapter.notifyDataSetChanged()
                        viewSpinner.setSelection(adapter.getPosition(filter))
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.action_export_books).setOnClickListener {
            exportBooksLauncher.launch("books.csv", null)
        }

        view.findViewById<Button>(R.id.action_export_filters).setOnClickListener {
            exportFiltersLauncher.launch("filters.csv", null)
        }

        view.findViewById<Button>(R.id.action_import).setOnClickListener {
            importLauncher.launch(arrayOf("text/*"), null)
        }

        view.findViewById<RadioButton>(R.id.reject_all).isChecked = true
    }

    private suspend fun toast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun doExport(path: Uri, export: suspend CoroutineScope.(file: OutputStream) -> Boolean) {
        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
            val file = requireContext().contentResolver.openOutputStream(path, "w")
            file?.let {
                var keep = false
                try {
                    keep = export(it)
                } finally {
                    it.close()
                    if (!keep) {
                        val del = DocumentFile.fromSingleUri(requireContext(), path)?.delete()?: false
                        toast(if (del) "Deleted" else "Kept")
                    }
                }
            }
        }
    }

    private fun doImport(path: Uri, import: suspend CoroutineScope.(file: InputStream) -> Boolean) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val file = requireContext().contentResolver.openInputStream(path)
            file?.let {
                try {
                    import(it)
                } finally {
                    it.close()
                }
            }
        }
    }

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

    private fun exportBooks(path: Uri) {
        viewModel.viewModelScope.launch {
            val bookFilter = filter.name?.let { viewModel.repo.findViewByName(it) }?.filter
            val source = bookFilter?.let { viewModel.repo.getBooks(it, requireContext()) }?: viewModel.repo.getBooks()
            @Suppress("BlockingMethodInNonBlockingContext")
            doExport(path) {stream ->
                for (i in exportBookFields.indices) {
                    if (i > 0)
                        stream.write(','.toInt())
                    stream.write(exportBookFields[i].first.toByteArray())
                }
                stream.write('\n'.toInt())

                val quote = Regex("[\",\n]")
                source.forEach {b ->
                    repeat(b.authors.size.coerceAtLeast(b.tags.size)
                        .coerceAtLeast(b.categories.size)
                        .coerceAtLeast(1)) { j ->
                        for (i in exportBookFields.indices) {
                            if (i > 0)
                                stream.write(','.toInt())
                            var s = exportBookFields[i].second(j, b)
                            if (s.contains(quote))
                                s = "\"${exportBookFields[i].second(j, b).replace("\"", "\"\"")}\""
                            stream.write(s.toByteArray())
                        }
                        stream.write('\n'.toInt())
                    }
                }
                true
            }
        }
    }

    private fun exportFilters(path: Uri) {
        viewModel.viewModelScope.launch {
            doExport(path) {
                toast("exportFilters($path)")
                false
            }
        }
    }

    private fun importData(path: Uri) {
        doImport(path) {
            toast("importData($path)")
            false
        }
    }
}
