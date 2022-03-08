package com.github.cleveard.bibliotech.ui.books

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookDatabase
import com.github.cleveard.bibliotech.db.BookEntity
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.gb.GoogleBookLookup
import com.github.cleveard.bibliotech.ui.gb.GoogleBookLoginFragment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow

/**
 * A dialog fragment used to update the series of books in the database
 */
internal class UpdateSeriesFragment : Fragment() {
    /**
     * View model for the UpdateSeriesFragment
     * Mostly used for its view model scope
     */
    class UpdateSeriesViewModel: ViewModel() {
        /** The book repository */
        val repo: BookRepository = BookRepository.repo
        /** The Google book lookup service */
        val lookup: GoogleBookLookup = GoogleBookLookup()
    }

    /** The view model */
    private val viewModel: UpdateSeriesViewModel by viewModels()

    /**
     * The currently running update job
     * This value is null if the job has completed
     */
    private var updateJob: Job? = null

    /**
     * Flag to indicate whether the current update job should continue
     */
    private var active: Boolean = false

    /**  @inheritDoc */
    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_update_series, null)
    }

    /**
     * @inheritDoc
     * When the fragment is stopped, the active flag is set to false
     */
    override fun onStop() {
        super.onStop()
        active = false
    }

    /** @inheritDoc */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.update_series_start).setOnClickListener {
            // Disable the start button
            it.isEnabled = false
            // Stop the current job
            active = false
            // Restart the update job
            updateSeries()
        }
        view.findViewById<Button>(R.id.update_series_stop).setOnClickListener {
            // Disable the stop button
            it.isEnabled = false
            // Stop the current job
            active = false
        }

        GoogleBookLoginFragment.login(this)
    }

    /**
     * Update the series for any books that haven't been updated
     */
    private fun updateSeries() {
        viewModel.viewModelScope.launch {
            // Join the current job if there is one
            updateJob?.join()
            // Set the current job
            updateJob = coroutineContext.job
            // Get the progress bar
            val progressBar = requireView().findViewById<ProgressBar>(R.id.update_series_progress)
            try {
                // Enable the stop button
                requireView().findViewById<Button>(R.id.update_series_stop).isEnabled = true

                // Get the list of books the need to be updated
                val list = viewModel.repo.getBooksWithoutSeriesUpdate()
                if (!list.isNullOrEmpty()) {
                    // We have some books to update
                    active = true
                    // Set the progress bar max value
                    progressBar.max = list.size
                    // Start a flow that runs the update and reports progress
                    val flow = channelFlow {
                        // Start the job that runs the update
                        launch {
                            // Run the update job in a non-cancellable context. This is kind of hacky.
                            // The update isn't cancellable, but when the fragment is stopped we will
                            // terminate the job anyway. Making it non-cancellable, means that we can
                            // finished up the undo and save the updates we have already made.
                            withContext(NonCancellable) {
                                // Start an undo transaction for the update
                                viewModel.repo.withUndo(requireContext().resources.getString(R.string.update_series)) {
                                    // Run the update on a background thread
                                    withContext(BookDatabase.db.queryExecutor.asCoroutineDispatcher()) {
                                        // Update each book
                                        list.forEachIndexed { index, bookAndAuthors ->
                                            // When active is false, we need to finish
                                            if (!active)
                                                return@forEachIndexed
                                            // Update a book
                                            updateBook(bookAndAuthors)
                                            // Send progress
                                            send(index + 1)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Collect the progress reports and display them
                    flow.collect {
                        progressBar.progress = it
                    }
                }
            } finally {
                // We are done, make active false and clear the current update job
                active = false
                updateJob = null
                // If update was cancelled, then skip the UI updates
                ensureActive()
                requireView().findViewById<Button>(R.id.update_series_start).isEnabled = true
                requireView().findViewById<Button>(R.id.update_series_stop).isEnabled = false
                progressBar.progress = 0
            }
        }
    }

    /**
     * Update one book
     */
    private suspend fun updateBook(book: BookAndAuthors) {
        // We only work with Google books
        when (book.book.sourceId) {
            GoogleBookLookup.kSourceId -> {
                // Get the repo
                val repo = viewModel.repo
                // Get the book from Google books
                val online = viewModel.lookup.getVolume(requireActivity() as BookCredentials, book.book.volumeId!!)
                // If we have a series, then we need to add make sure it
                // is in the database.
                online?.series?.let { series ->
                    // Lookup the series in the database
                    (repo.findSeriesBySeriesId(series.seriesId) ?: run {
                        // We didn't find it, so look up the series on google books
                        viewModel.lookup.getSeries(requireActivity() as BookCredentials, series.seriesId)
                    })?.let {
                        book.series = it
                        book.book.seriesOrder = online.book.seriesOrder
                        // If the series matches the id in the book, then we are done
                        if (it.id != book.book.seriesId) {
                            // Set the flag to mark the update
                            book.book.flags = book.book.flags or BookEntity.SERIES
                            // Update the book
                            repo.addOrUpdateBook(book) { true }
                            return
                        }
                    }?: return  // We couldn't find the series, so leave it for later.
                }
                // Set the flag to mark the update. Note: We do not clear an existing series.
                repo.bookFlags.changeBits(true, BookEntity.SERIES, book.book.id, null)
            }
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         * @return A new instance of fragment UpdateSeriesFragment.
         */
        @JvmStatic
        fun newInstance() = UpdateSeriesFragment()
    }
}