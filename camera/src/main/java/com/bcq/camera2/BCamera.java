package com.bcq.camera2;

import android.app.Activity;
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
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
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

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public abstract class BCamera extends CameraDevice.StateCallback implements ICamera {
    protected final static String TAG = "BCamera";

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

    //for camera preview.
    private AutoFitTextureView mTextureView;
    //A reference to the opened {@link CameraDevice}.
    private CameraDevice mCameraDevice;
    // A reference to the current {@link CameraCaptureSession} for preview.
    private CameraCaptureSession mPreviewSession;
    // handles several lifecycle events on a {@link TextureView.SurfaceTextureListener}
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;
    //The {@link Size} of camera preview.
    private Size mPreviewSize;
    // The {@link Size} of video recording.
    private Size mVideoSize;
    private MediaRecorder mMediaRecorder;
    private ImageReader mImageReader;
    //An additional thread for running tasks that shouldn't block the UI.
    private HandlerThread mBackgroundThread;
    // A {@link Handler} for running tasks in the background.
    private Handler mBackgroundHandler;
    // A {@link Semaphore} to prevent the app from exiting before closing the camera.
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private Integer mSensorOrientation;
    //    private String mNextVideoAbsolutePath;
    private CaptureRequest.Builder mPreviewBuilder;
    private WeakReference<Activity> actReference;
    private CameraListeren mCameraListeren;
    private Size textureViewSize;
    private Context context;
    private WindowManager windowManager;
    private CameraManager manager;
    protected String[] cameraIds;
    protected CameraType cameraType = CameraType.front;


    @Override
    public void init(Context context, AutoFitTextureView textureView) {
        this.context = context.getApplicationContext();
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mTextureView = textureView;
        mSurfaceTextureListener = new BSurfaceTextureListener(this);
        if (null != mTextureView) {
            mTextureView.post(new Runnable() {
                @Override
                public void run() {
                    textureViewSize = new Size(mTextureView.getWidth(), mTextureView.getHeight());
                }
            });
        } else {
            textureViewSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
        }
        getCameraManager();
        try {
            cameraIds = manager.getCameraIdList();
            for (String cameraId : cameraIds) {
                Log.e(TAG, "cameraId = " + cameraId);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected CameraManager getCameraManager() {
        if (null == manager) {
            manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        }
        return manager;
    }

    @Override
    public void setCameraListeren(CameraListeren mCameraListeren) {
        this.mCameraListeren = mCameraListeren;
    }

    @Override
    public void onResume() {
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
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
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void switchCamera() {
        if (CameraType.background != cameraType) {
            cameraType = CameraType.front;
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
        manager = getCameraManager();
        //设置 camera 配置
        setUpCameraOutputs(cameraId, textureViewSize);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            //打开相机
            manager.openCamera(cameraId, this, null);
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
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
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
                    mOnImageAvailableListener, mBackgroundHandler);
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

    private String mFile;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (null != mFile)
                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }
    };

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

    /**
     * Start the camera preview.
     */
    @Override
    public void startPreview() {
        Log.e(TAG, "startPreview");
        buildCaptureSession(PreType.previdew, new OnSessionListeren() {
            @Override
            public void onSession() {
                // TODO: 11/20/20
            }
        });
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
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
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
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
                mCameraListeren.onCameraError(-1, "MediaRecorder Record Prepare Error.");
        }
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int i, int i1) {
                if (null != mCameraListeren)
                    mCameraListeren.onCameraError(-1, "MediaRecorder Record Error.");
            }
        });
    }

    protected List<Surface> buildPreSurface(PreType preType) {
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
            if (null != mCameraListeren) mCameraListeren.onRecordError(-1, e.toString());
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
                        mCameraListeren.onRecordError(-1, "CameraDevice Configure Failed ,");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            if (null != mCameraListeren) mCameraListeren.onRecordError(-1, e.toString());
        }
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
        if (null != mCameraListeren) mCameraListeren.onRecordComplete();
        startPreview();
    }

    @Override
    public void takePicture(String path) {
        this.mFile = path;
        buildCaptureSession(PreType.picture, new OnSessionListeren() {
            @Override
            public void onSession() {
                if (null != mPreviewSession) {
                    lockFocus();
                }
            }
        });
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private int mState = STATE_PREVIEW;
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
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
    };

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Orientation
            int rotation = windowManager.getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    unlockFocus();
                }
            };
            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
            mPreviewSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void takePicture(String path) {
//        this.mFile = new File(path);
//        if (null == mTextureView || !mTextureView.isAvailable()) {
//            return;
//        }
//        SurfaceTexture texture = mTextureView.getSurfaceTexture();
//        assert texture != null;
//        try {
//            // We configure the size of default buffer to be the size of camera preview we want.
//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//
//            // This is the output Surface we need to start preview.
//            Surface surface = new Surface(texture);
//            mPreviewBuilder
//                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            mPreviewBuilder.addTarget(surface);
//            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
//                @Override
//                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    mPreviewSession = cameraCaptureSession;
//                    updatePreview(false);
//                    lockFocus();
//                }
//
//                @Override
//                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    if (null != mCameraListeren)
//                        mCameraListeren.onRecordError(-1, "CameraDevice Configure Failed ,");
//                }
//            }, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//            if (null != mCameraListeren) mCameraListeren.onRecordError(-1, e.toString());
//        }
//    }

    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
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


    /************* StateCallback Start ****************/
    // TODO: 11/19/20 StateCallback
    @Override
    public void onOpened(@NonNull CameraDevice cameraDevice) {
        mCameraDevice = cameraDevice;
        startPreview();
        mCameraOpenCloseLock.release();
        if (null != mTextureView) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        }
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
        if (null != mCameraListeren) mCameraListeren.onCameraError(-1, "Camera Disconnected.");
    }

    @Override
    public void onError(@NonNull CameraDevice cameraDevice, int error) {
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
        if (null != mCameraListeren) mCameraListeren.onCameraError(error, "Camera Open Error.");
    }
    /************* StateCallback End ****************/

    /**
     * AutoFitTextureView 的监听
     */
    private static class BSurfaceTextureListener implements TextureView.SurfaceTextureListener {
        private BCamera bCamera;

        BSurfaceTextureListener(BCamera bCamera) {
            this.bCamera = bCamera;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            bCamera.textureViewSize = new Size(width, height);
            bCamera.openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            bCamera.configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    }
}
