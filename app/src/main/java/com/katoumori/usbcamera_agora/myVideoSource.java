package com.katoumori.usbcamera_agora;

import androidx.appcompat.app.AppCompatActivity;

import com.katoumori.libusbcamera.UVCCameraFactory;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;

import io.agora.rtc.mediaio.IVideoFrameConsumer;
import io.agora.rtc.mediaio.IVideoSource;
import io.agora.rtc.mediaio.MediaIO;

public class myVideoSource implements IVideoSource {
    private volatile IVideoFrameConsumer mConsumer;
    private AppCompatActivity mActivity;
    private UVCCameraFactory mUVCCameraFactory;


    public myVideoSource(AppCompatActivity activity, UVCCameraFactory uvcCameraFactory) {
        mActivity = activity;
        mUVCCameraFactory = uvcCameraFactory;
    }

    @Override
    public boolean onInitialize(IVideoFrameConsumer consumer) {
        mConsumer = consumer;
        return true;
    }

    @Override
    public boolean onStart() {
        mUVCCameraFactory.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] data) {
                mConsumer.consumeByteArrayFrame(data,MediaIO.PixelFormat.NV21.intValue(),1280,720,0,System.currentTimeMillis());
            }
        });
        return true;
    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDispose() {
        mConsumer = null;
    }

    @Override
    public int getBufferType() {
        return MediaIO.BufferType.BYTE_ARRAY.intValue();
    }

    @Override
    public int getCaptureType() {
        return MediaIO.CaptureType.CAMERA.intValue();
    }

    @Override
    public int getContentHint() {
        return MediaIO.ContentHint.NONE.intValue();
    }
}
