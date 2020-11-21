package com.bcq.camera2;

import android.content.Context;
import android.media.Image;
import android.util.Log;

import com.bcq.camera2.api.CameraApi;
import com.bcq.camera2.api.VideoJoinHelper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Handler;

/**
 * Camera2 + MediaRecord 封装对象
 * 1.拍照（后置摄像头和前置摄像头）
 * - 1）正常拍照流程，先对焦 在拍照
 * - 2）由于前置摄像头可能不支持自动对焦，lockFocus的实现时判断，如果不支持对焦（前置摄像头）直接拍照。
 * 2.录制视频
 * - 1）暂停和恢复：由于MediaRecord Api对pause和resume的api没有实现。因此暂停执行的是stop() 恢复执行的是start()
 * - 2)录制结束后 对因暂停引起的产生多个子文件进行统一合并。
 * 3.录制中-实现拍照
 * - 1）录制过程中 正常执行拍照，录制不暂停 拍照结束后恢复录制的预览（拍照对焦过程中 视频会有不到1s的卡顿）。¬
 */
public class Camera extends CameraApi implements CameraApi.OnImageListeren {
    protected final static String TAG = "Camera";
    private ImageSaver imageTask;
    private VideoParam videoParam;
    private boolean mIsRecording = false;

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
        buildCaptureSession(PreType.previdew, null);
    }

    @Override
    public void startRecord(VideoParam param) {
        if (mIsRecording) {
            Log.e(TAG, "未结束");
            return;
        }
        if (null == param || !param.available()) {
            if (null != mCameraListeren)
                mCameraListeren.onRecordError(-1, "VideoParam Not Available.");
            return;
        }
        mIsRecording = false;
        videoParam = param;
        videoParam.addResumePath();
        if (!setUpMediaRecorder(param)) {
            return;
        }
        buildCaptureSession(PreType.video, new OnSessionListeren() {
            @Override
            public void onSession() {
                if (null != mPreviewSession) {
                    if (null != mCameraListeren) mCameraListeren.onPreRecord();
                    Log.e(TAG, "start");
                    try {
                        mMediaRecorder.start();
                        mBgHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mIsRecording = true;
                            }
                        }, 300);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void pauseRecord() {
        stopRecord(true);
    }

    @Override
    public void resumeRecord() {
        startRecord(videoParam);
    }

    @Override
    public void stopRecord() {
        stopRecord(false);
    }

    private void stopRecord(boolean pause) {
        if (!mIsRecording) {
            Log.e(TAG, "未开始");
            return;
        }
        try {
            mMediaRecorder.stop();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (null != mCameraListeren) {
                mCameraListeren.onRecordError(-1, "Media Record " + (pause ? "Pause" : "Stop") + " Fail.");
            }
        }
        mMediaRecorder.reset();
        startPreview();
        if (!pause) {
            mergeVideo(videoParam.getPaths());
            videoParam = null;
        }
        mIsRecording = false;
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
        //保存图片后
        if (mIsRecording) {
            buildCaptureSession(PreType.video, null);
        }
    }

    /**
     * 合并视频
     * index = 0：目标路径
     * other：待合并的子路径
     *
     * @param filePaths
     */
    private void mergeVideo(List<String> filePaths) {
        int size = null == filePaths ? 0 : filePaths.size();
        if (size < 2) return;
        String desPath = filePaths.get(0);
        if (2 == size) {//只有一个文件 重命名即可
            File chidFile = new File(filePaths.get(1));
            chidFile.renameTo(new File(desPath));
            if (null != mCameraListeren) mCameraListeren.onRecordComplete(desPath);
            return;
        }
        List<String> chidFilePaths = filePaths.subList(1, size);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VideoJoinHelper.mergeVideos(chidFilePaths, desPath);
                    if (null != mCameraListeren) mCameraListeren.onRecordComplete(desPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
