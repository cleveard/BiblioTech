/*
 * Barebones implementation of displaying camera preview.
 * 
 * Created by lisah0 on 2012-02-24
 */
package com.example.cleve.biblio_tech;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import android.content.Context;
import android.view.TextureView;

/** A basic Camera preview class */
public class CameraPreview extends TextureView {
    private static String TAG = "CameraPreview";
    private CameraManager mCameraManager;
    private CameraDevice mCamera = null;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private Callback mPreviewCallback;
    private ImageReader mImageReader = null;
    private Size mPreviewSize;
    private boolean mFlashSupported;

    private boolean mPreviewing = false;
    private boolean mScanning = false;

    public CameraPreview(Context context, Callback previewCb) {
        super(context);
        getCameraInstance(this.getWidth(), this.getHeight());
        mPreviewCallback = previewCb;

        /* 
         * Set camera to continuous focus if supported, otherwise use
         * software auto-focus. Only works for API level >=9.
         */
        /*
        Camera.Parameters parameters = camera.getParameters();
        for (String f : parameters.getSupportedFocusModes()) {
            if (f == Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) {
                mCamera.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                autoFocusCallback = null;
                break;
            }
        }
        */
    }

    boolean isScanning() {
        return mScanning;
    }

    void startPreview() {
        getCameraInstance(this.getWidth(), this.getHeight());
    }

    void startScanner() {
        startPreview();
        mScanning = true;
    }

    void stopScanner() {
        mScanning = false;
    }

    void stopPreview() {
        stopScanner();
    }

    void releaseCamera() {
        stopPreview();
    }

    public void onSizeChanged (int w, int h, int oldw, int oldh) {
        if (w != oldw && h != oldh) {
            releaseCamera();
        }
        if (mCamera == null) {
            getCameraInstance(w, h);
        }
    }
    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(Size previewSize, int viewWidth, int viewHeight) {
        Activity activity = (Activity) getContext();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        this.setTransform(matrix);
    }

    ImageReader.OnImageAvailableListener mImageAvailable = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;
            if (mScanning) {
                mPreviewCallback.OnPreviewFrame(image);
            }
            image.close();
        }
    };

    private boolean getCameraInstance(int width, int height) {
        if (width == 0 || height == 0) {
            return false;
        }

        mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        String[] cameraIds;
        try {
            cameraIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            return false;
        }
        for (int i = 0; i < cameraIds.length; ++i) {
            try {
                CameraCharacteristics cam = mCameraManager.getCameraCharacteristics(cameraIds[i]);

                // Check if the flash is supported.
                Boolean available = cam.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                if (cam.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    StreamConfigurationMap map =
                            cam.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    if (map == null) {
                        return false;
                    }

                    Size[] sizes = map.getOutputSizes(ImageReader.class);
                    if (sizes.length == 0)
                        return false;

                    mPreviewSize = chooseOptimalSize(sizes, width, height, new Size(width, height));
                    mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(),
                            mPreviewSize.getHeight(),
                            ImageFormat.YUV_420_888, 3);
                    mImageReader.setOnImageAvailableListener(mImageAvailable, null);

                    configureTransform(mPreviewSize, width, height);

                    // Found a back camera
                    if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        mCameraManager.openCamera(cameraIds[i], new OpenCameraCallback(), null);
                    }
                }
            }catch(CameraAccessException e) {
                // Ignore Camera Access errors
            }
        }

        // Couldn't find a camera
        return false;
    }

    private class StartCaptureCallback extends CameraCaptureSession.StateCallback {
        public StartCaptureCallback() {
            super();
        }

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
            super.onReady(session);
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            super.onActive(session);
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
        }

        @Override
        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
            super.onSurfacePrepared(session, surface);
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    }

    private class OpenCameraCallback extends CameraDevice.StateCallback {

        public OpenCameraCallback() {
            super();
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            // Start the capture
            try {
                SurfaceTexture texture = getSurfaceTexture();
                texture.setDefaultBufferSize(mPreviewSize.getWidth(),
                        mPreviewSize.getHeight());
                mPreviewBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                List surfaces = new ArrayList<>();

                Surface previewSurface = new Surface(texture);
                surfaces.add(previewSurface);
                mPreviewBuilder.addTarget(previewSurface);

                Surface readerSurface = mImageReader.getSurface();
                surfaces.add(readerSurface);
                mPreviewBuilder.addTarget(readerSurface);

                mCamera.createCaptureSession(surfaces,
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                mPreviewSession = cameraCaptureSession;
                                try {
                                    // Auto focus should be continuous for camera preview.
                                    mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                    // Flash is automatically enabled when necessary.
                                    setAutoFlash(mPreviewBuilder);

                                    // Finally, we start displaying the camera preview.
                                    CaptureRequest mPreviewRequest = mPreviewBuilder.build();
                                    mPreviewSession.setRepeatingRequest(mPreviewRequest,
                                            null, null);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession){
                                Log.w(TAG, "Create capture session failed");
                            }
                        }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
            if (mFlashSupported) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            }
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            releaseCamera();
            ((Activity)getContext()).finish();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            releaseCamera();
            ((Activity)getContext()).finish();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    }

    public interface Callback {
        void OnPreviewFrame(Image preview);
    }
}
