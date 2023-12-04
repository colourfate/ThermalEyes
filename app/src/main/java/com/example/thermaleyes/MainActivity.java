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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

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
    private ImageView mFusionImagePreview;
    private TextView mMaxTempTestView, mMinTempTestView;

    private NV21ToBitmap mNv21ToBitmap;
    private final ImageFusion mImageFusion = new ImageFusion(DEFAULT_WIDTH, DEFAULT_HEIGHT,
            ThermalDevice.IMAGE_WIDTH, ThermalDevice.IMAGE_HEIGHT) {

        @Override
        public void onFrame(byte[] frame) {
            Bitmap srcBm = mNv21ToBitmap.nv21ToBitmap(frame, DEFAULT_WIDTH, DEFAULT_HEIGHT);

            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            float scaleTimes = (float)screenWidth / srcBm.getWidth();

            Matrix matrix = new Matrix();
            matrix.postScale(scaleTimes, scaleTimes);
            Bitmap scaleBm = Bitmap.createBitmap(srcBm,
                    0, 0, srcBm.getWidth(), srcBm.getHeight(), matrix, true);

            runOnUiThread(() -> {
                mFusionImagePreview.setImageBitmap(scaleBm);
            });
        }
    };

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

        mNv21ToBitmap = new NV21ToBitmap(this);
        UsbManager usbManager = (UsbManager)getSystemService(USB_SERVICE);
        ThermalDevice thermalDevice = new ThermalDevice(usbManager) {
            @Override
            public void onFrame(ByteBuffer frame, float maxTemp, float minTemp) {
                Log.i(TAG, "Get temperature frame");

                byte[] therm_data = new byte[frame.remaining()];
                frame.get(therm_data);
                mImageFusion.putThermalImage(therm_data);

                if (TEMP_DISPLAY) {
                    runOnUiThread(() -> {
                        mMaxTempTestView.setText(String.format("max: %.2f", maxTemp));
                        mMinTempTestView.setText(String.format("min: %.2f", minTemp));
//                        mThermalCameraPreview.setImageBitmap(scaleBm);
                    });
                }
            }
        };

        try {
            thermalDevice.connect();
        } catch (IOException e) {
            Log.e(TAG, "Usb connect failed");
        }
    }

    private void initViews() {
        mFusionImagePreview = findViewById(R.id.ivFusionImagePreview);
        mMaxTempTestView = findViewById(R.id.tvMaxTemperature);
        mMinTempTestView = findViewById(R.id.tvMinTemperature);

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
        mImageFusion.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCameraHelper();
        mImageFusion.exit();
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
            mCameraHelper.setFrameCallback(frame -> {
                byte[] nv21 = new byte[frame.remaining()];
                frame.get(nv21,0,nv21.length);
                mImageFusion.putCameraImage(nv21);

                if (CAM_DISPLAY) {
//                    Bitmap bitmap = mNv21ToBitmap.nv21ToBitmap(nv21, size.width, size.height);
//                    runOnUiThread(() -> {
//                        mFrameCallbackPreview.setImageBitmap(bitmap);
//                    });
                }
            }, UVCCamera.PIXEL_FORMAT_NV21);
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onCameraClose:");
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