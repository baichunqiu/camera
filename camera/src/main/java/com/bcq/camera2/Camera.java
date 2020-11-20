package com.bcq.camera2;

import android.content.Context;
import android.media.Image;

public class Camera extends CameraApi implements CameraApi.OnImageListeren {
    protected final static String TAG = "Camera";
    private ImageSaver imageTask;

    @Override
    public void init(Context activity, AutoFitTextureView textureView) {
        super.init(activity, textureView);
        setOnImageListeren(this);
        imageTask = new ImageSaver();
    }

    @Override
    public void setCameraListeren(CameraListeren mCameraListeren) {
        super.setCameraListeren(mCameraListeren);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void switchCamera() {
        super.switchCamera();
    }

    @Override
    public void openCamera() {
        super.openCamera();
    }

    @Override
    public void closeCamera() {
        super.closeCamera();
    }

    @Override
    public void startPreview() {
        buildCaptureSession(PreType.previdew, new OnSessionListeren() {
            @Override
            public void onSession() {// TODO: 11/20/20 no need to nothing
            }
        });
    }

    @Override
    public void startRecord(VideoParam param) {
        setUpMediaRecorder(param);
        buildCaptureSession(PreType.video, new OnSessionListeren() {
            @Override
            public void onSession() {
                if (null != mPreviewSession) {
                    if (null != mCameraListeren) mCameraListeren.onPreRecord();
                    mMediaRecorder.start();
                }
            }
        });
    }

    @Override
    public void stopRecord() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        startPreview();
        if (null != mCameraListeren) mCameraListeren.onRecordComplete();
    }

    @Override
    public void takePicture(String path) {
        imageTask.clear();
        imageTask.setImagePath(path);
        buildCaptureSession(PreType.picture, new OnSessionListeren() {
            @Override
            public void onSession() {
                if (null != mPreviewSession) {
                    lockFocus();
                }
            }
        });
    }

    @Override
    public void onImage(Image image) {
        imageTask.setImage(image);
        if (imageTask.available()) {
            mBgHandler.post(imageTask);
        }
    }
}
