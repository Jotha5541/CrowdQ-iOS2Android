// SettingsViewController.swift

package stage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ContinuousControlFragment extends Fragment {

    private TextView valueLabel;
    private Switch statusSwitch;
    private SeekBar rangeSlider;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.continuous_control_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        valueLabel   = view.findViewById(R.id.valueLabel);
        statusSwitch = view.findViewById(R.id.statusSwitch);
        rangeSlider  = view.findViewById(R.id.rangeSlider);

        statusSwitch.setOnCheckedChangeListener((buttonView, isOn) -> {
            rangeSlider.setEnabled(isOn);
            valueLabel.setAlpha(isOn ? 1.0f : 0.5f);
        });

        rangeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // SeekBar is 0-200, map to 0.00-2.00
                float value = progress / 100.0f;
                valueLabel.setText(String.format("%.2f", value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
}