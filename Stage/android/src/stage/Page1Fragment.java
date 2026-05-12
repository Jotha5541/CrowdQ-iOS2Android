// Page1.swift

package stage;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;



import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Page1Fragment extends Fragment {
    private static final String ONE_TIME_MESSAGE =
            "Welcome to CrowdQ Stage\n\n" +
                    "• This is a one time message.\n" +
                    "• Without an email, you can access just the \"demo\" show\n" +
                    "• Provide and verify your email to access more free shows\n" +
                    "• Swipe left/right to navigate between app screens";

    private static final String BLANK_INSTRUCTIONS =
            "You currently have not entered an email. This locks CrowdQ Stage into demo mode. " +
                    "Swipe left to expose the current show.\n\n" +
                    "Enter your email and press verify. This will generate an email with a link to follow " +
                    "to make sure your email is valid. Check your SPAM folder. " +
                    "It will be coming from noreply@luxcedia.icu\n\n" +
                    "This will move your account to \"free\" status which allows a few more interesting shows. " +
                    "You can later upgrade this to a paid account to allow you to generate your own shows.\n\n" +
                    "You can unsubscribe/disconnect this device using the [unsubscribe] button below";

    private static final String ASK_TO_VERIFY_EMAIL =
            "You may now use the [Verify] button to verify your email.\n\n" +
                    "If you previously verified on another device, you'll be good to go.\n\n" +
                    "Otherwise, we'll email you a link to follow to get verified (check your SPAM). " +
                    "That link will also contain a link to unsubscribe (then or in the future). " +
                    "You may also request removal under your right to be forgotten.\n\n" +
                    "See our privacy policy at https://luxcedia.icu/privacyCrowdQStage.html";

    private static final String EMAIL_IS_VERIFIED =
            "You now have access to all the free shows.\n" +
                    "Swipe left to see the show play board.\n";

    // View Buttons
    private TextView instructions;
    private EditText emailTextField;
    private Button verifyButton;
    private Button unsubscribeButton;


    private MainActivity main;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void setMain(MainActivity main) {
        this.main = main;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.page1_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        instructions = view.findViewById(R.id.instructions);
        emailTextField = view.findViewById(R.id.emailTextField);
        verifyButton = view.findViewById(R.id.verifyButton);
        unsubscribeButton = view.findViewById(R.id.unsubscribeButton);

        instructions.setText(BLANK_INSTRUCTIONS);

        // Status button verify
        verifyButton.setOnClickListener(v -> {
            String email = emailTextField.getText().toString();
            verifyEmail(email);
        });

        unsubscribeButton.setOnClickListener(v -> unsubscribe());

        emailTextField.setOnEditorActionListener((v, actionId, event) -> {
            String email = emailTextField.getText().toString();
            newEmail(email);
            return true;
        });

        // Loading saved email
        SharedPreferences prefs = requireActivity().getSharedPreferences("CrowdQPrefs", 0);
        String savedEmail = prefs.getString("email", "");
        if (!savedEmail.isEmpty()) {
            emailTextField.setText(savedEmail);
            verifyEmail(savedEmail);
        }

        boolean flagged = prefs.getBoolean("flagged", false);
        if (!flagged) {
            prefs.edit().putBoolean("flagged", true).apply();
            new AlertDialog.Builder(requireContext())
                    .setMessage(ONE_TIME_MESSAGE)
                    .setPositiveButton("Dismiss", null)
                    .show();
        }
    }

    private void newEmail(String email) {
        mainHandler.post(() -> instructions.setText(ASK_TO_VERIFY_EMAIL));
        SharedPreferences prefs = requireActivity().getSharedPreferences("CrowdQPrefs", 0);
        prefs.edit().putString("email", email).apply();
    }

    private void verifyEmail(String email) {
        mainHandler.post(() -> instructions.setText("verifying email...."));

        android.content.Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            try {
                @SuppressLint("HardwareIds")
                String deviceUUID = android.provider.Settings.Secure.getString(
                        appContext.getContentResolver(),
                        android.provider.Settings.Secure.ANDROID_ID
                );

                URL url = new URL("https://luxcedia.icu/cgi-bin/verifyemail");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String body = "id=" + email + "&uuid=" + deviceUUID;
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes());
                os.flush();

                int status = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String answer = response.toString();
                mainHandler.post(() -> {
                    if (status != 200) {
                        instructions.setText("Problem with verify:\n\n" + answer);
                    }
                    else {
                        instructions.setText(EMAIL_IS_VERIFIED + "\n" + answer);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> instructions.setText("Error: " + e.getMessage()));
            }
        });
    }

    private void unsubscribe() {
        String email = emailTextField.getText().toString();



        executor.execute(() -> {
            try {
                URL url = new URL("https://luxcedia.icu/cgi-bin/unsubscribe");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String body = "id=" + email;
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes());
                os.flush();

                int status = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String answer = response.toString();
                mainHandler.post(() -> {
                    if (status != 200) {
                        instructions.setText("Problem with unsubscribe:\n\n" + answer);
                    }
                    else {
                        instructions.setText(answer);
                        emailTextField.setText("");
                        requireActivity().getSharedPreferences("CrowdQPrefs", 0)
                                .edit().putString("email", "").apply();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> instructions.setText("Error: " + e.getMessage()));
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
