/*
 * Basic no frills app which integrates the ZBar barcode scanner with
 * the camera.
 * 
 * Created by lisah0 on 2012-02-24
 */
package com.example.cleve.biblio_tech;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;

import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.Button;

/* Import ZBar Class files */
import com.yanzhenjie.zbar.ImageScanner;
import com.yanzhenjie.zbar.Symbol;
import com.yanzhenjie.zbar.SymbolSet;
import com.yanzhenjie.zbar.Config;

public class ScanActivity extends Activity
{
	public static final String kScanViewId = "scan_view_id";
    private final int REQUEST_CAMERA_PERMISSION = 1;
    private CameraPreview mPreview;
    private CameraPreview.Callback mPreviewCallback = new CameraPreview.Callback() {
        @Override
        public void OnPreviewFrame(android.media.Image preview) {
            processPreviewImage(preview);
        }
    };

    Button scanButton;
    boolean mBarcodeLookedUp = true;

    ImageScanner scanner;

    private long mViewId;
    //private final int kNumSounds = 3;
    //private SoundPool mSoundPool;
    //private int mSoundResIds[] = new int[] { R.raw.beep,R.raw.beepbeep, R.raw.longbeep };
    //private int mPoolIds[] = new int[kNumSounds];
    //private final int kBeep = 0;
    //private final int kBeepBeep = 1;
    //private final int kLongBeep = 2;
    //private AudioManager mAudioManager;
    
    static {
        System.loadLibrary("iconv");
    } 

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.scan);

		Intent intent = getIntent();
		mViewId = intent.getLongExtra(kScanViewId, 0);

        //mSoundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
        //for (int i = 0; i < kNumSounds; ++i) {
        //	mPoolIds[i] = mSoundPool.load(this, mSoundResIds[i], i);
        //}
        //mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        //setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        /* Instance barcode scanner */
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(this, mPreviewCallback);
        FrameLayout preview = (FrameLayout)findViewById(R.id.cameraPreview);
        preview.addView(mPreview);

        scanButton = (Button)findViewById(R.id.ScanButton);

        scanButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (mBarcodeLookedUp && !mPreview.isScanning()) {
                        mPreview.startScanner();
                    }
                }
            });
    }

    public void onPause() {
        mBarcodeLookedUp = false;
        mPreview.releaseCamera();
        super.onPause();
        //mSoundPool.release();
        //mSoundPool = null;
    }

    public void onResume() {
        super.onResume();
        mBarcodeLookedUp = true;
        requestCameraPermission();
    }

    private void finishStarting() {
        mPreview.startPreview();
        //mSoundPool.release();
        //mSoundPool = null;
    }

    void processPreviewImage(android.media.Image preview) {
        int width = preview.getWidth(), height = preview.getHeight();
        com.yanzhenjie.zbar.Image barcode = new com.yanzhenjie.zbar.Image(width, height, "Y800");
        byte[] data = new byte[width * height];
        android.media.Image.Plane planes[] = preview.getPlanes();
        android.media.Image.Plane plane = planes[0];
        if (plane.getPixelStride() != 1 || plane.getRowStride() != width) {
            // Don't understand the format, turn off the camera
            mPreview.stopScanner();
            return;
        }
        plane.getBuffer().get(data, 0, width * height);
        barcode.setData(data);

        int result = scanner.scanImage(barcode);

        if (result != 0) {
            //final float v = (float)mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            //			  / (float)mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            //mSoundPool.play(mPoolIds[kBeep], v, v, 2, 0, 1.0f);
            // Found a barcode. Stop callbacks, but keep the preview going.
            mBarcodeLookedUp = false;
            mPreview.stopScanner();

            SymbolSet syms = scanner.getResults();
            for (Symbol sym : syms) {
                ListActivity.addBookByISBN(sym.getData(), new ListActivity.AddBookByISBN(mViewId) {
                    @Override
                    public void BookLookupResult(Book[] result,
                                                 boolean more) {
                        //mSoundPool.play(mPoolIds[kBeepBeep], v, v, 2, 0, 1.0f);
                        mBarcodeLookedUp = true;
                        super.BookLookupResult(result, more);
                    }

                    @Override
                    public void BookLookupError(String error) {
                        //mSoundPool.play(mPoolIds[kLongBeep], v, v, 2, 0, 1.0f);
                        mBarcodeLookedUp = true;
                        super.BookLookupError(error);
                    }
                });
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    finishStarting();
                } else {
                    this.finish();
                }
                return;

            }
        }
    }

    private void requestCameraPermission() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }

        finishStarting();
    }
}
