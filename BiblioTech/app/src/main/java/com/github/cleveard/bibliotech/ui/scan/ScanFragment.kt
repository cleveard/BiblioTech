package com.github.cleveard.bibliotech.ui.scan

import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.gb.*
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
//import android.util.Log
import android.util.Size
import android.util.SparseArray
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
// import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.*
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.*
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.ui.books.BooksAdapter
import com.github.cleveard.bibliotech.ui.books.BooksViewModel
import com.github.cleveard.bibliotech.ui.gb.GoogleBookLoginFragment
import com.github.cleveard.bibliotech.ui.tags.TagViewModel
import com.github.cleveard.bibliotech.ui.tags.TagsFragment
import com.github.cleveard.bibliotech.ui.widget.ChipBox
import com.github.cleveard.bibliotech.utils.*
import com.github.cleveard.bibliotech.utils.ParentAccess
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.yanzhenjie.zbar.Config
import com.yanzhenjie.zbar.Image
import com.yanzhenjie.zbar.ImageScanner
import com.yanzhenjie.zbar.Symbol
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.lang.Exception
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

/**
 * Replace the entire contents of an editable with another
 * @param text The new contents
 * @return The editable
 */
fun Editable.setString(text: String): Editable {
    replace(0, length, text, 0, text.length)
    return this
}

internal class ScanViewModel: ViewModel() {
    /** Lookup to use with this view model */
    val lookup: GoogleBookLookup = GoogleBookLookup()
}

/**
 * Bar code scan fragment
 */
class ScanFragment : Fragment() {
    /**
     * Scan view model
     * Only used for its viewModelScope
     */
    private val scanViewModel: ScanViewModel by viewModels()

    /**
     * The books view model
     */
    private val booksViewModel: BooksViewModel by activityViewModels()

    /**
     * The tags view model
     */
    private val tagViewModel: TagViewModel by activityViewModels()

    /**
     * The current tags
     */
    private var tags: LiveData<List<TagEntity>>? = null

    /**
     * Root of the scan fragment layout
     */
    private lateinit var container: ConstraintLayout

    /**
     * The view that displays the camera preview
     */
    private lateinit var viewFinder: PreviewView

    /**
     * The camera
     */
    private var camera: Camera? = null

    /**
     * Broadcast manager for the app. Used to react to volume up/down button presses
     */
    private lateinit var broadcastManager: LocalBroadcastManager

    /**
     * Executor for the main thread
     */
    private lateinit var mainExecutor: Executor

    /**
     * The Z-Bar image scanner
     */
    private lateinit var scanner: ImageScanner

    /**
     * GPU compute interface
     */
    private lateinit var gpuCompute: GpuCompute.Compute

    /**
     * Display id of the camera preview view
     */
    private var displayId = -1

    /**
     * Lens to use for scanning
     */
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    /**
     * Camera provider
     */
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Preview camera stream
     */
    private var preview: Preview? = null

    /** Image capture stream */
    private var imageCapture: ImageCapture? = null

    /** Job capturing and interpreting image */
    private var captureJob: Job? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /** Listener used to detect when the camera is focused **/
    private var focusing: Boolean = false

    /**
     * Selection changed listener
     */
    private val selectChange: () -> Unit = {
        chipBox?.setChips(scanViewModel.viewModelScope, selectedNames(tags?.value))
    }

    /**
     * Observer of tag list live data
     */
    private val tagObserver: Observer<List<TagEntity>?> = Observer { list ->
        chipBox?.setChips(scanViewModel.viewModelScope, selectedNames(list))
    }

    /**
     * Observer for selection changes
     */
    private val selectionObserver = Observer<Int?> {
        // Update book stats on selection change
        updateBookStats()
    }

    private var chipBox: ChipBox? = null

    private val closeListener: View.OnClickListener = View.OnClickListener {chip ->
        chip as TagChip
        chipBox?.removeView(chip)
        tagViewModel.selection.selectAsync(chip.tag.id, false)
    }

    private val clickListener: View.OnClickListener = View.OnClickListener {chip ->
        chip as TagChip
        chipBox?.editChip(chip)
        tagViewModel.selection.selectAsync(chip.tag.id, false)
    }

    private inner class TagChip(val tag: TagEntity, context: Context): Chip(context) {
        init {
            text = tag.name
            isCloseIconVisible = true
            setOnCloseIconClickListener(closeListener)
            setOnClickListener(clickListener)
        }
    }

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.getOrDefault(Manifest.permission.CAMERA, false)) {
                // Take the user to the success fragment when permission is granted
                Toast.makeText(context, "Permission request granted", Toast.LENGTH_LONG).show()
                setUpCamera()
                view?.findViewById<View>(R.id.scan_permissions)?.visibility = View.GONE
            } else {
                view?.findViewById<View>(R.id.scan_permissions)?.visibility = View.VISIBLE
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }

    /**
     * @inheritDoc
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainExecutor = ContextCompat.getMainExecutor(requireContext())

        // Instance barcode scanner
        scanner = ImageScanner()
        scanner.setConfig(0, Config.X_DENSITY, 3)
        scanner.setConfig(0, Config.Y_DENSITY, 3)

        gpuCompute = GpuCompute.create(requireContext())
    }

    /**
     * Volume down button receiver used to trigger shutter
     * This listens for the intent broadcast by the activity when volume up/down is pressed
     */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, scan the bar code
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    captureJob = captureJob?: scanViewModel.viewModelScope.launch {
                        try {
                            if (focus()) {
                                // Capture an image
                                val image = capture()?: return@launch
                                // Convert it to a bitmap and crop to the viewFinder
                                val bitmap = extractAndRotate(image.use { convert(it) },
                                    viewFinder.width, viewFinder.height,
                                    image.imageInfo.rotationDegrees)
                                // Process the image
                                processImage(bitmap)
                            }
                        } finally {
                            captureJob = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Internal reference of the [DisplayManager]
     */
    private lateinit var displayManager: DisplayManager

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@ScanFragment.displayId) {
                // Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                this@ScanFragment.imageCapture?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        super.onDestroyView()
        tags?.removeObserver(tagObserver)
        tags = null
        chipBox = null

        // Shut down our background executor
        cameraExecutor.shutdown()

        booksViewModel.selection.selectedCount.removeObserver(selectionObserver)
        booksViewModel.selection.itemCount.removeObserver(selectionObserver)

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
        tagViewModel.selection.onSelectionChanged.remove(selectChange)
    }

    /**
     * @inheritDoc
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Initialize view model observers
        booksViewModel.selection.selectedCount.observe(viewLifecycleOwner, selectionObserver)
        booksViewModel.selection.itemCount.observe(viewLifecycleOwner, selectionObserver)

        // Add options menu provider
        requireActivity().addMenuProvider(
            object: MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.settings_option, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    // Direct action to onActionSelected
                    return when(menuItem.itemId) {
                        R.id.action_to_settingsFragment -> {
                            (activity as? ManageNavigation)?.navigate(
                                ScanFragmentDirections.actionNavScanToSettingsFragment()
                            )
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        return inflater.inflate(R.layout.scan_fragment, container, false)
    }

    /**
     * Clear isbn and title text views
     */
    private fun clearView() {
        container.findViewById<EditText>(R.id.scan_isbn).text.clear()
        container.findViewById<EditText>(R.id.scan_title).text.clear()
        container.findViewById<EditText>(R.id.scan_author).text.clear()
    }

    /**
     * @inheritDoc
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup the chips for the selected tag
        tagViewModel.selection.onSelectionChanged.add(selectChange)
        chipBox = view.findViewById<ChipBox>(R.id.selected_tags)?.also {
            setupChips(it)
        }

        // Setup local properties
        container = view.findViewById(R.id.scan_content)
        viewFinder = container.findViewById(R.id.view_finder)
        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Clear the isbn and title
        clearView()

        container.findViewById<Button>(R.id.scan_search).setOnClickListener {
            val titleView = container.findViewById<EditText>(R.id.scan_title)
            val authorView = container.findViewById<EditText>(R.id.scan_author)
            // Don't search by ISBN if the author or title have focus
            val isbn: String = if (titleView.hasFocus() || authorView.hasFocus())
                ""
            else
                container.findViewById<EditText>(R.id.scan_isbn).text.toString()
            searchForBooks(isbn, titleView.text.toString(), authorView.text.toString())
        }
        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, recompute layout
        displayManager = viewFinder.context
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Clear filter from books view model
        booksViewModel.filterName = null
        activity?.findViewById<TextView>(R.id.book_stats)?.visibility = View.VISIBLE
        updateBookStats()

        container.findViewById<MaterialButton>(R.id.scan_ask_permission).setOnClickListener {
            // Request camera-related permissions
            permissionLauncher.launch(PERMISSIONS_REQUIRED)
        }

        // Wait for the views to be properly laid out
        viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = viewFinder.display.displayId

            // Log.d(TAG, "ViewFinder: ${viewFinder.width}x${viewFinder.height}")
            if (hasPermissions(requireContext())) {
                setUpCamera()
                view.findViewById<View>(R.id.scan_permissions).visibility = View.GONE
            } else {
                view.findViewById<View>(R.id.scan_permissions).visibility = View.VISIBLE
            }
        }

        // Make sure we are logged in
        GoogleBookLoginFragment.login(this)
    }


    private fun updateBookStats() {
        // Update book stats on selection change
        val booksSelected = booksViewModel.selection.selectedCount.value?: 0
        val booksCount = booksViewModel.selection.itemCount.value?: 0
        activity?.findViewById<TextView>(R.id.book_stats)?.text =
            getString(R.string.book_stats, booksCount, booksSelected)
    }

    /**
     * Return sequence of selected names
     */
    private fun selectedNames(list: List<TagEntity>?): Sequence<String>? {
        return list?.asSequence()
            ?.filter { it.isSelected }
            ?.map { it.name }
    }

    /**
     * Call when we need to create a chip
     * @param scope CoroutineScope to use for sub-jobs
     * @param text The text for the chip
     */
    private suspend fun onCreateChip(scope: CoroutineScope, text: String): Chip? {
        // Only process if we have the list of tags
        return tags?.value?.let { list ->
            // Find the tag with the text
            val index = list.binarySearch {
                it.name.compareTo(text, true)
            }
            if (index < 0) {
                // Didn't find one, try to add one
                val tag = TagEntity(0L, text, "", 0)
                tag.id = TagsFragment.addOrEdit(
                    tag,
                    LayoutInflater.from(context),
                    tagViewModel.repo,
                    scope
                )
                // If we added a tag, then make the chip
                // otherwise return null
                if (tag.id != 0L)
                    makeChip(tag)
                else
                    null
            } else
                makeChip(list[index])
        }
    }

    /**
     * Setup the chips for the chip box
     */
    private fun setupChips(box: ChipBox) {
        /**
         * Get the auto complete query string for a column
         */
        fun getQuery(): Cursor {
            // return the query string
            return ColumnDataDescriptor.buildAutoCompleteCursor(
                tagViewModel.repo, BookDatabase.tagsTable,
                TAGS_NAME_COLUMN, ""
            )
        }

        box.delegate = object: ChipBox.Delegate {
            override val scope: CoroutineScope
                get() = scanViewModel.viewModelScope

            override suspend fun onCreateChip(
                chipBox: ChipBox,
                text: String,
                scope: CoroutineScope
            ): Chip? {
                return this@ScanFragment.onCreateChip(scope, text)
            }

            override suspend fun onChipAdded(chipBox: ChipBox, chip: View, scope: CoroutineScope) {
                tagViewModel.selection.selectAsync((chip as TagChip).tag.id, true)
            }

            override suspend fun onChipRemoved(
                chipBox: ChipBox,
                chip: View,
                scope: CoroutineScope
            ) {
                tagViewModel.selection.selectAsync((chip as TagChip).tag.id, false)
            }

            override fun onEditorFocusChange(chipBox: ChipBox, edit: View, hasFocus: Boolean) { }
        }

        // If the view is an AutoComplete view, then add a chip when an item is selected
        box.chipInput.autoCompleteClickListener =
            AdapterView.OnItemClickListener { _, _, _, _ ->
                box.onCreateChipAction()
            }
        box.chipInput.autoCompleteSelectOnly = true

        scanViewModel.viewModelScope.launch {
            // Get the cursor for the column, null means no auto complete
            val cursor = withContext(tagViewModel.repo.queryScope.coroutineContext) {
                getQuery()
            }

            // Get the adapter from the column description
            val adapter = SimpleCursorAdapter(
                context,
                R.layout.books_drawer_filter_auto_complete,
                cursor,
                arrayOf("_result"),
                intArrayOf(R.id.auto_complete_item),
                0
            )
            adapter.stringConversionColumn = cursor.getColumnIndex("_result")

            // Set the adapter on the text view
            box.chipInput.autoCompleteAdapter = ChipBox.SelectCursorItemAdapter(adapter)

            tags = tagViewModel.repo.getTagsLive().also {
                it.observeForever(tagObserver)
            }
            box.setChips(this, selectedNames(tags?.value))
        }
    }

    /**
     * Make a chip from a tag
     * @param tag The tag
     */
    private fun makeChip(tag: TagEntity?): TagChip? {
        return tag?.let { t ->
            TagChip(t, requireContext())
        }
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                else -> throw IllegalStateException("Back camera is unavailable")
            }

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /**
     * Declare and bind preview, capture and analysis use cases
     */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = requireContext().getSystemService(WindowManager::class.java).currentWindowMetrics
            Size(metrics.bounds.width(), metrics.bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            Size(metrics.widthPixels, metrics.heightPixels)
        }
        // Log.d(TAG, "Screen metrics: ${metrics.width} x ${metrics.height}")

        val screenAspectRatio = aspectRatio(metrics.width, metrics.height)
        // Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        val cameraProvider = cameraProvider?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // Set up the capture use case to allow users to take photos
        this.imageCapture = ImageCapture.Builder()
            // We request aspect ratio but no resolution to match preview config but letting
            // CameraX optimize for whatever specific resolution best fits requested capture mode
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // Apply declared configs to CameraX using the same lifecycle owner
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch(e: Exception) {
            // Log.e(TAG, "Use case binding failed", e)
        }
    }

    /**
     * Find books from a sequence of ISBNs
     * @param codes The ISBNs
     * @return True if a book was found. False otherwise.
     */
    private suspend fun findBooks(codes: Sequence<String>) {
        if (codes.firstOrNull() == null) {
            // If we got here we didn't scan anything
            Toast.makeText(
                context,
                requireContext().resources.getString(R.string.no_ISBNs),
                Toast.LENGTH_LONG
            ).show()
        }
        if (!lookupISBNs(codes) { scanViewModel.lookup.lookupISBN(requireActivity() as BookCredentials, it) } &&
            !lookupISBNs(codes) { scanViewModel.lookup.generalLookup(requireActivity() as BookCredentials, it, 0, 20) }) {
            // If we got here we didn't find anything
            Toast.makeText(
                context,
                requireContext().resources.getString(R.string.no_books_found),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private suspend fun addBook(book: BookAndAuthors) {
        val repo = booksViewModel.repo
        // If we have a series, then we need to add make sure it
        // is in the database.
        book.series = book.series?.let {series ->
            // If the series matches the id in the book, then we are done
            if (series.id == book.book.seriesId)
                series
            else {
                // Lookup the series in the database
                repo.findSeriesBySeriesId(series.seriesId)?: run {
                    // We didn't find it, so look up the series on google books
                    scanViewModel.lookup.getSeries(requireActivity() as BookCredentials, series.seriesId)
                }
            }
        }

        // Select the book
        book.book.isSelected = true
        // Add the book to the database
        repo.addOrUpdateBook(book)
    }

    /**
     * Lookup a book using isbn
     * @param codes The ISBNs to use for the lookup
     * @param lookup A callback to lookup each ISBN
     * @return True if a book was found. False otherwise
     */
    private suspend fun lookupISBNs(codes: Sequence<String>, lookup: suspend (isbn: String) -> GoogleQueryPagingSource.LookupResult<BookAndAuthors>?): Boolean {
        // Look through the codes we found
        for (isbn in codes) {
            try {
                // Lookup using isbn
                val result = lookup(isbn)
                if (result == null || result.list.isEmpty())
                    continue

                // Select a book from the ones we found
                val array = selectBook(result.list, isbn, result.itemCount, false)
                if (array.size() > 0) {
                    val book = array.valueAt(0)
                    // When a book is selected, set the title
                    container.findViewById<EditText>(R.id.scan_title).text.setString(book.book.title)
                    container.findViewById<EditText>(R.id.scan_author).text.setString(
                        buildAuthors(StringBuilder(), book.authors))

                    // Make sure the isbn in the book record is the one we searched
                    if (book.isbns.indexOfFirst { isbn.compareTo(it.isbn, true) == 0 } < 0) {
                        book.isbns = book.isbns.toMutableList().apply {
                            add(IsbnEntity(id = 0, isbn = isbn))
                        }
                    }
                    // Deselect everything else
                    booksViewModel.selection.selectAll(false)
                    // Add the book to the database
                    addBook(book)
                    // Stop looking
                    return true
                }
            } catch (e: GoogleBookLookup.LookupException) {
                // Display and error if we found one
                Toast.makeText(
                    context,
                    "Error finding book: $e",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        return false
    }

    private fun searchForBooks(isbn: String, title: String, author: String) {
        // Don't search if no input
        if (isbn.isEmpty() && title.isEmpty() && author.isEmpty())
            return

        // Start a coroutine job to run query for the book
        scanViewModel.viewModelScope.launch {
            // Look through the codes we found
            try {
                if (isbn.isNotEmpty()) {
                    val codes = ArrayList<String>()
                    for (s in isbn.replace(getXDigit, "X").split(splitIsbn)) {
                        val code = s.replace(onlyDigitsAndX, "")
                        if (code.isNotEmpty() && !addToCodes(codes, code)) {
                            Toast.makeText(
                                context,
                                requireContext().resources.getString(R.string.bad_isbn, code),
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                    }

                    // Find the books
                    findBooks(codes.asSequence())
                    return@launch
                }

                // Lookup using title and/or author
                val spec = scanViewModel.lookup.getTitleAuthorQuery(title, author)
                val result = scanViewModel.lookup.generalLookup(requireActivity() as BookCredentials, spec)
                // If we still didn't find anything, stop
                if (result == null || result.list.isEmpty()) {
                    Toast.makeText(
                        context,
                        requireContext().resources.getString(R.string.no_books_found),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Select books from the ones we found
                val array = selectBook(result.list, spec, result.itemCount, true)
                // Deselect all
                booksViewModel.selection.selectAll(false)
                for (i in 0 until array.size()) {
                    val book = array.valueAt(i)
                    // Add the book to the database
                    addBook(book)
                }
            } catch (e: GoogleBookLookup.LookupException) {
                // Display and error if we found one
                Toast.makeText(
                    context,
                    "Error finding book: $e",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysis] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /**
     * Start auto focus for the camera
     */
    private suspend fun focus(retries: Int = 3): Boolean {
        if (focusing)
            return false

        // Position where we want to focus
        val x = viewFinder.x + viewFinder.width / 2f
        val y = viewFinder.y + viewFinder.height / 2f

        val pointFactory = SurfaceOrientedMeteringPointFactory(
            viewFinder.width.toFloat(), viewFinder.height.toFloat())
        val afPointWidth = 1.0f / 6.0f  // 1/6 total area
        val aePointWidth = afPointWidth * 1.5f
        val afPoint = pointFactory.createPoint(x, y, afPointWidth)
        val aePoint = pointFactory.createPoint(x, y, aePointWidth)

        return camera?.let { cam ->
            try {
                fun buildAction(): FocusMeteringAction {
                    return FocusMeteringAction.Builder(afPoint, FocusMeteringAction.FLAG_AF)
                        .addPoint(aePoint, FocusMeteringAction.FLAG_AE)
                        .build()
                }
                focusing = true
                // Make sure the camera supports focusing. Emulator doesn't
                // support it.
                if (cam.cameraInfo.isFocusMeteringSupported(buildAction())) {
                    var attempts = 0

                    // Run in IO thread
                    withContext(Dispatchers.IO) {
                        // Loop until we get focused, or we run out of attempts
                        do {
                            // Not focused if too many attempts
                            if (attempts >= retries)
                                return@withContext false
                            ++attempts
                            // Start the auto focus
                            val result = cam.cameraControl.startFocusAndMetering(buildAction())
                        } while (result.get()?.isFocusSuccessful != true)
                        true        // Focused
                    }
                } else
                    true        // Assume focused if auto-focus isn't supported
            } catch (e: CameraInfoUnavailableException) {
                // Log.d(TAG, "cannot access camera", e)
                false
            } finally {
                focusing = false
            }
        }?: false
    }

    /**
     * Capture an image from the camera
     * @return The capture image, or null if the capture failed
     */
    private suspend fun capture(): ImageProxy? {
        return imageCapture?.let {cap ->
            suspendCoroutine { cont ->
                try {
                    cap.takePicture(
                        Dispatchers.IO.asExecutor(),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                super.onCaptureSuccess(image)
                                cont.resume(image)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                super.onError(exception)
                                // Log.d(TAG, "Start capture failed $exception")
                                cont.resume(null)
                            }
                        })
                } catch (e: Exception) {
                    // Log.d(TAG, "Start capture failed $e")
                    cont.resume(null)
                }
            }
        }
    }

    /**
     * Convert and image into a bitmap
     * @param image The image to convert
     * @return The converted bitmap
     */
    private suspend fun convert(image: ImageProxy): Bitmap {
        // Do this on a worker thread
        return withContext(Dispatchers.IO) {
            if (image.format == ImageFormat.JPEG) {
                // Decode Jpeg into a bitmap
                val array = image.planes[0].buffer.toByteArray()
                BitmapFactory.decodeByteArray(array, 0, array.size)
            } else {
                // Otherwise assume yuv and convert to rgb bitmap
                gpuCompute.yuvToRgb(image)
            }
        }
    }

    /**
     * Extract a subset of the image and rotate to proper orientation
     * @param bm The source bitmap
     * @param width The width of the view finder
     * @param height The height of the view finder
     * @param rotation The rotation
     * @return The new bitmap
     */
    private fun extractAndRotate(bm: Bitmap, width: Int, height: Int, rotation: Int): Bitmap {
        // Make a matrix for the rotation
        val tm = Matrix()
        tm.postRotate(rotation.toFloat())
        // Set the extents of the extracted image. The orientation of the
        // view finder depends on the rotation
        val vfW: Int
        val vfH: Int
        if (rotation == 90 || rotation == 270) {
            vfW = height
            vfH = width
        } else {
            vfW = width
            vfH = height
        }

        // Set the extents based on the aspect ratio of the view finder
        val x: Int
        val y: Int
        val w: Int
        val h: Int
        if (bm.width * vfH > bm.height * vfW) {
            y = 0
            h = bm.height
            w = (h * vfW + vfH - 1) / vfH   // Round up
            x = (bm.width - w) / 2
        } else {
            x = 0
            w = bm.width
            h = (w * vfH + vfW - 1) / vfW   // Round up
            y = (bm.height - h) / 2
        }

        // Create the bitmap
        return Bitmap.createBitmap(bm, x, y, w, h, tm, true)
    }

    /**
     * Process an image and extract the ISBNs
     * @param image The image in a bitmap
     */
    private suspend fun processImage(image: Bitmap) {
        // Look for bar codes
        val barCodes = ArrayList<String>()
        // Scan the bar codes
        scanBarcode(image, barCodes)

        // Display the bar codes in the UI
        container.findViewById<EditText>(R.id.scan_isbn).text.setString(barCodes.joinToString(" "))
        // Find the books
        findBooks(barCodes.asSequence())
    }

    /**
     * Scan an image for a barcode
     */
    private suspend fun scanBarcode(bm: Bitmap, codes: MutableList<String>) {
        withContext(Dispatchers.IO) {
            // Put the image in the format Z-bar wants
            val barcode = Image(bm.width, bm.height, "GREY")
            val array = gpuCompute.rgbToLum(bm)
            /* val showImage = Bitmap.createBitmap(IntArray(array.size) {
                val v = array[it].toInt() and 0xff
                (0xff shl 24) + (v shl 16) + (v shl 8) + v
            }, bm.width, bm.height, Bitmap.Config.ARGB_8888)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setMessage("Scanned Image")
                    .setView(ImageView(requireContext()).apply {
                        setImageBitmap(showImage)
                    })
                    .setPositiveButton(R.string.ok) {_, _ -> }
                    .show()
            } */
            barcode.data = array

            // Scan the image for bar code. Returns 0 if nothing was found
            val result = try {
                scanner.scanImage(barcode)
            } catch (e: Exception) {
                // Log.d(TAG, "ZBar failed $e")
                0
            }

            if (result != 0) {
                // Get the results and look for ISBN codes
                val symbols = scanner.results
                for (sym in symbols) {
                    // When we find an ISBN, add it to the codes
                    when (sym.type) {
                        Symbol.ISBN10,
                        Symbol.ISBN13,
                        Symbol.EAN13 ->
                            addToCodes(codes, sym.data)
                    }
                }
            }
        }
    }

    /**
     * Helper extension function used to extract a byte array from an image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    companion object {
        ///**
        // * Tag for Logging
        // */
        //private const val TAG = "CameraXBasic"

        /**
         * Common camera ratios
         */
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Expressions for splitting ISBNs **/
        private val splitIsbn: Regex = Regex("(\\s*,\\s*)+")
        private val getXDigit: Regex = Regex("[*#]")
        private val onlyDigitsAndX: Regex = Regex("[^\\dX]")

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        private fun buildAuthors(builder: StringBuilder, list: List<AuthorEntity>): String {
            for ((i, a) in list.withIndex()) {
                builder.append("${if (i > 0) ", " else ""}${a.name}")
            }
            return builder.toString()
        }

        /**
         * Convert ISBN 13 to an ISBN 10
         * @param isbn The ISBN 13 to convert
         * @return The ISBN 10, or null if the isbn13 can't be converted
         */
        private fun isbn13To10(isbn: CharSequence): String? {
            // Make sure the isbn can be converted
            if (isbn.length != 13 || !isbn.startsWith("978") || checkIsbn13(isbn) != 0)
                return null
            // Extract the isbn 10 without the check digit
            val isbn10 = isbn.subSequence(3, 12).toString()
            // Calculate the check digit
            val c = checkIsbn10(isbn10)
            // Append the check digit. c == 1 means the check digit should be 10, which is 'X'
            // Otherwise append the digit
            return isbn10 + if (c == 1)
                'X'
            else
                '0' + ((11 - c) % 11)
        }

        /**
         * Convert ISBN 10 to ISBN 13
         * @param isbn The isbn 10 to convert
         * @return The ISBN 13 or null if it can't be converted
         */
        private fun isbn10To13(isbn: CharSequence): String? {
            // Make sure the isbn can be converted
            if (isbn.length != 10 || checkIsbn10(isbn) != 0)
                return null
            // Form the isbn 13 without the check digit
            val isbn13 = "978" + isbn.subSequence(0, 9)
            // Check sum the isbn 13
            val c = checkIsbn13(isbn13)
            // Append the digit
            return isbn13 + ('0' + ((10 - c) % 10))
        }

        /**
         * Calculate the isbn 10 checksum
         * @param isbn The isbn 10 number with or without the check sum
         * @return The checksum
         * If isbn is shorter than 10, then the check sum assumes the missing
         * characters are all 0. If isbn is longer than 10, only the first 10
         * characters are used.
         */
        private fun checkIsbn10(isbn: CharSequence): Int {
            var sum = 0

            // Loop over all of the digits but no more than 9
            val end = isbn.length.coerceAtMost(9)
            for (i in 0 until end) {
                // Get the character and sum it. It must be a digit
                val c = isbn[i]
                sum += (10 - i) * when {
                    c.isDigit() -> c - '0'
                    else -> return -1
                }
            }
            // The last character, if present, can be a digit or 'X'
            if (isbn.length >= 10) {
                val c = isbn[9]
                sum += when {
                    c.isDigit() -> c - '0'
                    c == 'X' -> 10
                    else -> return -1            // Add in the extra digits
                }
            }

            // Return the checksum
            return sum % 11
        }

        /**
         * Checksum an ISBN 13
         * @param isbn The ISBN 13
         * @return The checksum
         * If isbn is shorter than 13, then the check sum assumes the missing
         * characters are all 0. If isbn is longer than 13, only the first 13
         * characters are used.
         */
        private fun checkIsbn13(isbn: CharSequence): Int {
            var e = 0
            var o = 0

            val end = isbn.length.coerceAtMost(13)
            // Loop over the characters in the isbn. Accumulate sums for even and
            // odd indices separately.
            for (i in 1 until end step 2) {
                // Sum even index
                var c = isbn[i - 1]
                e += when {
                    c.isDigit() -> c - '0'
                    else -> return -1
                }
                // Sum odd index
                c = isbn[i]
                o += when {
                    c.isDigit() -> c - '0'
                    else -> return -1
                }
            }
            // If there are an odd number of characters, then add in the last one
            if ((end and 1) != 0) {
                val c = isbn[end - 1]
                e += when {
                    c.isDigit() -> c - '0'
                    else -> return -1
                }
            }
            return (e + 3 * o) % 10
        }

        /**
         * Validate the isbn
         * @param isbn The isbn to validate
         * @return True if valid, false if invalid
         */
        private fun validIsbn(isbn: CharSequence): Boolean {
            return when(isbn.length) {
                10 -> checkIsbn10(isbn) == 0
                13 -> checkIsbn13(isbn) == 0
                else -> false
            }
        }

        /**
         * Add a symbol to the isbn codes
         */
        private fun addToCodes(codes: MutableList<String>, isbn: String): Boolean {
            // Make sure it is valid
            if (validIsbn(isbn)) {
                // Add it to the codes found
                codes.add(isbn)
                // Try to convert to other format
                when(isbn.length) {
                    10 -> isbn10To13(isbn)
                    else -> isbn13To10(isbn)
                }?.let {
                    // It converted, add it to the codes
                    codes.add(it)
                }
                return true
            }
            return false
        }
    }

    /**
     * Class to handle filter terms from an edit text
     * @param scope A coroutine scope for coroutine operations
     */
    private abstract class FilterHandler(val scope: CoroutineScope): TextWatcher {
        protected val filterTerms: ArrayList<String> = ArrayList()
        private var edit: EditText? = null
        private var adapter: BooksAdapter? = null
        private var changed: Boolean = false
        private var channel: Channel<Boolean> = Channel()
        private var job: Job? = null

        /**
         * Initialize the handler
         * @param edit The EditText that has the filter terms
         * @param adapter The recycler adapter that shows the filtered books
         */
        fun initialize(edit: EditText, adapter: BooksAdapter): Flow<Boolean> {
            this.edit = edit
            edit.addTextChangedListener(this)
            this.adapter = adapter

            // This flow is used send the filter terms to the adapter
            return flow {
                emit(true)
                for (l in channel) {
                    emit(l)
                }
            }
        }

        /**
         * Parse a string into filter terms
         * @param data The string to parse
         */
        fun parseFilter(data: Editable) {
            filterTerms.clear()
            val trim = data.toString().trim { it <= ' ' }
            if (trim.isEmpty()) {
                return
            }

            val term = StringBuilder(trim.length)
            // Extract filter terms
            var i = 0
            while (i < trim.length) {
                var ch = trim[i++]
                // We terminate a term on an unquoted whitespace
                if (ch.isWhitespace()) {
                    // If we collected anything add it to the list and clear the term
                    if (term.isNotEmpty()) {
                        filterTerms.add(term.toString())
                        term.clear()
                    }
                } else if (ch == '"') {
                    // If we get a quote the we start a quoted section
                    if (i < trim.length) {
                        // "" just becomes a single unquoted character
                        if (trim[i] == '"') {
                            // Add the " and skip the second one
                            term.append(ch)
                            i++
                        } else {
                            // Quoted section. collect characters until we get
                            // to the end of the string or the matching quote
                            do {
                                ch = trim[i++]
                                if (ch == '"') {
                                    // Found a quote, if it isn't "" then stop
                                    if (i >= trim.length || trim[i + 1] != '"')
                                        break
                                    i++ // skip the second "
                                }
                                // Add the character
                                term.append(ch)
                            } while (i < trim.length)
                        }
                    }
                } else
                    term.append(ch)     // Add other characters
            }
            // Add the last term, if there is one
            if (term.isNotEmpty())
                filterTerms.add(term.toString())
        }

        fun cancel() {
            channel.close()
            edit?.removeTextChangedListener(this)
            job?.cancel()
        }

        /**
         * @inheritDoc
         */
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

        /**
         * @inheritDoc
         */
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

        /**
         * @inheritDoc
         */
        override fun afterTextChanged(data: Editable?) {
            // Delay the parse to collect multiple characters
            changed = true
            job = job?: scope.launch {
                while (changed) {
                    changed = false
                    delay(750)
                }

                // parse the filter and notify that the data set has changed
                data?.let { parseFilter(it) }
                channel.send(true)
                job = null
            }
        }

        /**
         * Filter a book
         * @param book The book to filter
         * @return True if the book is kept, false if it is removed
         */
        abstract fun filter(book: BookAndAuthors): Boolean
    }

    /**
     * Select a book from a google query
     * @param list List of books in first page returned from the book query
     * @param spec query used to get the page of books
     * @param totalItems Total number of books the query found
     */
    private suspend fun selectBook(list: List<BookAndAuthors>, spec: String, totalItems: Int, isMulti: Boolean): SparseArray<BookAndAuthors> {
        return coroutineScope {
            if (list.size == 1 && !isMulti) {
                // If the query resulted in a single book, then select it
                return@coroutineScope SparseArray<BookAndAuthors>(1).also {
                    it.put(0, list[0])
                }
            }
            // Get the page and item counts
            val pageCount = list.size
            val itemCount = if (totalItems > 0) totalItems else pageCount
            // This is where we keep the pager job
            var pagerJob: Job? = null
            // Setup separate thumbnails for this query
            val thumbnails = Thumbnails("gb")

            // Filter for titles
            val titleFilter = object: FilterHandler(this) {
                override fun filter(book: BookAndAuthors): Boolean {
                    // If there are no filter terms, then keep the book
                    return if (filterTerms.isNotEmpty()) {
                        for (s in filterTerms) {
                            if (book.book.title.contains(s, true))
                                return true
                        }
                        false
                    } else
                        true
                }
            }

            // Filter for authors
            val authorFilter = object: FilterHandler(this) {
                override fun filter(book: BookAndAuthors): Boolean {
                    return if (filterTerms.isNotEmpty()) {
                        for (s in filterTerms) {
                            for (a in book.authors) {
                                if (a.name.contains(s, true))
                                    return true
                            }
                        }
                        false
                    } else
                        true
                }
            }

            try {
                // Otherwise display a dialog to select the book
                coroutineAlert<SparseArray<BookAndAuthors>>(requireContext(), { SparseArray() }) { alert ->

                    // Get the content view for the dialog
                    val content =
                        requireParentFragment().layoutInflater.inflate(R.layout.scan_select_book, null)

                    // Create an object the book adapter uses to get info
                    val access = object: ParentAccess {
                        // This array is used to map positions to selected books
                        // and is the final result of the select book dialog
                        val selection = alert.result
                        // This is the adapter
                        lateinit var adapter: BooksAdapter

                        // Clear the selected items - only use for !isMulti
                        fun clearSelection(selected: Boolean) {
                            for (i in 0 until selection.size())
                                selection.valueAt(i).book.isSelected = selected
                            selection.clear()
                            adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        }
                        // Toggle the selection for an id
                        override fun toggleSelection(id: Long, position: Int) {
                            val index = id.toInt()
                            if (selection.indexOfKey(index) >= 0) {
                                // Selection contains the key, remove it
                                // for multi selection, do nothing for single
                                if (!isMulti)
                                    return      // Don't refresh the adapter
                                selection.get(index).book.isSelected = false
                                selection.remove(index)
                            } else {
                                // Selection doesn't contain the key, add it
                                // clear any other selections if not multi selection
                                if (!isMulti) {
                                    clearSelection(false)
                                }
                                // Add the boo
                                adapter.getBook(position)?.let {
                                    it.book.isSelected = true
                                    selection.put(index, it)
                                }
                            }
                        }

                        // Get the context
                        override val context: Context
                            get() = this@ScanFragment.requireContext()

                        // Get the coroutine scope
                        override val scope: CoroutineScope
                            get() = this@coroutineScope

                        // Get a thumbnail
                        override suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
                            // Use the thumbnails we constructed above
                            return thumbnails.getThumbnail(bookId, large) {b, l ->
                                // Get the book from the adapter and the url from the book
                                adapter.getBook(b.toInt())?.let {
                                    if (l)
                                        it.book.largeThumb
                                    else
                                        it.book.smallThumb
                                }
                            }
                        }
                    }

                    // Find the recycler view and set the layout manager and adapter
                    val titles = content.findViewById<RecyclerView>(R.id.title_buttons)
                    access.adapter = BooksAdapter(access, R.layout.books_adapter_book_item_always, 0)
                    titles.adapter = access.adapter

                    // initialize the title and author filters
                    val filterFlow = titleFilter.initialize(content.findViewById(R.id.title_filter), access.adapter)
                        .combine(
                            authorFilter.initialize(content.findViewById(R.id.author_filter), access.adapter)
                        ) {t1, t2 ->
                            t1 || t2
                        }

                    // Create a pager to drive the recycler view
                    val config = PagingConfig(pageSize = 20, initialLoadSize = pageCount)
                    val pager = Pager(
                        config
                    ) {
                        scanViewModel.lookup.generalLookupPaging(requireActivity() as BookCredentials, spec, itemCount, list)
                    }
                    val flow = pager.flow.cachedIn(scanViewModel.viewModelScope)
                        .combine(filterFlow) {data, b ->
                            val clone = data.map {book ->
                                if (book is BookAndAuthors) {
                                    book.copy()
                                } else
                                    book
                            }
                            if (b) {
                                clone.filter { book ->
                                    // filter the books using the title and author edit text contents
                                    if (book is BookAndAuthors) {
                                        access.selection.indexOfKey(book.book.id.toInt()) >= 0 ||
                                            (titleFilter.filter(book) && authorFilter.filter(book))
                                    } else
                                        true
                                }
                            } else
                                clone
                        }

                    // Start the book stream to the recycler view
                    pagerJob = scanViewModel.viewModelScope.launch {
                        flow.collectLatest { data ->
                            access.adapter.submitData(data)
                        }
                    }

                    // Create an alert dialog with the content view
                    alert.builder.setTitle(if (isMulti) R.string.select_titles else R.string.select_title)
                        // Specify the list array, the items to be selected by default (null for none),
                        // and the listener through which to receive callbacks when items are selected
                        .setView(content)
                        // Set the action buttons
                        .setPositiveButton(R.string.ok, null)
                        .setNegativeButton(R.string.cancel, null)
                }.show()
            } finally {
                titleFilter.cancel()
                authorFilter.cancel()
                pagerJob?.cancel()
                thumbnails.deleteAllThumbFiles()
            }
        }
    }
}
