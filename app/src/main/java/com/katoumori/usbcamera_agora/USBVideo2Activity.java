package com.katoumori.usbcamera_agora;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.katoumori.libusbcamera.UVCCameraFactory;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import io.agora.advancedvideo.externvideosource.ExternalVideoInputManager;
import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.mediaio.IVideoFrameConsumer;
import io.agora.rtc.mediaio.IVideoSource;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class USBVideo2Activity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = USBVideo2Activity.class.getSimpleName();

    private RtcEngine engine;
    private FrameLayout fl_local;
    private Button join, usbVideo;
    private EditText et_channel;
    private boolean joined = false;
    private int myUid;
    private static final int LOCAL_VIDEO_WIDTH = 1280;
    private static final int LOCAL_VIDEO_HEIGHT = 720;

    private Handler handler = new Handler(Looper.getMainLooper());
    private UVCCameraFactory mCameraFactory;
    private myVideoSource videoSource;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediaio_video);
        join = findViewById(R.id.btn_join);
        usbVideo = findViewById(R.id.usbVideo);
        et_channel = findViewById(R.id.et_channel);
        fl_local = findViewById(R.id.fl_local);
        join.setOnClickListener(this);
        usbVideo.setOnClickListener(this);

        initUVC();
        initEngine();

    }

    private void initUVC(){
//        mCameraFactory = UVCCameraFactory.getInstance();
//        mCameraFactory.init(this,R.id.camera_view);
        mCameraFactory = UVCCameraFactory.getInstance();
        mCameraFactory.init(this);
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
        videoSource = new myVideoSource(this,mCameraFactory);
    }

    private void setVideoConfig(int sourceType, int width, int height) {
        VideoEncoderConfiguration.ORIENTATION_MODE mode;
        switch (sourceType) {
            case ExternalVideoInputManager.TYPE_LOCAL_VIDEO:
            case ExternalVideoInputManager.TYPE_SCREEN_SHARE:
                mode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT;
                break;
            default:
                mode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE;
                break;
        }
        /**Setup video stream encoding configs*/
        engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                new VideoEncoderConfiguration.VideoDimensions(width, height),
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE, mode
        ));
    }

    private void joinChannel(String channelId) {

        /** Sets the channel profile of the Agora RtcEngine.
         CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
         Use this profile in one-on-one calls or group calls, where all users can talk freely.
         CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
         channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
         an audience can only receive streams.*/
        engine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        /**Sets the role of a user (Live Broadcast only).*/
        engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
        /**Enable video module*/
        engine.enableVideo();
        /**Set up to play remote sound with receiver*/
        engine.setDefaultAudioRoutetoSpeakerphone(true);
        engine.setEnableSpeakerphone(false);

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
        int res = engine.joinChannel(accessToken, channelId, "Extra Optional Data", 0);
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
            handler.post(() -> {
                join.setEnabled(true);
                join.setText(getString(R.string.leave));
                usbVideo.setEnabled(true);
//                bindVideoService();
            });
        }

        /**Since v2.9.0.
         * Occurs when the remote video state changes.
         * PS: This callback does not work properly when the number of users (in the Communication
         *     profile) or broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         * @param uid ID of the remote user whose video state changes.
         * @param state State of the remote video:
         *   REMOTE_VIDEO_STATE_STOPPED(0): The remote video is in the default state, probably due
         *              to REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3), REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5),
         *              or REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7).
         *   REMOTE_VIDEO_STATE_STARTING(1): The first remote video packet is received.
         *   REMOTE_VIDEO_STATE_DECODING(2): The remote video stream is decoded and plays normally,
         *              probably due to REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY (2),
         *              REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4), REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6),
         *              or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9).
         *   REMOTE_VIDEO_STATE_FROZEN(3): The remote video is frozen, probably due to
         *              REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1) or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8).
         *   REMOTE_VIDEO_STATE_FAILED(4): The remote video fails to start, probably due to
         *              REMOTE_VIDEO_STATE_REASON_INTERNAL(0).
         * @param reason The reason of the remote video state change:
         *   REMOTE_VIDEO_STATE_REASON_INTERNAL(0): Internal reasons.
         *   REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1): Network congestion.
         *   REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY(2): Network recovery.
         *   REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3): The local user stops receiving the remote
         *               video stream or disables the video module.
         *   REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote
         *               video stream or enables the video module.
         *   REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5): The remote user stops sending the video
         *               stream or disables the video module.
         *   REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the video
         *               stream or enables the video module.
         *   REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         *   REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8): The remote media stream falls back to the
         *               audio-only stream due to poor network conditions.
         *   REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9): The remote media stream switches
         *               back to the video stream after the network conditions improve.
         * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method until
         *               the SDK triggers this callback.*/
        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed);
            Log.i(TAG, "onRemoteVideoStateChanged:uid->" + uid + ", state->" + state);
//            if (state == REMOTE_VIDEO_STATE_STARTING) {
//                /**Check if the context is correct*/
//                Context context = getContext();
//                if (context == null) {
//                    return;
//                }
//                handler.post(() ->
//                {
//                    /**Display remote video stream*/
//                    SurfaceView surfaceView = RtcEngine.CreateRendererView(context);
//                    surfaceView.setZOrderMediaOverlay(true);
//                    if (fl_remote.getChildCount() > 0) {
//                        fl_remote.removeAllViews();
//                    }
//                    fl_remote.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
//                            ViewGroup.LayoutParams.MATCH_PARENT));
//                    /**Setup remote video to render*/
//                    ENGINE.setupRemoteVideo(new VideoCanvas(surfaceView, RENDER_MODE_HIDDEN, uid));
//                });
//            }
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
//            showLongToast(String.format("user %d offline! reason:%d", uid, reason));
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    /**Clear render view
//                     Note: The video will stay at its last frame, to completely remove it you will need to
//                     remove the SurfaceView from its parent*/
//                    ENGINE.setupRemoteVideo(new VideoCanvas(null, RENDER_MODE_HIDDEN, uid));
//                    fl_remote.removeAllViews();
//                }
//            });
        }
    };

    protected void showAlert(String message)
    {

        new AlertDialog.Builder(this).setTitle("Tips").setMessage(message)
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
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.leaveChannel();
        }
        handler.post(RtcEngine::destroy);
        engine = null;
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_join) {
            if (!joined) {
                CommonUtil.hideInputBoard(this, et_channel);
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
            }else{
                joined = false;
                join.setText(getString(R.string.join));
                engine.leaveChannel();
            }
        } else if (v.getId() == R.id.usbVideo) {
            setVideoConfig(ExternalVideoInputManager.TYPE_LOCAL_VIDEO, LOCAL_VIDEO_WIDTH, LOCAL_VIDEO_HEIGHT);
            engine.setVideoSource(videoSource);
        }
    }

}
