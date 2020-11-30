package com.github.cleveard.BiblioTech.ui.scan

import com.github.cleveard.BiblioTech.db.*
import com.github.cleveard.BiblioTech.gb.*
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.BiblioTech.*
import com.github.cleveard.BiblioTech.R
import com.github.cleveard.BiblioTech.gb.GoogleBookLookup.Companion.generalLookup
import com.github.cleveard.BiblioTech.ui.books.BooksAdapter
import com.github.cleveard.BiblioTech.ui.books.BooksViewModel
import com.github.cleveard.BiblioTech.ui.tags.TagViewModel
import com.github.cleveard.BiblioTech.utils.AutoFitPreviewBuilder
import com.github.cleveard.BiblioTech.utils.CoroutineAlert
import com.github.cleveard.BiblioTech.utils.coroutineAlert
import com.yanzhenjie.zbar.Config
import com.yanzhenjie.zbar.Image
import com.yanzhenjie.zbar.ImageScanner
import com.yanzhenjie.zbar.Symbol
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.Exception
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

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
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
    }

    /**
     * @inheritDoc
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup local properties
        container = view as ConstraintLayout
        viewFinder = container.findViewById(R.id.view_finder)
        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Clear the isbn and title
        clearView()

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
                // Request camera-related permissions
                requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
            }
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
                            selectBook(result.list, isbn, result.itemCount)?.let { book ->
                                // When a book is selected, set the title
                                container.findViewById<TextView>(R.id.scan_title).text =
                                    book.book.title

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
                }
            })
        }

        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(
            viewLifecycleOwner, preview, imageCapture, imageAnalyzer)
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
    }

    /**
     * Select a book from a google query
     * @param list List of books in first page returned from the book query
     * @param spec query used to get the page of books
     * @param totalItems Total number of books the query found
     */
    private suspend fun selectBook(list: List<BookAndAuthors>, spec: String, totalItems: Int): BookAndAuthors? {
        return coroutineScope {
            if (list.size == 1) {
                // If the query resulted in a single book, then select it
                return@coroutineScope list[0]
            }
            val pageCount = list.size
            val itemCount = if (totalItems > 0) totalItems else pageCount
            var pagerJob: Job? = null
            return@coroutineScope try {
                // Otherwise display a dialog to select the book
                coroutineAlert<BookAndAuthors?>(context!!, null) { alert ->

                    // Get the content view for the dialog
                    val content =
                        parentFragment!!.layoutInflater.inflate(R.layout.scan_select_book, null)

                    // Create a pager to drive the recycler view
                    val config = PagingConfig(pageSize = 20, initialLoadSize = pageCount)
                    val pager = Pager(
                        config
                    ) {
                        GoogleBookLookup.generalLookupPaging(spec, itemCount, list)
                    }
                    val flow = pager.flow.cachedIn(booksViewModel.viewModelScope)

                    // Find the recycler view and set the layout manager and adapter
                    val titles = content.findViewById<RecyclerView>(R.id.title_buttons)
                    val adapter = getAdapter(alert)
                    titles.layoutManager = LinearLayoutManager(activity)
                    titles.adapter = adapter

                    // Start the book stream to the recycler view
                    pagerJob = booksViewModel.viewModelScope.launch {
                        flow.collectLatest { data ->
                            adapter.submitData(data)
                        }
                    }

                    // Create an alert dialog with the content view
                    alert.builder.setTitle(R.string.select_title)
                        // Specify the list array, the items to be selected by default (null for none),
                        // and the listener through which to receive callbacks when items are selected
                        .setView(content)
                        // Set the action buttons
                        .setPositiveButton(R.string.ok, null)
                        .setNegativeButton(R.string.cancel, null)
                }.show()
            } finally {
                pagerJob?.cancel()
            }
        }
    }

    /**
     * Get a paging adapter to show book titles
     * @param alert The CoroutineAlert object for the dialog
     * @return the paging adapter
     */
    private fun getAdapter(alert: CoroutineAlert<BookAndAuthors?>): PagingDataAdapter<Any, BooksAdapter.ViewHolder> {
        /**
         * Recycler adapter to display the  books
         */
        class BookPagingAdapter : PagingDataAdapter<Any, BooksAdapter.ViewHolder>(
            BooksAdapter.DIFF_CALLBACK
        ) {
            /**
             * Selected book position
             */
            private var selectedPosition = -1

            /**
             * Selected radio button view
             */
            private var selectedView: RadioButton? = null

            // On click listener for the radio buttons. Need to handle this manually because
            // the recycler view doesn't
            private val onRadioClick = View.OnClickListener { v ->
                try {
                    val button = v as RadioButton
                    val position = v.tag as Int
                    // If we select a new book
                    if (position != selectedPosition) {
                        // Uncheck the previously selected book
                        selectedView?.isChecked = false
                        // Check this book
                        button.isChecked = true
                        // Save the position, view and book selected
                        selectedPosition = position
                        selectedView = button
                        alert.result = getItem(position) as BookAndAuthors
                    }
                }
                catch(e: Exception) {
                }
            }

            /**
             * @inheritDoc
             */
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): BooksAdapter.ViewHolder {
                // Just create a single radio button for each book
                val button = RadioButton(context)
                val viewHolder = BooksAdapter.ViewHolder(button)
                button.setOnClickListener(onRadioClick)
                return viewHolder
            }

            /**
             * @inheritDoc
             */
            override fun onBindViewHolder(holder: BooksAdapter.ViewHolder, position: Int) {
                val book = getItem(position)
                val button = holder.itemView as RadioButton
                // If we are binding the currently selected book, deselect it
                if (button == selectedView && position != selectedPosition)
                    selectedView = null
                // Set the title, tag and checked
                button.text = (book as? BookAndAuthors)?.book?.title?: ""
                button.tag = position
                button.isChecked = position == selectedPosition
            }
        }

        return BookPagingAdapter()
    }
}
