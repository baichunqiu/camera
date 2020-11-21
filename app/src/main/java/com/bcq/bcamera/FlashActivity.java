package com.bcq.bcamera;

import android.Manifest;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bcq.camera2.AutoFitTextureView;
import com.bcq.camera2.Camera;
import com.bcq.camera2.CameraListeren;
import com.bcq.camera2.VideoParam;
import com.bcq.camera2.ui.CameraActivity;

import java.io.File;

public class FlashActivity extends BasePermissionActivity implements View.OnClickListener {
    private final static String TAG = "FlashActivity";
    private final static String[] CAMERA_AUDIO = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected String[] onCheckPermission() {
        return CAMERA_AUDIO;
    }

    @Override
    protected void onPermissionAccept(boolean accept) {
        if (accept) {
            init();
//            startActivity(new Intent(this, CameraActivity.class));
        } else {
            Toast.makeText(this, "请赋予相关权限", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (null != camera) camera.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != camera) camera.onPause();
    }


    Camera camera;
    private AutoFitTextureView view_finder;
    private View capture_button, type_button, switch_button, pause_button;

    private String getFilePath() {
        final File dir = getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis();
    }

    void init() {
        setContentView(R.layout.activity_camera);
        view_finder = findViewById(R.id.view_finder);
        capture_button = findViewById(R.id.capture_button);
        type_button = findViewById(R.id.type_button);
        switch_button = findViewById(R.id.switch_button);
        pause_button = findViewById(R.id.pause_button);
        capture_button.setOnClickListener(this);
        type_button.setOnClickListener(this);
        switch_button.setOnClickListener(this);
        pause_button.setOnClickListener(this);
        camera = new Camera();
        camera.init(this, view_finder);
        camera.setCameraListeren(new CameraListeren() {
            @Override
            public void onCameraError(int code, String error) {
                Log.e(TAG, "onCameraError:code = " + code + " error = " + error);
            }

            @Override
            public void onRecordError(int code, String error) {
                Log.e(TAG, "onRecordError:code = " + code + " error = " + error);
            }

            @Override
            public void onPreRecord() {
                Log.i(TAG, "onPreRecord");
            }

            @Override
            public void onResumeRecord() {
                Log.i(TAG, "onResumeRecord");
            }

            @Override
            public void onPauseRecord() {
                Log.i(TAG, "onPauseRecord");
            }

            @Override
            public void onRecordComplete(String videoPath) {
                Log.e(TAG, "onRecordComplete:videoPath = " + videoPath);
            }

            @Override
            public void onTakePicture() {
                Log.i(TAG, "onTakePicture");
            }

            @Override
            public void onTakeComplete(String image) {
                Log.e(TAG, "onTakeComplete:image = " + image);
            }
        });
    }

    boolean record = false;
    boolean pause = false;

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.capture_button) {
            if (!record) {
                record = true;
                VideoParam param = VideoParam.get(getFilePath() + ".mp4");
                param.fps = 30;
                param.bitRate = 10 * 1280 * 720;
                camera.startRecord(param);
                capture_button.setBackgroundResource(R.drawable.ic_shutter_activite);
            } else {
                record = false;
                camera.stopRecord();
                capture_button.setBackgroundResource(R.drawable.ic_shutter);
            }
        } else if (id == R.id.type_button) {
            if (null != camera) camera.takePicture(getFilePath() + ".jpeg");
        } else if (id == R.id.switch_button) {
            if (record) {
                Toast.makeText(this, "正在进行视频采集.", Toast.LENGTH_LONG).show();
                return;
            }
            if (null != camera) camera.switchCamera();
        } else if (id == R.id.pause_button) {
            if (!record) {
                Toast.makeText(this, "暂未开启采集.", Toast.LENGTH_LONG).show();
                return;
            }
            if (pause) {
                pause = false;
                camera.resumeRecord();
                Toast.makeText(this, "采集已恢复.", Toast.LENGTH_LONG).show();
            } else {
                pause = true;
                camera.pauseRecord();
                Toast.makeText(this, "采集已暂停.", Toast.LENGTH_LONG).show();
            }
        }
    }
}