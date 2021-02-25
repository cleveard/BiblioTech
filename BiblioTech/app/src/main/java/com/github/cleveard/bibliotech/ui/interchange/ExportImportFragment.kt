package com.github.cleveard.bibliotech.ui.interchange

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavArgs
import androidx.navigation.fragment.navArgs
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookFilter

class ExportImportFragment : Fragment() {

    companion object {
        fun newInstance() = ExportImportFragment()
    }

    private val args: ExportImportFragmentArgs by navArgs()

    private lateinit var viewModel: ExportImportViewModel

    private val allBookExportLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) {
            exportBooks(it, null)
        }

    private val viewedBookExportLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) {
            exportBooks(it, null)
        }

    private val filterExportLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) {
            exportFilters(it)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.export_import_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(ExportImportViewModel::class.java)
        view?.findViewById<TextView>(R.id.filter_name)?.text = "Filter name: \"${args.filterName?: ""}\""
    }

    private fun exportBooks(uri: Uri, filter: BookFilter?) {

    }

    private fun exportFilters(uri: Uri) {

    }

}