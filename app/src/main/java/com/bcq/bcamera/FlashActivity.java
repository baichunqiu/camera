package com.bcq.bcamera;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bcq.camera2.AutoFitTextureView;
import com.bcq.camera2.Camera;
import com.bcq.camera2.ICamera;
import com.bcq.camera2.CameraListeren;
import com.bcq.camera2.VideoParam;

import java.io.File;

public class FlashActivity extends AppCompatActivity {
    private final static String TAG = "FlashActivity";
    private final static String[] CAMERA_AUDIO = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private AutoFitTextureView view_finder;
    private View capture_button;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtil.REQUEST_CODE == requestCode) {
            String[] arr = PermissionUtil.getDeniedPermissions(this, permissions);
            if (null == arr || arr.length == 0) {
                init();
            }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PermissionUtil.checkPermissions(this, CAMERA_AUDIO)) {
            init();
        }
    }

    boolean record = false;
    ICamera camera;

    private String getFilePath() {
        final File dir = getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis();
    }

    void init() {
//        startActivity(new Intent(this, CameraActivity.class));
        setContentView(R.layout.activity_camera);
        view_finder = findViewById(R.id.view_finder);
        capture_button = findViewById(R.id.capture_button);
        capture_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!record) {
                    record = true;
                    VideoParam param = new VideoParam();
                    param.fps = 30;
                    param.bitRate = 10 * 1280 * 720;
                    param.filePath = getFilePath() + ".mp4";
                    camera.startRecord(param);
                    capture_button.setSelected(true);
                } else {
                    record = false;
                    camera.stopRecord();
                    capture_button.setSelected(false);
                }
//                camera.takePicture(getFilePath() + ".jpeg");
            }
        });
//        camera = new Camera2();
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

}