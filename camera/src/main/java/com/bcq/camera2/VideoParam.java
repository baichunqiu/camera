package com.bcq.camera2;

import android.text.TextUtils;

public class VideoParam {
    public String filePath;
    public int bitRate = 30 * 1280 * 720;
    public int fps = 30;

    public boolean available() {
        return !TextUtils.isEmpty(filePath) && bitRate > 0 && fps > 0;
    }
}
