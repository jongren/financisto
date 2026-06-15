package ru.orangesoftware.financisto.utils;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class BiometricAuthHelper {

    public interface Callback {
        void onAuthenticated();
        void onFailed(String message);
        void onError(int code, String message);
        void onHelp(String message);
    }

    public static boolean isAvailable(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return can == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static boolean isHardwareDetected(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return can != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;
    }

    public static boolean hasEnrolledFingerprints(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return can != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
    }

    public static void authenticate(@NonNull Context context,
                                    @NonNull BiometricPrompt.PromptInfo promptInfo,
                                    @NonNull Callback callback) {
        Executor executor = ContextCompat.getMainExecutor(context);
        BiometricPrompt biometricPrompt = new BiometricPrompt((androidx.fragment.app.FragmentActivity) context,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        callback.onAuthenticated();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        callback.onFailed("Authentication failed");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        callback.onError(errorCode, errString.toString());
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }
}

