package com.example.thermaleyes;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.IOException;
import java.util.HashMap;

public class ThermalDevice {
    private static final String TAG = ThermalDevice.class.getSimpleName();
    private final static int VENDOR_ID = 1155;
    private final static int PRODUCT_ID = 22336;
    private final UsbManager myUsbManager;
    private final UsbSerialInterface.UsbReadCallback mCallback;

    public ThermalDevice(UsbManager usbManager) {
        this.myUsbManager = usbManager;
        this.mCallback = new UsbSerialInterface.UsbReadCallback() {
            @Override
            public void onReceivedData(byte[] arg0) {
                Log.e(TAG, "Get data from device");
            }
        };
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
            Log.d(TAG, "DeviceInfo: " + device.getVendorId() + " , "
                    + device.getProductId());

            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                Log.d(TAG, "enumerate device success");
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
            Log.e(TAG, "openDevice: Not permission");
            return null;
        }
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
}
