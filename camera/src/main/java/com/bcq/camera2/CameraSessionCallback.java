package com.bcq.camera2;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import androidx.annotation.NonNull;

/**
 * 拍照时session状态监听
 */
public class CameraSessionCallback extends CameraCaptureSession.CaptureCallback {
    private CameraApi camera;

    protected CameraSessionCallback(CameraApi camera) {
        this.camera = camera;
    }

    private void process(CaptureResult result) {
        switch (camera.mState) {
            case ICamera.STATE_PREVIEW: {
                break;
            }
            case ICamera.STATE_WAITING_LOCK: {
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                if (afState == null) {
                    camera.captureStillPicture();
                } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        camera.mState = ICamera.STATE_PICTURE_TAKEN;
                        camera.captureStillPicture();
                    } else {
                        camera.runPrecaptureSequence();
                    }
                }
                break;
            }
            case ICamera.STATE_WAITING_PRECAPTURE: {
                // CONTROL_AE_STATE can be null on some devices
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                    camera.mState = ICamera.STATE_WAITING_NON_PRECAPTURE;
                }
                break;
            }
            case ICamera.STATE_WAITING_NON_PRECAPTURE: {
                // CONTROL_AE_STATE can be null on some devices
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    camera.mState = ICamera.STATE_PICTURE_TAKEN;
                    camera.captureStillPicture();
                }
                break;
            }
        }
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureResult partialResult) {
        process(partialResult);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                   @NonNull CaptureRequest request,
                                   @NonNull TotalCaptureResult result) {
        process(result);
    }
}
