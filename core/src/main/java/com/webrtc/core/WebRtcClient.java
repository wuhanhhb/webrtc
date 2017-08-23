package com.webrtc.core;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.util.EnumSet;

class WebRtcClient {
    public static final String TAG = WebRtcService.TAG;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_TRACK_TYPE = "video";
    public static final int VIDEO_FPS = 30;
    public static final String VIDEO_CODEC_VP8 = "VP8";
    public static final String VIDEO_CODEC_VP9 = "VP9";
    public static final String VIDEO_CODEC_H264 = "H264";
    public static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    public static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    public static final String AUDIO_CODEC_OPUS = "opus";
    public static final String AUDIO_CODEC_ISAC = "ISAC";
    public static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    public static final String VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    public static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    public static final String VIDEO_H264_HIGH_PROFILE_FIELDTRIAL =
            "WebRTC-H264HighProfile/Enabled/";
    public static final String DISABLE_WEBRTC_AGC_FIELDTRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";
    public static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    public static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    public static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    public static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    public static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    public static final String AUDIO_LEVEL_CONTROL_CONSTRAINT = "levelControl";
    public static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
    public static final int HD_VIDEO_WIDTH = 1280;
    public static final int HD_VIDEO_HEIGHT = 720;
    public static final int BPS_IN_KBPS = 1000;

    private final static String[] AUDIO_PARAMS = {
            "levelControl",
            //"echoCancellation",
//            "googEchoCancellation",
            //"googEchoCancellation2",
//            "googNoiseSuppression",
            //"googNoiseSuppression2",
//            "googHighpassFilter",
//            "googTypingNoiseDetection"
    };
    private PeerConnectionFactory factory;
    private PeerConnectionParameters pcParams;
    //    private PeerConnection peerConnection;
    private MediaStream localMS;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private AudioSource audioSource;

    public WebRtcClient(Context context, PeerConnectionParameters params, EglBase.Context gGLcontext) {
//        PeerConnectionFactory.initializeAndroidGlobals(context, true, true,
//                params.videoCodecHwAcceleration, mEGLcontext);
        PeerConnectionFactory.initializeInternalTracer();
        PeerConnectionFactory.startInternalTracingCapture(
                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                        + "webrtc-trace.txt");
        // Initialize field trials.
        String fieldTrials = "";
//        if (peerConnectionParameters.videoFlexfecEnabled) {
//            fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
//            Log.d(TAG, "Enable FlexFEC field trial.");
//        }
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
//        if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
//            fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
//            Log.d(TAG, "Disable WebRTC AGC field trial.");
//        }

        // Check preferred video codec.
        PeerConnectionFactory.initializeFieldTrials(fieldTrials);
        Log.d(TAG, "Field trials: " + fieldTrials);

        PeerConnectionFactory.initializeAndroidGlobals(context, true, true, params.videoCodecHwAcceleration);
        pcParams = params;

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = new PeerConnectionFactory(options);
        factory.setVideoHwAccelerationOptions(gGLcontext, gGLcontext);
        // Set default WebRTC tracing and INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_WARNING, Logging.TraceLevel.TRACE_ERROR));
        Logging.enableLogToDebugOutput(Logging.Severity.LS_ERROR);
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void close() {
//        if (peerConnection != null) {
//            peerConnection.dispose();
//            peerConnection = null;
//        }
        Log.d(TAG, "Closing audio source.");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        Log.d(TAG, "Stopping capture.");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        Log.d(TAG, "Closing video source.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        Log.d(TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        Log.d(TAG, "Closing peer connection done.");
    }

    private void start(VideoRenderer.Callbacks renderer) {
        MediaStream localMS = factory.createLocalMediaStream("ARDAMS");
        if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            final VideoTrack videoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            videoTrack.setEnabled(true);
            videoTrack.addRenderer(new VideoRenderer(renderer));
            localMS.addTrack(videoTrack);
        }
        //TODO
        MediaConstraints audioConstraints = new MediaConstraints();
        if (AUDIO_PARAMS != null && AUDIO_PARAMS.length > 0) {
            for (String param : AUDIO_PARAMS) {
                audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(param, "true"));
            }
        }
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("echoCancellation", "false"));
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "false"));
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation2", "false"));
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"));
        //audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("levelControl", "true"));
        audioSource = factory.createAudioSource(audioConstraints);
        AudioTrack audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        boolean ret = audioTrack.setEnabled(true);
        ret &= localMS.addTrack(audioTrack);
        this.localMS = localMS;
    }

    private VideoCapturer getVideoCapturer() {
//        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        String frontCameraDeviceName = CameraEnumerationAndroid.getNameOfFrontFacingDevice();
        return videoCapturer = VideoCapturerAndroid.create(frontCameraDeviceName, null);
    }

    public PeerConnection createPeerConnection(PeerConnection.Observer observer, VideoRenderer.Callbacks callbacks) {
        start(callbacks);
        final PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(WebRtcService.getInstance().getIceServers());
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        return factory.createPeerConnection(rtcConfig, WebRtcService.getInstance().getPcConstraints(), observer);
    }

    public MediaStream getLocalMS() {
        return localMS;
    }
}
