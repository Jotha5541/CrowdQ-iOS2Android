// Page1.swift

package stage;

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


}
