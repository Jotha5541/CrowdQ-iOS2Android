// Primary.swift

package live;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PrimaryFragment extends Fragment {

    private static final String ONE_TIME_MESSAGE =
            "This is a one-time message.\n\n" +
            "When you start this app up, it will look for a local CrowdQ performance. " +
            "If there aren't any running yet, you'll see a bouncing red guitar while our app " +
            "scans the bluetooth airwaves for a show server. The guitar will be dismissed when " +
            "the server is discovered.\n\n" +
            "You can swipe left to see status and \"about\" pages";

    private AlertDialog alert;
    private ImageView iconView;

    private float x, y;
    private float dx = 5f, dy = 5f;
    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private boolean bouncing = false;
    private Runnable bounceRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.primary_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        iconView = view.findViewById(R.id.iconView);

        toggleBouncing(true);

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("CrowdQPrefs", 0);
        boolean flagged = prefs.getBoolean("flagged", false);
        if (!flagged) {
            prefs.edit().putBoolean("flagged", true).apply();
            new AlertDialog.Builder(requireContext())
                    .setMessage(ONE_TIME_MESSAGE)
                    .setPositiveButton("Dismiss", null)
                    .show();
        }
    }

    public void toggleBouncing(boolean isOn) {
        bouncing = isOn;

        if (isOn) {
            iconView.setVisibility(View.VISIBLE);
            startBouncing();
        }
        else {
            animHandler.removeCallbacks(bounceRunnable);
            iconView.setVisibility(View.GONE);
        }
    }

    private void startBouncing() {
        iconView.post(() -> {
            ViewGroup parent = (ViewGroup) iconView.getParent();
            if (parent == null) return;

            float parentWidth = parent.getWidth();
            float parentHeight = parent.getHeight();
            float iconWidth = iconView.getWidth();
            float iconHeight = iconView.getHeight();

            x = 100; y = 100;   // CGRect

            dx = 8f; dy = 8f;   // CGVector

            bounceRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!bouncing) return;

                    x += dx; y += dy;

                    if (x <= 0) {   // Bounce left/right walls
                        x = 0;
                        dx = Math.abs(dx);  // Elasticity = 1.0
                    }
                    else if (x + iconWidth >= parentWidth) {
                        x = parentWidth - iconWidth;
                        dx = -Math.abs(dx);
                    }

                    if (y <= 0) { // Bounce top/bottom walls
                        y = 0;
                        dy = Math.abs(dy);
                    }
                    else if (y + iconHeight >= parentHeight) {
                        y = parentHeight - iconHeight;
                        dy = -Math.abs(dy);
                    }

                    iconView.setX(x);
                    iconView.setY(y);

                    animHandler.postDelayed(this, 16); // 60 fps frame update
                }
            };

            animHandler.post(bounceRunnable);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bounceRunnable != null) {
            animHandler.removeCallbacks(bounceRunnable);
        }
        bouncing = false;
    }
}
