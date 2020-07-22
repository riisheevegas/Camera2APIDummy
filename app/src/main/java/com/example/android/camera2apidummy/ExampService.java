package com.example.android.camera2apidummy;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;

import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.example.android.camera2apidummy.ServiceActivity.REQUEST_CAMERA_PERMISSION_RESULT;

public class ExampService extends Service {
    ViewGroup v;
    TextureView mTextureView;
    WindowManager wm;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private static final Object CHANNEL_ID = "examp_service_id";
    static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }
    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.d("Rishi","Tanuj");
            setupCamera(i, i1);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }


        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };
    CameraDevice mCameraDevice;
    CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
           // Log.d("Rishi","Tanuj");
            mCameraDevice = cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent,int flags, int startId) {
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        v = (ViewGroup) layoutInflater.inflate(R.layout.activity_service, null, false);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                , WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 10;
        params.y = 100;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.addView(v, params);
        mTextureView = v.findViewById(R.id.textureView);
        startBackgroundThread();
        if(mTextureView.isAvailable()){
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        }else{
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);}
        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
        Log.d("Came","Camess");
        return START_NOT_STICKY;
    }
    @Override
    public void onCreate() {
        startForeground();
        super.onCreate();
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
 public void stopServiceButton(View view) {
       stopSelf();
       wm.removeView(view);
    }
    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2Image");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setupCamera(int width, int height) {
        Log.d("Rishi","Khushi");
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                Log.d("R","K");
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                   // Log.d("R","K");
                    continue;
                }
                //int deviceOrientation = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics,int deviceOrientation){
        int sensorOrientation=cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation=ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation+deviceOrientation+360)%360;
    }
    private static Size chooseOptimalSize(Size[] choices,int width,int height){
        List<Size> bigEnough=new ArrayList<Size>();
        for(Size option:bigEnough){
            if(option.getHeight()==option.getWidth()*height/width&&
                    option.getWidth() >=width && option.getHeight()>=height){
                bigEnough.add(option);
            }
        }
        if(bigEnough.size()>0){
            return Collections.min(bigEnough,new CompareSizeByArea());
        }else{
            return choices[0];
        }
    }
    private void startPreview(){
        SurfaceTexture surfaceTexture=mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface=new Surface(surfaceTexture);
        try {
            mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            try {
                                cameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),null,mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(ExampService.this, "Unable to set up camera preview", Toast.LENGTH_SHORT).show();
                        }
                    },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d("Ca","Camera2");
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            return;
        }
        try {
            Log.d("Camera !!!!","Camera2");
           // Toast.makeText(this, "Camera Connected", Toast.LENGTH_SHORT).show();
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    private void startForeground() {
        Intent notificationIntent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent=PendingIntent.getActivity(this, 0, notificationIntent, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel=new NotificationChannel(
                    (String) CHANNEL_ID,
                    "Example Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }

        Notification notification= new NotificationCompat.Builder(this, (String) CHANNEL_ID)
                .setContentTitle("Camera Service Started")
                .setContentText("Preview in actvity")
                .setSmallIcon(R.drawable.notification_template_icon_bg)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.app_name))
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        closeCamera();
        stopBackgroundThread();
        super.onDestroy();
    }
}