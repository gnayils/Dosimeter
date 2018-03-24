package com.gnayils.dosimeter;

/**
 * Created by Gnayils on 18/3/2018.
 */

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback  {

    public static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder mHolder;
    private Camera mCamera;

    private byte[] frameBuffer;

    private HandlerThread handlerThread;
    private Handler handler;

    public CameraPreview(Context context) {
        super(context);

        handlerThread = new HandlerThread("CameraBackground");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            handler.post(new Runnable() {

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
        frameBuffer = new byte[parameters.getPreviewSize().width * parameters.getPreviewSize().height * 3 / 2];
        List<String> allFocus = parameters.getSupportedFocusModes();
        if(allFocus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if(allFocus.contains(Camera.Parameters.FLASH_MODE_AUTO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        mCamera.setParameters(parameters);
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

    private boolean hasCamera() {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        System.out.println(Thread.currentThread().getName() + ", on preview frame: " + camera.getParameters().getPreviewFormat() + " " + camera.getParameters().getPreviewSize().width + " " + camera.getParameters().getPreviewSize().height);
        mCamera.addCallbackBuffer(frameBuffer);
    }
}
