package ru.orangesoftware.financisto.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import androidx.multidex.MultiDexApplication;

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
                android.util.TypedValue outValue = new android.util.TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.windowIsFloating, outValue, true);
                boolean isFloating = outValue.type == android.util.TypedValue.TYPE_INT_BOOLEAN && outValue.data != 0;

                if (!isFloating) {
                    View decorView = activity.getWindow().getDecorView();
                    decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                        int top = insets.getSystemWindowInsetTop();
                        int bottom = insets.getSystemWindowInsetBottom();
                        v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
                        return insets;
                    });
                }
            }

            @Override public void onActivityStarted(Activity activity) {}
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
