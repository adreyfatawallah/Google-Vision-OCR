/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.ocrreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.samples.vision.ocrreader.ui.camera.CameraSource;
import com.google.android.gms.samples.vision.ocrreader.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.ocrreader.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity for the Ocr Detecting app.  This app detects text and displays the value with the
 * rear facing camera. During detection overlay graphics are drawn to indicate the position,
 * size, and contents of each TextBlock.
 */
public final class OcrCaptureActivity extends AppCompatActivity {
    private static final String TAG = "OcrCaptureActivity";

    // Intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // Permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // Constants used to pass extra data in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";
    public static final String TextBlockObject = "String";

    private CameraSource cameraSource;
    private CameraSourcePreview preview;
    private GraphicOverlay<OcrGraphic> graphicOverlay;

    // Helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    // A TextToSpeech engine for speaking a String value.
    private TextToSpeech tts;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.ocr_capture);

        preview = (CameraSourcePreview) findViewById(R.id.preview);
        graphicOverlay = (GraphicOverlay<OcrGraphic>) findViewById(R.id.graphicOverlay);

        // Set good defaults for capturing text.
        boolean autoFocus = true;
        boolean useFlash = false;

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash);
        } else {
            requestCameraPermission();
        }

        gestureDetector = new GestureDetector(this, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        Snackbar.make(graphicOverlay, "Tap to Speak. Pinch/Stretch to zoom",
                Snackbar.LENGTH_LONG)
                .show();

        // TODO: Set up the Text To Speech engine.
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Log.d("TTS", "Text to speech engine started successfully");
                    tts.setLanguage(Locale.US);
                } else {
                    Log.d("TTS", "Error starting the text to speech engine.");
                }
            }
        };
        tts = new TextToSpeech(this.getApplicationContext(), listener);
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(graphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean b = scaleGestureDetector.onTouchEvent(e);

        boolean c = gestureDetector.onTouchEvent(e);

        return b || c || super.onTouchEvent(e);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the ocr detector to detect small text samples
     * at long distances.
     * <p>
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        Context context = getApplicationContext();

        // TODO: Create the TextRecognizer
        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();

        // TODO: Set the TextRecognizer's Processor.
        final OcrDetectorProcessor ocrDetectorProcessor = new OcrDetectorProcessor(graphicOverlay);
        textRecognizer.setProcessor(ocrDetectorProcessor);

        // TODO: Check if the TextRecognizer is operational.
        if (!textRecognizer.isOperational()) {
            Log.w(TAG, "Detector dependencies are not yet available");

            // Check for low storage. If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowStorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_SHORT).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // TODO: Create the cameraSource using the TextRecognizer.
        cameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1280, 1024)
                .setRequestedFps(15.0f)
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO : null)
                .build();

        final Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SparseArray<TextBlock> items = ocrDetectorProcessor.getItems();
                List<TextBlock> tempItems = new ArrayList<>();
                List<TextBlock> sortItems = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    TextBlock item = items.valueAt(i);
                    if (item != null && item.getValue() != null) {
//                        Log.e("GET_DATA", "index " + i + " : " + item.getValue());

                        tempItems.add(item);
                        Log.e("RESULT TEMP", "======= " + item.getValue() + " =======");
                        Log.e("RESULT TOP", String.valueOf(item.getBoundingBox().top));
                        Log.e("RESULT LEFT", String.valueOf(item.getBoundingBox().left));
                    }

                    /**
                     * Get Component
                     */
//                    List<? extends Text> textComponents = item.getComponents();
//                    for (Text currentText : textComponents) {
//                        float left = translateX(currentText.getBoundingBox().left);
//                        float bottom = translateY(currentText.getBoundingBox().bottom);
//                    }
                }

                /**
                 * Get Graphic
                 */
//                OcrGraphic graphic = graphicOverlay.getGraphicAtLocation(5, 5);
//                TextBlock text;
//                if (graphic != null) {
//                    text = graphic.getTextBlock();
//                    if (text != null && text.getValue() != null) {
//                        Log.e("RESULT", "text data is being spoken! " + text.getValue());
//                    } else {
//                        Log.e("RESULT", "text data is null");
//                    }
//                } else
//                    Log.e("RESULT", "graphic null");

                for (int i = 0; i < tempItems.size(); i++) {
                    if (sortItems.size() > 0) {
                        int pos = -1;
                        boolean loop = true;
                        for (int j = 0; j < sortItems.size(); j++) {
                            if (loop) {
                                int tempTop = tempItems.get(i).getBoundingBox().top;
                                int sortTop = sortItems.get(j).getBoundingBox().top;

                                if (tempTop <= sortTop) {
                                    pos = j;
                                    loop = false;
                                }
                            }
                        }

                        if (pos > -1) {
                            List<Integer> listSame = new ArrayList<>();
                            for (int j = 0; j < sortItems.size(); j++) {
                                int tempTop = tempItems.get(i).getBoundingBox().top;
                                int sortTop = sortItems.get(j).getBoundingBox().top;

                                if (tempTop == sortTop)
                                    listSame.add(j);
                            }

                            int count = 0;
                            boolean loop2 = true;
                            for (int j = 0; j < listSame.size(); j++) {
                                if (loop2) {
                                    int posSame = listSame.get(j);
                                    int tempLeft = tempItems.get(i).getBoundingBox().left;
                                    int sortLeft = sortItems.get(posSame).getBoundingBox().left;

                                    if (tempLeft <= sortLeft)
                                        loop2 = false;
                                    else
                                        count++;
                                }
                            }

                            if (count > 0)
                                sortItems.add(pos + count, tempItems.get(i));
                            else
                                sortItems.add(pos, tempItems.get(i));
                        } else
                            sortItems.add(tempItems.get(i));
                    } else
                        sortItems.add(tempItems.get(i));
                }

                for (int i = 0; i < sortItems.size(); i++) {
                    Log.e("RESULT SORT", "======= " + sortItems.get(i).getValue() + " =======");
                    Log.e("RESULT TOP", String.valueOf(sortItems.get(i).getBoundingBox().top));
                    Log.e("RESULT LEFT", String.valueOf(sortItems.get(i).getBoundingBox().left));
                }

                String nama = sortItems.get(0).getValue();
                String quantity = sortItems.get(1).getValue();
                String harga = sortItems.get(2).getValue();

                Log.e("RESULT RESULT", String.valueOf(sortItems.size()) + " - " + nama + " - " + quantity + " - " + harga);

                Intent result = new Intent(getApplicationContext(), ResultActivity.class);
                Bundle b = new Bundle();
                b.putString("nama", nama);
                b.putString("quantity", quantity);
                b.putString("harga", harga);
                result.putExtras(b);
                startActivity(result);
            }
        });
    }

    /**
     * Translate X
     */
    public float translateX(float x) {
        if (graphicOverlay.getFacing() == com.google.android.gms.vision.CameraSource.CAMERA_FACING_FRONT) {
            return graphicOverlay.getWidth() - scaleX(x);
        } else {
            return scaleX(x);
        }
    }
    public float scaleX(float horizontal) {
        return horizontal * graphicOverlay.getWidthScaleFactor();
    }

    /**
     * Translate Y
     */
    public float translateY(float y) {
        return scaleY(y);
    }
    public float scaleY(float vertical) {
        return vertical * graphicOverlay.getHeightScaleFactor();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (preview != null) {
            preview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (preview != null) {
            preview.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // We have permission, so create the camerasource
            boolean autoFocus = getIntent().getBooleanExtra(AutoFocus, false);
            boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);
            createCameraSource(autoFocus, useFlash);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Multitracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (cameraSource != null) {
            try {
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    /**
     * onTap is called to speak the tapped TextBlock, if any, out loud.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the tap was on a TextBlock
     */
    private boolean onTap(float rawX, float rawY) {
        // TODO: Speak the text when the user taps on screen.
        OcrGraphic graphic = graphicOverlay.getGraphicAtLocation(rawX, rawY);
        TextBlock text = null;
        if (graphic != null) {
            text = graphic.getTextBlock();
            if (text != null && text.getValue() != null) {
                Log.d(TAG, "text data is being spoken! " + text.getValue());
                Log.e("RESULT TAP", String.valueOf(rawX) + " - " + String.valueOf(rawY));
                // TODO: Speak the string
                tts.speak(text.getValue(), TextToSpeech.QUEUE_ADD, null, "DEFAULT");
            } else {
                Log.d(TAG, "text data is null");
            }
        } else {
            Log.d(TAG, "no text detected");
        }

        return text != null;
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (cameraSource != null) {
                cameraSource.doZoom(detector.getScaleFactor());
            }
        }
    }
}