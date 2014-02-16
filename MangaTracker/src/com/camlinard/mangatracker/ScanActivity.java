/*
 * Basic no frills app which integrates the ZBar barcode scanner with
 * the camera.
 * 
 * Created by lisah0 on 2012-02-24
 */
package com.camlinard.mangatracker;

import com.camlinard.mangatracker.CameraPreview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.Button;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Size;

/* Import ZBar Class files */
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Config;

public class ScanActivity extends Activity
{
	public static final String kScanList = "scan_list";
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;

    Button scanButton;

    ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;

    private int mList;
    private final int kNumSounds = 3;
    private SoundPool mSoundPool;
    private int mSoundResIds[] = new int[] { R.raw.beep,R.raw.beepbeep, R.raw.longbeep };
    private int mPoolIds[] = new int[kNumSounds];
    private final int kBeep = 0;
    private final int kBeepBeep = 1;
    private final int kLongBeep = 2;
    private AudioManager mAudioManager;
    
    static {
        System.loadLibrary("iconv");
    } 

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.scan);

		Intent intent = getIntent();
		mList = intent.getIntExtra(kScanList, 0);
   	
        mSoundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
    	for (int i = 0; i < kNumSounds; ++i) {
    		mPoolIds[i] = mSoundPool.load(this, mSoundResIds[i], i);
    	}
    	mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    	setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        autoFocusHandler = new Handler();
        mCamera = getCameraInstance();

        /* Instance barcode scanner */
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(this, mCamera, previewCb, autoFocusCB);
        FrameLayout preview = (FrameLayout)findViewById(R.id.cameraPreview);
        preview.addView(mPreview);

        scanButton = (Button)findViewById(R.id.ScanButton);

        scanButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (barcodeScanned) {
                        barcodeScanned = false;
                        mCamera.setPreviewCallback(previewCb);
                        mCamera.startPreview();
                        previewing = true;
                        mCamera.autoFocus(autoFocusCB);
                    }
                }
            });
    }

    public void onPause() {
        super.onPause();
        releaseCamera();
        mSoundPool.release();
        mSoundPool = null;
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e){
        }
        return c;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private Runnable doAutoFocus = new Runnable() {
            public void run() {
                if (previewing)
                    mCamera.autoFocus(autoFocusCB);
            }
        };

    PreviewCallback previewCb = new PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Parameters parameters = camera.getParameters();
                Size size = parameters.getPreviewSize();

                Image barcode = new Image(size.width, size.height, "Y800");
                barcode.setData(data);

                int result = scanner.scanImage(barcode);
                
                if (result != 0) {
                	final float v = (float)mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                				  / (float)mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                	mSoundPool.play(mPoolIds[kBeep], v, v, 2, 0, 1.0f);
                    previewing = false;
                    mCamera.setPreviewCallback(null);
                    mCamera.stopPreview();
                    
                    SymbolSet syms = scanner.getResults();
                    for (Symbol sym : syms) {
                        barcodeScanned = true;
                     	ListActivity.addBookByISBN(sym.getData(), new ListActivity.AddBookByISBN(mList) {
							@Override
							public void BookLookupResult(Book[] result,
									boolean more) {
								mSoundPool.play(mPoolIds[kBeepBeep], v, v, 2, 0, 1.0f);
								super.BookLookupResult(result, more);
							}

							@Override
							public void BookLookupError(String error) {
								mSoundPool.play(mPoolIds[kLongBeep], v, v, 2, 0, 1.0f);
								super.BookLookupError(error);
							}
                    	} );
                    }
                }
            }
        };

    // Mimic continuous auto-focusing
    AutoFocusCallback autoFocusCB = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                autoFocusHandler.postDelayed(doAutoFocus, 1000);
            }
        };
}
