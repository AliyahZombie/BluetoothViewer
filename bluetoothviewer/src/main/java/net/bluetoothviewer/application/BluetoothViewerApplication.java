package net.bluetoothviewer.application;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import net.bluetoothviewer.library.R;
import net.bluetoothviewer.bluetooth.BluetoothDeviceManager;
import net.bluetoothviewer.ws.WebSocketController;

public abstract class BluetoothViewerApplication extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        BluetoothDeviceManager.INSTANCE.initialize(this);

        WebSocketController.INSTANCE.start(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String prefName) {
        String enabledKey = getString(R.string.pref_ws_enabled);
        String bindAllKey = getString(R.string.pref_ws_bind_all);
        String portKey = getString(R.string.pref_ws_port);
        if (prefName.equals(enabledKey) || prefName.equals(bindAllKey) || prefName.equals(portKey)) {
            WebSocketController.INSTANCE.restart(this);
        }
    }

    public abstract boolean isLiteVersion();
}
