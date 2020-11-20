package com.bcq.camera2.api;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.bcq.camera2.AutoFitTextureView;
import com.bcq.camera2.CameraListeren;
import com.bcq.camera2.ICamera;
import com.bcq.camera2.VideoParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 相机相关api的封装
 * 1.处理预览
 * 2.切换摄像头
 */
public abstract class CameraApi implements ICamera {
    protected final static String TAG = "CameraApi";

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);

        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);

        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    // protected 子类有可能会使用的API实例对象
    protected CameraCaptureSession mPreviewSession;
    protected MediaRecorder mMediaRecorder;
    protected Handler mBgHandler;
    protected CameraListeren mCameraListeren;
    protected CameraManager mCameraManager;
    // 拍照锁定焦点后状态回调使用
    protected int mState = STATE_PREVIEW;

    //for camera preview.
    private AutoFitTextureView mTextureView;
    //A reference to the opened {@link CameraDevice}.
    private CameraDevice mCameraDevice;
    //The {@link Size} of camera preview.
    private Size mPreviewSize;
    // The {@link Size} of video recording.
    private Size mVideoSize;
    private ImageReader mImageReader;
    //An additional thread for running tasks that shouldn't block the UI.
    private HandlerThread mBgThread;
    // A {@link Handler} for running tasks in the background.
    // A {@link Semaphore} to prevent the app from exiting before closing the camera.
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private Integer mSensorOrientation;
    private CaptureRequest.Builder mPreviewBuilder;
    private Size textureViewSize;
    private Context context;
    private WindowManager windowManager;
    private String[] cameraIds;
    private CameraType cameraType = CameraType.front;
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraSessionCallback(this);
    private OnImageListeren onImageListeren;

    @Override
    public void init(Context context, AutoFitTextureView textureView) {
        this.context = context.getApplicationContext();
        this.mTextureView = textureView;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        textureViewSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
        try {
            cameraIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setCameraListeren(CameraListeren mCameraListeren) {
        this.mCameraListeren = mCameraListeren;
    }

    @Override
    public void onResume() {
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            textureViewSize = new Size(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
                    textureViewSize = new Size(w, h);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {
                    configureTransform(w, h);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBgThread = new HandlerThread("CameraBackground");
        mBgThread.start();
        mBgHandler = new Handler(mBgThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBgThread.quitSafely();
        try {
            mBgThread.join();
            mBgThread = null;
            mBgHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void switchCamera() {
        if (CameraType.background == cameraType) {
            cameraType = CameraType.front;
        } else {
            cameraType = CameraType.background;
        }
        openCamera();
    }

    @Override
    public void openCamera() {
        String cameraId = cameraIds[cameraType.valueOf()];
        Log.e(TAG, "cameraId = " + cameraId);
        openCamera(cameraId);
    }

    @SuppressWarnings("MissingPermission")
    protected final void openCamera(String cameraId) {
        if (TextUtils.isEmpty(cameraId)) {
            Log.e(TAG, "Open Camera Fail For CameraId is null.");
            return;
        }
        if (null == mCameraManager) {
            Log.e(TAG, "Open Camera Fail For Not Init.");
            return;
        }
        //设置 camera 配置
        setUpCameraOutputs(cameraId, textureViewSize);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // TODO: 11/20/20 open camera
            mCameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    startPreview();
                    mCameraOpenCloseLock.release();
                    if (null != mTextureView) {
                        configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                    if (null != mCameraListeren)
                        mCameraListeren.onCameraError(-1, "Camera Disconnected.");
                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                    if (null != mCameraListeren)
                        mCameraListeren.onCameraError(error, "Camera Open Error.");
                }
            }, null);
        } catch (CameraAccessException e) {
            if (null != mCameraListeren)
                mCameraListeren.onCameraError(-1, "Cannot access the camera.");
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            if (null != mCameraListeren)
                mCameraListeren.onCameraError(-1, "This device doesn\'t support Camera2 API.");
        } catch (InterruptedException e) {
            if (null != mCameraListeren)
                mCameraListeren.onCameraError(-1, "Interrupted while trying to lock camera opening.");
        }
    }

    /**
     * Sets up member variables related to camera.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(String cameraId, Size textureViewSize) {
        try {
            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = SizeUtil.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = SizeUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    textureViewSize.getWidth(), textureViewSize.getHeight(), mVideoSize);
            Log.e(TAG, "mVideoSize:width = " + mVideoSize.getWidth() + " height = " + mVideoSize.getHeight());
            Log.e(TAG, "mPreviewSize:width = " + mPreviewSize.getWidth() + " height = " + mPreviewSize.getHeight());
            //预览
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(textureViewSize.getWidth(), textureViewSize.getHeight());
            //record video
            mMediaRecorder = new MediaRecorder();
            //image
            // For still image captures, we use the largest available size.
            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new CompareSizesByArea());
            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(
                    new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            if (null != onImageListeren) {
                                onImageListeren.onImage(reader.acquireNextImage());
                            }
                        }
                    }, mBgHandler);
        } catch (CameraAccessException e) {
            if (null != mCameraListeren)
                mCameraListeren.onCameraError(-1, "Cannot access the camera.");
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            if (null != mCameraListeren)
                mCameraListeren.onCameraError(-1, "This device doesn\'t support Camera2 API.");
        }
    }

    @Override
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    protected void updatePreview(PreType preType) {
        if (null == mCameraDevice || null == mPreviewSession) {
            return;
        }
        try {
            if (PreType.video != preType) {
                // Auto focus should be continuous for camera preview.
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // Flash is automatically enabled when necessary.
                setAutoFlash(mPreviewBuilder);
                // Finally, we start displaying the camera preview.
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mBgHandler);
            } else {
                mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                HandlerThread thread = new HandlerThread("CameraPreview");
                thread.start();
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, new Handler(thread.getLooper()));
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    public void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = windowManager.getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    protected void setUpMediaRecorder(VideoParam param) {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(param.filePath);
        mMediaRecorder.setVideoEncodingBitRate(param.bitRate);
        mMediaRecorder.setVideoFrameRate(param.fps);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            if (null != mCameraListeren)
                mCameraListeren.onRecordError(-1, "MediaRecorder Record Prepare Error.");
        }
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int i, int i1) {
                if (null != mCameraListeren)
                    mCameraListeren.onRecordError(-1, "MediaRecorder Record Error.");
            }
        });
    }

    private List<Surface> buildPreSurface(PreType preType) {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return null;
        }
        Log.e(TAG, "mPreviewSize W:" + mPreviewSize.getWidth() + " H:" + mPreviewSize.getHeight());
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if (null == texture) return null;
        List<Surface> surfaces = new ArrayList<>();
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(texture);
        try {
            if (PreType.video == preType) {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                // Set up Surface for the camera preview
                mPreviewBuilder.addTarget(previewSurface);
                // Set up Surface for the MediaRecorder
                Surface recorderSurface = mMediaRecorder.getSurface();
                mPreviewBuilder.addTarget(recorderSurface);
                surfaces.add(previewSurface);
                surfaces.add(recorderSurface);
            } else {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewBuilder.addTarget(previewSurface);
                surfaces.add(previewSurface);
                if (PreType.picture == preType) {
                    if (null != mImageReader)
                        surfaces.add(mImageReader.getSurface());
                } else {
                    // TODO: 11/20/20 preview 只有输入
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            if (null != mCameraListeren) mCameraListeren.onCameraError(-1, e.toString());
        }
        return surfaces;

    }

    protected void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    public interface OnSessionListeren {
        //session 创建成功才会回调
        void onSession();
    }

    /**
     * 构建CaptureSession实例
     *
     * @param preType
     * @param onSessionListeren
     */
    protected void buildCaptureSession(PreType preType, OnSessionListeren onSessionListeren) {
        if (null == mCameraDevice) {
            Log.e(TAG, "Build Capture Session Fail For CameraDevice is null.");
            return;
        }
        try {
            closePreviewSession();
            List<Surface> surfaces = buildPreSurface(preType);
            int size = null == surfaces ? 0 : surfaces.size();
            Log.e(TAG, "size = " + size + " preType = " + preType);
            boolean buildOk = PreType.previdew == preType ? (1 == size) : (2 == size);
            if (!buildOk) {
                Log.e(TAG, "Build Preview Surface Fail.");
                return;
            }
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    if (null == mPreviewSession) return;
                    updatePreview(preType);
                    if (null != onSessionListeren) onSessionListeren.onSession();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null != mCameraListeren)
                        mCameraListeren.onCameraError(-1, "CameraDevice Configure Failed ,");
                }
            }, mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            if (null != mCameraListeren) mCameraListeren.onCameraError(-1, e.toString());
        }
    }

    protected void lockFocus() {
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_LOCK;
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback, mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    }

    /******************************************************************
     *  以下 拍照状态CaptureCallback中执行的方法 可以忽略
     *******************************************************************/

    protected void unlockFocus() {
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback, mBgHandler);
            mState = STATE_PREVIEW;
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback,
                    mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void runPrecaptureSequence() {
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback,
                    mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    protected void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Orientation
            int rotation = windowManager.getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
            mPreviewSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    protected void setOnImageListeren(OnImageListeren onImageListeren) {
        this.onImageListeren = onImageListeren;
    }

    public interface OnImageListeren {
        void onImage(Image image);
    }
}
