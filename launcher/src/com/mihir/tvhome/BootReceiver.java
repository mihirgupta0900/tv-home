package com.mihir.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After power-on, make sure paired audio devices are set to never connect on their own. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        BtPolicy.lockDown(ctx);
    }
}
