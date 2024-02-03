package com.example.thermaleyes;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.hjq.permissions.XXPermissions;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final boolean CAM_DISPLAY = true;
    private static final boolean TEMP_DISPLAY = true;
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private ICameraHelper mCameraHelper;
    private ImageView mFusionImagePreview;
    private TextView mMaxTempTestView, mMinTempTestView, mConnectTextView;

    private NV21ToBitmap mNv21ToBitmap;
    private ParameterDialogFragment mControlsDialog;
    private boolean mIsCameraConnected = false;
    private ImageFusion mImageFusion;

    private final ThermalDevice mThermalDevice = new ThermalDevice() {
        @Override
        public void onFrame(ByteBuffer frame, float maxTemp, float minTemp, int maxLoc, int minLoc) {
            FrameInfo thermInfo = new FrameInfo();
            thermInfo.data = new byte[frame.remaining()];
            thermInfo.width = ThermalDevice.IMAGE_WIDTH;
            thermInfo.height = ThermalDevice.IMAGE_HEIGHT;
            thermInfo.maxVal = maxTemp;
            thermInfo.minVal = minTemp;
            /* Thermal location need to mirror */
            thermInfo.maxLoc = new Point(ThermalDevice.IMAGE_WIDTH - maxLoc % ThermalDevice.IMAGE_WIDTH,
                    maxLoc / ThermalDevice.IMAGE_WIDTH);
            thermInfo.minLoc = new Point(ThermalDevice.IMAGE_WIDTH - minLoc % ThermalDevice.IMAGE_WIDTH,
                    minLoc / ThermalDevice.IMAGE_WIDTH);
            frame.get(thermInfo.data);

            mImageFusion.putThermalImage(thermInfo);

            if (TEMP_DISPLAY) {
                runOnUiThread(() -> {
                    mMaxTempTestView.setText(String.format("Max: %.2f", maxTemp));
                    mMinTempTestView.setText(String.format("Min: %.2f", minTemp));
                });
            }
        }
    };

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
        mThermalDevice.setUsbManager((UsbManager)getSystemService(USB_SERVICE));
    }

    private void drawTemptationTrack(Bitmap bitmap, FrameInfo frame) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        canvas.drawBitmap(bitmap, new Matrix(), paint);

        float xScale = (float)bitmap.getWidth() / frame.width;
        float yScale = (float)bitmap.getHeight() / frame.height;

        Point[] tempCoordinate = { frame.maxLoc, frame.minLoc };
        for (int i = 0; i < tempCoordinate.length; i++) {
            int x = (int)(tempCoordinate[i].x * xScale);
            int y = (int)(tempCoordinate[i].y * yScale);
            int unit = bitmap.getWidth() / ThermalDevice.IMAGE_WIDTH;
            Rect rect = new Rect(x - unit / 2, y - unit / 2, x + unit / 2,
                    y + unit / 2);

            if (i == 0) {
                paint.setColor(Color.RED);
            } else {
                paint.setColor(Color.BLUE);
            }
            canvas.drawRect(rect, paint);
        }
    }

    private void initViews() {
        mFusionImagePreview = findViewById(R.id.ivFusionImagePreview);
        mMaxTempTestView = findViewById(R.id.tvMaxTemperature);
        mMinTempTestView = findViewById(R.id.tvMinTemperature);
        mConnectTextView = findViewById(R.id.tvConnectHip);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
        initThermalDevice();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCameraHelper();
        mImageFusion.exit();
    }

    private void initCameraHelper() {
        if (CAM_DISPLAY) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void initThermalDevice() {
        if (mImageFusion != null) {
            return;
        }

        mImageFusion = new ImageFusion(DEFAULT_WIDTH, DEFAULT_HEIGHT,
                ThermalDevice.IMAGE_WIDTH, ThermalDevice.IMAGE_HEIGHT) {

            @Override
            public void onFrame(FrameInfo frame) {
                Bitmap srcBm = mNv21ToBitmap.nv21ToBitmap(frame.data, DEFAULT_WIDTH, DEFAULT_HEIGHT);

                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                int screenWidth = dm.widthPixels;
                float scaleTimes = (float)screenWidth / srcBm.getWidth();

                Matrix matrix = new Matrix();
                matrix.postScale(scaleTimes, scaleTimes);
                Bitmap scaleBm = Bitmap.createBitmap(srcBm,
                        0, 0, srcBm.getWidth(), srcBm.getHeight(), matrix, true);

                drawTemptationTrack(scaleBm, frame);
                mIsCameraConnected = true;

                runOnUiThread(() -> {
                    mFusionImagePreview.setImageBitmap(scaleBm);
                    invalidateOptionsMenu();
                });
            }
        };
        mImageFusion.start();
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
                Log.i(TAG, "onAttach: Not support device: " + device.getVendorId());
                return;
            }

            if (CAM_DISPLAY) Log.v(TAG, "onAttach:");
            selectDevice(device);
            mFusionImagePreview.setVisibility(View.VISIBLE);
            mConnectTextView.setVisibility(View.GONE);
            mMaxTempTestView.setVisibility(View.VISIBLE);
            mMinTempTestView.setVisibility(View.VISIBLE);

            try {
                mThermalDevice.connect();
            } catch (IOException e) {
                Log.e(TAG, "Usb connect failed");
                return;
            }
            mThermalDevice.setFPS(ThermalDevice.FPS_8);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "onDeviceOpen: Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onDeviceOpen:");
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "onCameraOpen: Not support device: " + device.getVendorId());
                return;
            }

            if (CAM_DISPLAY) Log.v(TAG, "onCameraOpen:");
            mCameraHelper.startPreview();

            Size size = mCameraHelper.getPreviewSize();
            mCameraHelper.setFrameCallback(frame -> {
                FrameInfo frameInfo = new FrameInfo();
                frameInfo.data = new byte[frame.remaining()];
                frameInfo.width = size.width;
                frameInfo.height = size.height;

                frame.get(frameInfo.data);
                mImageFusion.putCameraImage(frameInfo);

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
                Log.i(TAG, "onCameraClose: Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onCameraClose:");
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "onDevicesClose: Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "onDetach: Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onDetach:");
            mFusionImagePreview.setVisibility(View.GONE);
            mConnectTextView.setVisibility(View.VISIBLE);
            mMaxTempTestView.setVisibility(View.GONE);
            mMinTempTestView.setVisibility(View.GONE);
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (device.getVendorId() != CAMERA_VIP) {
                Log.i(TAG, "onCancel: Not support device: " + device.getVendorId());
                return;
            }
            if (CAM_DISPLAY) Log.v(TAG, "onCancel:");
        }

    };

    @Override
    public void onClick(View v) { }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_control) {
            showCameraControlsDialog();
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mIsCameraConnected) {
            menu.findItem(R.id.action_control).setVisible(true);
        } else {
            menu.findItem(R.id.action_control).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void showCameraControlsDialog() {
        if (mControlsDialog == null) {
            mControlsDialog = new ParameterDialogFragment(mImageFusion, mThermalDevice);
        }
        // When DialogFragment is not showing
        if (!mControlsDialog.isAdded()) {
            mControlsDialog.show(getSupportFragmentManager(), "camera_controls");
        }
    }
}