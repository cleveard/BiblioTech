package com.github.cleveard.BiblioTech.ui.scan

import com.github.cleveard.BiblioTech.db.*
import com.github.cleveard.BiblioTech.gb.*
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.widget.*
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.BiblioTech.*
import com.github.cleveard.BiblioTech.R
import com.github.cleveard.BiblioTech.gb.GoogleBookLookup.Companion.generalLookup
import com.github.cleveard.BiblioTech.ui.books.BooksAdapter
import com.github.cleveard.BiblioTech.ui.books.BooksViewModel
import com.github.cleveard.BiblioTech.ui.tags.TagViewModel
import com.github.cleveard.BiblioTech.ui.tags.TagsFragment
import com.github.cleveard.BiblioTech.ui.widget.ChipBox
import com.github.cleveard.BiblioTech.utils.*
import com.github.cleveard.BiblioTech.utils.ParentAccess
import com.google.android.material.chip.Chip
import com.yanzhenjie.zbar.Config
import com.yanzhenjie.zbar.Image
import com.yanzhenjie.zbar.ImageScanner
import com.yanzhenjie.zbar.Symbol
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

/**
 * Helper type alias used for analysis use case callbacks
 */
typealias BarcodeListener = (codes: List<String>) -> Unit

/**
 * Bar code scan fragment
 */
class ScanFragment : Fragment() {
    /**
     * The books view model
     */
    private lateinit var booksViewModel: BooksViewModel

    /**
     * The tags view model
     */
    private lateinit var tagViewModel: TagViewModel

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
    private lateinit var viewFinder: TextureView

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
     * Display id of the camera preview view
     */
    private var displayId = -1

    /**
     * Lens to use for scanning
     */
    private var lensFacing = CameraX.LensFacing.BACK

    /**
     * Preview camera stream
     */
    private var preview: Preview? = null

    /**
     * Image capture stream
     */
    // TODO: Is this needed?
    private var imageCapture: ImageCapture? = null

    /**
     * Image analyzer - used to analyze images captured from the camera
     */
    private var imageAnalyzer: ImageAnalysis? = null

    /**
     * Flag that turns bar code scanning on and off.
     * Set this to true to start scanning for a barcode
     * Set to false when you are done.
     */
    private var previewing = false

    /**
     * Selection changed listener
     */
    private val selectChange: () -> Unit = {
        chipBox?.setChips(tagViewModel.viewModelScope, selectedNames(tags?.value))
    }

    /**
     * Observer of tag list live data
     */
    private val tagObserver: Observer<List<TagEntity>?> = Observer {list ->
        chipBox?.setChips(tagViewModel.viewModelScope, selectedNames(list))
    }

    private var chipBox: ChipBox? = null

    private val closeListener: View.OnClickListener = View.OnClickListener {chip ->
        chip as TagChip
        chipBox?.removeView(chip)
        tagViewModel.selection.select(chip.tag.id, false)
    }

    private val clickListener: View.OnClickListener = View.OnClickListener {chip ->
        chip as TagChip
        chipBox?.editChip(chip)
        tagViewModel.selection.select(chip.tag.id, false)
    }

    private inner class TagChip(val tag: TagEntity, context: Context): Chip(context) {
        init {
            text = tag.name
            isCloseIconVisible = true
            setOnCloseIconClickListener(closeListener)
            setOnClickListener(clickListener)
        }
    }

    /**
     * @inheritDoc
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true
        mainExecutor = ContextCompat.getMainExecutor(requireContext())

        // Instance barcode scanner
        scanner = ImageScanner()
        scanner.setConfig(0, Config.X_DENSITY, 3)
        scanner.setConfig(0, Config.Y_DENSITY, 3)
    }

    /**
     * @inheritDoc
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                // Take the user to the success fragment when permission is granted
                Toast.makeText(context, "Permission request granted", Toast.LENGTH_LONG).show()
                bindCameraUseCases()
            } else {
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
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
                    // Clear the isbn and title fields
                    clearView()
                    // Focus the camera
                    focus()
                    // Turn on bar code scanning in the analyzer
                    previewing = true
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
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                preview?.setTargetRotation(view.display.rotation)
                imageCapture?.setTargetRotation(view.display.rotation)
                imageAnalyzer?.setTargetRotation(view.display.rotation)
            }
        } ?: Unit
    }

    /**
     * @inheritDoc
     */
    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since user could have removed them
        //  while the app was on paused state
        if (!hasPermissions(requireContext())) {
            // TODO: Request permissions
        }
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        super.onDestroyView()
        tags?.removeObserver(tagObserver)
        tags = null
        chipBox = null

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
        savedInstanceState: Bundle?): View? {
        booksViewModel =
            ViewModelProviders.of(activity!!).get(BooksViewModel::class.java)
        tagViewModel =
            ViewModelProviders.of(activity!!).get(TagViewModel::class.java)
        return inflater.inflate(R.layout.scan_fragment, container, false)
    }

    /**
     * Clear isbn and title text views
     */
    private fun clearView() {
        container.findViewById<TextView>(R.id.scan_isbn).text = ""
        container.findViewById<TextView>(R.id.scan_title).text = ""
        container.findViewById<TextView>(R.id.scan_author).text = ""
    }

    /**
     * @inheritDoc
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            searchForBooks(
                container.findViewById<TextView>(R.id.scan_title).text.toString(),
                container.findViewById<TextView>(R.id.scan_author).text.toString()
            )
        }
        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, recompute layout
        displayManager = viewFinder.context
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Wait for the views to be properly laid out
        viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = viewFinder.display.displayId

            if (hasPermissions(requireContext()))
                bindCameraUseCases()
            else {
                booksViewModel.viewModelScope.launch {
                    if (activity!!.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        coroutineAlert(context!!, Unit) { alert ->
                            // Present the dialog
                            alert.builder.setTitle(R.string.camera_permission_title)
                                .setMessage(R.string.camera_permission_message)
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok, null)
                        }.show()
                        // Request camera-related permissions
                        requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
                    }
                }
            }
        }
    }

    /**
     * Return sequence of selected names
     */
    private fun selectedNames(list: List<TagEntity>?): Sequence<String>? {
        return list?.asSequence()
            ?.filter { tagViewModel.selection.isSelected(it.id) }
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
                val tag = TagEntity(0L, text, "")
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
            // Extract the token at the end of the selection
            val edit = box.textView
            val token = edit.text.toString().trim { it <= ' ' }
            // return the query string
            return ColumnDataDescriptor.buildAutoCompleteCursor(
                tagViewModel.repo, TAGS_ID_COLUMN,
                TAGS_NAME_COLUMN, TAGS_TABLE, token
            )
        }

        var autoCompleteJob: Job? = null
        box.delegate = object: ChipBox.Delegate {
            override val scope: CoroutineScope
                get() = tagViewModel.viewModelScope

            override suspend fun onCreateChip(
                chipBox: ChipBox,
                text: String,
                scope: CoroutineScope
            ): Chip? {
                return this@ScanFragment.onCreateChip(scope, text)
            }

            override suspend fun onChipAdded(chipBox: ChipBox, chip: View, scope: CoroutineScope) {
                tagViewModel.selection.select((chip as TagChip).tag.id, true)
            }

            override suspend fun onChipRemoved(
                chipBox: ChipBox,
                chip: View,
                scope: CoroutineScope
            ) {
                tagViewModel.selection.select((chip as TagChip).tag.id, false)
            }

            override fun onEditorFocusChange(chipBox: ChipBox, edit: View, hasFocus: Boolean) {
                // Get the value field
                edit as AutoCompleteTextView
                if (hasFocus) {
                    // Setting focus, setup adapter and set it in the text view
                    // This is done in a coroutine job and we use the job
                    // to flag that the job is still active. When we lose focus
                    // we cancel the job if it is still active
                    autoCompleteJob = booksViewModel.viewModelScope.launch {
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
                        adapter.setFilterQueryProvider { getQuery() }

                        // Set the adapter on the text view
                        edit.setAdapter(adapter)
                        // Flag that the job is done
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
        }

        (box.textView as AutoCompleteTextView).let {
            it.threshold = 1
            // If the view is an AutoComplete view, then add a chip when an item is selected
            it.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, _, _ ->
                    box.onCreateChipAction()
                }
        }

        tagViewModel.viewModelScope.launch {
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
            TagChip(t, context!!)
        }
    }

    /**
     * Declare and bind preview, capture and analysis use cases
     */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")
        // Set up the view finder use case to display camera preview
        val viewFinderConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            // We request aspect ratio but no resolution to let CameraX optimize our use cases
            setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        // Use the auto-fit preview builder to automatically handle size and orientation changes
        preview = AutoFitPreviewBuilder.build(viewFinderConfig, viewFinder)

        // Set up the capture use case to allow users to take photos
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setCaptureMode(CaptureMode.MIN_LATENCY)
            // We request aspect ratio but no resolution to match preview config but letting
            // CameraX optimize for whatever specific resolution best fits requested capture mode
            setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        imageCapture = ImageCapture(imageCaptureConfig)

        // Setup image analysis pipeline that computes average pixel luminance in real time
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(lensFacing)
            // In our analysis, we care more about the latest image than analyzing *every* image
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        // Setup the image analyzer to scan for bar codes
        val repo = booksViewModel.repo
        imageAnalyzer = ImageAnalysis(analyzerConfig).apply {
            // Run the analysis on the main thread and use the BarcodeScanner class
            setAnalyzer(mainExecutor, BarcodeScanner {codes ->
                // Display the bar codes in the UI
                container.findViewById<TextView>(R.id.scan_isbn).text = codes.joinToString(", ")

                // Start a coroutine job to run query for the book
                booksViewModel.viewModelScope.launch {
                    var notFound = true         // Flag if we found anything
                    // Look through the codes we found
                    for (isbn in codes) {
                        try {
                            // Lookup using isbn
                            var result = GoogleBookLookup.lookupISBN(isbn)
                            if (result == null || result.list.isEmpty()) {
                                // If we didn't find anything find books with the isbn
                                // somewhere in the book data
                                result = generalLookup(isbn, 0, 20)
                                // If we still didn't find anything, continue with next code
                                if (result == null || result.list.isEmpty())
                                    continue
                            }

                            // Select a book from the ones we found
                            notFound = false
                            val array = selectBook(result.list, isbn, result.itemCount, false)
                            if (array.size() > 0) {
                                val book = array.valueAt(0)
                                // When a book is selected, set the title
                                container.findViewById<TextView>(R.id.scan_title).text =
                                    book.book.title
                                container.findViewById<TextView>(R.id.scan_author).text =
                                    buildAuthors(StringBuilder(), book.authors)

                                // Make sure the isbn in the book record is the one we searched
                                book.book.ISBN = isbn
                                // Add the book to the database
                                repo.addOrUpdateBook(
                                    book, tagViewModel.selection.selection,
                                    tagViewModel.selection.inverted
                                )
                                // Stop looking
                                return@launch
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

                    if (notFound) {
                        // If we got here we didn't find anything
                        Toast.makeText(
                            context,
                            context!!.resources.getString(R.string.no_books_found),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }

        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(
            viewLifecycleOwner, preview, imageCapture, imageAnalyzer)
    }

    private fun searchForBooks(title: String, author: String) {
        // Don't search if no input
        if (title.isEmpty() && author.isEmpty())
            return

        // Start a coroutine job to run query for the book
        booksViewModel.viewModelScope.launch {
            // Look through the codes we found
            try {
                // Lookup using isbn
                val spec = GoogleBookLookup.getTitleAuthorQuery(title, author)
                val result = generalLookup(spec)
                // If we still didn't find anything, stop
                if (result == null || result.list.isEmpty()) {
                    Toast.makeText(
                        context,
                        context!!.resources.getString(R.string.no_books_found),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Select books from the ones we found
                val array = selectBook(result.list, spec, result.itemCount, true)
                val repo = booksViewModel.repo
                for (i in 0 until array.size()) {
                    val book = array.valueAt(i)

                    // Add the book to the database
                    repo.addOrUpdateBook(
                        book, tagViewModel.selection.selection,
                        tagViewModel.selection.inverted
                    )
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
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): AspectRatio {
        val previewRatio = max(width, height).toDouble() / min(width, height)

        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /**
     * Start auto focus for the camera
     */
    private fun focus(): Boolean {
        // Position where we want to focus
        val x = viewFinder.x + viewFinder.width / 2f
        val y = viewFinder.y + viewFinder.height / 2f

        val pointFactory = DisplayOrientedMeteringPointFactory(viewFinder.display,
            lensFacing, viewFinder.width.toFloat(), viewFinder.height.toFloat())
        val afPointWidth = 1.0f / 6.0f  // 1/6 total area
        val aePointWidth = afPointWidth * 1.5f
        val afPoint = pointFactory.createPoint(x, y, afPointWidth, 1.0f)
        val aePoint = pointFactory.createPoint(x, y, aePointWidth, 1.0f)

        try {
            // Start the auto focus
            CameraX.getCameraControl(lensFacing).startFocusAndMetering(
                FocusMeteringAction.Builder.from(afPoint, FocusMeteringAction.MeteringMode.AF_ONLY)
                                           .addPoint(aePoint, FocusMeteringAction.MeteringMode.AE_ONLY)
                                           .build()
            )
        } catch (e: CameraInfoUnavailableException) {
            Log.d(TAG, "cannot access camera", e)
        }

        return true
    }

    /**
     * Custom analysis code to scan for bar codes
     */
    private inner class BarcodeScanner(listener: BarcodeListener? = null) : ImageAnalysis.Analyzer {
        private val listeners = ArrayList<BarcodeListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: do not close the image, it will be
         * automatically closed after this method returns
         * @return the image analysis result
         */
        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty() || !previewing) return

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()

            // scan barcode no more often than every .25 seconds
            if (currentTime - lastAnalyzedTimestamp >= TimeUnit.MILLISECONDS.toMillis(250)) {
                lastAnalyzedTimestamp = currentTime

                // Put the image in the format Z-bar wants
                val barcode = Image(image.width, image.height, "GREY")
                // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance
                //  plane. Use that as a gray level image
                val data = image.planes[0].buffer.toByteArray()
                barcode.data = data

                // Scan the image for bar code. Returns 0 if nothing was found
                val result: Int = scanner.scanImage(barcode)

                if (result != 0) {
                    // We found something, so stop analyzing
                    previewing = false
                    // Get the results and look for ISBN codes
                    val symbols = scanner.results
                    val codes = ArrayList<String>()
                    for (sym in symbols) {
                        // When we find an ISBN, add it to the codes
                        when (sym.type) {
                            Symbol.ISBN10,
                            Symbol.ISBN13,
                            Symbol.EAN13 ->
                                codes.add(sym.data)
                        }
                    }

                    // Call all listeners with codes we found
                    listeners.forEach { it(codes) }
                }
            }
        }
    }

    companion object {
        /**
         * Tag for Logging
         */
        private const val TAG = "CameraXBasic"

        /**
         * Common camera ratios
         */
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        private fun buildAuthors(builder: StringBuilder, list: List<AuthorEntity>): String {
            for ((i, a) in list.withIndex()) {
                if (i > 0)
                    builder.append(", ")
                when {
                    a.remainingName.isEmpty() -> builder.append(a.lastName)
                    a.lastName.isEmpty() -> builder.append(a.remainingName)
                    else -> builder.append(a.remainingName + " " + a.lastName)
                }
            }
            return builder.toString()
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
                                if ((a.remainingName + " " + a.lastName).contains(s, true))
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
                coroutineAlert<SparseArray<BookAndAuthors>>(context!!, SparseArray()) { alert ->

                    // Get the content view for the dialog
                    val content =
                        parentFragment!!.layoutInflater.inflate(R.layout.scan_select_book, null)

                    // Create an object the book adapter uses to get info
                    val access = object: ParentAccess {
                        // This array is used to map positions to selected books
                        // and is the final result of the select book dialog
                        val selection = alert.result
                        var selectedPos = -1
                        // This is the adapter
                        lateinit var adapter: BooksAdapter

                        // Clear the selected items - only use for !isMulti
                        fun clearSelection(selected: Boolean) {
                            for (i in 0 until selection.size())
                                selection.valueAt(i).selected = selected
                            selectedPos = -1
                            selection.clear()
                        }
                        // Toggle the selection for an id
                        override fun toggleSelection(id: Long, position: Int) {
                            val index = id.toInt()
                            if (selection.indexOfKey(index) >= 0) {
                                // Selection contains the key, remove it
                                // for multi selection, do nothing for single
                                if (!isMulti)
                                    return      // Don't refresh the adapter
                                selection.get(index).selected = false
                                selection.remove(index)
                            } else {
                                // Selection doesn't contain the key, add it
                                // clear any other selections if not multi selection
                                if (!isMulti) {
                                    if (selectedPos >= 0)
                                        adapter.notifyItemChanged(selectedPos)
                                    clearSelection(false)
                                }
                                // Add the boo
                                adapter.getBook(position)?.let {
                                    it.selected = true
                                    selection.put(index, it)
                                    selectedPos = position
                                }
                            }
                            // Refresh the adapter
                            adapter.notifyItemChanged(position)
                        }

                        // Get the context
                        override val context: Context
                            get() = this@ScanFragment.context!!

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
                    access.adapter = BooksAdapter(access, R.layout.books_adapter_book_item_always)
                    titles.layoutManager = LinearLayoutManager(activity)
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
                        GoogleBookLookup.generalLookupPaging(spec, itemCount, list)
                    }
                    val flow = pager.flow.map {
                        it.map { b ->
                            (b as? BookAndAuthors)?.apply { selected = access.selection.indexOfKey(book.id.toInt()) >= 0 }?: b
                        }
                    }.cachedIn(booksViewModel.viewModelScope).combine(filterFlow) {data, b ->
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
                    pagerJob = booksViewModel.viewModelScope.launch {
                        flow.collectLatest { data ->
                            if (!isMulti && access.selectedPos >= 0)
                                access.clearSelection(true)
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
