package com.example.thermaleyes;

import android.os.Build;
import android.util.Log;
import android.util.Size;

import androidx.annotation.RequiresApi;

import java.util.LinkedList;

public abstract class ImageFusion extends Thread {
    private static final int QUEUE_LEN = 2;
    private volatile boolean mThreadRun = true;
    private final LinkedList<byte[]> mCameraQueue = new LinkedList<>();
    private final LinkedList<byte[]> mThermalQueue = new LinkedList<>();
    private static final String TAG = ImageFusion.class.getSimpleName();
    private final int mCamWidth;
    private final int mCamHeight;
    private final int mThermWidth;
    private final int mThermHeight;

    // NV21
    public abstract void onFrame(byte[] argbFrame);

    public ImageFusion(int camWidth, int camHeight, int thermWidth, int thermHeight) {
        mCamWidth = camWidth;
        mCamHeight = camHeight;
        mThermWidth = thermWidth;
        mThermHeight = thermHeight;
    }

    public void putCameraImage(byte[] frame) {
        if (frame.length != mCamWidth * mCamHeight * 3 / 2) {
            Log.e(TAG, "Invalid camera frame length");
            return;
        }

        synchronized(mCameraQueue) {
            putImage(mCameraQueue, frame);
        }
    }

    public void putThermalImage(byte[] frame) {
        if (frame.length != mThermWidth * mThermHeight) {
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

            byte[] fusionData = new byte[cameraFrame.length];
            getFusionImage(fusionData, cameraFrame, thermalFrame,
                    mCamWidth, mCamHeight, mThermWidth, mThermHeight);

            onFrame(fusionData);
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

    static {
        System.loadLibrary("img_algo");
    }

    private native void getFusionImage(byte[] fusionData, byte[] camData, byte[] thermData,
                                       int camWidth, int camHeight, int thermWidth, int thermHeight);
}
