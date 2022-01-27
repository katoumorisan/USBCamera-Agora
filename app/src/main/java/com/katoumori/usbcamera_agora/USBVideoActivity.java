package com.katoumori.usbcamera_agora;

import static io.agora.rtc.video.VideoCanvas.RENDER_MODE_HIDDEN;
import static io.agora.rtc.video.VideoEncoderConfiguration.STANDARD_BITRATE;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.opengl.EGLSurface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.katoumori.libusbcamera.UVCCameraHelper;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.models.ChannelMediaOptions;
import io.agora.rtc.video.AgoraVideoFrame;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class USBVideoActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = USBVideoActivity.class.getSimpleName();
//    private final int DEFAULT_CAPTURE_WIDTH = 640;
//    private final int DEFAULT_CAPTURE_HEIGHT = 480;
//    private final int DEFAULT_CAPTURE_WIDTH = 1280;
//    private final int DEFAULT_CAPTURE_HEIGHT = 720;
    private final int DEFAULT_CAPTURE_WIDTH = 640;
    private final int DEFAULT_CAPTURE_HEIGHT = 360;

    private FrameLayout fl_local, fl_remote;
    private Button join;
    private EditText et_channel;
    private RtcEngine engine;
    private int myUid;
    private volatile boolean joined = false;

    private int mPreviewTexture;
    private SurfaceTexture mPreviewSurfaceTexture;
//    private EglCore mEglCore;
    private EGLSurface mDummySurface;
    private EGLSurface mDrawSurface;
//    private ProgramTextureOES mProgram;
    private float[] mTransform = new float[16];
    private float[] mMVPMatrix = new float[16];
    private boolean mMVPMatrixInit = false;
    private Camera mCamera;
    private int mFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private boolean mPreviewing = false;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private boolean mTextureDestroyed;

    public View mTextureView;
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private AlertDialog mDialog;

    private boolean isRequest;
    private boolean isPreview;
//    private UVCCameraTextureView uvcCameraTextureView;

    private Handler handler = new Handler(Looper.getMainLooper());

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
        }
    };

    private CameraViewInterface.Callback callback = new CameraViewInterface.Callback() {
        @Override
        public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
            if (!isPreview && mCameraHelper.isCameraOpened()) {
                mCameraHelper.startPreview(mUVCCameraView);
                isPreview = true;
            }
        }

        @Override
        public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

        }

        @Override
        public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
            if (isPreview && mCameraHelper.isCameraOpened()) {
                mCameraHelper.stopPreview();
                isPreview = false;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_externalvideo);
        join = findViewById(R.id.btn_join);
        et_channel = findViewById(R.id.et_channel);
        findViewById(R.id.btn_join).setOnClickListener(this);
        fl_local = findViewById(R.id.fl_local);
        fl_remote = findViewById(R.id.fl_remote);

        initUVC();
        initEngine();

    }

    private void initUVC(){
        mTextureView = findViewById(R.id.camera_view);
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(callback);

        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
        mCameraHelper.updateResolution(DEFAULT_CAPTURE_WIDTH,DEFAULT_CAPTURE_HEIGHT);
        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
                Log.d(TAG, "onPreviewResult: "+nv21Yuv.length);
                if(joined){
                    AgoraVideoFrame frame = new AgoraVideoFrame();
//                    frame.textureID = mPreviewTexture;
                    frame.format = AgoraVideoFrame.FORMAT_NV21;
//                    frame.transform = mTransform;
                    frame.stride = DEFAULT_CAPTURE_WIDTH;
                    frame.height = DEFAULT_CAPTURE_HEIGHT;
//                    frame.eglContext14 = mEglCore.getEGLContext();
                    frame.timeStamp = System.currentTimeMillis();
                    /**Pushes the video frame using the AgoraVideoFrame class and passes the video frame to the Agora SDK.
                     * Call the setExternalVideoSource method and set pushMode as true before calling this
                     * method. Otherwise, a failure returns after calling this method.
                     * @param frame AgoraVideoFrame
                     * @return
                     *   true: The frame is pushed successfully.
                     *   false: Failed to push the frame.
                     * PS:
                     *   In the Communication profile, the SDK does not support textured video frames.*/
                    frame.buf = nv21Yuv;
                    boolean a = engine.pushExternalVideoFrame(frame);
                    Log.e(TAG, "pushExternalVideoFrame:" + a);
                }
            }
        });
    }


    private void initEngine(){
        try {
            /**Creates an RtcEngine instance.
             * @param context The context of Android Activity
             * @param appId The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id">
             *              How to get the App ID</a>
             * @param handler IRtcEngineEventHandler is an abstract class providing default implementation.
             *                The SDK uses this class to report to the app on SDK runtime events.*/
            engine = RtcEngine.create(getApplicationContext(), getString(R.string.agora_app_id), iRtcEngineEventHandler);
        }
        catch (Exception e) {
            e.printStackTrace();
            onBackPressed();
        }
    }

    private void joinChannel(String channelId) {
//        engine.setParameters("{\"rtc.log_filter\":65535}");
        // Check if the context is valid
//        Context context = getContext();
//        if (context == null) {
//            return;
//        }

        // Create render view by RtcEngine
//        TextureView textureView = new TextureView(this);

        //add SurfaceTextureListener
//        textureView.setSurfaceTextureListener(this);
        // Add to the local container
//        fl_local.addView(mTextureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT));
        /**Set up to play remote sound with receiver*/
        engine.setDefaultAudioRoutetoSpeakerphone(true);
        engine.setEnableSpeakerphone(false);

        /** Sets the channel profile of the Agora RtcEngine.
         CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
         Use this profile in one-on-one calls or group calls, where all users can talk freely.
         CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
         channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
         an audience can only receive streams.*/
        engine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        /**In the demo, the default is to enter as the anchor.*/
        engine.setClientRole(IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_BROADCASTER);
        // Enables the video module.
        engine.enableVideo();
        // Setup video encoding configs
        engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                ((MainApplication)getApplication()).getGlobalSettings().getVideoEncodingDimensionObject(),
                VideoEncoderConfiguration.FRAME_RATE.valueOf(((MainApplication)getApplication()).getGlobalSettings().getVideoEncodingFrameRate()),
                STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.valueOf(((MainApplication)getApplication()).getGlobalSettings().getVideoEncodingOrientation())
        ));
        /**Configures the external video source.
         * @param enable Sets whether or not to use the external video source:
         *                 true: Use the external video source.
         *                 false: Do not use the external video source.
         * @param useTexture Sets whether or not to use texture as an input:
         *                     true: Use texture as an input.
         *                     false: (Default) Do not use texture as an input.
         * @param pushMode Sets whether or not the external video source needs to call the PushExternalVideoFrame
         *                 method to send the video frame to the Agora SDK:
         *                   true: Use the push mode.
         *                   false: Use the pull mode (not supported).*/
        engine.setExternalVideoSource(true, false, true);

        /**Please configure accessToken in the string_config file.
         * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
         *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
         * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
         *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/
        String accessToken = getString(R.string.agora_access_token);
        if (TextUtils.equals(accessToken, "") || TextUtils.equals(accessToken, "<#YOUR ACCESS TOKEN#>")) {
            accessToken = null;
        }
        /** Allows a user to join a channel.
         if you do not specify the uid, we will generate the uid for you*/

        ChannelMediaOptions option = new ChannelMediaOptions();
        option.autoSubscribeAudio = true;
        option.autoSubscribeVideo = true;
        int res = engine.joinChannel(accessToken, channelId, "Extra Optional Data", 0, option);
        if (res != 0) {
            // Usually happens with invalid parameters
            // Error code description can be found at:
            // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
            // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
            showAlert(RtcEngine.getErrorDescription(Math.abs(res)));
            return;
        }
        // Prevent repeated entry
        join.setEnabled(false);
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_join) {
            if (!joined) {
                CommonUtil.hideInputBoard(USBVideoActivity.this, et_channel);
                // call when join button hit
                String channelId = et_channel.getText().toString();
                // Check permission
                if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE, Permission.Group.CAMERA)) {
                    joinChannel(channelId);
                    return;
                }
                // Request permission
                AndPermission.with(this).runtime().permission(
                        Permission.Group.STORAGE,
                        Permission.Group.MICROPHONE,
                        Permission.Group.CAMERA
                ).onGranted(permissions ->
                {
                    // Permissions Granted
                    joinChannel(channelId);
                }).start();
            } else {
                fl_local.setVisibility(View.GONE);
                onBackPressed();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    @Override
    public void onDestroy() {
        /**leaveChannel and Destroy the RtcEngine instance*/
        if (engine != null) {
            /**After joining a channel, the user must call the leaveChannel method to end the
             * call before joining another channel. This method returns 0 if the user leaves the
             * channel and releases all resources related to the call. This method call is
             * asynchronous, and the user has not exited the channel when the method call returns.
             * Once the user leaves the channel, the SDK triggers the onLeaveChannel callback.
             * A successful leaveChannel method call triggers the following callbacks:
             *      1:The local client: onLeaveChannel.
             *      2:The remote client: onUserOffline, if the user leaving the channel is in the
             *          Communication channel, or is a BROADCASTER in the Live Broadcast profile.
             * @returns 0: Success.
             *          < 0: Failure.
             * PS:
             *      1:If you call the destroy method immediately after calling the leaveChannel
             *          method, the leaveChannel process interrupts, and the SDK does not trigger
             *          the onLeaveChannel callback.
             *      2:If you call the leaveChannel method during CDN live streaming, the SDK
             *          triggers the removeInjectStreamUrl method.*/
            engine.leaveChannel();
        }
        handler.post(RtcEngine::destroy);
        engine = null;
        super.onDestroy();
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
    }

    /**
     * IRtcEngineEventHandler is an abstract class providing default implementation.
     * The SDK uses this class to report to the app on SDK runtime events.
     */
    private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler() {
        /**Reports a warning during SDK runtime.
         * Warning code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_warn_code.html*/
        @Override
        public void onWarning(int warn) {
            Log.w(TAG, String.format("onWarning code %d message %s", warn, RtcEngine.getErrorDescription(warn)));
        }

        /**Reports an error during SDK runtime.
         * Error code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html*/
        @Override
        public void onError(int err) {
            Log.e(TAG, String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
            showAlert(String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
        }

        /**Occurs when a user leaves the channel.
         * @param stats With this callback, the application retrieves the channel information,
         *              such as the call duration and statistics.*/
        @Override
        public void onLeaveChannel(RtcStats stats) {
            super.onLeaveChannel(stats);
            Log.i(TAG, String.format("local user %d leaveChannel!", myUid));
            showLongToast(String.format("local user %d leaveChannel!", myUid));
        }

        /**Occurs when the local user joins a specified channel.
         * The channel name assignment is based on channelName specified in the joinChannel method.
         * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
         * @param channel Channel name
         * @param uid User ID
         * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered*/
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            showLongToast(String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            myUid = uid;
            joined = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    join.setEnabled(true);
                    join.setText(getString(R.string.leave));
                }
            });
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         * @param uid ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.*/
        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);
            showLongToast(String.format("user %d joined!", uid));
            /**Check if the context is correct*/
            Context context = USBVideoActivity.this;
            if (context == null) {
                return;
            }
            handler.post(() ->
            {
                /**Display remote video stream*/
                // Create render view by RtcEngine
                SurfaceView surfaceView = RtcEngine.CreateRendererView(context);
                surfaceView.setZOrderMediaOverlay(true);
                if (fl_remote.getChildCount() > 0) {
                    fl_remote.removeAllViews();
                }
                // Add to the remote container
                fl_remote.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                // Setup remote video to render
                engine.setupRemoteVideo(new VideoCanvas(surfaceView, RENDER_MODE_HIDDEN, uid));
            });
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
         * @param uid ID of the user whose audio state changes.
         * @param reason Reason why the user goes offline:
         *   USER_OFFLINE_QUIT(0): The user left the current channel.
         *   USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
         *              packet was received within a certain period of time. If a user quits the
         *               call and the message is not passed to the SDK (due to an unreliable channel),
         *               the SDK assumes the user dropped offline.
         *   USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
         *               the host to the audience.*/
        @Override
        public void onUserOffline(int uid, int reason) {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
            showLongToast(String.format("user %d offline! reason:%d", uid, reason));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    /**Clear render view
                     Note: The video will stay at its last frame, to completely remove it you will need to
                     remove the SurfaceView from its parent*/
                    engine.setupRemoteVideo(new VideoCanvas(null, RENDER_MODE_HIDDEN, uid));
                }
            });
        }
    };
    protected void showAlert(String message)
    {
        Context context = USBVideoActivity.this;
        if (context == null) {
            return;
        }

        new AlertDialog.Builder(context).setTitle("Tips").setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    protected final void showLongToast(final String msg)
    {
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (USBVideoActivity.this == null )
                {return;}
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }
    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
