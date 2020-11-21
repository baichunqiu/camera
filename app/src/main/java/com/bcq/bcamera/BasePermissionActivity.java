package com.bcq.bcamera;

import android.Manifest;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BasePermissionActivity extends AppCompatActivity {
    private final String TAG = getClass().getSimpleName();


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtil.REQUEST_CODE == requestCode) {
            String[] arr = PermissionUtil.getDeniedPermissions(this, permissions);
            boolean accept = null == arr || 0 == arr.length;
            onPermissionAccept(accept);
        }

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PermissionUtil.checkPermissions(this, onCheckPermission())) {
            onPermissionAccept(true);
        }
    }


    /**
     * 设置检测权限的数组
     *
     * @return
     */
    protected abstract String[] onCheckPermission();

    /**
     * 权限检测结果
     *
     * @param accept
     */
    protected abstract void onPermissionAccept(boolean accept);
}