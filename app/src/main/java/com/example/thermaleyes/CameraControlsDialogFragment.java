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
import com.warkiz.widget.IndicatorSeekBar;
import com.warkiz.widget.OnSeekChangeListener;

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
            resetAllControlParams(mImageFusion);
            setAllControlParams(mImageFusion);
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
        setAllControlParams(mImageFusion);
        setAllControlChangeListener();
    }

    private void setAllControlParams(ImageFusion imageFusion) {
        setSeekBarParams(
                mBinding.isbHighFreq,
                true,
                new int[]{0, 3},
                imageFusion.getHighFreqRatio());

        setSeekBarParams(
                mBinding.isbParallaxOffset,
                true,
                new int[]{0, 30},
                imageFusion.getParallaxOffset());

        setColorTabRadioGroup(
                mBinding.rgColorTab,
                true,
                new int[]{ImageFusion.PSEUDO_COLOR_TAB_PLASMA, ImageFusion.PSEUDO_COLOR_TAB_JET},
                imageFusion.getColorTab());

        setFusionModeRadioGroup(
                mBinding.rgFusionMode,
                true,
                new int[]{ImageFusion.FUSION_MODE_COLOR_MAP, ImageFusion.FUSION_MODE_HIGH_FREQ_EXTRACT},
                imageFusion.getMode());
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
    }

    private void resetAllControlParams(ImageFusion imageFusion) {
        imageFusion.resetConfig();
    }

    private void setSeekBarParams(IndicatorSeekBar seekBar, boolean isEnable, int[] limit, int value) {
        seekBar.setEnabled(isEnable);
        if (isEnable) {
            seekBar.setMax(limit[1]);
            seekBar.setMin(limit[0]);
            seekBar.setProgress(value);
        }
    }

    private void setColorTabRadioGroup(RadioGroup radioGroup, boolean isEnable, int[] limit, int value) {
        radioGroup.setEnabled(isEnable);
        if (isEnable) {
            switch (value) {
                case ImageFusion.PSEUDO_COLOR_TAB_PLASMA:
                    radioGroup.check(R.id.rbPLASMA);
                    break;
                case ImageFusion.PSEUDO_COLOR_TAB_JET:
                    radioGroup.check(R.id.rbJet);
                    break;
            }
        }
    }

    private void setFusionModeRadioGroup(RadioGroup radioGroup, boolean isEnable, int[] limit, int value) {
        radioGroup.setEnabled(isEnable);
        if (isEnable) {
            switch (value) {
                case ImageFusion.FUSION_MODE_COLOR_MAP:
                    radioGroup.check(R.id.rbColorMap);
                    break;
                case ImageFusion.FUSION_MODE_HIGH_FREQ_EXTRACT:
                    radioGroup.check(R.id.rbPseudoColor);
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
