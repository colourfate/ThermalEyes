package com.example.thermaleyes;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.thermaleyes.databinding.FragmentParamterBinding;
import com.warkiz.widget.IndicatorSeekBar;
import com.warkiz.widget.OnSeekChangeListener;

public class ParameterDialogFragment extends DialogFragment {

    private final ImageFusion mImageFusion;
    private final ThermalDevice mThermalDevice;

    private FragmentParamterBinding mBinding;
    private static final String TAG = ParameterDialogFragment.class.getSimpleName();

    public ParameterDialogFragment(ImageFusion imageFusion, ThermalDevice thermalDevice) {
        mImageFusion = imageFusion;
        mThermalDevice = thermalDevice;
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
        mBinding = FragmentParamterBinding.inflate(getLayoutInflater(), container, false);
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
            resetAllControlParams();
            setAllControlParams();
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
        setAllControlParams();
        setAllControlChangeListener();
    }

    private void setAllControlParams() {
        setSeekBarParams(
                mBinding.isbHighFreq,
                true,
                new int[]{0, 3},
                mImageFusion.getHighFreqRatio());

        setSeekBarParams(
                mBinding.isbParallaxOffset,
                true,
                new int[]{0, 30},
                mImageFusion.getParallaxOffset());

        setRadioGroup(mBinding.rgColorTab,
                true,
                new int[]{ ImageFusion.PSEUDO_COLOR_TAB_PLASMA, ImageFusion.PSEUDO_COLOR_TAB_JET },
                mImageFusion.getColorTab());

        setRadioGroup(
                mBinding.rgFusionMode,
                true,
                new int[]{ ImageFusion.FUSION_MODE_COLOR_MAP, ImageFusion.FUSION_MODE_HIGH_FREQ_EXTRACT },
                mImageFusion.getMode());

        setRadioGroup(
                mBinding.rgThermalFPS,
                true,
                new int[]{ ThermalDevice.FPS_4, ThermalDevice.FPS_8},
                mThermalDevice.getFPS());
    }

    private void setAllControlChangeListener() {
        mBinding.isbHighFreq.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> mImageFusion.setHighFreqRatio(seekParams.progress));

        mBinding.isbParallaxOffset.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> mImageFusion.setParallaxOffset(seekParams.progress)
        );

        mBinding.rgColorTab.setOnCheckedChangeListener((group, checkedId) -> {
            int colorTab;
            if (checkedId == R.id.rbPLASMA) {
                colorTab = ImageFusion.PSEUDO_COLOR_TAB_PLASMA;
            } else {
                colorTab = ImageFusion.PSEUDO_COLOR_TAB_JET;
            }
            mImageFusion.setColorTab(colorTab);
        });

        mBinding.rgFusionMode.setOnCheckedChangeListener((group, checkedId) -> {
            int fusionMode;
            if (checkedId == R.id.rbColorMap) {
                fusionMode = ImageFusion.FUSION_MODE_COLOR_MAP;
            } else {
                fusionMode = ImageFusion.FUSION_MODE_HIGH_FREQ_EXTRACT;
            }
            mImageFusion.setMode(fusionMode);
        });

        mBinding.rgThermalFPS.setOnCheckedChangeListener((group, checkedId) -> {
            int fps;
            if (checkedId == R.id.rb8Hz) {
                fps = ThermalDevice.FPS_4;
            } else {
                fps = ThermalDevice.FPS_8;
            }
            mThermalDevice.setFPS(fps);
        });
    }

    private void resetAllControlParams() {

    }

    private void setSeekBarParams(IndicatorSeekBar seekBar, boolean isEnable, int[] limit, int value) {
        seekBar.setEnabled(isEnable);
        if (isEnable) {
            seekBar.setMax(limit[1]);
            seekBar.setMin(limit[0]);
            seekBar.setProgress(value);
        }
    }

    private void setRadioGroup(RadioGroup radioGroup, boolean isEnable, int[] limit, int value) {
        radioGroup.setEnabled(isEnable);
        if (isEnable) {
            for (int i = 0; i < limit.length; i++) {
                if (limit[i] == value) {
                    RadioButton rb = (RadioButton)radioGroup.getChildAt(i);
                    rb.setChecked(true);
                }
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
