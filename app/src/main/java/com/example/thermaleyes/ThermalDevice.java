package com.example.thermaleyes;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.utils.ProtocolBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

@RequiresApi(api = Build.VERSION_CODES.O)
public abstract class ThermalDevice {
    public final static int IMAGE_WIDTH = 32;
    public final static int IMAGE_HEIGHT = 24;

    private final static String TAG = ThermalDevice.class.getSimpleName();
    private final static int VENDOR_ID = 1155;
    private final static int PRODUCT_ID = 22336;
    private final static int IMAGE_PIXEL = IMAGE_WIDTH * IMAGE_HEIGHT;
    private final static int DATA_LEN = IMAGE_PIXEL * 2 + 2;
    private final UsbManager myUsbManager;
    private final UsbSerialInterface.UsbReadCallback mCallback =
            arg0 -> {
                Log.i(TAG, "Get data from device");

                if (arg0.length != DATA_LEN ||
                        arg0[arg0.length - 2] != -128 || arg0[arg0.length - 1] != 0) {
                    //Log.e(TAG, "Receive invalid data, len: " + arg0.length + " EOF: " +
                    //        Integer.toHexString(arg0[arg0.length - 2]) + ", " +
                    //        Integer.toHexString(arg0[arg0.length - 1]));
                    Log.e(TAG, "Receive invalid data");
                    return;
                }

                float[] tempData = getTemperatureData(arg0);
                ByteBuffer tempImage = getTemperatureImage(tempData);
                onFrame(tempImage);
            };

    public ThermalDevice(UsbManager usbManager) {
        this.myUsbManager = usbManager;
    }

    /* RGB888 temperature image */
    public abstract void onFrame(ByteBuffer frame);

    public void connect() throws IOException {
        UsbDevice device = enumerateDevice(VENDOR_ID, PRODUCT_ID);
        if (device == null) {
            throw new IOException("Not find device");
        }

        UsbDeviceConnection deviceConnection = openDevice(device);
        if (deviceConnection == null) {
            throw new IOException("Connect device failed");
        }

        UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(device, deviceConnection);
        if (serial == null) {
            Log.e(TAG, "createUsbSerialDevice failed");
            throw new IOException("Not support terminal");
        }

        Log.i(TAG, "Create CDC devices success");
        // No physical serial port, no need to set parameters
        serial.open();
        serial.read(mCallback);
    }

    private UsbDevice enumerateDevice(int vendorId, int productId) {
        if (myUsbManager == null) {
            Log.e(TAG, "Class not instantiate");
            return null;
        }

        HashMap<String, UsbDevice> deviceList = myUsbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            Log.e(TAG, "Not usb device connect");
            return null;
        }

        for (UsbDevice device : deviceList.values()) {
            Log.i(TAG, "DeviceInfo: " + device.getVendorId() + " , "
                    + device.getProductId());

            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                Log.i(TAG, "enumerate device success");
                return device;
            }
        }

        return null;
    }

    private UsbDeviceConnection openDevice(UsbDevice device) {
        // specified by AndroidManifest.xml and res/xml/device_filter.xml
        if (myUsbManager.hasPermission(device)) {
            return myUsbManager.openDevice(device);
        } else {
            Log.e(TAG, "openDevice: Not permission vid: " + device.getVendorId());
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private float[] getTemperatureData(byte[] thermalData) {
        float[] tempData = new float[IMAGE_PIXEL];

        for (int i = 0; i < tempData.length; i++) {
            int highByte = Byte.toUnsignedInt(thermalData[2 * i]);
            int lowByte = Byte.toUnsignedInt(thermalData[2 * i + 1]);

            tempData[i] = ((short)(highByte << 8 | lowByte)) / 100.0f;
        }

        Log.i(TAG, Integer.toHexString(thermalData[0]) + " " +
                Integer.toHexString(thermalData[1]) + " --> " + tempData[0]);
        return tempData;
    }

    private ByteBuffer getTemperatureImage(float[] tempMatrix) {
        float maxTemp = tempMatrix[0];
        float minTemp = tempMatrix[0];
        for (float t : tempMatrix) {
            maxTemp = Math.max(maxTemp, t);
            minTemp = Math.min(minTemp, t);
        }

        /* ARGB8888 */
        ByteBuffer buffer = ByteBuffer.allocate(tempMatrix.length * 4);
        for (float tmp : tempMatrix) {
            int gray = (int) ((tmp - minTemp) / (maxTemp - minTemp) * 255);
            int red = Math.abs(0 - gray);
            int green = Math.abs(127 - gray);
            int blue = Math.abs(255 - gray);
            buffer.put((byte) (-1));
            buffer.put((byte) red);
            buffer.put((byte) green);
            buffer.put((byte) blue);
        }
        Log.e(TAG, "Max: " + maxTemp + " Min: " + minTemp);

        buffer.position(0);
        return buffer;
    }
}
