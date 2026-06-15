/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.activity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricPrompt;

import io.reactivex.disposables.Disposable;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.PinProtection;
import ru.orangesoftware.financisto.utils.BiometricAuthHelper;
import ru.orangesoftware.financisto.view.PinView;

public class PinActivity extends AppCompatActivity implements PinView.PinListener {

    public static final String SUCCESS = "PIN_SUCCESS";

    private Disposable disposable;

    private final Handler handler = new Handler();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pin = MyPreferences.getPin(this);
        if (pin == null) {
            onSuccess(null);
        } else if (BiometricAuthHelper.isAvailable(this) && MyPreferences.isPinLockUseFingerprint(this)) {
            setContentView(R.layout.lock_fingerprint);
            askForFingerprint();
        } else {
            usePinLock();
        }
    }

    private void usePinLock() {
        String pin = MyPreferences.getPin(this);
        PinView v = new PinView(this, this, pin, R.layout.lock);
        setContentView(v.getView());
        View useFingerprintButton = findViewById(R.id.use_fingerprint);
        if (BiometricAuthHelper.isAvailable(this)) {
            useFingerprintButton.setOnClickListener(v2 -> askForFingerprint());
        } else {
            useFingerprintButton.setVisibility(View.GONE);
        }
    }

    private void askForFingerprint() {
        View usePinButton = findViewById(R.id.use_pin);
        if (MyPreferences.isUseFingerprintFallbackToPinEnabled(this)) {
            usePinButton.setOnClickListener(v -> {
                usePinLock();
            });
        } else {
            usePinButton.setVisibility(View.GONE);
        }
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.pin_protection_use_fingerprint))
                .setSubtitle(getString(R.string.fingerprint_hint))
                .setDescription(getString(R.string.fingerprint_description))
                .setNegativeButtonText(getString(R.string.use_pin))
                .build();

        BiometricAuthHelper.authenticate(this, promptInfo, new BiometricAuthHelper.Callback() {
            @Override
            public void onAuthenticated() {
                setFingerprintStatus(R.string.fingerprint_auth_success, R.drawable.ic_check_circle_black_48dp, R.color.material_teal);
                handler.postDelayed(() -> onSuccess(null), 200);
            }

            @Override
            public void onFailed(String message) {
                setFingerprintStatus(R.string.fingerprint_auth_failed, R.drawable.ic_error_black_48dp, R.color.material_orange);
            }

            @Override
            public void onError(int code, String message) {
                if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    usePinLock();
                } else {
                    setFingerprintStatus(R.string.fingerprint_error, R.drawable.ic_error_black_48dp, R.color.holo_red_dark);
                    Toast.makeText(PinActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onHelp(String message) {
                Toast.makeText(PinActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setFingerprintStatus(int messageResId, int iconResId, int colorResId) {
        TextView status = findViewById(R.id.fingerprint_status);
        ImageView icon = findViewById(R.id.fingerprint_icon);
        int color = getResources().getColor(colorResId);
        status.setText(messageResId);
        status.setTextColor(color);
        icon.setImageResource(iconResId);
        icon.setColorFilter(color);
    }

    @Override
    public void onConfirm(String pinBase64) {
    }

    @Override
    public void onSuccess(String pinBase64) {
        disposeFingerprintListener();
        PinProtection.pinUnlock(this);
        Intent data = new Intent();
        data.putExtra(SUCCESS, true);
        setResult(RESULT_OK, data);
        finish();
    }

    private void disposeFingerprintListener() {
        // no-op with BiometricPrompt
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

}
