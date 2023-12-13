package com.example.thermaleyes;

import android.graphics.Point;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.LinkedList;
import java.util.Map;

public abstract class ImageFusion extends Thread {
    private static final int QUEUE_LEN = 2;
    private volatile boolean mThreadRun = true;
    private final LinkedList<FrameInfo> mCameraQueue = new LinkedList<>();
    private final LinkedList<FrameInfo> mThermalQueue = new LinkedList<>();
    private static final String TAG = ImageFusion.class.getSimpleName();
    private final int mCamWidth;
    private final int mCamHeight;
    private final int mThermWidth;
    private final int mThermHeight;

    // NV21
    public abstract void onFrame(FrameInfo frame);

    public ImageFusion(int camWidth, int camHeight, int thermWidth, int thermHeight) {
        mCamWidth = camWidth;
        mCamHeight = camHeight;
        mThermWidth = thermWidth;
        mThermHeight = thermHeight;
    }

    public void putCameraImage(FrameInfo frame) {
        if (frame.data.length != mCamWidth * mCamHeight * 3 / 2) {
            Log.e(TAG, "Invalid camera frame length: " + frame.data.length);
            return;
        }

        synchronized(mCameraQueue) {
            putImage(mCameraQueue, frame);
        }
    }

    public void putThermalImage(FrameInfo frame) {
        if (frame.data.length != mThermWidth * mThermHeight) {
            Log.e(TAG, "Invalid thermal frame length: " + frame.data.length);
            return;
        }

        synchronized(mThermalQueue) {
            putImage(mThermalQueue, frame);
        }
    }

    public void setConfig(AlgorithmConfig config) {
        setFusionMode(config.fusionMode);               // RadioButton
        setFusionHighFreqRatio(config.highFreqRatio);   // SeekBar
        setFusionColorTab(config.pseudoColorTab);       // RadioButton
        setFusionParallaxOffset(config.parallaxOffset); // SeekBar

        float[] paramTab = { config.camYK, config.camUVK, config.thermYK, config.thermUVK };
        setFusionParams(paramTab);                      // SeekBar
    }

    public AlgorithmConfig getConfig() {
        AlgorithmConfig config = new AlgorithmConfig();

        config.fusionMode = getFusionMode();
        config.highFreqRatio = getFusionHighFreqRatio();
        config.pseudoColorTab = getFusionColorTab();
        config.parallaxOffset = getFusionParallaxOffset();

        float[] paramTab = new float[4];
        getFusionParams(paramTab);
        config.camYK = paramTab[0];
        config.camUVK = paramTab[1];
        config.thermYK = paramTab[2];
        config.thermUVK = paramTab[3];

        return config;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void run() {
        FrameInfo cameraFrame, thermalFrame;

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

            FrameInfo fusionFrame = new FrameInfo();
            fusionFrame.data = new byte[cameraFrame.data.length];
            fusionFrame.width = cameraFrame.width;
            fusionFrame.height = cameraFrame.height;
            fusionFrame.maxVal = thermalFrame.maxVal;
            fusionFrame.minVal = thermalFrame.minVal;
            float xScale = (float)cameraFrame.width / thermalFrame.width;
            float yScale = (float)cameraFrame.height / thermalFrame.height;
            fusionFrame.maxLoc = new Point((int)(thermalFrame.maxLoc.x * xScale), (int)(thermalFrame.maxLoc.y * yScale) - 15);
            fusionFrame.minLoc = new Point((int)(thermalFrame.minLoc.x * xScale), (int)(thermalFrame.minLoc.y * yScale) - 15);

            getFusionImage(fusionFrame.data, cameraFrame.data, thermalFrame.data,
                    mCamWidth, mCamHeight, mThermWidth, mThermHeight);

            onFrame(fusionFrame);
        }
    }

    public void exit() {
        mThreadRun = false;
        //mThermalQueue.notifyAll();
        //mCameraQueue.notifyAll();
    }

    private void putImage(LinkedList<FrameInfo> queue, FrameInfo frame) {
        if (queue.size() >= QUEUE_LEN) {
            queue.poll();
        }
        queue.offer(frame);
        queue.notifyAll();
    }

    private FrameInfo getImage(LinkedList<FrameInfo> queue) {
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
    private native void setFusionMode(int fusionMode);
    private native int getFusionMode();
    private native void setFusionHighFreqRatio(int highFreqRatio);
    private native int getFusionHighFreqRatio();
    private native void setFusionColorTab(int colorTab);
    private native int getFusionColorTab();
    private native void setFusionParallaxOffset(int offset);
    private native int getFusionParallaxOffset();
    private native void setFusionParams(float[] params);
    private native void getFusionParams(float[] params);
}
