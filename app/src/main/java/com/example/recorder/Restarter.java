package com.example.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class Restarter extends BroadcastReceiver {
    private static final String TAG = "RESTARTER";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("Broadcast Listened", "Service tried to stop");

        Intent recordIntent = RecordService.newIntent(MainActivity.mrecordContext, MainActivity.mresultCode, MainActivity.mpermissionResult);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(recordIntent);
        } else {
            context.startService(recordIntent);
        }
    }
}
