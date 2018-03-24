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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback  {

    public static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder mHolder;
    private Camera mCamera;

    private int previewWidth;
    private int previewHeight;
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
        List<String> allFocus = parameters.getSupportedFocusModes();
        if(allFocus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if(allFocus.contains(Camera.Parameters.FLASH_MODE_AUTO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        Camera.Size miniSize = Collections.min(parameters.getSupportedPreviewSizes(), new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                return Integer.signum(o1.width * o1.height -  o2.width * o2.height);
            }
        });
        parameters.setPreviewSize(miniSize.width, miniSize.height);
        mCamera.setParameters(parameters);

        previewWidth = parameters.getPreviewSize().width;
        previewHeight = parameters.getPreviewSize().height;
        frameBuffer = new byte[previewWidth * previewHeight * 3 / 2];


//        System.out.println("parameters.getPreviewFormat(): " + parameters.get("preview-format"));
//        for(Camera.Size size : parameters.getSupportedPictureSizes()) {
//            System.out.println(size.width + " " + size.height);
//        }
//        System.out.println(parameters.getPreviewSize().width + " " + parameters.getPreviewSize().height);

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

    int i = 0;
    boolean b = true;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if(b && i % 50 == 0) {
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < previewWidth * previewHeight; i++) {
                int luminance = data[i] & 0xFF;
                char c;
                if (luminance < 0x40) {
                    c = '#';
                } else if (luminance < 0x80) {
                    c = '+';
                } else if (luminance < 0xC0) {
                    c = '.';
                } else {
                    c = ' ';
                }
                sb.append(c);
                if(i > 0 && i % previewWidth == 0) {
                    System.out.println(sb.toString());
                    sb = new StringBuilder();
                }
            }
        }
        i ++ ;
        System.out.println(Thread.currentThread().getName());
        mCamera.addCallbackBuffer(frameBuffer);
    }
}
