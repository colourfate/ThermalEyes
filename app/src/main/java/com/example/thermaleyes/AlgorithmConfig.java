package com.example.thermaleyes;

public class AlgorithmConfig {
    public static final int FUSION_MODE_COLOR_MAP = 0;
    public static final int FUSION_MODE_HIGH_FREQ_EXTRACT = 1;

    public static final int HIGH_FREQ_RATIO_LOW = 0;
    public static final int HIGH_FREQ_RATIO_MEDIUM = 1;
    public static final int HIGH_FREQ_RATIO_HIGH = 2;

    public static final int PSEUDO_COLOR_TAB_JET = 2;
    public static final int PSEUDO_COLOR_TAB_PLASMA = 15;

    public int fusionMode;
    public int highFreqRatio;
    public int pseudoColorTab;
    public int parallaxOffset;
    public float camYK;
    public float camUVK;
    public float thermYK;
    public float thermUVK;
}
