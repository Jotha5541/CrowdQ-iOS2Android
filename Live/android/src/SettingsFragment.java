// Settings.swift

package live;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsFragment extends Fragment {
    private TextView showLabel;
    private TextView textView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NOnNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        showLabel = view.findViewById(R.id.showLabel);
        textView = view.findViewById(R.id.textView);

        textView.setText("Waiting for a show...\n");

        showLabel.setText("-- No Show Loaded --");
    }

    public void add(String text) {
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timeString = df.format(new Date());
        String info = "\n" + timeString + " - " + text;

        mainHandler.postDelayed(() -> {
            textView.append(info);

            int scrollAmt = texxtView.getLayout() != null
                    ? textView.getLayout().getLineTop(textView.getLineCount()) - textView.getHeight()
                    : 0;
            if (scrollAmt > 0) {
                textView.scrollT(0, scrollAmt);
            }
        }, 1600);
    }

    public void setShowName(String name) {
        mainHandler.post(() -> showLabel.setText(name));
    }
}