package com.example.recorder;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DATARECORDER";
    private static final int PERMISSION_CODE = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private MediaProjectionManager mProjectionManager;
    private ToggleButton mToggleButton;
    public boolean fromPass = false;

    public static Intent mpermissionResult;
    public static int mresultCode;
    public static Context mrecordContext;

    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    private static final boolean noPassword = false;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG,"App running on " + Build.VERSION.INCREMENTAL + " aka " + Build.VERSION.CODENAME);
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        setContentView(R.layout.activity_main);
        mProjectionManager = (MediaProjectionManager) getSystemService (Context.MEDIA_PROJECTION_SERVICE);
        mToggleButton = (ToggleButton) findViewById(R.id.toggle);

        boolean isRecording = isServiceRunning(RecordService.class);
        if(isRecording){
            mToggleButton.setChecked(true);
        }



        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleScreenShare(v);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    @Override
    public void onDestroy() {

//        Intent broadcastIntent = new Intent();
//        broadcastIntent.setAction("restartservice");
//        broadcastIntent.setClass(this, Restarter.class);
//        this.sendBroadcast(broadcastIntent);

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!noPassword){
            if (!fromPass) {

                startActivity(new Intent(this, passwordActivity.class));
                fromPass = true;
            } else {
                fromPass = false;
            }
        }
    }

    public void onBackPressed() {
        return;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode == RESULT_OK) {
            mresultCode = resultCode;
            mpermissionResult = data;
            mrecordContext = this;
            startRecordingService(resultCode, data);
        } else {
            Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            return;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onToggleScreenShare(View view) {
        if ( ((ToggleButton)view).isChecked() ) {
            // ask for permission to capture screen and act on result after
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
            //startActivity(mProjectionManager.createScreenCaptureIntent());
            Log.v(TAG, "onToggleScreenShare");
        } else {
            Log.v(TAG, "onToggleScreenShare: Recording Stopped");
            stopRecordingService();
        }
    }

    private void startRecordingService(int resultCode, Intent data){
        Log.v(TAG, "Main activity: starting service");
//        recordContext = this;
        Intent intent = RecordService.newIntent(this, resultCode, data);
        startService(intent);
    }

    private void stopRecordingService(){
        Intent intent = new Intent(this, RecordService.class);
        stopService(intent);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
