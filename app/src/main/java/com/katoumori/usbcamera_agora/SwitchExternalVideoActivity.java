package com.katoumori.usbcamera_agora;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import io.agora.advancedvideo.externvideosource.ExternalVideoInputManager;
import io.agora.advancedvideo.externvideosource.ExternalVideoInputService;
import io.agora.advancedvideo.externvideosource.IExternalVideoInputService;
import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.VideoEncoderConfiguration;

import static android.app.Activity.RESULT_OK;
import static io.agora.api.component.Constant.ENGINE;
import static io.agora.api.component.Constant.TEXTUREVIEW;

import com.katoumori.libusbcamera.UVCCameraFactory;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.io.File;

public class SwitchExternalVideoActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = SwitchExternalVideoActivity.class.getSimpleName();

    private RelativeLayout fl_local;
    private FrameLayout fl_uvc;
    private Button join, localVideo, usbVideo, screenshare;
    private EditText et_channel;
    private int myUid;
    private boolean joined = false;
    private static final String VIDEO_NAME = "localvideo.mp4";
    private static final int PROJECTION_REQ_CODE = 1 << 2;
    private static final int DEFAULT_SHARE_FRAME_RATE = 15;
    /**
     * The developers should defines their video dimension, for the
     * video info cannot be obtained before the video is extracted.
     */
    private static final int LOCAL_VIDEO_WIDTH = 1280;
    private static final int LOCAL_VIDEO_HEIGHT = 720;
    private String mLocalVideoPath;
    private boolean mLocalVideoExists = false;
    private IExternalVideoInputService mService;
    private VideoInputServiceConnection mServiceConnection;

    private myVideoSource videoSource;

    private UVCCameraFactory mCameraFactory;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_switch_external_video);
        join = findViewById(R.id.btn_join);
        localVideo = findViewById(R.id.localVideo);
        usbVideo = findViewById(R.id.usbVideo);
        screenshare = findViewById(R.id.screenshare);
        et_channel = findViewById(R.id.et_channel);
        fl_local = findViewById(R.id.fl_local);
        fl_uvc = findViewById(R.id.fl_uvc);
//        fl_uvc.setVisibility(View.INVISIBLE);
        join.setOnClickListener(this);
        localVideo.setOnClickListener(this);
        usbVideo.setOnClickListener(this);
        screenshare.setOnClickListener(this);
        checkLocalVideo();

        initUVC();
        initEngine();
    }

    private void initUVC() {
        mCameraFactory = UVCCameraFactory.getInstance();
        mCameraFactory.init(this,R.id.camera_view);
    }

    private void initEngine(){
        try {
            /**Creates an RtcEngine instance.
             * @param context The context of Android Activity
             * @param appId The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id">
             *              How to get the App ID</a>
             * @param handler IRtcEngineEventHandler is an abstract class providing default implementation.
             *                The SDK uses this class to report to the app on SDK runtime events.*/
            ENGINE = RtcEngine.create(getApplicationContext(), getString(R.string.agora_app_id), iRtcEngineEventHandler);
            videoSource = new myVideoSource(this,mCameraFactory);
        }
        catch (Exception e) {
            e.printStackTrace();
            onBackPressed();
        }
    }

    private boolean checkLocalVideo() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        File videoFile = new File(dir, VIDEO_NAME);
        mLocalVideoPath = videoFile.getAbsolutePath();
        mLocalVideoExists = videoFile.exists();
        if (!mLocalVideoExists) {
            showAlert(String.format(getString(R.string.alert_no_local_video_message), mLocalVideoPath));
        }
        return mLocalVideoExists;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PROJECTION_REQ_CODE && resultCode == RESULT_OK) {
            try {
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                data.putExtra(ExternalVideoInputManager.FLAG_SCREEN_WIDTH, metrics.widthPixels);
                data.putExtra(ExternalVideoInputManager.FLAG_SCREEN_HEIGHT, metrics.heightPixels);
                data.putExtra(ExternalVideoInputManager.FLAG_SCREEN_DPI, (int) metrics.density);
                data.putExtra(ExternalVideoInputManager.FLAG_FRAME_RATE, DEFAULT_SHARE_FRAME_RATE);

                setVideoConfig(ExternalVideoInputManager.TYPE_SCREEN_SHARE, metrics.widthPixels, metrics.heightPixels);
                mService.setExternalVideoInput(ExternalVideoInputManager.TYPE_SCREEN_SHARE, data);
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_join) {
            if (!joined) {
                CommonUtil.hideInputBoard(this, et_channel);
                /**Instantiate the view ready to display the local preview screen*/
                TEXTUREVIEW = new TextureView(this);
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
                joined = false;
                join.setText(getString(R.string.join));
                localVideo.setEnabled(false);
                screenshare.setEnabled(false);
                usbVideo.setEnabled(false);
                fl_local.removeAllViews();
                fl_uvc.setVisibility(View.INVISIBLE);
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
                ENGINE.leaveChannel();
                TEXTUREVIEW = null;
                unbindVideoService();
            }
        } else if (v.getId() == R.id.localVideo) {
            fl_uvc.setVisibility(View.INVISIBLE);
            fl_local.setVisibility(View.VISIBLE);
            try {
                Intent intent = new Intent();
                setVideoConfig(ExternalVideoInputManager.TYPE_LOCAL_VIDEO, LOCAL_VIDEO_WIDTH, LOCAL_VIDEO_HEIGHT);
                intent.putExtra(ExternalVideoInputManager.FLAG_VIDEO_PATH, mLocalVideoPath);
                if (mService.setExternalVideoInput(ExternalVideoInputManager.TYPE_LOCAL_VIDEO, intent)) {
                    fl_local.removeAllViews();
                    fl_local.addView(TEXTUREVIEW,
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.MATCH_PARENT);
                }
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (v.getId() == R.id.screenshare) {
            fl_uvc.setVisibility(View.INVISIBLE);
            fl_local.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                /**remove local preview*/
                fl_local.removeAllViews();
                /***/
                MediaProjectionManager mpm = (MediaProjectionManager)
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                Intent intent = mpm.createScreenCaptureIntent();
                startActivityForResult(intent, PROJECTION_REQ_CODE);
            } else {
                showAlert(getString(R.string.lowversiontip));
            }
        } else if(v.getId() == R.id.usbVideo) {
            fl_uvc.setVisibility(View.VISIBLE);
            fl_local.setVisibility(View.INVISIBLE);
            try {
                if(mService.setExternalVideoInput(ExternalVideoInputManager.TYPE_USB_CAMERA,null)){
                    if(videoSource!=null){
                        ENGINE.setVideoSource(videoSource);
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
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
        ENGINE.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                new VideoEncoderConfiguration.VideoDimensions(width, height),
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE, mode
        ));
    }

    private void joinChannel(String channelId) {

        ENGINE.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        /**Sets the role of a user (Live Broadcast only).*/
        ENGINE.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
        /**Enable video module*/
        ENGINE.enableVideo();
        /**Set up to play remote sound with receiver*/
        ENGINE.setDefaultAudioRoutetoSpeakerphone(true);
        ENGINE.setEnableSpeakerphone(false);

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
        int res = ENGINE.joinChannel(accessToken, channelId, "Extra Optional Data", 0);
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

    private void bindVideoService() {
        Intent intent = new Intent();
        intent.setClass(this, ExternalVideoInputService.class);
        mServiceConnection = new VideoInputServiceConnection();
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindVideoService() {
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
            mServiceConnection = null;
        }
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
                localVideo.setEnabled(mLocalVideoExists);
                screenshare.setEnabled(true);
                usbVideo.setEnabled(true);
                bindVideoService();
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

    private class VideoInputServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = (IExternalVideoInputService) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    }


    @Override
    public void onDestroy() {
        unbindVideoService();
        TEXTUREVIEW = null;
        /**leaveChannel and Destroy the RtcEngine instance*/
        if (ENGINE != null) {
            ENGINE.leaveChannel();
        }
        handler.post(RtcEngine::destroy);
        ENGINE = null;
        super.onDestroy();
    }

    protected void showAlert(String message)
    {
        new AlertDialog.Builder(this).setTitle("Tips").setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    protected final void showLongToast(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
