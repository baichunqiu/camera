package com.bcq.camera2;

import android.media.Image;

public interface CameraListeren {
    /**
     * 相机错误
     *
     * @param code
     * @param error
     */
    void onCameraError(int code, String error);

    /**
     * 开始录制回调
     */
    void onPreRecord();

    void onPauseRecord();

    void onResumeRecord();

    /**
     * 录制错误
     *
     * @param error
     */
    void onRecordError(int code, String error);

    /**
     * 录制结束回调
     */
    void onRecordComplete(String path);

    /**
     * 开始拍照回调
     */
    void onTakePicture();

    /**
     * 拍照完成回调
     */
    void onTakeComplete(String image);
}
