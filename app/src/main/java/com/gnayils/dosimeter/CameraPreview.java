package com.gnayils.dosimeter;

/**
 * Created by Gnayils on 18/3/2018.
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** A basic Camera preview class */
public class CameraPreview implements ImageReader.OnImageAvailableListener {

    public static final String TAG = CameraPreview.class.getSimpleName();

    private static final int LUMINANCE_VALUE_THRESHOLD = 25;

    private Activity activity;
    private Handler messageHandler;

    private boolean isCalculateDose;
    private List<long[]> list = new ArrayList<>();

    private Size previewSize = new Size(1280, 720);
    private int pixelCount;
    private byte[] pixelDataBuffer;

    private CaptureRequest previewRequest;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private ImageReader imageReader;

    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private String cameraId;
    private CameraDevice cameraDevice;
    private final CameraDevice.StateCallback cameraOpenCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice device) {
            cameraOpenCloseLock.release();
            cameraDevice = device;
            try {
                previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(imageReader.getSurface());
                cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        if(cameraDevice == null)
                            return;
                        cameraCaptureSession = session;
                        try {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            previewRequest = previewRequestBuilder.build();
                            cameraCaptureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Tooltip.showToast(activity, "Failed");
                    }

                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            cameraOpenCloseLock.release();
            device.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            cameraOpenCloseLock.release();
            device.close();
            cameraDevice = null;
            if (activity != null)
                activity.finish();
        }
    };


    public CameraPreview(Activity activity, Handler messageHandler) {
        this.activity = activity;
        this.messageHandler = messageHandler;
    }

    public void resume() {
        startBackgroundThread();
        openCamera();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void openCamera() {
        if(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            return;
        setUpCameraOutputs();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if(!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
                throw new RuntimeException("Timeout waiting to lock camera opening");
            manager.openCamera(cameraId, cameraOpenCallback, backgroundHandler);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(map == null)
                    continue;

                Size outputSize = null;
                Size[] outputSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                for(Size size : outputSizes) {
                    if(size.equals(previewSize)) {
                        outputSize = size;
                        break;
                    }
                }
                if(outputSize == null) {
                    outputSize = Collections.max(Arrays.asList(outputSizes), new Comparator<Size>() {
                        @Override
                        public int compare(Size lhs, Size rhs) {
                            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
                        }
                    });
                    previewSize = outputSize;
                    Tooltip.showToast(activity, "The preview size of 1280x720 does't supported by your device，the accuracy may affected");
                }

                imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 1);
                imageReader.setOnImageAvailableListener(this, backgroundHandler);

                pixelCount = previewSize.getWidth() * previewSize.getHeight();
                pixelDataBuffer = new byte[pixelCount];

                cameraId = id;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Tooltip.showToast(activity, activity.getString(R.string.camera_error));
        }
    }

    public void pause() {
        closeCamera();
        stopBackgroundThread();
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != cameraCaptureSession) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void toggleDoseCalculation() {
        isCalculateDose = !isCalculateDose;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireNextImage();
        if(!isCalculateDose) {
            image.close();
            return;
        }

        image.getPlanes()[0].getBuffer().get(pixelDataBuffer);
        int totalLuminanceValuePerFrame = 0;
        int totalLuminanceCountPerFrame = 0;
        for(int i = 0; i < pixelCount; i++) {
            if(pixelDataBuffer[i] >= LUMINANCE_VALUE_THRESHOLD) {
                totalLuminanceCountPerFrame ++;
                totalLuminanceValuePerFrame += pixelDataBuffer[i] & 0xff;
            }
        }
        list.add(new long[]{totalLuminanceValuePerFrame, totalLuminanceCountPerFrame});
        System.out.println("count: " + totalLuminanceCountPerFrame + ", value: " + totalLuminanceValuePerFrame);

        if(list.size() >= 10) {
            long totalLuminanceCount = 0, totalLuminanceValue = 0;
            for(long[] longs : list) {
                totalLuminanceValue += longs[0];
                totalLuminanceCount += longs[1];
            }
            double dose = 0.0;
            if(totalLuminanceCount != 0) {
                double averageLuminanceValue = ((double) totalLuminanceValue) / totalLuminanceCount;
                double averageLuminanceCount = ((double) totalLuminanceCount) / list.size();
                dose = (averageLuminanceCount / (100 + (averageLuminanceValue - 24) * 98)) * 1.25;
            }

            Message message = messageHandler.obtainMessage();
            message.obj = dose;
            messageHandler.sendMessage(message);
            list.clear();
        }

        image.close();
    }

    private void onPreviewFrame(byte[] data) {

    }

}
