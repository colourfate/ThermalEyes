package com.example.thermaleyes;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

    private final static float MAX_RANGE = 20.0f;
    private UsbManager myUsbManager;

    private final UsbSerialInterface.UsbReadCallback mCallback =
            arg0 -> {
                Log.i(TAG, "Get data from device, len: " + arg0.length);

                ThermalDataPacket packet = new ThermalDataPacket(arg0);
                if (packet.isInvalid() || packet.getType() != ThermalDataPacket.TYPE_DATA) {
                    Log.e(TAG, "Receive invalid data, Header: " + packet.getHeader() +
                            ", Type: " + packet.getType() + ", len: " + packet.getLength());
                    return;
                }

                Log.i(TAG, "Receive invalid data, Header: " + packet.getHeader() +
                        ", Type: " + packet.getType() + ", len: " + packet.getLength());
                float[] tempData = getTemperatureData(packet.getData());

                float maxTemp = tempData[0];
                float minTemp = tempData[0];
                int maxLoc = 0;
                int minLoc = 0;
                for (int i = 0; i < tempData.length; i++) {
                    if (maxTemp < tempData[i]) {
                        maxTemp = tempData[i];
                        maxLoc = i;
                    }
                    if (minTemp > tempData[i]) {
                        minTemp = tempData[i];
                        minLoc = i;
                    }
                }
                Log.i(TAG, "Max: " + maxTemp + " Min: " + minTemp);

                ByteBuffer tempImage = getTemperatureImage(tempData, maxTemp, minTemp);
                onFrame(tempImage, maxTemp, minTemp, maxLoc, minLoc);
            };

    public abstract void onFrame(ByteBuffer frame, float maxTemp, float minTemp, int maxLoc, int minLoc);

    public void setUsbManager(UsbManager usbManager) {
        myUsbManager = usbManager;
    }

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

        Log.i(TAG, "data length: " + thermalData.length);
        for (int i = 0; i < tempData.length; i++) {
            int lowByte = Byte.toUnsignedInt(thermalData[2 * i]);
            int highByte = Byte.toUnsignedInt(thermalData[2 * i + 1]);

            tempData[i] = ((short)(highByte << 8 | lowByte)) / 100.0f;
        }

        return tempData;
    }

    private ByteBuffer getTemperatureImage(float[] tempMatrix, float maxTemp, float minTemp) {
        /* Only luminance */
        ByteBuffer buffer = ByteBuffer.allocate(tempMatrix.length);
        float range = Math.max(maxTemp - minTemp, MAX_RANGE);

        for (float tmp : tempMatrix) {
            int gray = (int) ((tmp - minTemp) / range * 255);
            buffer.put((byte) gray);
        }

        buffer.position(0);
        return buffer;
    }

    private class ThermalDataPacket {
        public static final int TYPE_CMD_FPS = 0;
        public static final int TYPE_DATA = 1;

        private byte[] mData;

        ThermalDataPacket(byte[] data) {
            mData = data;
        }

        public boolean isInvalid() {
            return getHeader() != 0x8000 || getLength() <= 6 || (getLength() + 6 != mData.length);
        }

        public int getHeader() {
            return Byte.toUnsignedInt(mData[0]) | Byte.toUnsignedInt(mData[1]) << 8;
        }

        public int getType() {
            return Byte.toUnsignedInt(mData[2]) | Byte.toUnsignedInt(mData[3]) << 8;
        }

        public int getLength() {
            return Byte.toUnsignedInt(mData[4]) | Byte.toUnsignedInt(mData[5]) << 8;
        }

        public byte[] getData() {
            return Arrays.copyOfRange(mData, 6, 6 + getLength());
        }
    }
}
