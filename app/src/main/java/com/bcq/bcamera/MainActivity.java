//package com.bcq.bcamera;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.content.res.Configuration;
//import android.graphics.Matrix;
//import android.graphics.RectF;
//import android.graphics.SurfaceTexture;
//import android.hardware.Camera;
//import android.hardware.camera2.CameraAccessException;
//import android.hardware.camera2.CameraCaptureSession;
//import android.hardware.camera2.CameraCharacteristics;
//import android.hardware.camera2.CameraDevice;
//import android.hardware.camera2.CameraManager;
//import android.hardware.camera2.CameraMetadata;
//import android.hardware.camera2.CaptureRequest;
//import android.hardware.camera2.params.StreamConfigurationMap;
//import android.media.MediaCodec;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.util.Log;
//import android.util.Size;
//import android.util.SparseIntArray;
//import android.view.Surface;
//import android.view.SurfaceHolder;
//import android.view.TextureView;
//import android.view.View;
//import android.widget.Toast;
//
//import com.example.android.camera.utils.AutoFitSurfaceView;
//import com.example.android.camera.utils.CameraSizesKt;
//import com.bcq.camera2.ui.CameraActivity;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.Enumeration;
//import java.util.List;
//import java.util.concurrent.Semaphore;
//import java.util.concurrent.TimeUnit;
//
//public class MainActivity extends AppCompatActivity {
//    private final static String TAG = "MainActivity";
//    private static int CAMERA_ID = CameraCharacteristics.LENS_FACING_BACK;
//    private static String S_CAMERA_ID = "Back";
//    private static int FPS = 30;
//    private static Size SIZE = new Size(1280, 720);
//    private final static String[] CAMERA_AUDIO = new String[]{
//            Manifest.permission.CAMERA,
//            Manifest.permission.RECORD_AUDIO
//    };
//    private AutoFitSurfaceView mTextureView;
//    private View capture_button;
//    private final static String PATH = Environment.getExternalStorageDirectory().getPath();
//    private CameraCharacteristics characteristics;
//    private CameraDevice camera;
//    private CameraManager manager;
//    private MediaRecorder mMediaRecorder;
//    private HandlerThread mBackgroundThread;
//    private Handler mBackgroundHandler;
//    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
//    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
//    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
//    static {
//        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
//        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
//        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
//        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
//    }
//
//    static {
//        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
//        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
//        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
//        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (PermissionUtil.REQUEST_CODE == requestCode) {
//            String[] arr = PermissionUtil.getDeniedPermissions(this, permissions);
//            if (null == arr || arr.length == 0) {
//                init();
//            }
//        }
//    }
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        if (PermissionUtil.checkPermissions(this, CAMERA_AUDIO)) {
//            init();
////            startActivity(new Intent(this, CameraActivity.class));
//        }
//    }
//
//    boolean mIsRecordingVideo = false;
//
//    void init() {
//        Log.e(TAG, "init PATH = " + PATH);
//        mTextureView = findViewById(R.id.view_finder);
//        capture_button = findViewById(R.id.capture_button);
//        capture_button.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (mIsRecordingVideo) {
//                    stopRecordingVideo();
//                } else {
//                    startRecordingVideo();
//                }
//            }
//        });
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        startBackgroundThread();
//        if (mTextureView.isAvailable()) {
//            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
//        } else {
//            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
//        }
//
//    }
//
//    @Override
//    public void onPause() {
//        closeCamera();
//        stopBackgroundThread();
//        super.onPause();
//    }
//
//
//    /**
//     * Starts a background thread and its {@link Handler}.
//     */
//    private void startBackgroundThread() {
//        mBackgroundThread = new HandlerThread("CameraBackground");
//        mBackgroundThread.start();
//        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
//    }
//
//    /**
//     * Stops the background thread and its {@link Handler}.
//     */
//    private void stopBackgroundThread() {
//        mBackgroundThread.quitSafely();
//        try {
//            mBackgroundThread.join();
//            mBackgroundThread = null;
//            mBackgroundHandler = null;
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//
////    /**
////     * Requests permissions needed for recording video.
////     */
////    private void requestVideoPermissions() {
////        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
////            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
////        } else {
////            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
////        }
////    }
//
//    //    @Override
////    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
////                                           @NonNull int[] grantResults) {
////        Log.d(TAG, "onRequestPermissionsResult");
////        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
////            if (grantResults.length == VIDEO_PERMISSIONS.length) {
////                for (int result : grantResults) {
////                    if (result != PackageManager.PERMISSION_GRANTED) {
////                        ErrorDialog.newInstance(getString(R.string.permission_request))
////                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
////                        break;
////                    }
////                }
////            } else {
////                ErrorDialog.newInstance(getString(R.string.permission_request))
////                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
////            }
////        } else {
////            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
////        }
////    }
//
//    private static Size chooseVideoSize(Size[] choices) {
//        for (Size size : choices) {
//            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
//                return size;
//            }
//        }
//        Log.e(TAG, "Couldn't find any suitable video size");
//        return choices[choices.length - 1];
//    }
//
//    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
//        // Collect the supported resolutions that are at least as big as the preview Surface
//        List<Size> bigEnough = new ArrayList<Size>();
//        int w = aspectRatio.getWidth();
//        int h = aspectRatio.getHeight();
//        for (Size option : choices) {
//            if (option.getHeight() == option.getWidth() * h / w &&
//                    option.getWidth() >= width && option.getHeight() >= height) {
//                bigEnough.add(option);
//            }
//        }
//
//        // Pick the smallest of those, assuming we found any
//        if (bigEnough.size() > 0) {
//            return Collections.min(bigEnough, new CompareSizesByArea());
//        } else {
//            Log.e(TAG, "Couldn't find any suitable preview size");
//            return choices[0];
//        }
//    }
//
//    private Integer mSensorOrientation;
//    private String mNextVideoAbsolutePath;
//    private CaptureRequest.Builder mPreviewBuilder;
//    private Surface mRecorderSurface;
//    private Size mPreviewSize;
//    private Size mVideoSize;
//
//    /**
//     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
//     */
//    private void openCamera(int width, int height) {
////        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
////            requestVideoPermissions();
////            return;
////        }
//        if (isFinishing()) {
//            return;
//        }
//        CameraManager manager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
//        try {
//            Log.d(TAG, "tryAcquire");
//            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
//                throw new RuntimeException("Time out waiting to lock camera opening.");
//            }
//            String cameraId = manager.getCameraIdList()[0];
//
//            // Choose the sizes for camera preview and video recording
//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//            StreamConfigurationMap map = characteristics
//                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
//            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
//            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                    width, height, mVideoSize);
//
//            int orientation = getResources().getConfiguration().orientation;
//            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//            } else {
//                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
//            }
//            configureTransform(width, height);
//            mMediaRecorder = new MediaRecorder();
//            try {
//                manager.openCamera(cameraId, mStateCallback, null);
//            } catch (SecurityException e) {
//                e.printStackTrace();
//            }
//
//        } catch (CameraAccessException e) {
//            Toast.makeText(this, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
//            finish();
//        } catch (NullPointerException e) {
//            // Currently an NPE is thrown when the Camera2API is used but not supported on the
//            // device this code runs.
////            ErrorDialog.newInstance(getString(R.string.camera_error))
////                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
//        } catch (InterruptedException e) {
//            throw new RuntimeException("Interrupted while trying to lock camera opening.");
//        }
//    }
//
//    private void closeCamera() {
//        try {
//            mCameraOpenCloseLock.acquire();
//            closePreviewSession();
//            if (null != mCameraDevice) {
//                mCameraDevice.close();
//                mCameraDevice = null;
//            }
//            if (null != mMediaRecorder) {
//                mMediaRecorder.release();
//                mMediaRecorder = null;
//            }
//        } catch (InterruptedException e) {
//            throw new RuntimeException("Interrupted while trying to lock camera closing.");
//        } finally {
//            mCameraOpenCloseLock.release();
//        }
//    }
//
//    private CameraCaptureSession mPreviewSession;
//    private CameraDevice mCameraDevice;
//
//    /**
//     * Start the camera preview.
//     */
//    private void startPreview() {
//        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
//            return;
//        }
//        try {
//            closePreviewSession();
////            SurfaceTexture texture = mTextureView.getSurfaceTexture();
//            SurfaceTexture texture = mTextureView.getSurfaceTexture();
//            assert texture != null;
//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//
//            Surface previewSurface = new Surface(texture);
//            mPreviewBuilder.addTarget(previewSurface);
//
//            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
//
//                @Override
//                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
//                    mPreviewSession = cameraCaptureSession;
//                    updatePreview();
//                }
//
//                @Override
//                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
//                    Toast.makeText(MainActivity.this, "Failed", Toast.LENGTH_SHORT).show();
//                }
//            }, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
//     */
//    private void updatePreview() {
//        if (null == mCameraDevice) {
//            return;
//        }
//        try {
//            setUpCaptureRequestBuilder(mPreviewBuilder);
//            HandlerThread thread = new HandlerThread("CameraPreview");
//            thread.start();
//            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
//        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//    }
//
//    /**
//     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
//     * This method should not to be called until the camera preview size is determined in
//     * openCamera, or until the size of `mTextureView` is fixed.
//     *
//     * @param viewWidth  The width of `mTextureView`
//     * @param viewHeight The height of `mTextureView`
//     */
//    private void configureTransform(int viewWidth, int viewHeight) {
//        if (null == mTextureView || null == mPreviewSize || isFinishing()) {
//            return;
//        }
//        int rotation = getWindowManager().getDefaultDisplay().getRotation();
//        Matrix matrix = new Matrix();
//        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
//        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
//        float centerX = viewRect.centerX();
//        float centerY = viewRect.centerY();
//        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
//            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
//            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
//            float scale = Math.max(
//                    (float) viewHeight / mPreviewSize.getHeight(),
//                    (float) viewWidth / mPreviewSize.getWidth());
//            matrix.postScale(scale, scale, centerX, centerY);
//            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
//        }
//        mTextureView.setTransform(matrix);
//    }
//
//    private void setUpMediaRecorder() throws IOException {
//        if (isFinishing()) {
//            return;
//        }
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
//            mNextVideoAbsolutePath = getVideoFilePath(this);
//        }
//        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
//        mMediaRecorder.setVideoEncodingBitRate(10000000);
//        mMediaRecorder.setVideoFrameRate(30);
//        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//        int rotation = getWindowManager().getDefaultDisplay().getRotation();
//        switch (mSensorOrientation) {
//            case 90:
//                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
//                break;
//            case 270:
//                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
//                break;
//        }
//        mMediaRecorder.prepare();
//    }
//
//    private String getVideoFilePath(Context context) {
//        return context.getExternalFilesDir(null).getAbsolutePath() + "/"
//                + System.currentTimeMillis() + ".mp4";
//    }
//
//    private void startRecordingVideo() {
//        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
//            return;
//        }
//        try {
//            closePreviewSession();
//            setUpMediaRecorder();
//            SurfaceTexture texture = mTextureView.getSurfaceTexture();
//            assert texture != null;
//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//            List<Surface> surfaces = new ArrayList<>();
//
//            // Set up Surface for the camera preview
//            Surface previewSurface = new Surface(texture);
//            surfaces.add(previewSurface);
//            mPreviewBuilder.addTarget(previewSurface);
//
//            // Set up Surface for the MediaRecorder
//            mRecorderSurface = mMediaRecorder.getSurface();
//            surfaces.add(mRecorderSurface);
//            mPreviewBuilder.addTarget(mRecorderSurface);
//
//            // Start a capture session
//            // Once the session starts, we can update the UI and start recording
//            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
//
//                @Override
//                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    mPreviewSession = cameraCaptureSession;
//                    updatePreview();
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            // UI
////                            mButtonVideo.setText(R.string.stop);
//                            mIsRecordingVideo = true;
//
//                            // Start recording
//                            mMediaRecorder.start();
//                        }
//                    });
//                }
//
//                @Override
//                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    Toast.makeText(MainActivity.this, "Failed", Toast.LENGTH_SHORT).show();
//                }
//            }, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }
//
//    private void closePreviewSession() {
//        if (mPreviewSession != null) {
//            try {
//                mPreviewSession.stopRepeating();
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
//            mPreviewSession.close();
//            mPreviewSession = null;
//        }
//    }
//
//    private void stopRecordingVideo() {
//        // UI
//        mIsRecordingVideo = false;
//        startPreview();
//        // Stop recording
//        mMediaRecorder.stop();
//        mMediaRecorder.reset();
//
//        Toast.makeText(MainActivity.this, "Video saved: " + mNextVideoAbsolutePath,
//                Toast.LENGTH_SHORT).show();
//        Log.d(TAG, "Video saved: " + mNextVideoAbsolutePath);
//        mNextVideoAbsolutePath = null;
//    }
//
//    /**
//     * Compares two {@code Size}s based on their areas.
//     */
//    static class CompareSizesByArea implements Comparator<Size> {
//
//        @Override
//        public int compare(Size lhs, Size rhs) {
//            // We cast here to ensure the multiplications won't overflow
//            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
//                    (long) rhs.getWidth() * rhs.getHeight());
//        }
//
//    }
//
//
//    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
//
//        @Override
//        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
//                                              int width, int height) {
//            openCamera(width, height);
//        }
//
//        @Override
//        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
//                                                int width, int height) {
//            configureTransform(width, height);
//        }
//
//        @Override
//        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
//            return true;
//        }
//
//        @Override
//        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
//        }
//
//    };
//
//    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
//
//        @Override
//        public void onOpened(CameraDevice cameraDevice) {
//            mCameraDevice = cameraDevice;
//            startPreview();
//            mCameraOpenCloseLock.release();
//            if (null != mTextureView) {
//                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
//            }
//        }
//
//        @Override
//        public void onDisconnected(CameraDevice cameraDevice) {
//            mCameraOpenCloseLock.release();
//            cameraDevice.close();
//            mCameraDevice = null;
//        }
//
//        @Override
//        public void onError(CameraDevice cameraDevice, int error) {
//            mCameraOpenCloseLock.release();
//            cameraDevice.close();
//            mCameraDevice = null;
//            finish();
//        }
//
//    };
//}