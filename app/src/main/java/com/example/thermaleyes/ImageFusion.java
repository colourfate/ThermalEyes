package com.example.thermaleyes;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

public abstract class ImageFusion extends Thread {
    private static final int QUEUE_LEN = 2;
    private volatile boolean mThreadRun = true;
    private final LinkedList<byte[]> mCameraQueue = new LinkedList<>();
    private final LinkedList<byte[]> mThermalQueue = new LinkedList<>();
    private static final String TAG = ImageFusion.class.getSimpleName();
    private final int mWidth;
    private final int mHeight;

    // NV21
    public abstract void onFrame(byte[] argbFrame);

    public ImageFusion(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void putCameraImage(byte[] frame) {
        if (frame.length != mWidth * mHeight * 3 / 2) {
            Log.e(TAG, "Invalid camera frame length");
            return;
        }

        synchronized(mCameraQueue) {
            putImage(mCameraQueue, frame);
        }
    }

    public void putThermalImage(byte[] frame) {
        if (frame.length != mWidth * mHeight) {
            Log.e(TAG, "Invalid thermal frame length");
            return;
        }

        synchronized(mThermalQueue) {
            putImage(mThermalQueue, frame);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void run() {
        byte[] cameraFrame, thermalFrame;

        synchronized(mThermalQueue) {
            thermalFrame = getImage(mThermalQueue);
        }

        while (mThreadRun) {
            synchronized(mCameraQueue) {
                cameraFrame = getImage(mCameraQueue);
            }

            if (mThermalQueue.size() != 0) {
                synchronized(mThermalQueue) {
                    thermalFrame = mThermalQueue.poll();
                }
            }

            Log.e(TAG, "get camera data: " + Integer.toHexString(cameraFrame[0]));
            Log.e(TAG, "get thermal data: " + Integer.toHexString(thermalFrame[0]));

            byte[] fusionFrame = calculate(cameraFrame, thermalFrame, mWidth, mHeight);
            onFrame(fusionFrame);
        }
    }

    public void exit() {
        mThreadRun = false;
        //mThermalQueue.notifyAll();
        //mCameraQueue.notifyAll();
    }

    private void putImage(LinkedList<byte[]> queue, byte[] frame) {
        if (queue.size() >= QUEUE_LEN) {
            queue.poll();
        }
        queue.offer(frame);
        queue.notifyAll();
    }

    private byte[] getImage(LinkedList<byte[]> queue) {
        while (queue.size() == 0) {
            try {
                queue.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Camera Queue wait interrupt");
            }
        }

        return queue.poll();
    }

    private byte[] calculate(byte[] camData, byte[] thermData, int width, int height) {
        byte[] fusionData = new byte[camData.length];
        getFusionImage(fusionData, camData, thermData, width, height);
        return fusionData;
    }

    static {
        System.loadLibrary("img_algo");
    }

    private native void getFusionImage(byte[] fusionData, byte[] camData, byte[] thermData, int width, int height);
}
