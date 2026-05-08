// ImagePopupViewController.swift

package live;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class ImagePopupFragment extends DialogFragment {

    private static final String ARG_IMAGE_RES_ID = "imageResId";
    private static final String ARG_DURATION = "duration";
    private static final String ARG_WIDTH = "width";
    private static final String ARG_HEIGHT = "height";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ImagePopupFragment newInstance(int imageResId, int durationMs, int width, int height) {
        ImagePopupFragment fragment = new ImagePopupFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_IMAGE_RES_ID, imageResId);
        args.putInt(ARG_DURATION, durationMs);
        args.putInt(ARG_WIDTH, width);
        args.putInt(ARG_HEIGHT, height);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME,
                android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );
        }

        ImageView imageView = new ImageView(requireContext());
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.argb(179, 0, 0, 0)); // .black.withAlphaComponent(0.7)

        if (getArguments() != null) {
            int resId  = getArguments().getInt(ARG_IMAGE_RES_ID);
            int width  = getArguments().getInt(ARG_WIDTH);
            int height = getArguments().getInt(ARG_HEIGHT);

            imageView.setImageResource(resId);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
            params.gravity = Gravity.CENTER;
            imageView.setLayoutParams(params);

            scheduleSelfDismiss(getArguments().getInt(ARG_DURATION));
        }

        return imageView;
    }


    private void scheduleSelfDismiss(int durationMs) {
        mainHandler.postDelayed(() -> {
            if (isAdded()) {
                dismiss();
            }
        }, durationMs);
    }
}