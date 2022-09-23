package com.igalia.wolvic.util;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

public class DisplayUtil {

    /**
     * Check whether TCL AR device exist.<br/>
     * If true, return the device<br/>
     * If false, null will be returned
     */
    public static Display checkTCLGlasses(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        int size = dm.getDisplays().length;
        if (size == 1)
            return null;

        //get Last Display
        Display display = dm.getDisplays()[size - 1];
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        Log.e("Kaidi", "Display " + width +" " + height);
        //在Oppo、一加手机上，高度少了112像素，所以这里的判断逻辑要修改
        if (width == 3840)
            return display;
        else
            return null;
    }
}
