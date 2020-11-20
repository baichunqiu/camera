package com.bcq.camera2;

import android.Manifest;
import android.app.Activity;
import android.util.SparseIntArray;

public interface ICamera {
    int MAX_PREVIEW_WIDTH = 1920;
    int MAX_PREVIEW_HEIGHT = 1080;

    int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    SparseIntArray ORIENTATIONS = new SparseIntArray();

    int REQUEST_VIDEO_PERMISSIONS = 10006;
    String FRAGMENT_DIALOG = "dialog";

    String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    /**
     * Camera state: Showing camera preview.
     */
    int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    int STATE_PICTURE_TAKEN = 4;

    void init(Activity activity, AutoFitTextureView textureView);

    void onResume();

    void onPause();

    void openCamera();

    void startPreview();

    void closeCamera();

    void startRecord(VideoParam param);

    void stopRecord();

    void takePicture(String path);

    void setCameraListeren(ICameraListeren mCameraListeren);
}
