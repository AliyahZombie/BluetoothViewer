package net.bluetoothviewer.application;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public abstract class BluetoothViewerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    public abstract boolean isLiteVersion();
}
