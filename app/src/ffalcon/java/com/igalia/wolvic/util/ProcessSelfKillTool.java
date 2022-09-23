package com.igalia.wolvic.util;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * 进程自杀广播
 * Created by rilke on 18-6-6.
 */
public class ProcessSelfKillTool {

    private ProcessSelfKillTool() {
    }

    private static final String ACTION_KILL_PROCESS = "com.tcl.xr.wbrowser.ACTION_KILL_PROCESS";

    private static class ProcessSelfKillReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (ACTION_KILL_PROCESS.equals(intent.getAction())) {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }

    public static void register(Application app) {
        if (app == null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_KILL_PROCESS);
        app.registerReceiver(new ProcessSelfKillReceiver(), filter);
    }

    public static void performSelfKill(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(ACTION_KILL_PROCESS);
        context.sendBroadcast(intent);
    }
}



