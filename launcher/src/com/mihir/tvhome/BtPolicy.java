package com.mihir.tvhome;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.provider.Settings;

import java.util.Locale;

/**
 * Keeps the TV from grabbing Bluetooth audio devices that someone else is using.
 *
 * Android stores a per-device A2DP priority in Settings.Global: 1000 auto-connects on power-on,
 * 100 connects only when asked, 0 refuses every connection (ours and the device's own). We hold
 * paired audio devices at 0 and open them to 100 only while the user is connecting one.
 * Needs WRITE_SECURE_SETTINGS (granted over ADB at install).
 */
final class BtPolicy {
    static final int OFF = 0, ON = 100;
    private static final String KEY = "bluetooth_a2dp_sink_priority_";

    private BtPolicy() {}

    static String key(String address) { return KEY + address.toUpperCase(Locale.ROOT); }

    /** True for headphones, speakers and other audio gear. Remotes and keyboards (HID) are left alone. */
    static boolean isAudio(BluetoothDevice d) {
        BluetoothClass c = d.getBluetoothClass();
        return c != null && c.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    static int get(Context ctx, BluetoothDevice d) {
        return Settings.Global.getInt(ctx.getContentResolver(), key(d.getAddress()), -1);
    }

    /** Returns false if the app has not been granted WRITE_SECURE_SETTINGS. */
    static boolean set(Context ctx, BluetoothDevice d, int priority) {
        try {
            return Settings.Global.putInt(ctx.getContentResolver(), key(d.getAddress()), priority);
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Put every paired audio device back to "never connect on its own". Returns how many changed. */
    static int lockDown(Context ctx) {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null || !bt.isEnabled()) return 0;
        int changed = 0;
        try {
            for (BluetoothDevice d : bt.getBondedDevices()) {
                if (isAudio(d) && get(ctx, d) != OFF && set(ctx, d, OFF)) changed++;
            }
        } catch (SecurityException ignored) {
            // No Bluetooth permission (Android 12+ without BLUETOOTH_CONNECT): nothing we can do.
        }
        return changed;
    }
}
