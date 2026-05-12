// ScrollingTextPage.swift
// Extension of Page1 fragment

package stage;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ScrollingTextFragment extends Fragment {
    // scrollView, contentView, stackView
    protected ScrollView scrollView;
    protected LinearLayout stackView;

    // textCallback
    protected OnTextSubmitListener textCallback;

    public interface OnTextSubmitListener {
        void onTextSubmit(EditText field, String text);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.scrolling_text_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        scrollView = view.findViewById(R.id.scrollView);
        stackView = view.findViewById(R.id.stackView);

        // backgroundColor = systemBackground
        view.setBackgroundColor(android.graphics.Color.WHITE);

        setupStackView();
        setupKeyboardHandling(view);
    }

    protected void setupStackView() {

    }

    private void setupKeyboardHandling(View rootView) {
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect rect = new Rect();
            rootView.getWindowVisibleDisplayFrame(rect);
            int screenHeight = rootView.getRootView().getHeight();
            int keyboardHeight = screenHeight - rect.bottom;

            if (keyboardHeight > screenHeight * 0.15) {
                scrollView.setPadding(
                        scrollView.getPaddingLeft(),
                        scrollView.getPaddingTop(),
                        scrollView.getPaddingRight(),
                        keyboardHeight
                );
            }
            else {
                scrollView.setPadding(
                        scrollView.getPaddingLeft(),
                        scrollView.getPaddingTop(),
                        scrollView.getPaddingRight(),
                        0
                );
            }
        });
    }

    protected void scrollToView(View targetView) {
        scrollView.post(() -> {
            int[] location = new int[2];
            targetView.getLocationOnScreen(location);
            scrollView.smoothScrollTo(0, location[1]);
        });
    }
}