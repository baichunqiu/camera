package com.bcq.camera2;

import android.media.Image;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageSaver implements Runnable {
    private Image mImage;
    private String imagePath;

    public ImageSaver() {
    }

    public ImageSaver(Image image, String imagePath) {
        this.imagePath = imagePath;
        this.mImage = image;
    }

    public void setImage(Image image) {
        this.mImage = image;
    }

    public void setImagePath(String path) {
        this.imagePath = path;
    }

    /**
     * 清理任务数据
     */
    public void clear() {
        imagePath = "";
        mImage = null;
    }

    /**
     * 判断任务是否可用
     *
     * @return
     */
    public boolean available() {
        return !TextUtils.isEmpty(imagePath) && null != mImage;
    }

    @Override
    public void run() {
        if (!available()) {
            return;
        }
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(imagePath);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mImage.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}