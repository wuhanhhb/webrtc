package com.webrtc.core;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

public class CallActivity extends Activity implements WebRtcService.RtcListener {
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
    //    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private boolean flag;
    private CallAudioManager audioManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);

        vsv = findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        flag = intent.getBooleanExtra("flag", false);

        audioManager = CallAudioManager.create(this);
        audioManager.init();
    }

    private void init() {
        if (!WebRtcService.getInstance().createClient(this, this, VideoRendererGui.getEglBaseContext())) {
//        if (!WebRtcService.getInstance().createClient(this, this, VideoRendererGui.getEGLContext())) {
            Log.d(WebRtcService.TAG, "failed to create client!");
            finish();
        } else {
            Log.d(WebRtcService.TAG, "Success to create client! :" + WebRtcService.other);
            try {
                WebRtcService.getInstance().readyToCall();
                if (flag) {
                    WebRtcService.getInstance().startCall();
                }
//                WebRtcService.getInstance().emit("testStream", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
//            if (callerId != null) {
//                try {
//                    WebRtcService.getInstance().startCall(callerId);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        WebRtcService.getInstance().pauseCall();
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
        WebRtcService.getInstance().resumeCall();
    }

    @Override
    public void onDestroy() {
        try {
            WebRtcService.getInstance().stopCall();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onDestroy();
        audioManager.close();
        //how to restart service again!!!
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public VideoRenderer.Callbacks getVideoRenderer() {
        return localRender;
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        remoteStream.audioTracks.get(0).setEnabled(true);
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType, false);
    }

    @Override
    public void onRemoveRemoteStream() {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
    }

    @Override
    public void onDisConnected() {
        rejectCall(null);
    }

    public void rejectCall(View view) {
        WebRtcService.getInstance().pauseCall();
        onDestroy();
    }
}