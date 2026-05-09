// ButtonGridView.swift

package stage;

import android.animation.ObjectAnimator;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exchange.CrowdQExchangeTag;

public class ButtonGridFragment extends Fragment {

    // Equivalent to var main: ViewController?
    private MainActivity main;

    private LinearLayout stackView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Equivalent to var radios: [RadioButton]
    private final List<Button> radios = new ArrayList<>();

    // Equivalent to var toggleState: [String: Int]
    private final Map<String, Integer> toggleState = new HashMap<>();

    // Equivalent to var dollarArgument: Int = 0
    private int dollarArgument = 0;

    public void setMain(MainActivity main) {
        this.main = main;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_button_grid, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        stackView = view.findViewById(R.id.stackView);

        // Equivalent to setupDynamicButtonGrid(actions: [], toggles: [])
        setupDynamicButtonGrid(new ArrayList<>(), new ArrayList<>());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Equivalent to supportedInterfaceOrientations = .landscape
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Restore portrait when leaving
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    // Equivalent to func newShow()
    public void newShow(List<String> immediates, List<String> sensors) {
        mainHandler.post(() -> {
            setupDynamicButtonGrid(immediates, sensors);
        });
    }

    // Equivalent to func setupDynamicButtonGrid()
    private void setupDynamicButtonGrid(List<String> actions, List<String> toggles) {
        stackView.removeAllViews();
        stackView.setOrientation(LinearLayout.VERTICAL);

        // Track toggle states — equivalent to toggleState[toggle] = -1
        toggleState.clear();
        for (String toggle : toggles) {
            toggleState.put(toggle, -1);
        }

        // Radio button row — equivalent to createHorizontalStack() + RadioButton loop
        LinearLayout radioRow = createHorizontalStack();
        stackView.addView(radioRow);
        radios.clear();

        for (int i = 0; i <= 20; i++) {
            Button radio = createRadioButton(i);
            if (i == 0) setRadioSelected(radio, true);
            radios.add(radio);
            radioRow.addView(radio);
        }

        // Action + toggle buttons — equivalent to actions+toggles loop
        LinearLayout currentRow = createHorizontalStack();
        stackView.addView(currentRow);

        List<String> allActions = new ArrayList<>(actions);
        allActions.addAll(toggles);

        for (String name : allActions) {
            if (name.startsWith("_")) continue; // Ignore private names

            Button button = createStyledButton(name);

            // Wrap to new row if too many buttons
            if (currentRow.getChildCount() > 3) {
                currentRow = createHorizontalStack();
                stackView.addView(currentRow);
            }

            currentRow.addView(button);
        }
    }

    // Equivalent to createHorizontalStack()
    private LinearLayout createHorizontalStack() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 15);
        row.setLayoutParams(params);
        row.setPadding(12, 0, 12, 0);
        return row;
    }

    // Equivalent to RadioButton class
    private Button createRadioButton(int number) {
        Button button = new Button(requireContext());
        button.setText(String.valueOf(number));
        button.setTextColor(Color.BLACK);
        button.setBackgroundColor(Color.LTGRAY);
        button.setTag(number);

        int size = (int) (80 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(8, 8, 8, 8);
        button.setLayoutParams(params);

        // Equivalent to RadioButton.isSelected didSet
        button.setOnClickListener(v -> radioTapped(button));
        return button;
    }

    // Equivalent to isSelected didSet on RadioButton
    private void setRadioSelected(Button button, boolean selected) {
        if (selected) {
            button.setBackgroundColor(Color.BLUE);
            button.setTextColor(Color.WHITE);
        } else {
            button.setBackgroundColor(Color.LTGRAY);
            button.setTextColor(Color.BLACK);
        }
    }

    // Equivalent to createStyledButton()
    private Button createStyledButton(String title) {
        Button button = new Button(requireContext());
        button.setText(" " + title + " ");
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.BLUE);
        button.setTextSize(22);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 8, 8, 8);
        button.setLayoutParams(params);

        // Equivalent to toggle vs action detection
        if (toggleState.containsKey(title)) {
            button.setOnClickListener(v -> toggleTapped(button, title));
        } else {
            button.setOnClickListener(v -> buttonTapped(button, title));
        }

        return button;
    }

    // Equivalent to @objc func radioTapped()
    private void radioTapped(Button sender) {
        for (Button radio : radios) {
            setRadioSelected(radio, false);
        }
        setRadioSelected(sender, true);
        dollarArgument = Integer.parseInt(sender.getText().toString().trim());
    }

    // Equivalent to @objc func buttonTapped()
    private void buttonTapped(Button sender, String action) {
        // Equivalent to UIView.animate fade out and back in
        sender.setAlpha(0.3f);
        ObjectAnimator.ofFloat(sender, "alpha", 0.3f, 1.0f)
                .setDuration(400)
                .start();

        if (main != null) {
            main.bleClient.enqueue(
                    requireContext(),
                    CrowdQExchangeTag.COMMAND,
                    dollarArgument,
                    action.trim()
            );
        }
    }

    // Equivalent to @objc func toggleTapped()
    private void toggleTapped(Button sender, String action) {
        // Swap background and text color — equivalent to oldBG/oldText swap
        int oldBg   = Color.BLUE;
        int oldText = Color.WHITE;

        sender.setBackgroundColor(oldText);
        sender.setTextColor(oldBg);

        Integer state = toggleState.get(action);
        if (state == null || state == -1) {
            toggleState.put(action, dollarArgument);
            if (main != null) {
                main.bleClient.enqueue(
                        requireContext(),
                        CrowdQExchangeTag.COMMAND,
                        dollarArgument,
                        action
                );
            }
        } else {
            toggleState.put(action, -1);
            if (main != null) {
                main.bleClient.enqueue(
                        requireContext(),
                        CrowdQExchangeTag.COMMAND,
                        state,
                        "_off"
                );
            }
        }
    }

    // Equivalent to func onVerified()
    public void onVerified() {
        // Reserved for future use
    }
}