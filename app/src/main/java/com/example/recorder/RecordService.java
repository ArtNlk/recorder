package com.example.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

import static android.app.Activity.RESULT_OK;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
import static android.os.Environment.DIRECTORY_MOVIES;

/**
 * RecordService Class
 *
 * Background service for recording the device screen.
 * Listens for commands to stop or start recording by the user
 * and by screen locked/unlock events. Notification in the
 * notification center informs user about the running recording
 * service.
 */
public final class RecordService extends Service {

    private ServiceHandler mServiceHandler;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private int resultCode;
    private Intent data;
    private BroadcastReceiver mScreenStateReceiver;
    private Surface recorderSurface;

    private static final String TAG = "RECORDERSERVICE";
    private static final String EXTRA_RESULT_CODE = "resultcode";
    private static final String EXTRA_DATA = "data";
    private static final int ONGOING_NOTIFICATION_ID = 23;

    /*
     *
     */
    static Intent newIntent(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, RecordService.class);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_DATA, data);
        Log.v(TAG, "Intent requested");
        return intent;
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "Broadcast reciever: recieved "+action);
            switch(action){
                case Intent.ACTION_SCREEN_ON:
                    startRecording(resultCode, data);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    stopRecording();
                    break;
                case Intent.ACTION_CONFIGURATION_CHANGED:
                    stopRecording();
                    startRecording(resultCode, data);
                    break;
            }
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void handleMessage(Message msg) {
            if (resultCode == RESULT_OK) {
                startRecording(resultCode, data);
            }else{
                Log.v(TAG, "Service handler: message result not RESULT_OKAY");
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "Starting service (onCreate)");
        // run this service as foreground service to prevent it from getting killed
        // when the main app is being closed
        Intent notificationIntent =  new Intent(this, RecordService.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new Notification.Builder(this)
                        .setContentTitle("DataRecorder")
                        .setContentText("Your screen is being recorded and saved to your phone.")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .setTicker("Tickertext")
                        .build();

        // register receiver to check if the phone screen is on or off
        mScreenStateReceiver = new MyBroadcastReceiver();
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, notification);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground()
    {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent,flags,startId);
        Log.v(TAG, "Starting service (onStartCommand)");
        resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        data = intent.getParcelableExtra(EXTRA_DATA);

        if (resultCode == 0 || data == null) {
            throw new IllegalStateException("Result code or data missing.");
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        return START_REDELIVER_INTENT;
    }

    public void onRecorderError(MediaRecorder mr, int what, int extra)
    {
        Log.v(TAG, "Media recorder error: mr=" + mr + " what=" + what + " extra=" + extra);
    }

    public void onRecorderInfo(MediaRecorder mr, int what, int extra)
    {
        Log.v(TAG, "Media recorder info: mr=" + mr + " what=" + what + " extra=" + extra);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecording(int resultCode, Intent data) {
        Log.v(TAG, "Starting recording...");

        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService (Context.MEDIA_PROJECTION_SERVICE);
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnErrorListener(this::onRecorderError);
        mMediaRecorder.setOnInfoListener(this::onRecorderInfo);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int mScreenDensity = metrics.densityDpi;
        int displayWidth = metrics.widthPixels;
        int displayHeight = metrics.heightPixels;

//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
//        mMediaRecorder.setAudioSamplingRate(16000);
//        mMediaRecorder.setAudioEncodingBitRate(16000);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//
//        Log.v(TAG, "Audio params done, setting up video...");

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(8 * 1000 * 100);
        mMediaRecorder.setVideoFrameRate(15);
        mMediaRecorder.setVideoSize(displayWidth/4, displayHeight/4);





        Log.v(TAG, "Recorder parameters done");

        String videoDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES).getAbsolutePath();
        Long timestamp = System.currentTimeMillis();

        String orientation = "portrait";
        if( displayWidth > displayHeight ) {
            orientation = "landscape";
        }
        String filePathAndName = videoDir + "/time_" + timestamp.toString() + "_mode_" + orientation + ".mp4";

        mMediaRecorder.setOutputFile( filePathAndName );

        Log.v(TAG, "Recorder output path done: " + filePathAndName);

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e){
//            Log.v(TAG,"Thread.sleep interrupted!");
//        }

        Log.v(TAG, "Recorder prepaired");
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        Log.v(TAG, "Media projection ready");
        Surface surface = mMediaRecorder.getSurface(); //Stops here
        Log.v(TAG, "Surface ready");
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("MainActivity",
                displayWidth/4, displayHeight/4, mScreenDensity, VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null);
        Log.v(TAG, "Display ready");
        mMediaRecorder.start();

        Log.v(TAG, "Started recording");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopRecording() {
        Log.v(TAG,"Stopping recording");
        mMediaRecorder.stop();
        mMediaProjection.stop();
        mMediaRecorder.release();
        mVirtualDisplay.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy() {
        Log.v(TAG, "Service stopping");
        stopRecording();
        unregisterReceiver(mScreenStateReceiver);
        stopSelf();
    }
}
