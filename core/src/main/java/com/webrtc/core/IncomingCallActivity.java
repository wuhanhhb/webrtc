package com.webrtc.core;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.webrtc.io.P2PSocket;

import org.json.JSONObject;

public class IncomingCallActivity extends AppCompatActivity {
    private TextView mCallerID;
    private boolean flag;
    private Vibrator vib;
    private MediaPlayer mMediaPlayer;
    static int INRING = R.raw.skype_call;
    private static IncomingCallActivity instance;

    public static IncomingCallActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        Bundle extras = getIntent().getExtras();
        flag = extras.getBoolean("flag");

        this.mCallerID = (TextView) findViewById(R.id.caller_id);
        this.mCallerID.setText(WebRtcService.other);
        mMediaPlayer = new MediaPlayer();
        if (INRING > 0) {
            if (INRING == 0) {
                mMediaPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            } else {
                mMediaPlayer = MediaPlayer.create(this, INRING);
            }
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.start();
        }

        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] pattern = {0, 100, 1000};
        vib.vibrate(pattern, 0);

        instance = this;

        findViewById(R.id.acceptCall).setVisibility(flag ? View.VISIBLE : View.GONE);
    }

    public void acceptCall(View view) {
        vib.cancel();
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
        finish();
        startCallWithFlag(false);
        JSONObject message = new JSONObject();
        WebRtcService.getInstance().emit(P2PSocket.ACCEPT_CALL, message);
    }

    /**
     * Publish a hangup command if rejecting call.
     *
     * @param view
     */
    public void rejectCall(View view) {
        vib.cancel();
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
        JSONObject message = new JSONObject();
        WebRtcService.getInstance().emit(P2PSocket.EJECT_CALL, message);

        finish();
//        Intent intent = new Intent(IncomingCallActivity.this, MainActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(intent);
    }

    public void onAcceptCall() {
        vib.cancel();
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
        finish();
        startCallWithFlag(true);
    }

    private void startCallWithFlag(boolean flag) {
        Intent intent = new Intent(IncomingCallActivity.this, CallActivity.class);
        intent.putExtra("flag", flag);
        //incointent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    public static void startIncomingCall(Context context, boolean flag) {
        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("flag", flag);
        context.startActivity(intent);
    }
}
