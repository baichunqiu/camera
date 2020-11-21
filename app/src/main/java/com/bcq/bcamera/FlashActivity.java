package com.bcq.bcamera;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bcq.camera2.AutoFitTextureView;
import com.bcq.camera2.Camera;
import com.bcq.camera2.ICamera;
import com.bcq.camera2.CameraListeren;
import com.bcq.camera2.VideoParam;

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


    boolean record = false;
    Camera camera;
    private AutoFitTextureView view_finder;
    private View capture_button, type_button, switch_button;

    private String getFilePath() {
        final File dir = getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis();
    }

    int count = 0;

    void init() {
        setContentView(R.layout.activity_camera);
        view_finder = findViewById(R.id.view_finder);
        capture_button = findViewById(R.id.capture_button);
        type_button = findViewById(R.id.type_button);
        switch_button = findViewById(R.id.switch_button);
        capture_button.setOnClickListener(this);
        type_button.setOnClickListener(this);
        switch_button.setOnClickListener(this);
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
                Log.e(TAG, "onPreRecord");
            }

            @Override
            public void onResumeRecord() {
                Log.e(TAG, "onResumeRecord");
            }

            @Override
            public void onPauseRecord() {
                Log.e(TAG, "onPauseRecord");
            }

            @Override
            public void onRecordComplete(String videoPath) {
                Log.e(TAG, "onRecordComplete:videoPath = " + videoPath);
            }

            @Override
            public void onTakePicture() {
                Log.e(TAG, "onTakePicture");
            }

            @Override
            public void onTakeComplete(String image) {
                Log.e(TAG, "onTakeComplete:image = " + image);
            }
        });
    }

    boolean picture = true;

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.capture_button) {
            if (!record) {
                record = true;
                VideoParam param = new VideoParam();
                param.fps = 30;
                param.bitRate = 10 * 1280 * 720;
                param.filePath = getFilePath() + ".mp4";
                camera.startRecord(param);
                capture_button.setBackgroundResource(R.drawable.ic_shutter_activite);
            } else {
                record = false;
                camera.stopRecord();
                capture_button.setBackgroundResource(R.drawable.ic_shutter);
            }
        } else if (id == R.id.type_button) {
//            picture = !picture;
            if (null != camera) camera.takePicture(getFilePath() + ".jpeg");
        } else if (id == R.id.switch_button) {
            if (null != camera) camera.switchCamera();
        }
    }
}