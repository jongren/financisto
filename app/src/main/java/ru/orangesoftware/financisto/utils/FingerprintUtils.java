package ru.orangesoftware.financisto.utils;

import android.content.Context;

import androidx.biometric.BiometricManager;

import ru.orangesoftware.financisto.R;

public class FingerprintUtils {

    public static boolean fingerprintUnavailable(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return can != BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static String reasonWhyFingerprintUnavailable(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (can == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            return context.getString(R.string.fingerprint_unavailable_hardware);
        } else if (can == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return context.getString(R.string.fingerprint_unavailable_enrolled_fingerprints);
        } else {
            return context.getString(R.string.fingerprint_unavailable_unknown);
        }
    }

}
