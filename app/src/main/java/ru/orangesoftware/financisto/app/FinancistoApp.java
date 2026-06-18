package ru.orangesoftware.financisto.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import androidx.multidex.MultiDexApplication;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EApplication;

import ru.orangesoftware.financisto.bus.GreenRobotBus;
import ru.orangesoftware.financisto.export.drive.GoogleDriveClient;
import ru.orangesoftware.financisto.utils.MyPreferences;

@EApplication
public class FinancistoApp extends MultiDexApplication {

    @Bean
    public GreenRobotBus bus;

    @Bean
    public GoogleDriveClient driveClient;

    @AfterInject
    public void init() {
        bus.register(driveClient);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
                android.util.TypedValue outValue = new android.util.TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.windowIsFloating, outValue, true);
                boolean isFloating = outValue.type == android.util.TypedValue.TYPE_INT_BOOLEAN && outValue.data != 0;

                if (!isFloating) {
                    if (android.os.Build.VERSION.SDK_INT >= 35) {
                        View decorView = activity.getWindow().getDecorView();
                        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
                            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
                            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), bottomPadding);
                            return insets;
                        });
                    }
                }
            }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        MyPreferences.switchLocale(this);
    }
}
