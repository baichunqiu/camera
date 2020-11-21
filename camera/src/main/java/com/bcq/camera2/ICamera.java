package com.bcq.camera2;

import android.Manifest;
import android.content.Context;
import android.util.SparseIntArray;

public interface ICamera {
    SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    SparseIntArray ORIENTATIONS = new SparseIntArray();

    enum PreType {
        picture,//拍照预览
        video,//录制视频预览
        previdew//预览
    }

    enum CameraType {
        background,//后置
        front;//前置

        public int valueOf() {
            return this == background ? 0 : 1;
        }
    }

    int MAX_PREVIEW_WIDTH = 1920;
    int MAX_PREVIEW_HEIGHT = 1080;

    int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;

    String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    //Camera state: Showing camera preview.
    int STATE_PREVIEW = 0;
    //Camera state: Waiting for the focus to be locked.
    int STATE_WAITING_LOCK = 1;
    //Camera state: Waiting for the exposure to be precapture state.
    int STATE_WAITING_PRECAPTURE = 2;
    //Camera state: Waiting for the exposure state to be something other than precapture.
    int STATE_WAITING_NON_PRECAPTURE = 3;
    //Camera state: Picture was taken.
    int STATE_PICTURE_TAKEN = 4;

    void init(Context context, AutoFitTextureView textureView);

    void setCameraListeren(CameraListeren mCameraListeren);

    void onResume();

    void onPause();

    void openCamera();

    void closeCamera();

    void startPreview();

    void switchCamera();

    void startRecord(VideoParam param);

    void pauseRecord();

    void resumeRecord();

    void stopRecord();

    void takePicture(String path);
}
