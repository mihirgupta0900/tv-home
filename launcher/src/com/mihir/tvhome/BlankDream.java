package com.mihir.tvhome;

import android.service.dreams.DreamService;
import android.view.View;

/** Screensaver that shows nothing. Pick it, and set the delay, in Settings > Device Preferences > Screen saver. */
public class BlankDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        View v = new View(this);
        v.setBackgroundColor(0xFF000000);
        setContentView(v);
    }
}
