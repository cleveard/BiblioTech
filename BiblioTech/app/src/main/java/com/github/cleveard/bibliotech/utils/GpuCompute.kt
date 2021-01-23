package com.github.cleveard.bibliotech.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.renderscript.*
import androidx.camera.core.ImageProxy
import com.github.cleveard.bibliotech.BuildConfig
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Class to interface with GPU compute
 */
class GpuCompute(context: Context) {
    /** The RenderScript */
    private val rs = RenderScript.create(context)
    /** Lock for RgbToLum */
    private val lockRgbToLum = ReentrantLock()
    /** Script for RgbToLum */
    private lateinit var scriptRgbToLum: ScriptIntrinsicColorMatrix
    /** Lock for YuvToRgb */
    private val lockYuvToRgb = ReentrantLock()
    /** Script for YuvToRgb */
    private lateinit var scriptYuvToRgb: ScriptIntrinsicYuvToRGB

    /**
     * Convert RGB to Grey
     * @param input RGB Bitmap
     * @param output Array for byte lum values
     */
    fun rgbToLum(input: Bitmap, output: ByteArray) {
        RgbToLum().compute(input, output)
    }

    /**
     * Convert RGB to Grey
     * @param input RGB Bitmap
     * @param output Bitmap for Lum values
     */
    @Suppress("unused")
    fun rgbToLum(input: Bitmap, output: Bitmap) {
        RgbToLum().compute(input, output)
    }

    /**
     * Convert RGB to Grey in a stream
     * @return Stream processor
     */
    @Suppress("unused")
    fun rgbToLumStream(): RgbToLumStream {
        return RgbToLum()
    }

    /**
     * Convert RGB to Grey
     * @param image Image in an ImageProxy
     * @param output Bitmap for rgb values
     */
    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        YuvToRgb().compute(image, output)
    }

    /**
     * Convert YUV to RGB in a stream
     * @return Stream processor
     */
    @Suppress("unused")
    fun yuvToRgbStream(): YuvToRgbStream {
        return YuvToRgb()
    }

    /**
     * Interface for stream RGB to LUM conversions
     */
    interface RgbToLumStream {
        /**
         * Convert bitmap to byte array
         * @param input RGB Bitmap
         * @param output Array for byte lum values
         */
        fun compute(input: Bitmap, output: Bitmap)

        /**
         * Convert bitmap to bitmap
         * @param input RGB Bitmap
         * @param output Bitmap for Lum values
         */
        fun compute(input: Bitmap, output: ByteArray)
    }

    /**
     * Implementation for RGB to LUM conversion
     */
    private inner class RgbToLum: RgbToLumStream {
        /** Input GPU Buffer */
        private lateinit var inputAllocation: Allocation
        /** Array output GPU Buffer */
        private lateinit var arrayAllocation: Allocation
        /** Bitmap output GPU Buffer */
        private lateinit var bmAllocation: Allocation

        /** @inheritDoc */
        override fun compute(input: Bitmap, output: ByteArray) {
            lockRgbToLum.withLock {
                // Allocate buffer for array if not done yet
                if (!::arrayAllocation.isInitialized) {
                    //val typeBm = Type.createXY(rs, Element.RGBA_8888(rs), input.width, input.height)
                    //val bmAllocation = Allocation.createTyped(rs, typeBm)
                    val type = Type.createXY(rs, Element.U8(rs), input.width, input.height)
                    arrayAllocation = Allocation.createTyped(rs, type)
                }

                // Make the conversion
                compute(input, arrayAllocation)
                // Copy GPU buffer to output
                arrayAllocation.copyTo(output)
            }
        }

        /** @inheritDoc */
        override fun compute(input: Bitmap, output: Bitmap) {
            lockRgbToLum.withLock {
                // Allocate buffer to bitmap
                if (!::bmAllocation.isInitialized) {
                    //val typeBm = Type.createXY(rs, Element.RGBA_8888(rs), input.width, input.height)
                    //val bmAllocation = Allocation.createTyped(rs, typeBm)
                    bmAllocation = Allocation.createFromBitmap(rs, input)
                }

                // Make the conversion
                compute(input, bmAllocation)
                // Copy GPU buffer to output
                bmAllocation.copyTo(output)
            }
        }

        /**
         * Run conversion on GPU
         * @param input The input bitmap
         * @param output The output GPU Buffer
         */
        private fun compute(input: Bitmap, output: Allocation) {
            // Initialize the conversion script
            if (!::scriptRgbToLum.isInitialized) {
                scriptRgbToLum = ScriptIntrinsicColorMatrix.create(rs).also {
                    it.setGreyscale()
                }
            }

            // Allocate the GPU Buffer for the input
            if (!::inputAllocation.isInitialized) {
                //val bm = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
                // Ensure that the RenderScript inputs and outputs are allocated
                inputAllocation = Allocation.createFromBitmap(rs, input)
            }

            // Copy the input to the GPU Buffer
            inputAllocation.copyFrom(input)
            // Convert RGB to Lum
            scriptRgbToLum.forEach(inputAllocation, output)
        }
    }

    /**
     * Stream interface for converting YUV to RGB
     */
    interface YuvToRgbStream {
        /**
         * Convert YUV image to RGB bitmap
         * @param image The YUV image in an ImageProxy
         * @param output The output bitmap
         */
        fun compute(image: ImageProxy, output: Bitmap)
    }

    /**
     * Helper class used to efficiently convert a [android.media.Image] object from
     * [ImageFormat.YUV_420_888] format to an RGB [Bitmap] object.
     *
     * The [yuvToRgb] method is able to achieve the same FPS as the CameraX image
     * analysis use case on a Pixel 3 XL device at the default analyzer resolution,
     * which is 30 FPS with 640x480.
     *
     * NOTE: This has been tested in a limited number of devices and is not
     * considered production-ready code. It was created for illustration purposes,
     * since this is not an efficient camera pipeline due to the multiple copies
     * required to convert each frame.
     */
    private inner class YuvToRgb: YuvToRgbStream {
        /** Count of pixels in the yuv image */
        private var pixelCount: Int = -1
        /** Interleaved buffer for YUV planes */
        private lateinit var yuvBuffer: ByteBuffer
        /** YUV input GPU Buffer */
        private lateinit var inputAllocation: Allocation
        /** RGB output GPU Buffer */
        private lateinit var outputAllocation: Allocation

        override fun compute(image: ImageProxy, output: Bitmap) {
            lockYuvToRgb.withLock {
                // Initialize the script
                if (!::scriptYuvToRgb.isInitialized) {
                    scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
                }

                // Ensure that the intermediate output byte buffer is allocated
                if (!::yuvBuffer.isInitialized) {
                    pixelCount = image.cropRect.width() * image.cropRect.height()
                    // Bits per pixel is an average for the whole image, so it's useful to compute the size
                    // of the full buffer but should not be used to determine pixel offsets
                    val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
                    yuvBuffer = ByteBuffer.allocateDirect(pixelCount * pixelSizeBits / 8)
                }

                // Rewind the buffer; no need to clear it since it will be filled
                yuvBuffer.rewind()

                // Get the YUV data in byte array form using NV21 format
                imageToByteBuffer(image, yuvBuffer.array())

                // Ensure that the RenderScript inputs and outputs are allocated
                if (!::inputAllocation.isInitialized) {
                    // Explicitly create an element with type NV21, since that's the pixel format we use
                    val elemType =
                        Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21).create()
                    inputAllocation =
                        Allocation.createSized(rs, elemType.element, yuvBuffer.array().size)
                }
                if (!::outputAllocation.isInitialized) {
                    outputAllocation = Allocation.createFromBitmap(rs, output)
                }

                // Convert NV21 format YUV to RGB
                inputAllocation.copyFrom(yuvBuffer.array())
                scriptYuvToRgb.setInput(inputAllocation)
                scriptYuvToRgb.forEach(outputAllocation)
                outputAllocation.copyTo(output)
            }
        }

        /**
         * Interleave the YUV planes
         * @param image The image with the YUV planes
         * @param outputBuffer The output GPU buffer
         */
        private fun imageToByteBuffer(image: ImageProxy, outputBuffer: ByteArray) {
            if (BuildConfig.DEBUG && image.format != ImageFormat.YUV_420_888) {
                error("Assertion failed - image.format != ImageFormat.YUV_420_888")
            }

            val imageCrop = image.cropRect
            val imagePlanes = image.planes

            imagePlanes.forEachIndexed { planeIndex, plane ->
                // How many values are read in input for each output value written
                // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
                //
                // Y Plane            U Plane    V Plane
                // ===============    =======    =======
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y    U U U U    V V V V
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                val outputStride: Int

                // The index in the output buffer the next value will be written at
                // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
                //
                // First chunk        Second chunk
                // ===============    ===============
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y    V U V U V U V U
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                // Y Y Y Y Y Y Y Y
                var outputOffset: Int

                when (planeIndex) {
                    0 -> {
                        outputStride = 1
                        outputOffset = 0
                    }
                    1 -> {
                        outputStride = 2
                        // For NV21 format, U is in odd-numbered indices
                        outputOffset = pixelCount + 1
                    }
                    2 -> {
                        outputStride = 2
                        // For NV21 format, V is in even-numbered indices
                        outputOffset = pixelCount
                    }
                    else -> {
                        // Image contains more than 3 planes, something strange is going on
                        return@forEachIndexed
                    }
                }

                val planeBuffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride

                // We have to divide the width and height by two if it's not the Y plane
                val planeCrop = if (planeIndex == 0) {
                    imageCrop
                } else {
                    Rect(
                        imageCrop.left / 2,
                        imageCrop.top / 2,
                        imageCrop.right / 2,
                        imageCrop.bottom / 2
                    )
                }

                val planeWidth = planeCrop.width()
                val planeHeight = planeCrop.height()

                // Intermediate buffer used to store the bytes of each row
                val rowBuffer = ByteArray(plane.rowStride)

                // Size of each row in bytes
                val rowLength = if (pixelStride == 1 && outputStride == 1) {
                    planeWidth
                } else {
                    // Take into account that the stride may include data from pixels other than this
                    // particular plane and row, and that could be between pixels and not after every
                    // pixel:
                    //
                    // |---- Pixel stride ----|                    Row ends here --> |
                    // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                    //
                    // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                    (planeWidth - 1) * pixelStride + 1
                }

                for (row in 0 until planeHeight) {
                    // Move buffer position to the beginning of this row
                    planeBuffer.position(
                        (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
                    )

                    if (pixelStride == 1 && outputStride == 1) {
                        // When there is a single stride value for pixel and output, we can just copy
                        // the entire row in a single step
                        planeBuffer.get(outputBuffer, outputOffset, rowLength)
                        outputOffset += rowLength
                    } else {
                        // When either pixel or output have a stride > 1 we must copy pixel by pixel
                        planeBuffer.get(rowBuffer, 0, rowLength)
                        for (col in 0 until planeWidth) {
                            outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                            outputOffset += outputStride
                        }
                    }
                }
            }
        }
    }
}
