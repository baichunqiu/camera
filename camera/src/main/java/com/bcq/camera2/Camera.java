package com.bcq.camera2;

import android.content.Context;
import android.media.Image;
import android.util.Log;

import com.bcq.camera2.api.CameraApi;

public class Camera extends CameraApi implements CameraApi.OnImageListeren {
    protected final static String TAG = "Camera";
    private ImageSaver imageTask;
    private VideoParam videoParam;

    @Override
    public void init(Context activity, AutoFitTextureView textureView) {
        super.init(activity, textureView);
        setOnImageListeren(this);
        imageTask = ImageSaver.get(new ImageSaver.Callback() {
            @Override
            public void onComplete(String path) {
                if (null != mCameraListeren) mCameraListeren.onTakeComplete(path);
            }
        });
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
        Log.e(TAG, "startRecord");
        if (null == param || !param.available()) {
            if (null != mCameraListeren)
                mCameraListeren.onRecordError(-1, "VideoParam Not Available.");
            return;
        }
        videoParam = param;
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
    public void pauseRecord() {
        Log.e(TAG, "pauseRecord");
        try {
            mMediaRecorder.stop();
            startPreview();
            if (null != mCameraListeren) mCameraListeren.onPauseRecord();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (null != mCameraListeren)
                mCameraListeren.onRecordError(-1, "Media Record Pause Fail.");
        }

    }

    @Override
    public void resumeRecord() {
        Log.e(TAG, "resumeRecord");
        setUpMediaRecorder(videoParam);
        buildCaptureSession(PreType.video, new OnSessionListeren() {
            @Override
            public void onSession() {
                if (null != mPreviewSession) {
                    if (null != mCameraListeren) mCameraListeren.onResumeRecord();
                    mMediaRecorder.start();
                } else {
                    mCameraListeren.onRecordError(-1, "Media Record Resume Fail.");
                }
            }
        });
    }

    @Override
    public void stopRecord() {
        Log.e(TAG, "stopRecord");
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        startPreview();
        if (null != mCameraListeren) mCameraListeren.onRecordComplete(videoParam.filePath);
        videoParam = null;
    }

    @Override
    public void takePicture(String path) {
        imageTask.clear();
        imageTask.setImagePath(path);
        buildCaptureSession(PreType.picture, new OnSessionListeren() {
            @Override
            public void onSession() {
                if (null != mPreviewSession) {
                    if (null != mCameraListeren) mCameraListeren.onTakePicture();
                    lockFocus();
                }
            }
        });
    }

    @Override
    public void onImage(Image image) {
        Log.e(TAG, "onImage");
        imageTask.setImage(image);
        if (imageTask.available()) {
            mBgHandler.post(imageTask);
        }
    }
}
