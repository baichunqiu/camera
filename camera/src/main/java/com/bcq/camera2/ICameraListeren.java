package com.bcq.camera2;

public interface ICameraListeren {
    /**
     * 相机错误
     *
     * @param code
     * @param error
     */
    void onCameraError(int code, String error);

    /**
     * 录制错误
     *
     * @param error
     */
    void onRecordError(int code, String error);

    void onPreRecord();

    void onRecordComplete();
}
