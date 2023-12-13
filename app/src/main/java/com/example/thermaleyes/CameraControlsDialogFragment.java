package com.example.thermaleyes;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.thermaleyes.databinding.FragmentCameraControlsBinding;
import com.herohan.uvcapp.ICameraHelper;
import com.serenegiant.usb.UVCControl;
import com.warkiz.widget.IndicatorSeekBar;
import com.warkiz.widget.OnSeekChangeListener;
import com.warkiz.widget.SeekParams;

import java.lang.ref.WeakReference;

public class CameraControlsDialogFragment extends DialogFragment {

    private ImageFusion mImageFusion;

    private FragmentCameraControlsBinding mBinding;

    public CameraControlsDialogFragment(ImageFusion imageFusion) {
        mImageFusion = imageFusion;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //transparent background
        setStyle(STYLE_NO_TITLE, R.style.TransparentDialogFragment);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentCameraControlsBinding.inflate(getLayoutInflater(), container, false);
        setButtonListeners();
        return mBinding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        disableDimBehind();
        showCameraControls();
    }

    private void setButtonListeners() {
        mBinding.btnCameraControlsCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBinding.btnCameraControlsReset.setOnClickListener(v -> {

        });
    }

    /**
     * disable feature that everything behind this window will be dimmed.
     */
    private void disableDimBehind() {
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.0f;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(params);
    }

    private void showCameraControls() {
        AlgorithmConfig algoConfig = mImageFusion.getConfig();
        setAllControlParams(algoConfig);
        setAllControlChangeListener();
    }

    private void setAllControlParams(AlgorithmConfig algoConfig) {
        // Brightness
        setSeekBarParams(
                mBinding.isbHighFreq,
                true,
                new int[]{0, 3},
                algoConfig.highFreqRatio);

        // Power Line Frequency
        setRadioGroup(
                mBinding.rgColorTab,
                true,
                new int[]{AlgorithmConfig.PSEUDO_COLOR_TAB_PLASMA, AlgorithmConfig.PSEUDO_COLOR_TAB_JET},
                algoConfig.pseudoColorTab);
    }

    private void setAllControlChangeListener() {
        mBinding.isbHighFreq.setOnSeekChangeListener((MyOnSeekChangeListener) seekParams -> {
                AlgorithmConfig algoConfig = mImageFusion.getConfig();
                algoConfig.highFreqRatio = seekParams.progress;
                mImageFusion.setConfig(algoConfig);
            }
        );

        mBinding.rgColorTab.setOnCheckedChangeListener((group, checkedId) -> {
            AlgorithmConfig algoConfig = mImageFusion.getConfig();
            if (checkedId == R.id.rbPLASMA) {
                algoConfig.pseudoColorTab = AlgorithmConfig.PSEUDO_COLOR_TAB_PLASMA;
            } else {
                algoConfig.pseudoColorTab = AlgorithmConfig.PSEUDO_COLOR_TAB_JET;
            }
            mImageFusion.setConfig(algoConfig);
        });
    }

    private void resetAllControlParams(UVCControl control) {
        // Brightness
        control.resetBrightness();

        // Contrast
        control.resetContrast();
        // Contrast Auto
        control.resetContrastAuto();

        // Hue
        control.resetHue();
        // Hue Auto
        control.resetHueAuto();

        // Saturation
        control.resetSaturation();

        // Sharpness
        control.resetSharpness();

        // Gamma
        control.resetGamma();

        // White Balance
        control.resetWhiteBalance();
        // White Balance Auto
        control.resetWhiteBalanceAuto();

        // Backlight Compensation
        control.resetBacklightComp();

        // Gain
        control.resetGain();

        // Exposure Time
        control.resetExposureTimeAbsolute();
        // Auto-Exposure Mode
        control.resetAutoExposureMode();

        // Iris
        control.resetIrisAbsolute();

        // Focus
        control.resetFocusAbsolute();
        // Focus Auto
        control.resetFocusAuto();

        // Zoom
        control.resetZoomAbsolute();

        // Pan
        control.resetPanAbsolute();

        // Tilt
        control.resetTiltAbsolute();

        // Roll
        control.resetRollAbsolute();

        // Power Line Frequency
        control.resetPowerlineFrequency();
    }

    private void setSeekBarParams(IndicatorSeekBar seekBar, boolean isEnable, int[] limit, int value) {
        seekBar.setEnabled(isEnable);
        if (isEnable) {
            seekBar.setMax(limit[1]);
            seekBar.setMin(limit[0]);
            seekBar.setProgress(value);
        }
    }

    private void setCheckBoxParams(CheckBox checkBox, boolean isEnable, boolean isCheck) {
        checkBox.setEnabled(isEnable);
        if (isEnable) {
            checkBox.setChecked(isCheck);
        }
    }

    private void setRadioGroup(RadioGroup radioGroup, boolean isEnable, int[] limit, int value) {
        radioGroup.setEnabled(isEnable);
        if (isEnable) {
            switch (value) {
                case 1: // 50Hz
                    radioGroup.check(R.id.rbPLASMA);
                    break;
                case 2: // 60Hz
                    radioGroup.check(R.id.rbJet);
                    break;
            }
        }
    }

    interface MyOnSeekChangeListener extends OnSeekChangeListener {
        @Override
        default void onStartTrackingTouch(IndicatorSeekBar seekBar) {

        }

        @Override
        default void onStopTrackingTouch(IndicatorSeekBar seekBar) {

        }
    }
}
