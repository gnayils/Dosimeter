package com.gnayils.dosimeter;

/**
 * Created by Gnayils on 18/3/2018.
 */

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback  {

    public static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder mHolder;
    private Camera mCamera;

    private int previewWidth;
    private int previewHeight;
    private byte[] frameBuffer;

    private boolean isCalculateDose;
    private int pixelCount;
    private double maxLuminance;

    private HandlerThread cameraHandlerThread;
    private Handler cameraHandler;

    private Handler messageHandler;

    private static final double COEFFICIENT_A = 13;
    private static final double COEFFICIENT_B = 0.8956;
    private static final double COEFFICIENT_C = -2.223;
    private static final int LUMINANCE_VALUE_THRESHOLD = 13;

    private String sdcardPath;

    public CameraPreview(Context context, Handler messageHandler) {
        super(context);
        this.messageHandler = messageHandler;

        cameraHandlerThread = new HandlerThread("CameraBackground");
        cameraHandlerThread.start();
        cameraHandler = new Handler(cameraHandlerThread.getLooper());

        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            cameraHandler.post(new Runnable() {

                @Override
                public void run() {
                    mCamera = Camera.open();
                    synchronized (CameraPreview.this) { CameraPreview.this.notifyAll();}
                }
            });
            synchronized (this) { wait(); }
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<String> allFocus = parameters.getSupportedFocusModes();
        if(allFocus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if(allFocus.contains(Camera.Parameters.FLASH_MODE_AUTO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        /**
        Camera.Size miniSize = Collections.min(parameters.getSupportedPreviewSizes(), new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                return Integer.signum(o1.width * o1.height -  o2.width * o2.height);
            }
        });*/



        parameters.setPreviewSize(1280, 720);
        mCamera.setParameters(parameters);

        previewWidth = parameters.getPreviewSize().width;
        previewHeight = parameters.getPreviewSize().height;
        frameBuffer = new byte[previewWidth * previewHeight * 3 / 2];
        pixelCount = previewWidth * previewHeight;
        maxLuminance = pixelCount * 255;


        /**
        System.out.println("parameters.getPreviewFormat(): " + parameters.get("preview-format"));
        for(Camera.Size size : parameters.getSupportedPictureSizes()) {
            System.out.println(size.width + " " + size.height);
        }
        System.out.println(parameters.getPreviewSize().width + " " + parameters.getPreviewSize().height);*/

    }


    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mHolder.getSurface() == null){
            return;
        }
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            e.printStackTrace();
        }
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.addCallbackBuffer(frameBuffer);
            mCamera.startPreview();
        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null){
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
        }
    }

    public void toggleDoseCalculation() {
        isCalculateDose = !isCalculateDose;
    }

    private boolean hasCamera() {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    private List<long[]> list = new ArrayList<>();

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mCamera.addCallbackBuffer(frameBuffer);
        if(!isCalculateDose) return;

        int totalLuminanceValuePerFrame = 0;
        int totalCountPerFrame = 0;
        for(int i = 0; i < pixelCount; i++) {
            if(data[i] >= LUMINANCE_VALUE_THRESHOLD) {
                totalCountPerFrame ++;
                totalLuminanceValuePerFrame += data[i] & 0xff;
            }
        }
        if(totalCountPerFrame > 4) {
            list.add(new long[] {totalLuminanceValuePerFrame, totalCountPerFrame});
        }

        if(list.size() >= 10) {
            long totalCount = 0, totalLuminanceValue = 0;
            for(int i=1; i<list.size() - 2; i++) {
                long[] longs = list.get(i);
                totalLuminanceValue += longs[0];
                totalCount += longs[1];
            }

            double averageLuminanceValue = ((double) totalLuminanceValue) / totalCount;
            double averageCount = ((double)totalCount) / (list.size() - 2);

            double dose = (averageCount / (100 + (averageLuminanceValue - 24) * 98)) * 1.25;
            Message message = messageHandler.obtainMessage();
            message.what = 2;
            message.obj = dose;
            messageHandler.sendMessage(message);
            list.clear();
        }
    }

    private double calculateDose(double count, double mean) {
        return Math.pow(count, COEFFICIENT_B) * Math.pow(Math.max(0, mean - COEFFICIENT_A), COEFFICIENT_C);
    }
}
