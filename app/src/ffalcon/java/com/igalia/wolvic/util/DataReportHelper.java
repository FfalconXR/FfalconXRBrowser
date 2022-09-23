package com.igalia.wolvic.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 用于数据上报，通过广播让白羊Launcher去处理
 */
public class DataReportHelper {

    private final static String TAG = DataReportHelper.class.getSimpleName();
    private final static String ACTION = "com.tcl.xr.browser.report";
    private final static String KEY_TYPE = "type";
    private final static String TYPE_URL = "url";
    private final static String TYPE_EXIT = "exit";
    private final static String KEY_URL = "url";

    public static void reportUrl(Context context, String url) {
        String sha256 = SHA256.getSHA256(url);
        Log.d(TAG, "reportUrl " + url + " " + sha256);
        Intent intent = new Intent(ACTION);
        intent.putExtra(KEY_TYPE, TYPE_URL);
        intent.putExtra(KEY_URL, sha256);
        context.sendBroadcast(intent);
    }

    public static void reportExit(Context context) {
        Log.d(TAG, "reportExit");
        Intent intent = new Intent(ACTION);
        intent.putExtra(KEY_TYPE, TYPE_EXIT);
        context.sendBroadcast(intent);
    }
}
