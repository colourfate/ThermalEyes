package com.example.thermaleyes;

import java.nio.ByteBuffer;

public interface ThermalCallback {
    /* When obtaining a frame call, the data is ARGB format */
    void onFrame(ByteBuffer frame);
}
