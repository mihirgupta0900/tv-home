package com.mihir.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bluetooth manager opened from the home screen's Bluetooth pill: paired devices with
 * connect / disconnect / forget, and discovery to pair new ones. Audio devices stay locked to
 * "never connect on their own" (see BtPolicy) and are opened only while the user connects one.
 * OK connects or disconnects, holding OK forgets.
 */
public class BluetoothActivity extends Activity {
    private BluetoothAdapter bt;
    private BluetoothA2dp a2dp;
    private LinearLayout list;
    private TextView status;
    private boolean scanning;
    private String pairingAddress;
    private final Map<String, BluetoothDevice> nearby = new LinkedHashMap<>();

    private final BluetoothProfile.ServiceListener proxy = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile p) { a2dp = (BluetoothA2dp) p; refresh(); }
        @Override public void onServiceDisconnected(int profile) { a2dp = null; }
    };

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                if (d != null && d.getName() != null && d.getBondState() == BluetoothDevice.BOND_NONE) nearby.put(d.getAddress(), d);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a)) {
                scanning = false;
                setStatus(nearby.isEmpty() ? "Nothing found. Put the device in pairing mode and try again." : "Select a device to pair.");
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(a) && d != null) {
                int state = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if (state == BluetoothDevice.BOND_BONDED) {
                    nearby.remove(d.getAddress());
                    // Pairing was the user's choice, so connect it straight away; otherwise re-lock.
                    if (d.getAddress().equals(pairingAddress) && BtPolicy.isAudio(d)) connect(d); else BtPolicy.lockDown(c);
                    pairingAddress = null;
                } else if (state == BluetoothDevice.BOND_NONE && d.getAddress().equals(pairingAddress)) {
                    pairingAddress = null;
                    setStatus("Pairing failed.");
                }
            } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(a)) {
                int s = i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                if (s == BluetoothProfile.STATE_DISCONNECTED) BtPolicy.lockDown(c);
                if (s == BluetoothProfile.STATE_CONNECTED && d != null) setStatus("Connected to " + name(d) + ".");
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
                BtPolicy.lockDown(c);
            }
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        bt = BluetoothAdapter.getDefaultAdapter();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF0E0E12);
        col.setPadding(dp(72), dp(40), dp(72), dp(24));

        TextView title = text(26, "sans-serif-light", Color.WHITE);
        title.setText("Bluetooth");
        col.addView(title);
        TextView hint = text(11, "sans-serif", 0x99FFFFFF);
        hint.setText("OK connects or disconnects  ·  hold OK to forget  ·  audio devices never connect on their own");
        col.addView(hint, lp(dp(4), 0));
        status = text(12, "sans-serif-medium", 0xCCFFFFFF);
        col.addView(status, lp(dp(14), dp(10)));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);

        if (bt != null) bt.getProfileProxy(this, proxy, BluetoothProfile.A2DP);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(events, f);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(events);
        stopScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bt != null && a2dp != null) bt.closeProfileProxy(BluetoothProfile.A2DP, a2dp);
    }

    // ---------------------------------------------------------------- actions

    private void connect(BluetoothDevice d) {
        if (a2dp == null) { setStatus("Bluetooth audio isn't ready yet. Try again."); return; }
        BtPolicy.set(this, d, BtPolicy.ON);
        if (call(a2dp, "connect", d)) setStatus("Connecting to " + name(d) + "…");
        else setStatus("Couldn't connect to " + name(d) + ".");
    }

    private void disconnect(BluetoothDevice d) {
        if (a2dp != null) call(a2dp, "disconnect", d);
        BtPolicy.set(this, d, BtPolicy.OFF);
        setStatus("Disconnected from " + name(d) + ".");
    }

    private void forget(BluetoothDevice d) {
        new AlertDialog.Builder(this).setTitle("Forget " + name(d) + "?")
                .setPositiveButton("Forget", (x, y) -> {
                    if (isConnected(d) && a2dp != null) call(a2dp, "disconnect", d);
                    setStatus(call(d, "removeBond") ? "Forgot " + name(d) + "." : "Couldn't forget " + name(d) + ".");
                    refresh();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void startScan() {
        if (bt == null || !bt.isEnabled()) return;
        nearby.clear();
        scanning = bt.startDiscovery();
        setStatus(scanning ? "Searching for devices…" : "Couldn't start searching.");
    }

    private void stopScan() {
        if (bt != null && bt.isDiscovering()) bt.cancelDiscovery();
        scanning = false;
    }

    private void pair(BluetoothDevice d) {
        stopScan();
        pairingAddress = d.getAddress();
        setStatus(d.createBond() ? "Pairing with " + name(d) + "…" : "Couldn't pair with " + name(d) + ".");
    }

    // ---------------------------------------------------------------- list

    private void refresh() {
        View focused = getCurrentFocus();
        Object focusKey = focused == null ? null : focused.getTag(R.id.row_key);
        list.removeAllViews();
        List<View> rows = new ArrayList<>();

        if (bt == null) { setStatus("This TV has no Bluetooth."); return; }
        if (!bt.isEnabled()) {
            add(rows, header("BLUETOOTH IS OFF"));
            add(rows, row("key:on", "Turn on Bluetooth", "", () -> bt.enable(), null));
            finishList(rows, focusKey);
            return;
        }

        add(rows, header("MY DEVICES"));
        List<BluetoothDevice> paired = new ArrayList<>();
        for (BluetoothDevice d : bt.getBondedDevices()) if (!isPhoneOrComputer(d)) paired.add(d);
        Collections.sort(paired, (x, y) -> name(x).compareToIgnoreCase(name(y)));
        for (BluetoothDevice d : paired) {
            boolean on = isConnected(d);
            boolean audio = BtPolicy.isAudio(d);
            String right = on ? "Connected" : audio ? "Tap to connect" : "Not connected";
            add(rows, row(d.getAddress(), name(d), right, () -> {
                if (!audio) setStatus(name(d) + " connects on its own when switched on.");
                else if (on) disconnect(d); else connect(d);
                refresh();
            }, () -> forget(d)));
        }
        if (paired.isEmpty()) add(rows, note("No paired devices."));

        add(rows, header("ADD A DEVICE"));
        add(rows, row("key:scan", scanning ? "Stop searching" : "Discover devices", "", () -> {
            if (scanning) { stopScan(); setStatus(""); } else startScan();
            refresh();
        }, null));
        for (BluetoothDevice d : nearby.values()) add(rows, row("n:" + d.getAddress(), name(d), "Tap to pair", () -> pair(d), null));

        finishList(rows, focusKey);
    }

    private void finishList(List<View> rows, Object focusKey) {
        View target = null, first = null;
        for (View v : rows) {
            if (v.getTag(R.id.row_key) == null) continue;
            if (first == null) first = v;
            if (v.getTag(R.id.row_key).equals(focusKey)) target = v;
        }
        if (target == null) target = first;
        if (target != null) target.requestFocus();
    }

    private void add(List<View> rows, View v) {
        rows.add(v);
        list.addView(v, new LinearLayout.LayoutParams(-1, v.getTag(R.id.row_key) == null ? -2 : dp(46)));
    }

    private View header(String s) {
        TextView t = text(10, "sans-serif-medium", 0x88FFFFFF);
        t.setText(s);
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(4), dp(18), 0, dp(6));
        return t;
    }

    private View note(String s) {
        TextView t = text(12, "sans-serif", 0x99FFFFFF);
        t.setText(s);
        t.setPadding(dp(4), dp(4), 0, dp(4));
        return t;
    }

    private View row(String key, String label, String right, Runnable click, Runnable longClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag(R.id.row_key, key);
        TextView l = text(15, "sans-serif", Color.WHITE);
        l.setText(label);
        l.setSingleLine(true);
        TextView s = text(12, "sans-serif", 0x99FFFFFF);
        s.setText(right);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(s);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111111 : Color.WHITE);
            s.setTextColor(has ? 0x99111111 : 0x99FFFFFF);
        });
        r.setOnClickListener(v -> click.run());
        if (longClick != null) r.setOnLongClickListener(v -> { longClick.run(); return true; });
        return r;
    }

    // ---------------------------------------------------------------- helpers

    private void setStatus(String s) { status.setText(s); }

    private static String name(BluetoothDevice d) {
        String n = d.getName();
        return n == null || n.isEmpty() ? d.getAddress() : n;
    }

    /** Phones and computers that paired with the TV aren't things to manage here. */
    private static boolean isPhoneOrComputer(BluetoothDevice d) {
        BluetoothClass c = d.getBluetoothClass();
        if (c == null) return false;
        int m = c.getMajorDeviceClass();
        return m == BluetoothClass.Device.Major.PHONE || m == BluetoothClass.Device.Major.COMPUTER;
    }

    /** BluetoothDevice.isConnected() is hidden on Android 9, so ask through reflection. */
    private boolean isConnected(BluetoothDevice d) {
        try {
            return (Boolean) BluetoothDevice.class.getMethod("isConnected").invoke(d);
        } catch (Exception e) {
            return a2dp != null && a2dp.getConnectedDevices().contains(d);
        }
    }

    /** Calls a hidden Bluetooth method (connect, disconnect, removeBond); false if it isn't there or failed. */
    private static boolean call(Object target, String method, BluetoothDevice... arg) {
        try {
            Class<?>[] types = arg.length == 0 ? new Class<?>[0] : new Class<?>[]{BluetoothDevice.class};
            Object r = target.getClass().getMethod(method, types).invoke(target, (Object[]) arg);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean call(BluetoothDevice target, String method) { return call((Object) target, method); }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(0, top, 0, bottom);
        return p;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
