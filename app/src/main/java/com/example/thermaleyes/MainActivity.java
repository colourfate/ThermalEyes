package com.example.thermaleyes;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.hjq.permissions.XXPermissions;
import com.serenegiant.opengl.renderer.MirrorMode;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final boolean CAM_DISPLAY = true;
    private static final boolean TEMP_DISPLAY = true;
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private ICameraHelper mCameraHelper;
    private AspectRatioSurfaceView mCameraViewMain;
    private ImageView mFrameCallbackPreview;
    private ImageView mThermalCameraPreview;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.entry_basic_preview);
        List<String> needPermissions = new ArrayList<>();
        needPermissions.add(Manifest.permission.CAMERA);

        XXPermissions.with(this)
                .permission(needPermissions)
                .request((permissions, all) -> {
                    if (!all) {
                        return;
                    }
                    initViews();
                });

        UsbManager usbManager = (UsbManager)getSystemService(USB_SERVICE);
        ThermalDevice mThermalDevice = new ThermalDevice(usbManager) {
            @Override
            public void onFrame(ByteBuffer frame) {
                Log.i(TAG, "Get temperature RGB frame");

                int[] argb = new int[frame.remaining() / 3];
                for (int i = 0; i < argb.length; i++) {
                    int red = frame.get();
                    int green = frame.get();
                    int blue = frame.get();

                    argb[i] = Color.argb(255, red, green, blue);
                }

                Bitmap originBm = Bitmap.createBitmap(
                        ThermalDevice.IMAGE_WIDTH, ThermalDevice.IMAGE_HEIGHT,
                        Bitmap.Config.ARGB_8888);
                originBm.setPixels(argb, 0, ThermalDevice.IMAGE_WIDTH,
                        0, 0, ThermalDevice.IMAGE_WIDTH,  ThermalDevice.IMAGE_HEIGHT);
                Matrix matrix = new Matrix();
                matrix.postScale(-20, 20);   // Scale to 640x480 and mirror
                Bitmap scaleBm = Bitmap.createBitmap(originBm,
                        0, 0, originBm.getWidth(), originBm.getHeight(), matrix, true);

                if (TEMP_DISPLAY) {
                    runOnUiThread(() -> {
                        mThermalCameraPreview.setImageBitmap(scaleBm);
                    });
                }
            }
        };

        try {
            mThermalDevice.connect();
        } catch (IOException e) {
            Log.e(TAG, "Usb connect failed");
        }
    }

    private void initViews() {
        mCameraViewMain = findViewById(R.id.svCameraViewMain);
        mCameraViewMain.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        mCameraViewMain.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });
        mFrameCallbackPreview = findViewById(R.id.ivFrameCallbackPreview);
        mThermalCameraPreview = findViewById(R.id.ivThermalCameraPreview);

        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        btnOpenCamera.setOnClickListener(this);
        Button btnCloseCamera = findViewById(R.id.btnCloseCamera);
        btnCloseCamera.setOnClickListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCameraHelper();
    }

    public void initCameraHelper() {
        if (CAM_DISPLAY) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void clearCameraHelper() {
        if (CAM_DISPLAY) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {
        if (CAM_DISPLAY) Log.v(TAG, "selectDevice:device=" + device.getDeviceName());
        mCameraHelper.selectDevice(device);
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        static final int CAMERA_VIP = 2316;

        @Override
        public void onAttach(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
            }
            if (CAM_DISPLAY) Log.v(TAG, "onAttach:");
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onDeviceOpen:");
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }

            if (CAM_DISPLAY) Log.v(TAG, "onCameraOpen:");
            mCameraHelper.startPreview();

            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                int width = size.width;
                int height = size.height;
                //auto aspect ratio
                mCameraViewMain.setAspectRatio(width, height);
            }

            mCameraHelper.addSurface(mCameraViewMain.getHolder().getSurface(), false);

            mCameraHelper.setFrameCallback(frame -> {
                int[] argb = new int[frame.remaining() / 4];
                for (int i = 0; i < argb.length; i++) {
                    int rgbx = frame.getInt();

                    argb[i] = Color.argb(255, (rgbx >> 24) & 0xff, (rgbx >> 16) & 0xff, (rgbx >> 8) & 0xff);
                }

                if (CAM_DISPLAY) {
                    Bitmap image = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);

                    image.setPixels(argb, 0, size.width, 0, 0, size.width, size.height);
                    runOnUiThread(() -> {
                        mFrameCallbackPreview.setImageBitmap(image);
                    });
                }
            }, UVCCamera.PIXEL_FORMAT_RGBX);
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onCameraClose:");
            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onCancel:");
        }

    };

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnOpenCamera) {
            // select a uvc device
            if (mCameraHelper != null) {
                final List<UsbDevice> list = mCameraHelper.getDeviceList();
                if (list != null && list.size() > 0) {
                    mCameraHelper.selectDevice(list.get(0));
                }
            }
        } else if (v.getId() == R.id.btnCloseCamera) {
            // close camera
            if (mCameraHelper != null) {
                mCameraHelper.closeCamera();
            }
        }
    }
}