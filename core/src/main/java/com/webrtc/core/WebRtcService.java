package com.webrtc.core;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.webrtc.io.P2PSocket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoRenderer;

import java.util.HashMap;
import java.util.LinkedList;

import static com.webrtc.core.IncomingCallActivity.startIncomingCall;
import static com.webrtc.core.Util.preferCodec;
import static com.webrtc.core.WebRtcClient.AUDIO_CODEC_OPUS;
import static com.webrtc.core.WebRtcClient.VIDEO_CODEC_VP8;
import static com.webrtc.core.WebRtcClient.VIDEO_FPS;

public class WebRtcService extends Service {
    protected final static String TAG = WebRtcService.class.getCanonicalName();
    private String preferredAudioCodec = AUDIO_CODEC_OPUS;
    private String preferredVideoCodec = VIDEO_CODEC_VP8;

    //every import!
    protected static String self;
    protected static String other;

    private static WebRtcService instance;

    private HashMap<String, Peer> peers = new HashMap<>();
    private MediaConstraints pcConstraints = new MediaConstraints();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();

    WebRtcClient client;
    RtcListener mListener;

    private P2PSocket socket;
//    private final IBinder mBinder = new LocalBinder();

    protected static WebRtcService getInstance() {
        if (instance == null) throw new NullPointerException("WebRtcService is not bind.");
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
//        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        //iceServers.add(new PeerConnection.IceServer("stun:113.57.135.90:33478"));
        //iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        //iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        Log.d(TAG, "WebRtcService initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

//    @Override
//    public boolean onUnbind(Intent intent) {
//        close();
//        instance = null;
//        return super.onUnbind(intent);
//    }
//
//    public class LocalBinder extends Binder {
//        public WebRtcService getService() {
//            return WebRtcService.this;
//        }
//    }

    /**
     * Start WebRtcService with this method.
     */
    public static void startServie(Context context) {
        context.startService(new Intent(context, WebRtcService.class));
    }

    /**
     * like this : A -> B
     * A -> answer -> send init msg -> B
     * B -> recv init,create Peer && add localStream to Peer -> createOffer(SDP)-> setLocalDescription -> offer
     * A -> offer -> setRemoteDescription -> createAnswer -> answer
     * B -> answer -> setRemoteDescription
     */
    /**
     * @param context
     * @param self    yourself's name
     * @param other   who you want to call.
     * @param ring
     */
    public static void startClient(Context context, String self, String other, int ring) {
        if (WebRtcService.getInstance() == null || context == null || WebRtcService.getInstance().socket == null || TextUtils.isEmpty(self) || TextUtils.isEmpty(other)) {
            throw new RuntimeException("NPE Exception here.");
        }
        WebRtcService.self = self;
        WebRtcService.other = other;
        IncomingCallActivity.INRING = ring;

        IncomingCallActivity.startIncomingCall(context, false);

        WebRtcService.getInstance().emit(P2PSocket.SEND_CALL, null);
    }

    /**
     * Must call first
     *
     * @param socket
     */
    public static void configNetWork(P2PSocket socket) {
        WebRtcService.getInstance().socket = socket;
        WebRtcService.getInstance().config();
    }

    public static void addIceService(PeerConnection.IceServer iceServer) {
        if (getInstance() != null) {
            getInstance().iceServers.add(iceServer);
        }
    }

    boolean createClient(RtcListener listener, Activity app, EglBase.Context gGLcontext) {
        if (client == null) {
            Point displaySize = new Point();
            app.getWindowManager().getDefaultDisplay().getSize(displaySize);
            PeerConnectionParameters params = new PeerConnectionParameters(
                    true, false, displaySize.x, displaySize.y, VIDEO_FPS, 1, preferredVideoCodec, true, 1, preferredAudioCodec, true);
            WebRtcClient client = new WebRtcClient(app, params, gGLcontext);
            mListener = listener;
            this.client = client;
            return true;
        } else {
            throw new RuntimeException("Faied to create WebRtcClient!");
        }
    }

//    void readyToCall() throws JSONException {
//        //client.start();
//        JSONObject message = new JSONObject();
//        message.put("name", "android_test");
//        emit("readyToStream", message);
////        if (mListener != null) {
////            mListener.onLocalStream(client.getLocalMS());
////        }
//    }

    void startCall() throws JSONException {
        sendMessage(P2PSocket.INIT, null);
    }

    void stopCall() throws JSONException {
        emit(P2PSocket.CANCEL_CALL, null);
        //TODO
        //maybe crash
        for (Peer peer : peers.values()) {
            peer.pc.dispose();
        }

        Log.e(TAG, "stopCall 2");
        if (client != null) {
            client.close();
        }
        client = null;
        mListener = null;
    }

    void resumeCall() {
        if (client != null) {
            client.onResume();
        }
    }

    void pauseCall() {
        if (client != null) {
            client.onPause();
        }
    }

    /***************************************************WebSocket Module**********************************************/

    private void config() {
        if (socket != null) {
            Log.d(TAG, "try to config " + socket);
            MessageHandler messageHandler = new MessageHandler();
            socket.on(P2PSocket.MESSAGE_CALL, messageHandler.onMessage);
            socket.on(P2PSocket.EJECT_CALL, messageHandler.onEject);
            socket.on(P2PSocket.ACCEPT_CALL, messageHandler.onAccept);
            socket.on(P2PSocket.CANCEL_CALL, messageHandler.onRemoveCall);
            socket.on(P2PSocket.RECV_CALL, messageHandler.onReceiveCall);
        } else {
            throw new RuntimeException("You must archive the socket by yourself!");
        }
    }

    /**
     * Send a message through the signaling server
     *
     * @param type    type of message
     * @param payload payload of message
     * @throws JSONException
     */
    void sendMessage(String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("type", type);
        message.put("payload", payload);
        emit(P2PSocket.MESSAGE_CALL, message);
    }

    void emit(String type, JSONObject payload) {
        socket.emit(type, payload);
    }

    /***************************************************Message Module**********************************************/
    private interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    private class MessageHandler {
        private HashMap<String, Command> commandMap;

        private MessageHandler() {
            this.commandMap = new HashMap<>();
            commandMap.put(P2PSocket.INIT, new CreateOfferCommand());
            commandMap.put(P2PSocket.OFFER, new CreateAnswerCommand());
            commandMap.put(P2PSocket.ANSWER, new SetRemoteSDPCommand());
            commandMap.put(P2PSocket.CANDIDATE, new AddIceCandidateCommand());
        }

        private P2PSocket.Listener onMessage = new P2PSocket.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String type = data.getString("type");
                    JSONObject payload = null;
                    if (!type.equals("init")) {
                        payload = data.getJSONObject("payload");
                    }
                    // if peer is unknown, try to add him
                    if (!peers.containsKey(other)) {
                        // if MAX_PEER is reach, ignore the call
                        addPeer(other);
                        commandMap.get(type).execute(other, payload);
                    } else {
                        commandMap.get(type).execute(other, payload);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        private P2PSocket.Listener onEject = new P2PSocket.Listener() {
            @Override
            public void call(Object... args) {
                if (mListener != null) {
                    mListener.onDisConnected();
                } else {
                    if (IncomingCallActivity.getInstance() != null) {
                        IncomingCallActivity.getInstance().finish();
                    }
                    //android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        };

        private P2PSocket.Listener onRemoveCall = new P2PSocket.Listener() {
            @Override
            public void call(Object... args) {
                if (mListener != null) {
                    mListener.onDisConnected();
                } else {
                    //android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        };

        private P2PSocket.Listener onAccept = new P2PSocket.Listener() {
            @Override
            public void call(Object... args) {
                if (IncomingCallActivity.getInstance() != null)
                    IncomingCallActivity.getInstance().onAcceptCall();

            }
        };

        /**
         * Receive call emitter callback when others call you.
         *
         * @param args json value contain callerid, userid and caller name
         */
        private P2PSocket.Listener onReceiveCall = new P2PSocket.Listener() {
            @Override
            public void call(Object... args) {
                startIncomingCall(getApplicationContext(), true);
            }
        };
    }

    /***************************************************Peer Module**********************************************/
    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String other;

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            // TODO: modify sdp to use pcParams prefered codecs
            try {
                String localSdp = sdp.description;
                localSdp = preferCodec(localSdp, preferredAudioCodec, true);
                localSdp = preferCodec(localSdp, preferredVideoCodec, false);
                //localSdp = setStartBitrate(AUDIO_CODEC_OPUS, false, localSdp, 1);

                JSONObject payload = new JSONObject();
                payload.put("type", sdp.type.canonicalForm());
                payload.put("sdp", localSdp);
                sendMessage(sdp.type.canonicalForm(), payload);
                Log.e(TAG, "onCreateSuccess:" + localSdp);
                SessionDescription localSD = new SessionDescription(sdp.type, localSdp);
                pc.setLocalDescription(Peer.this, localSD);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                //removePeer(id);
                //mListener.onStatusChanged("DISCONNECTED");
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("label", candidate.sdpMLineIndex);
                payload.put("id", candidate.sdpMid);
                payload.put("candidate", candidate.sdp);
                sendMessage("candidate", payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(other);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        public Peer(WebRtcClient client, String other) {
            Log.d(TAG, "new Peer: " + other);
            this.pc = client.createPeerConnection(this, mListener.getVideoRenderer());
            this.other = other;

            pc.addStream(client.getLocalMS()); //, new MediaConstraints()
            mListener.onStatusChanged("CONNECTING");
        }
    }

    private Peer addPeer(String other) {
        if (client == null) {
            throw new RuntimeException("Fata Exception for here");
        }
        Peer peer = new Peer(client, other);
        peers.put(other, peer);

        return peer;
    }

    private void removePeer(String other) {
        Peer peer = peers.get(other);
        mListener.onRemoveRemoteStream();
        peer.pc.dispose();
        peers.remove(other);
    }

    private class CreateOfferCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateOfferCommand");
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, pcConstraints);
        }
    }

    private class CreateAnswerCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateAnswerCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, pcConstraints);
        }
    }

    private class SetRemoteSDPCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "SetRemoteSDPCommand");
            Peer peer = peers.get(peerId);

            String remoteSdp = payload.getString("sdp");
            remoteSdp = preferCodec(remoteSdp, preferredAudioCodec, true);
            remoteSdp = preferCodec(remoteSdp, preferredVideoCodec, false);
            //remoteSdp = setStartBitrate(AUDIO_CODEC_OPUS, false, remoteSdp, 1);

            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    remoteSdp
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }

    private class AddIceCandidateCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "AddIceCandidateCommand");
            PeerConnection pc = peers.get(peerId).pc;
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString("id"),
                        payload.getInt("label"),
                        payload.getString("candidate")
                );
                pc.addIceCandidate(candidate);
            }
        }
    }

    /**
     * Implement this interface to be notified of events.
     */
    interface RtcListener {

        void onStatusChanged(String newStatus);

        VideoRenderer.Callbacks getVideoRenderer();

        void onAddRemoteStream(MediaStream remoteStream);

        void onRemoveRemoteStream();

        void onDisConnected();
    }

    MediaConstraints getPcConstraints() {
        return pcConstraints;
    }

    LinkedList<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    WebRtcClient getClient() {
        return client;
    }

}