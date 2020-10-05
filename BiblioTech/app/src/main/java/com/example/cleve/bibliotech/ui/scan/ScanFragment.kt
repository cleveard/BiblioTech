package com.example.cleve.bibliotech.ui.scan

//import android.hardware.Camera

/* Import ZBar Class files */
import com.example.cleve.bibliotech.db.*
import com.example.cleve.bibliotech.gb.*
import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Parcelable
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.*
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.ui.books.BooksAdapter
import com.example.cleve.bibliotech.utils.AutoFitPreviewBuilder
import com.yanzhenjie.zbar.Config
import com.yanzhenjie.zbar.Image
import com.yanzhenjie.zbar.ImageScanner
import com.yanzhenjie.zbar.Symbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.Serializable
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

/** Helper type alias used for analysis use case callbacks */
typealias BarcodeListener = (codes: Array<String>) -> Unit

class ScanFragment : Fragment() {

    private lateinit var scanViewModel: ScanViewModel
    private lateinit var container: ConstraintLayout
    private lateinit var viewFinder: TextureView
    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var mainExecutor: Executor
    private lateinit var scanner: ImageScanner

    private var displayId = -1
    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var previewing = false
    private val lookup = GoogleBookLookup()


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

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, scan the bar code
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    clearView()
                    focus()
                    previewing = true
                }
            }
        }
    }

    /** Internal reference of the [DisplayManager] */
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

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since user could have removed them
        //  while the app was on paused state
        if (!hasPermissions(requireContext())) {
            // TODO: Request permissions
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_scan, container, false)

    private fun clearView() {
        container.findViewById<TextView>(R.id.scan_isbn).text = ""
        container.findViewById<TextView>(R.id.scan_title).text = ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout
        viewFinder = container.findViewById(R.id.view_finder)
        broadcastManager = LocalBroadcastManager.getInstance(view.context)

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

    /** Declare and bind preview, capture and analysis use cases */
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

        fun selectBook(list: List<BookAndAuthors>, spec: String, totalItems: Int, callback: (BookAndAuthors) -> Unit) {
            if (list.size == 1) {
                callback(list[0])
                return
            }

            SelectBookDialogFragment.createDialog(list, spec, totalItems, callback).show(fragmentManager!!, "select_book")
        }

        val repo = BookRepository.repo
        imageAnalyzer = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(mainExecutor, BarcodeScanner(
                fun(codes: Array<String>) {
                    container.findViewById<TextView>(R.id.scan_isbn).text = codes.joinToString(", ")

                    for (isbn in codes) {
                        var second = false
                        lookup.lookupISBN(object : GoogleBookLookup.LookupDelegate {
                            override fun bookLookupResult(result: List<BookAndAuthors>?, itemCount: Int) {
                                if (result == null || result.isEmpty()) {
                                    if (!second) {
                                        second = true
                                        lookup.generalLookup(this, isbn, 0, 20)
                                    }
                                    return
                                }

                                selectBook(result, isbn, itemCount) {
                                    container.findViewById<TextView>(R.id.scan_title).text =
                                        it.book.title

                                    it.book.ISBN = isbn
                                    repo.addOrUpdateBook(it)
                                }
                            }

                            override fun bookLookupError(error: String?) {
                                Toast.makeText(
                                    context,
                                    "Error finding book: ${error ?: "Unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }, isbn)
                    }
                }
            ))
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

    private fun focus(): Boolean {
        val x = viewFinder.x + viewFinder.width / 2f
        val y = viewFinder.y + viewFinder.height / 2f

        val pointFactory = DisplayOrientedMeteringPointFactory(viewFinder.display,
            lensFacing, viewFinder.width.toFloat(), viewFinder.height.toFloat())
        val afPointWidth = 1.0f / 6.0f  // 1/6 total area
        val aePointWidth = afPointWidth * 1.5f
        val afPoint = pointFactory.createPoint(x, y, afPointWidth, 1.0f)
        val aePoint = pointFactory.createPoint(x, y, aePointWidth, 1.0f)

        try {
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
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    private inner class BarcodeScanner(listener: BarcodeListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<BarcodeListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each barcode scanned
         */
        fun onFrameAnalyzed(listener: BarcodeListener) = listeners.add(listener)

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
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

            // scan barcode no more often than every .25 seconds
            if (frameTimestamps.first - lastAnalyzedTimestamp >= TimeUnit.MILLISECONDS.toMillis(250)) {
                lastAnalyzedTimestamp = frameTimestamps.first

                val barcode =
                    Image(image.width, image.height, "GREY")
                // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance
                //  plane. Use that as a gray level image
                val data = image.planes[0].buffer.toByteArray()
                barcode.data = data

                val result: Int = scanner.scanImage(barcode)

                if (result != 0) {
                    previewing = false
                    val symbols = scanner.results
                    val extract = arrayOfNulls<String>(symbols.size)
                    var i = 0
                    for (sym in symbols) {
                        when (sym.type) {
                            Symbol.ISBN10,
                            Symbol.ISBN13,
                            Symbol.EAN13 ->
                                extract[i++] = sym.data
                        }
                    }

                    val codes = Array(i) { extract[it]!! }
                    // Call all listeners with new value
                    listeners.forEach { it(codes) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    internal  class SelectBookDialogFragment private constructor() : DialogFragment() {
        companion object {
            private const val kList = "list"
            private const val kSpec = "spec"
            private const val kItemCount = "itemCount"
            private const val kCallback = "callback"
            fun createDialog(list: List<BookAndAuthors>, spec: String, totalItems: Int, callback: (BookAndAuthors) -> Unit) : DialogFragment {
                val bundle = Bundle()
                bundle.putParcelableArrayList(kList, list as ArrayList<out Parcelable>?)
                bundle.putString(kSpec, spec)
                bundle.putInt(kItemCount, totalItems)
                bundle.putSerializable(kCallback, callback as Serializable)
                val dialog = SelectBookDialogFragment()
                dialog.arguments = bundle
                return dialog
            }
        }
        // Use this instance of the interface to deliver action events
        private lateinit var list: List<BookAndAuthors>
        private lateinit var spec: String
        private lateinit var callback: (BookAndAuthors) -> Unit
        private lateinit var content: View
        private var itemCount = 0
        private lateinit var pagerJob: Job
        private var book: BookAndAuthors? = null
        private var selectedPosition = -1
        private var selectedView: RadioButton? = null

        inner class BookPagingAdapter : PagingDataAdapter<BookAndAuthors, BooksAdapter.ViewHolder>(
            BooksAdapter.DIFF_CALLBACK
        ) {
            private val onRadioClick = View.OnClickListener { v ->
                try {
                    val button = v as RadioButton
                    val position = v.tag as Int
                    if (position != selectedPosition) {
                        selectedView?.setChecked(false)
                        button.setChecked(true)
                        selectedPosition = position
                        selectedView = button
                        book = getItem(position)
                    }
                }
                catch(e: Exception) {
                }
            }

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): BooksAdapter.ViewHolder {
                val button = RadioButton(context)
                val viewHolder = BooksAdapter.ViewHolder(button)
                button.setOnClickListener(onRadioClick)
                return viewHolder
            }

            override fun onBindViewHolder(holder: BooksAdapter.ViewHolder, position: Int) {
                val book = getItem(position)
                val button = holder.itemView as RadioButton
                if (button == selectedView && position != selectedPosition)
                    selectedView = null
                button.text = book?.book?.title?: ""
                button.tag = position
                button.setChecked(position == selectedPosition)
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?) : Dialog {
            val args = arguments!!
            list = args.getParcelableArrayList(kList)!!
            val pageCount = list.size
            val i = args.getInt(kItemCount)
            itemCount = if (i > 0) i else pageCount
            spec = args.getString(kSpec)!!
            @Suppress("UNCHECKED_CAST")
            callback = args.getSerializable(kCallback) as (BookAndAuthors) -> Unit
            book = null

            content = parentFragment!!.layoutInflater.inflate(R.layout.select_book, null)

            val scope = MainScope()
            val config = PagingConfig(pageSize = 20, initialLoadSize = pageCount)
            val pager = Pager(
                config
            ) {
                GoogleBookLookup.BookQueryPagingSource(spec, itemCount, list)
            }
            val flow = pager.flow.cachedIn(scope)
            val titles = content.findViewById<RecyclerView>(R.id.title_buttons)
            val adapter = BookPagingAdapter()
            titles.layoutManager = LinearLayoutManager(activity)
            titles.adapter = adapter

            pagerJob = scope.launch {
                flow.collectLatest {
                    data -> adapter.submitData(data)
                }
            }

            val builder = AlertDialog.Builder(activity)
            builder.setTitle(R.string.select_title)
                // Specify the list array, the items to be selected by default (null for none),
                // and the listener through which to receive callbacks when items are selected
                .setView(content)
                // Set the action buttons
                .setPositiveButton(
                    R.string.ok
                ) { _, _ ->
                    val curBook = book
                    if (curBook != null)
                        callback(curBook)
                }
                .setNegativeButton(
                    R.string.cancel
                ) { _, _ ->
                }
            return builder.create()
        }

        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            pagerJob.cancel()
        }
    }

}
